import AxeBuilder from '@axe-core/playwright';
import { expect, Page } from '@playwright/test';

/**
 * Analyse axe de la page courante, règles WCAG 2.1 A et AA (dont le contraste du texte : 4,5:1,
 * 3:1 pour les grands textes). Échoue en listant chaque violation, son niveau d'impact et les
 * éléments en cause, pour corriger sans relancer l'outil à la main.
 */
export async function verifierAccessibilite(
  page: Page,
  nomPage: string,
): Promise<void> {
  // Polices chargées : le rendu analysé est le rendu final.
  await page.evaluate(() => document.fonts.ready);
  // Animations finies (ouverture d'un dialogue : fondu) : sinon axe mesure des couleurs à
  // moitié transparentes et signale de faux défauts de contraste.
  await page.evaluate(() =>
    Promise.all(
      document
        .getAnimations()
        .filter((a) => a.effect?.getComputedTiming().iterations !== Infinity)
        .map((a) => a.finished.catch(() => null)),
    ),
  );
  const resultat = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();
  const violations = resultat.violations.map(
    (v) =>
      `${v.id} (${v.impact}) : ${v.help}\n` +
      v.nodes
        .map((n) => `    ${n.target.join(' ')} — ${n.failureSummary ?? ''}`)
        .join('\n'),
  );
  expect(violations, `Accessibilité de « ${nomPage} »`).toEqual([]);
  // Le contraste fait partie des règles vérifiées (et n'est pas désactivé par erreur).
  expect(
    [...resultat.passes, ...resultat.violations, ...resultat.incomplete].some(
      (r) => r.id === 'color-contrast',
    ),
  ).toBe(true);
}
