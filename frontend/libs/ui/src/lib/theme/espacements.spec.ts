// @vitest-environment node
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { compileString, compile } from 'sass';
import { describe, expect, it } from 'vitest';

const ICI = dirname(fileURLToPath(import.meta.url));

/** CSS réellement produit par les jetons (bureau, puis surcharges téléphone). */
const css = compileString('@use "jetons"; @include jetons.variables();', {
  loadPaths: [ICI],
}).css;

function variables(bloc: string): Record<string, string> {
  return Object.fromEntries(
    [...bloc.matchAll(/(--ecole-espace-[a-z0-9]+):\s*([^;]+);/g)].map(
      ([, nom, valeur]) => [nom, valeur.trim()],
    ),
  );
}

const [racine, telephoneBloc = ''] = css.split('@media');
const bureau = variables(racine);
const telephone = { ...bureau, ...variables(telephoneBloc) };

/** Valeur finale en px d'un jeton (suit les var(--ecole-espace-n)). */
function px(jetons: Record<string, string>, nom: string): number {
  const valeur = jetons[nom];
  const ref = valeur.match(/^var\((--ecole-espace-[a-z0-9]+)\)$/);
  return ref ? px(jetons, ref[1]) : Number.parseFloat(valeur);
}

const ECHELLE = [4, 8, 12, 16, 24, 32, 48, 64];

describe('jetons d’espacement', () => {
  it('définit l’échelle --ecole-espace-1 à 8', () => {
    ECHELLE.forEach((valeur, i) => {
      expect(bureau[`--ecole-espace-${i + 1}`]).toBe(`${valeur}px`);
    });
    expect(bureau['--ecole-espace-9']).toBeUndefined();
  });

  it.each([
    // jeton, bureau, téléphone
    ['libelle', 8, 8],
    ['champs', 20, 16],
    ['groupes', 32, 24],
    ['carte', 24, 16],
    ['sections', 24, 16],
    ['page', 32, 16],
    ['boutons', 12, 12],
  ])('--ecole-espace-%s : %i px, %i px sur téléphone', (nom, b, t) => {
    expect(px(bureau, `--ecole-espace-${nom}`)).toBe(b);
    expect(px(telephone, `--ecole-espace-${nom}`)).toBe(t);
  });

  it('n’a qu’une valeur hors échelle : --ecole-espace-champs à 20 px', () => {
    const horsEchelle = Object.keys(bureau).filter(
      (nom) =>
        !/^--ecole-espace-\d$/.test(nom) && !bureau[nom].startsWith('var('),
    );
    expect(horsEchelle).toEqual(['--ecole-espace-champs']);
    // Sur téléphone, tout repasse par l'échelle.
    expect(variables(telephoneBloc)['--ecole-espace-champs']).toBe(
      'var(--ecole-espace-4)',
    );
  });

  it('bascule au point de rupture téléphone des composants', () => {
    expect(css).toContain('@media (max-width: 599.98px)');
  });
});

describe('mise en page des formulaires (ecran.scss)', () => {
  const ecran = compile(join(ICI, '..', 'styles', 'ecran.scss')).css;

  it('limite un formulaire à 720 px, sur une colonne', () => {
    const bloc = ecran.match(/\.formulaire \{([^}]*)\}/)?.[1] ?? '';
    expect(bloc).toContain('max-width: 720px');
    expect(bloc).toContain('grid-template-columns: minmax(0, 1fr)');
    expect(bloc).toContain('gap: var(--ecole-espace-champs)');
  });

  it('passe à deux colonnes à partir de 900 px', () => {
    expect(ecran).toMatch(
      /@media \(min-width: 900px\) \{\s*\.formulaire \{\s*grid-template-columns: repeat\(2, minmax\(0, 1fr\)\);/,
    );
  });

  it('espace deux groupes de champs de --ecole-espace-groupes', () => {
    expect(ecran).toContain(
      'margin-top: calc(var(--ecole-espace-groupes) - var(--ecole-espace-champs))',
    );
  });
});
