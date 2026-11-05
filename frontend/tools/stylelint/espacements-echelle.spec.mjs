import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import stylelint from 'stylelint';
import { describe, expect, it } from 'vitest';
import { NOM, valeurAutorisee } from './espacements-echelle.mjs';

const ICI = dirname(fileURLToPath(import.meta.url));
const RACINE = join(ICI, '..', '..');

/** Lance la configuration réelle du dépôt sur un extrait SCSS. */
async function erreurs(code, fichier = 'libs/x/src/lib/x.scss') {
  const { results } = await stylelint.lint({
    code,
    codeFilename: join(RACINE, fichier),
    configFile: join(RACINE, 'stylelint.config.mjs'),
  });
  return results[0].warnings.filter((w) => w.rule === NOM).map((w) => w.text);
}

describe('ecole/espacements-echelle', () => {
  it.each([
    '0',
    'auto',
    '0 auto',
    'var(--ecole-espace-1)',
    'var(--ecole-espace-8)',
    'var(--ecole-espace-champs) var(--ecole-espace-boutons)',
    'var(--ecole-espace-2) 0 var(--ecole-espace-4)',
    'calc(-1 * var(--ecole-espace-2))',
    'calc(var(--ecole-espace-2) + env(safe-area-inset-bottom))',
    'max(var(--ecole-espace-4), env(safe-area-inset-left))',
    'inherit',
  ])('accepte « %s »', (valeur) => {
    expect(valeurAutorisee(valeur)).toBe(true);
  });

  it.each([
    '12px',
    '20px',
    '1rem',
    '0 16px',
    '10%',
    'var(--ecole-espace-9)',
    'var(--autre)',
    'var(--ecole-espace-2, 8px)',
    'calc(8px + var(--ecole-espace-2))',
    'calc(2 * 4px)',
    '$espace',
    '#{$x}',
    'env(titlebar-area-x)',
  ])('refuse « %s »', (valeur) => {
    expect(valeurAutorisee(valeur)).toBe(false);
  });

  it('contrôle toutes les propriétés d’espacement', async () => {
    const texte = await erreurs(`
      .a {
        margin: 8px;
        padding-inline: 4px;
        margin-block-start: 2px;
        gap: 12px;
        row-gap: 1rem;
        column-gap: 3px;
        scroll-padding-top: 10px;
        width: 13px;
        top: 7px;
      }
    `);
    expect(texte).toHaveLength(7);
  });

  it('accepte les jetons et ignore les autres propriétés', async () => {
    expect(
      await erreurs(`
        .a {
          margin: 0 auto var(--ecole-espace-5);
          gap: var(--ecole-espace-champs) var(--ecole-espace-4);
          border-width: 3px;
          @media (max-width: 599.98px) {
            padding: var(--ecole-espace-page);
          }
        }
      `),
    ).toEqual([]);
  });

  it('ne laisse définir les jetons que dans _jetons.scss', async () => {
    const code = ':root { --ecole-espace-champs: 20px; margin: 20px; }';
    expect(await erreurs(code)).toHaveLength(2);
    expect(await erreurs(code, 'libs/ui/src/lib/theme/_jetons.scss')).toEqual(
      [],
    );
  });

  it('nomme le jeton à utiliser dans le message', async () => {
    const [message] = await erreurs('.a { padding: 20px; }');
    expect(message).toContain('padding: 20px');
    expect(message).toContain('var(--ecole-espace-1…8)');
  });
});
