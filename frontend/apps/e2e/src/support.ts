import { expect, Page, test } from '@playwright/test';

/**
 * Prérequis : `docker compose -f infra/docker-compose.yml up -d` puis le backend en profil dev
 * (`./mvnw spring-boot:run`) sur le port 8080. Comptes fictifs du realm de dev (infra/keycloak) :
 * le mot de passe vient de E2E_MOT_DE_PASSE (par défaut, celui, public, du realm de dev).
 */
const MOT_DE_PASSE = process.env['E2E_MOT_DE_PASSE'] ?? 'Passer-2026-dev';
const KEYCLOAK = process.env['E2E_KEYCLOAK'] ?? 'http://localhost:8180';
const API = process.env['E2E_API'] ?? 'http://localhost:8080';

/** Échoue tout de suite, avec un message clair, si l'infra n'est pas démarrée. */
export function exigerInfra(): void {
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
}

export async function seConnecter(
  page: Page,
  utilisateur: string,
): Promise<void> {
  await page.goto('/');
  // Redirection vers Keycloak (code + PKCE).
  await page.waitForURL(/\/realms\/ecole\/protocol\/openid-connect\/auth/);
  await page.locator('#username').fill(utilisateur);
  await page.locator('#password').fill(MOT_DE_PASSE);
  await page.locator('#kc-login').click();
}

export async function choisirEcole(page: Page, nom: string): Promise<void> {
  await expect(
    page.getByRole('heading', { name: 'Choisissez votre école' }),
  ).toBeVisible();
  await page.getByRole('button', { name: nom }).click();
}

export async function ouvrirMenuUtilisateur(
  page: Page,
  utilisateur: string,
): Promise<void> {
  // Coquille Material : bouton ; coquille légère : <summary>. Dans les deux cas, le nom visible.
  await page.getByText(utilisateur, { exact: true }).first().click();
}

/** « Changer d'école » depuis le menu (menuitem Material ou bouton de la coquille légère). */
export async function changerEcole(
  page: Page,
  utilisateur: string,
): Promise<void> {
  await ouvrirMenuUtilisateur(page, utilisateur);
  await page.getByText("Changer d'école", { exact: true }).click();
}

export async function seDeconnecter(
  page: Page,
  utilisateur: string,
): Promise<void> {
  await ouvrirMenuUtilisateur(page, utilisateur);
  await page.getByText('Se déconnecter', { exact: true }).click();
}

export async function verifierProfil(
  page: Page,
  ecole: string,
  roles: string[],
): Promise<void> {
  await expect(page.getByTestId('moi-ecole')).toHaveText(ecole);
  await expect(page.getByTestId('moi-roles').locator('li')).toHaveText(roles);
}

export async function verifierAccesRefuse(page: Page): Promise<void> {
  await expect(
    page.getByRole('heading', { name: 'Accès refusé' }),
  ).toBeVisible();
}
