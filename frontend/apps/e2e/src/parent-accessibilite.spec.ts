import { expect, test } from '@playwright/test';
import { verifierAccessibilite } from './accessibilite';
import { exigerInfra, seConnecter, verifierProfil } from './support';

// Accessibilité de l'espace parent (axe, WCAG 2.1 AA, dont les contrastes), sur téléphone : c'est
// son usage principal. Une seule entrée de menu : la barre du bas n'apparaît pas.
exigerInfra();

test.use({ viewport: { width: 390, height: 844 } });

test("l'espace parent respecte WCAG 2.1 AA sur téléphone", async ({ page }) => {
  await seConnecter(page, 'eleve.b');
  await verifierProfil(page, 'École Exemple B', ['Élève']);
  await expect(page.locator('nav.barre-basse')).toHaveCount(0);
  await verifierAccessibilite(page, 'Accueil');
});
