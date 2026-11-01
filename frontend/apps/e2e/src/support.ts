import { APIRequestContext, expect, Page, test } from '@playwright/test';

/**
 * Prérequis : `docker compose -f infra/docker-compose.yml up -d` puis le backend en profil dev
 * (`./mvnw spring-boot:run`) sur le port 8080. Comptes fictifs du realm de dev (infra/keycloak) :
 * le mot de passe vient de E2E_MOT_DE_PASSE (par défaut, celui, public, du realm de dev).
 */
// `||` et non `??` : en CI, un secret non défini arrive sous forme de chaîne VIDE, pas absente.
const MOT_DE_PASSE = process.env['E2E_MOT_DE_PASSE'] || 'Passer-2026-dev';
const KEYCLOAK = process.env['E2E_KEYCLOAK'] || 'http://localhost:8180';
const API = process.env['E2E_API'] || 'http://localhost:8080';

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
  // Retour vers l'application, sinon échec explicite (mot de passe refusé…) plutôt qu'un
  // « élément introuvable » plus loin dans le scénario.
  await page
    .waitForURL((url) => !url.pathname.startsWith('/realms/'), {
      timeout: 15_000,
    })
    .catch(async () => {
      const message = await page
        .locator('#input-error, .kc-feedback-text, [class*="alert"]')
        .first()
        .textContent()
        .catch(() => null);
      throw new Error(
        `Connexion Keycloak refusée pour « ${utilisateur} » : ${message?.trim() ?? 'aucun message'}` +
          ' (vérifier E2E_MOT_DE_PASSE).',
      );
    });
}

export async function choisirEcole(page: Page, nom: string): Promise<void> {
  await expect(
    page.getByRole('heading', { name: 'Choisissez votre école' }),
  ).toBeVisible({ timeout: 20_000 });
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

/** Adresses des trois applications en développement. */
export const APPS = {
  backoffice: /localhost:4200/,
  'vie-scolaire': /localhost:4202/,
  parent: /localhost:4201/,
} as const;

/** L'utilisateur est dans l'application de son rôle, et n'a vu aucune page « Accès refusé ». */
export async function verifierDansApp(
  page: Page,
  app: keyof typeof APPS,
): Promise<void> {
  await expect(page).toHaveURL(APPS[app]);
  await expect(page.getByRole('heading', { name: 'Accès refusé' })).toHaveCount(
    0,
  );
}

/** Identifiant de l'école A de dev (voir backend/src/main/resources/db/dev). */
export const ECOLE_A_ID = '01930000-0000-7000-8000-0000000000a1';

export const URL_API = API;

const ADMIN_KC = process.env['E2E_KEYCLOAK_ADMIN'] || 'admin';
const ADMIN_KC_MOT_DE_PASSE =
  process.env['E2E_KEYCLOAK_ADMIN_MOT_DE_PASSE'] || 'admin-dev';

/**
 * Crée dans le realm de dev un compte tout neuf, sans aucune appartenance : celui d'un parent qui
 * n'a pas encore activé son compte. Un nom unique par exécution rend le scénario rejouable.
 */
export async function creerUtilisateurSansEcole(
  request: APIRequestContext,
  utilisateur: string,
): Promise<void> {
  const jeton = await request.post(
    `${KEYCLOAK}/realms/master/protocol/openid-connect/token`,
    {
      form: {
        grant_type: 'password',
        client_id: 'admin-cli',
        username: ADMIN_KC,
        password: ADMIN_KC_MOT_DE_PASSE,
      },
    },
  );
  expect(jeton.ok(), 'Jeton administrateur Keycloak (dev)').toBe(true);
  const { access_token: acces } = (await jeton.json()) as {
    access_token: string;
  };
  const creation = await request.post(`${KEYCLOAK}/admin/realms/ecole/users`, {
    headers: { Authorization: `Bearer ${acces}` },
    data: {
      username: utilisateur,
      email: `${utilisateur}@exemple.test`,
      firstName: 'Parent',
      lastName: 'Fictif E2E',
      enabled: true,
      emailVerified: true,
      credentials: [
        { type: 'password', value: MOT_DE_PASSE, temporary: false },
      ],
    },
  });
  expect(creation.status(), 'Création du compte parent fictif').toBe(201);
}

/** Lien du menu principal (et non un raccourci de l'accueil qui porte le même intitulé). */
export function lienMenu(page: Page, nom: string, exact = true) {
  return page
    .getByRole('navigation', { name: 'Navigation principale' })
    .getByRole('link', { name: nom, exact });
}
