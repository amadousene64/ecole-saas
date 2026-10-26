import { expect, Page, test } from '@playwright/test';

/**
 * Parcours réels : Keycloak (realm « ecole », utilisateurs fictifs de infra/keycloak) et API en
 * profil dev (données de db/dev). Prérequis : `docker compose -f infra/docker-compose.yml up -d`
 * puis le backend (`./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`) sur le port 8080.
 *
 * Le mot de passe des comptes fictifs de dev vient de l'environnement (E2E_MOT_DE_PASSE) ; la
 * valeur par défaut est celle, publique, du realm de dev.
 */
const MOT_DE_PASSE = process.env['E2E_MOT_DE_PASSE'] ?? 'Passer-2026-dev';
const KEYCLOAK = process.env['E2E_KEYCLOAK'] ?? 'http://localhost:8180';
const API = process.env['E2E_API'] ?? 'http://localhost:8080';

test.beforeAll(async ({ request }) => {
  for (const [nom, url] of [
    ['Keycloak', `${KEYCLOAK}/realms/ecole`],
    ['API', `${API}/actuator/health`],
  ]) {
    const reponse = await request.get(url).catch(() => null);
    if (!reponse?.ok()) {
      throw new Error(
        `${nom} injoignable (${url}) : démarrez l'infra et le backend en profil dev.`,
      );
    }
  }
});

async function seConnecter(page: Page, utilisateur: string): Promise<void> {
  await page.goto('/');
  // Redirection vers Keycloak (code + PKCE).
  await page.waitForURL(/\/realms\/ecole\/protocol\/openid-connect\/auth/);
  await page.locator('#username').fill(utilisateur);
  await page.locator('#password').fill(MOT_DE_PASSE);
  await page.locator('#kc-login').click();
}

test('une seule école : connexion directe et affichage de /moi', async ({
  page,
}) => {
  await seConnecter(page, 'directeur.a');

  await expect(page.getByTestId('moi-ecole')).toHaveText('Lycée Exemple A');
  await expect(page.getByTestId('moi-utilisateur')).toHaveText('directeur.a');
  await expect(page.getByTestId('moi-roles').locator('li')).toHaveText([
    'Directeur',
  ]);
  await expect(page).toHaveURL('/');
});

test('plusieurs écoles : choix, rôles propres à chaque école, changement', async ({
  page,
}) => {
  await seConnecter(page, 'enseignant.parent');

  // Deux écoles : le choix est demandé.
  await expect(
    page.getByRole('heading', { name: 'Choisissez votre école' }),
  ).toBeVisible();
  await page.getByRole('button', { name: 'Lycée Exemple A' }).click();

  // Dans A il est enseignant : le back-office lui est ouvert.
  await expect(page.getByTestId('moi-ecole')).toHaveText('Lycée Exemple A');
  await expect(page.getByTestId('moi-roles').locator('li')).toHaveText([
    'Enseignant',
  ]);

  // Dans B il n'est que parent : le back-office lui est fermé.
  await page.getByRole('button', { name: 'enseignant.parent' }).click();
  await page.getByRole('menuitem', { name: "Changer d'école" }).click();
  await page.getByRole('button', { name: 'École Exemple B' }).click();
  await expect(
    page.getByRole('heading', { name: 'Accès refusé' }),
  ).toBeVisible();

  // Retour dans A : l'accès revient.
  await page.getByRole('button', { name: 'enseignant.parent' }).click();
  await page.getByRole('menuitem', { name: "Changer d'école" }).click();
  await page.getByRole('button', { name: 'Lycée Exemple A' }).click();
  await expect(page.getByTestId('moi-roles').locator('li')).toHaveText([
    'Enseignant',
  ]);
});

test('un utilisateur sans rôle autorisé dans sa seule école voit « Accès refusé »', async ({
  page,
}) => {
  // surveillant.b n'est que surveillant dans B : pas de rôle du back-office.
  await seConnecter(page, 'surveillant.b');
  await expect(
    page.getByRole('heading', { name: 'Accès refusé' }),
  ).toBeVisible();
});

test('la déconnexion renvoie vers la connexion', async ({ page }) => {
  await seConnecter(page, 'directeur.a');
  await expect(page.getByTestId('moi-ecole')).toBeVisible();

  await page.getByRole('button', { name: 'directeur.a' }).click();
  await page.getByRole('menuitem', { name: 'Se déconnecter' }).click();
  await page.waitForURL(
    /\/realms\/ecole\/protocol\/openid-connect\/(auth|logout)|localhost:4200/,
  );

  await page.goto('/');
  await expect(page.locator('#username')).toBeVisible();
});
