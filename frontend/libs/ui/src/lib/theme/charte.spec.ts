import { readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

/**
 * Garde-fous de la charte dans les gabarits de tout le frontend. Les champs Material sont réglés
 * gabarit par gabarit (appearance="outline") plutôt que par un fournisseur global : le jeton de
 * configuration ferait entrer le code des champs dans le premier chargement (≈ 116 Ko).
 */
const RACINE = join(dirname(fileURLToPath(import.meta.url)), '../../../../..');

function sources(dossier: string): string[] {
  return readdirSync(dossier).flatMap((nom) => {
    const chemin = join(dossier, nom);
    if (statSync(chemin).isDirectory()) {
      return nom === 'node_modules' ? [] : sources(chemin);
    }
    return /\.(ts|html)$/.test(nom) && !nom.endsWith('.spec.ts')
      ? [chemin]
      : [];
  });
}

const FICHIERS = ['libs', 'apps'].flatMap((d) => sources(join(RACINE, d)));

describe('charte dans les gabarits', () => {
  it('trouve les sources du frontend', () => {
    expect(FICHIERS.length).toBeGreaterThan(20);
  });

  it('chaque champ Material est bordé (appearance="outline")', () => {
    const fautifs = FICHIERS.filter((f) =>
      /<mat-form-field(?![^>]*appearance="outline")[\s>]/.test(
        readFileSync(f, 'utf8'),
      ),
    ).map((f) => relative(RACINE, f));
    expect(fautifs).toEqual([]);
  });

  it('chaque carte Material est bordée, sans ombre (appearance="outlined")', () => {
    const fautifs = FICHIERS.filter((f) =>
      /<mat-card(?![^>]*appearance="outlined")[\s>]/.test(
        readFileSync(f, 'utf8'),
      ),
    ).map((f) => relative(RACINE, f));
    expect(fautifs).toEqual([]);
  });
});
