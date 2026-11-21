import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { analyser, verifierDepot } from './champs-formulaire.mjs';

const RACINE = join(dirname(fileURLToPath(import.meta.url)), '..', '..');

describe('CHAMPS_FORMULAIRE sur les pages à formulaires (dépôt réel)', () => {
  const { violations, composants, racines } = verifierDepot(RACINE);

  it('chaque champ Material est rendu sous CHAMPS_FORMULAIRE', () => {
    expect(violations).toEqual([]);
  });

  it('analyse réellement les écrans (garde-fou contre un analyseur aveugle)', () => {
    const aChamps = [...composants.values()].filter((c) => c.champs);
    expect(aChamps.length).toBeGreaterThanOrEqual(10);
    expect(racines).toContain('PageNouvelEleve');
    expect(racines).toContain('PageFiche');
    // La fiche n'a pas de champ elle-même : ce sont ses onglets, atteints par ses imports.
    const parNom = (nom) => [...composants.values()].find((c) => c.nom === nom);
    expect(parNom('OngletIdentite')?.champs).toBe(true);
    expect(parNom('PageFiche')?.fournit).toBe(true);
  });
});

/** Petits dépôts fictifs pour vérifier les règles de l'analyseur. */
function cas(sources) {
  return analyser(
    Object.entries(sources).map(([chemin, contenu]) => ({ chemin, contenu })),
    () => '',
  ).violations;
}

const ONGLET = `
  @Component({ selector: 'ecole-onglet', template: '<mat-form-field></mat-form-field>' })
  export class Onglet {}`;

describe('analyseur CHAMPS_FORMULAIRE', () => {
  it('refuse une page routée à champs sans le jeton', () => {
    const v = cas({
      'routes.ts': `export const R = [{ path: '', loadComponent: () => import('./p').then((m) => m.Page) }];`,
      'p.ts': `@Component({ selector: 'p', template: '<mat-form-field></mat-form-field>' }) export class Page {}`,
    });
    expect(v).toHaveLength(1);
    expect(v[0]).toContain('Page affiche des champs');
  });

  it('accepte une page qui fournit le jeton à ses sections', () => {
    expect(
      cas({
        'routes.ts': `import { Page } from './p'; export const R = [{ path: '', component: Page }];`,
        'p.ts': `import { Onglet } from './o'; @Component({ selector: 'p', providers: [CHAMPS_FORMULAIRE], imports: [Onglet], template: '' }) export class Page {}`,
        'o.ts': ONGLET,
      }),
    ).toEqual([]);
  });

  it('refuse un onglet à champs importé par une page sans le jeton', () => {
    const v = cas({
      'routes.ts': `import { Page } from './p'; export const R = [{ path: '', component: Page }];`,
      'p.ts': `import { Onglet } from './o'; @Component({ selector: 'p', imports: [Onglet], template: '' }) export class Page {}`,
      'o.ts': ONGLET,
    });
    expect(v).toHaveLength(1);
    expect(v[0]).toContain('Page → Onglet');
  });

  it('traite un dialogue comme une racine (injecteur à part)', () => {
    const v = cas({
      'p.ts': `import { Dialogue } from './d';
        @Component({ selector: 'p', providers: [CHAMPS_FORMULAIRE], template: '' })
        export class Page { ouvrir() { this.dialogue.open(Dialogue, {}); } }`,
      'd.ts': `@Component({ selector: 'd', template: '<mat-form-field></mat-form-field>' }) export class Dialogue {}`,
      'routes.ts': `import { Page } from './p'; export const R = [{ path: '', component: Page }];`,
    });
    expect(v).toHaveLength(1);
    expect(v[0]).toContain('Dialogue');
  });

  it('refuse un composant à champs atteint par aucune racine, sauf s’il fournit le jeton', () => {
    expect(cas({ 'o.ts': ONGLET })).toHaveLength(1);
    expect(
      cas({
        'o.ts': ONGLET.replace(
          "selector: 'ecole-onglet',",
          "selector: 'ecole-onglet', providers: [CHAMPS_FORMULAIRE],",
        ),
      }),
    ).toEqual([]);
  });

  it('distingue deux composants de même nom dans deux apps', () => {
    const v = cas({
      'apps/a/routes.ts': `import { Page } from './page'; export const R = [{ path: '', component: Page }];`,
      'apps/a/page.ts': `@Component({ selector: 'a', providers: [CHAMPS_FORMULAIRE], template: '<mat-form-field></mat-form-field>' }) export class Page {}`,
      'apps/b/routes.ts': `import { Page } from './page'; export const R = [{ path: '', component: Page }];`,
      'apps/b/page.ts': `@Component({ selector: 'b', template: '<mat-form-field></mat-form-field>' }) export class Page {}`,
    });
    expect(v).toHaveLength(1);
    expect(v[0]).toContain('apps/b/page.ts');
  });

  it('suit un import d’une lib par son alias @ecole/…', () => {
    const v = cas({
      'apps/a/routes.ts': `import { Page } from './page'; export const R = [{ path: '', component: Page }];`,
      'apps/a/page.ts': `import { Champ } from '@ecole/ui'; @Component({ selector: 'a', imports: [Champ], template: '' }) export class Page {}`,
      'libs/ui/src/lib/champ.ts': `@Component({ selector: 'c', template: '<mat-form-field></mat-form-field>' }) export class Champ {}`,
    });
    expect(v).toHaveLength(1);
    expect(v[0]).toContain('Page → Champ');
  });

  it('refuse le jeton dans app.config', () => {
    const v = cas({
      'apps/x/src/app/app.config.ts': `export const appConfig = { providers: [CHAMPS_FORMULAIRE] };`,
    });
    expect(v.some((m) => m.includes('app.config'))).toBe(true);
  });
});
