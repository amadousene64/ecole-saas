import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

/** Couleurs lues dans _jetons.scss : la source unique, pas une copie. */
function couleurs(): Record<string, string> {
  const scss = readFileSync(
    join(dirname(fileURLToPath(import.meta.url)), '_jetons.scss'),
    'utf8',
  );
  const resultat: Record<string, string> = {};
  for (const [, nom, hex] of scss.matchAll(
    /'([a-z-]+)':\s*(#[0-9a-fA-F]{6})/g,
  )) {
    resultat[nom] = hex.toLowerCase();
  }
  return resultat;
}

/** Luminance relative WCAG 2.x. */
function luminance(hex: string): number {
  const canaux = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255);
  const [r, g, b] = canaux.map((c) =>
    c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4,
  );
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

export function contraste(a: string, b: string): number {
  const [clair, fonce] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (clair + 0.05) / (fonce + 0.05);
}

const c = couleurs();

/** Couples texte / fond réellement utilisés par le thème et les composants. */
const COUPLES: [texte: string, fond: string][] = [
  ['texte', 'fond'],
  ['texte', 'surface'],
  ['texte-secondaire', 'fond'],
  ['texte-secondaire', 'surface'],
  ['primaire', 'surface'],
  ['primaire', 'fond'],
  ['primaire', 'primaire-doux'],
  ['primaire-survol', 'primaire-doux'],
  ['sur-primaire', 'primaire'],
  ['sur-primaire', 'primaire-survol'],
  ['safran', 'safran-fond'],
  ['bissap', 'bissap-fond'],
  ['fleuve', 'fleuve-fond'],
  ['texte', 'primaire-doux'],
];

/**
 * Contours de composants (non-texte) : 3:1 minimum (WCAG 1.4.11). Champs et boutons secondaires
 * posés sur une surface ou le fond de l'app, et bouton secondaire au survol.
 */
const CONTOURS: [contour: string, fond: string][] = [
  ['bordure-champ', 'surface'],
  ['bordure-champ', 'fond'],
  ['bordure-champ', 'primaire-doux'],
];

describe('jetons de couleur', () => {
  it('définit la charte complète', () => {
    expect(c).toMatchObject({
      primaire: '#1f6b4f',
      'primaire-survol': '#16503b',
      'primaire-doux': '#e4f0ea',
      texte: '#16201c',
      'texte-secondaire': '#56635d',
      fond: '#f5f6f3',
      bordure: '#dde3df',
      'bordure-champ': '#7e8a84',
      surface: '#ffffff',
      safran: '#8a4b05',
      'safran-fond': '#fbefd9',
      bissap: '#b42318',
      'bissap-fond': '#fdecea',
      fleuve: '#1d5fa8',
      'fleuve-fond': '#e7eef8',
    });
  });

  it.each(COUPLES)(
    '%s sur %s atteint 4,5:1 (WCAG AA, texte normal)',
    (texte, fond) => {
      expect(c[texte], texte).toBeDefined();
      expect(c[fond], fond).toBeDefined();
      expect(contraste(c[texte], c[fond])).toBeGreaterThanOrEqual(4.5);
    },
  );

  it.each(CONTOURS)(
    'contour %s sur %s atteint 3:1 (WCAG 1.4.11, non-texte)',
    (contour, fond) => {
      expect(c[contour], contour).toBeDefined();
      expect(c[fond], fond).toBeDefined();
      expect(contraste(c[contour], c[fond])).toBeGreaterThanOrEqual(3);
    },
  );

  it('calcule le contraste comme la norme (noir sur blanc : 21:1)', () => {
    expect(contraste('#000000', '#ffffff')).toBeCloseTo(21, 5);
    expect(contraste('#ffffff', '#ffffff')).toBeCloseTo(1, 5);
  });
});
