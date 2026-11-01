import { expect, test } from '@playwright/test';
import {
  exigerInfra,
  seConnecter,
  seDeconnecter,
  verifierDansApp,
  verifierProfil,
} from './support';

// Back-office : direction, secrétariat, censeur, comptable. Le détail rôle par rôle (menus,
// renvois vers les autres applications) est dans backoffice-roles.spec.ts.
exigerInfra();

test('une seule école : connexion directe et affichage de /moi', async ({
  page,
}) => {
  await seConnecter(page, 'directeur.a');

  await verifierProfil(page, 'Lycée Exemple A', ['Directeur']);
  await expect(page.getByTestId('moi-utilisateur')).toHaveText('directeur.a');
  await expect(page).toHaveURL('/');
  await verifierDansApp(page, 'backoffice');
});

test('le censeur a accès au back-office', async ({ page }) => {
  await seConnecter(page, 'censeur.a');
  await verifierProfil(page, 'Lycée Exemple A', ['Censeur']);
});

test('la déconnexion renvoie vers la connexion', async ({ page }) => {
  await seConnecter(page, 'directeur.a');
  await expect(page.getByTestId('moi-ecole')).toBeVisible();

  await seDeconnecter(page, 'directeur.a');
  // La session Keycloak est fermée : on retombe sur la page de connexion.
  await expect(page.locator('#username')).toBeVisible({ timeout: 20_000 });

  // Rouvrir l'application ne reconnecte pas. Elle renvoie aussitôt vers Keycloak : la navigation
  // initiale peut être interrompue.
  await page.goto('/').catch(() => undefined);
  await expect(page.locator('#username')).toBeVisible({ timeout: 20_000 });
});
