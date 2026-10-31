import { test } from '@playwright/test';
import { verifierAccessibilite } from './accessibilite';
import { exigerInfra, seConnecter, verifierProfil } from './support';

// Accessibilité du poste vie scolaire (axe, WCAG 2.1 AA, dont les contrastes).
exigerInfra();

test('le poste vie scolaire respecte WCAG 2.1 AA', async ({ page }) => {
  await seConnecter(page, 'surveillant.b');
  await verifierProfil(page, 'École Exemple B', ['Surveillant']);
  await verifierAccessibilite(page, 'Accueil');
});
