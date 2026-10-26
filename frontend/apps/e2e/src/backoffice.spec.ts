import { expect, test } from '@playwright/test';
import {
  changerEcole,
  choisirEcole,
  exigerInfra,
  seConnecter,
  seDeconnecter,
  verifierAccesRefuse,
  verifierProfil,
} from './support';

// Back-office : direction, censeur, secrétariat, enseignants, comptabilité.
exigerInfra();

test('une seule école : connexion directe et affichage de /moi', async ({
  page,
}) => {
  await seConnecter(page, 'directeur.a');

  await verifierProfil(page, 'Lycée Exemple A', ['Directeur']);
  await expect(page.getByTestId('moi-utilisateur')).toHaveText('directeur.a');
  await expect(page).toHaveURL('/');
});

test('le censeur a accès au back-office', async ({ page }) => {
  await seConnecter(page, 'censeur.a');
  await verifierProfil(page, 'Lycée Exemple A', ['Censeur']);
});

test('plusieurs écoles : choix, rôles propres à chaque école, changement', async ({
  page,
}) => {
  await seConnecter(page, 'enseignant.parent');
  await choisirEcole(page, 'Lycée Exemple A');

  // Dans A il est enseignant : le back-office lui est ouvert.
  await verifierProfil(page, 'Lycée Exemple A', ['Enseignant']);

  // Dans B il n'est que parent : le back-office lui est fermé.
  await changerEcole(page, 'enseignant.parent');
  await choisirEcole(page, 'École Exemple B');
  await verifierAccesRefuse(page);

  // Retour dans A : l'accès revient.
  await changerEcole(page, 'enseignant.parent');
  await choisirEcole(page, 'Lycée Exemple A');
  await verifierProfil(page, 'Lycée Exemple A', ['Enseignant']);
});

test('un surveillant n’a pas accès au back-office', async ({ page }) => {
  await seConnecter(page, 'surveillant.b');
  await verifierAccesRefuse(page);
});

test('la déconnexion renvoie vers la connexion', async ({ page }) => {
  await seConnecter(page, 'directeur.a');
  await expect(page.getByTestId('moi-ecole')).toBeVisible();

  await seDeconnecter(page, 'directeur.a');
  await page.waitForURL(
    /\/realms\/ecole\/protocol\/openid-connect\/(auth|logout)|localhost:4200/,
  );

  await page.goto('/');
  await expect(page.locator('#username')).toBeVisible();
});
