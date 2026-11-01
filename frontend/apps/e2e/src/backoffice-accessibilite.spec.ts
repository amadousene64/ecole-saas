import { expect, test } from '@playwright/test';
import { verifierAccessibilite } from './accessibilite';
import { exigerInfra, seConnecter, verifierProfil, lienMenu } from './support';

// Accessibilité du back-office (axe, WCAG 2.1 AA, dont les contrastes) : coquille et écrans du
// module établissement, sur ordinateur et sur téléphone.
exigerInfra();

const PAGES = [
  'Établissement',
  'Structure pédagogique',
  'Années et périodes',
  'Classes et salles',
];

test('les écrans du back-office respectent WCAG 2.1 AA', async ({ page }) => {
  await seConnecter(page, 'directeur.a');
  await verifierProfil(page, 'Lycée Exemple A', ['Directeur']);
  await verifierAccessibilite(page, 'Accueil');

  for (const lien of PAGES) {
    await lienMenu(page, lien).click();
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await verifierAccessibilite(page, lien);
  }
});

test('le back-office reste accessible sur petit écran', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await seConnecter(page, 'directeur.a');
  await verifierProfil(page, 'Lycée Exemple A', ['Directeur']);
  await verifierAccessibilite(page, 'Accueil (mobile)');
});
