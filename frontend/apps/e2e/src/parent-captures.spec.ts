import { expect as expectBase, test } from '@playwright/test';
import { mkdirSync } from 'node:fs';
import { verifierAccessibilite } from './accessibilite';
import { exigerInfra, seConnecter } from './support';

/**
 * Espace parent aux trois largeurs de référence : un parent de démo avec deux enfants. Une seule
 * entrée de menu, donc pas de barre du bas ; aucun défilement horizontal ; axe sans violation.
 */
exigerInfra();

const expect = expectBase.configure({ timeout: 15_000 });
const DOSSIER = 'dist/.playwright/captures';

for (const [nom, largeur, hauteur] of [
  ['390', 390, 844],
  ['768', 768, 1024],
  ['1440', 1440, 900],
] as const) {
  test(`parent de démo à ${nom} px`, async ({ page }) => {
    await page.setViewportSize({ width: largeur, height: hauteur });
    await seConnecter(page, 'parent.demo');

    await expect(page.getByTestId('enfant')).toHaveCount(2);
    await expect(page.locator('nav.barre-basse')).toHaveCount(0);
    const debord = await page.evaluate(
      () =>
        document.documentElement.scrollWidth -
        document.documentElement.clientWidth,
    );
    expect(debord).toBeLessThanOrEqual(0);

    mkdirSync(DOSSIER, { recursive: true });
    await page.screenshot({
      path: `${DOSSIER}/parent-accueil-${nom}.png`,
      fullPage: true,
    });
    await verifierAccessibilite(page, `Espace parent (${nom} px)`);
  });
}
