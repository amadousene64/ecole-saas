import { expect as expectBase, Page, request, test } from '@playwright/test';
import { mkdirSync } from 'node:fs';
import { verifierAccessibilite } from './accessibilite';
import {
  creerUtilisateurSansEcole,
  exigerInfra,
  exigerMailpit,
  garantirLogoE2E,
  lienDuCourriel,
  URL_MARQUE,
} from './support';

/**
 * Pages de connexion Keycloak du personnel (thème « ecole ») : marque de l'école (nom, ville,
 * phrase, logo), deux colonnes puis bandeau sur téléphone, pas de création de compte, mot de passe
 * oublié (courriel reçu dans Mailpit), nouveau mot de passe, information, erreur. axe sur chaque
 * page, à 1440 et à 390 px.
 */
exigerInfra();

const expect = expectBase.configure({ timeout: 15_000 });
const DOSSIER = 'dist/.playwright/captures';
const MOT_DE_PASSE = process.env['E2E_MOT_DE_PASSE'] || 'Passer-2026-dev';

test.beforeAll(async () => {
  const api = await request.newContext();
  await exigerMailpit(api);
  await garantirLogoE2E(api);
  await api.dispose();
});

async function ouvrirConnexion(page: Page): Promise<void> {
  await page.goto('/');
  await page.waitForURL(/\/realms\/ecole\/protocol\/openid-connect\/auth/);
  await expect(page.locator('#username')).toBeVisible();
}

async function capturer(page: Page, nom: string): Promise<void> {
  mkdirSync(DOSSIER, { recursive: true });
  await page.screenshot({ path: `${DOSSIER}/${nom}.png`, fullPage: true });
}

test('la marque de l’école s’affiche : nom, ville, phrase et logo, sans création de compte', async ({
  page,
}) => {
  await ouvrirConnexion(page);

  const marque = page.locator('#ecole-marque');
  await expect(page.locator('#ecole-nom')).toHaveText('Lycée Exemple A');
  await expect(page.locator('#ecole-ville')).toHaveText('Dakar');
  await expect(page.locator('#ecole-phrase')).not.toBeEmpty();
  // Le logo est réellement chargé depuis l'API publique (pas une image cassée).
  const logo = page.locator('#ecole-logo');
  await expect(logo).toBeVisible();
  expect(
    await logo.evaluate((i: HTMLImageElement) => i.naturalWidth),
  ).toBeGreaterThan(0);
  expect(await logo.getAttribute('src')).toContain(`${URL_MARQUE}/logo`);
  await expect(marque).toHaveAttribute('aria-label', 'École');

  // Le personnel reçoit son compte de l'école : ni lien, ni encart de l'espace parent.
  await expect(
    page.getByRole('link', { name: /Créer mon compte/ }),
  ).toHaveCount(0);
  await expect(page.locator('#kc-registration')).toHaveCount(0);
  await expect(page.locator('#ecole-encart-parent')).toHaveCount(0);

  await capturer(page, 'keycloak-connexion-personnel-1440');
  await verifierAccessibilite(
    page,
    'Keycloak : connexion du personnel (1440 px)',
  );
});

test('sur téléphone la marque devient un bandeau et le formulaire reste en vue', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await ouvrirConnexion(page);
  await expect(page.locator('#ecole-nom')).toHaveText('Lycée Exemple A');

  const bandeau = await page.locator('#ecole-marque').boundingBox();
  expect(bandeau!.height, 'bandeau compact').toBeLessThan(120);
  // Le bouton de connexion se voit sans défiler.
  const bouton = await page.locator('#kc-login').boundingBox();
  expect(bouton!.y + bouton!.height).toBeLessThanOrEqual(844);
  // La phrase est masquée dans le bandeau (le formulaire passe d'abord).
  await expect(page.locator('#ecole-phrase')).toBeHidden();
  const debord = await page.evaluate(
    () =>
      document.documentElement.scrollWidth -
      document.documentElement.clientWidth,
  );
  expect(debord, 'défilement horizontal').toBeLessThanOrEqual(0);

  await capturer(page, 'keycloak-connexion-personnel-390');
  await verifierAccessibilite(
    page,
    'Keycloak : connexion du personnel (390 px)',
  );
});

test('sans la marque (API injoignable) la page reste utilisable avec le texte neutre', async ({
  page,
}) => {
  await page.route('**/api/v1/public/marque**', (route) => route.abort());
  await ouvrirConnexion(page);

  await expect(page.locator('#ecole-plateforme')).toHaveText(
    'Plateforme de gestion scolaire',
  );
  await expect(page.locator('#ecole-nom')).toBeHidden();
  await expect(page.locator('#ecole-logo')).toBeHidden();
  await verifierAccessibilite(page, 'Keycloak : connexion, marque neutre');
  // Le formulaire fonctionne.
  await page.locator('#username').fill('directeur.a');
  await page.locator('#password').fill(MOT_DE_PASSE);
  await page.locator('#kc-login').click();
  await page.waitForURL((url) => !url.pathname.startsWith('/realms/'));
});

test('mot de passe oublié → courriel → nouveau mot de passe, avec axe à chaque page', async ({
  page,
  request: api,
}) => {
  const utilisateur = `reinit.e2e.${Date.now().toString(36)}`;
  await creerUtilisateurSansEcole(api, utilisateur);
  await ouvrirConnexion(page);

  await page.getByRole('link', { name: /Mot de passe oublié/ }).click();
  await expect(page.locator('#kc-reset-password-form')).toBeVisible();
  // Compte sans adresse e-mail : le message le dit et renvoie au secrétariat.
  await expect(page.locator('#ecole-sans-email')).toContainText('secrétariat');
  await expect(page.locator('#ecole-nom')).toHaveText('Lycée Exemple A');
  await verifierAccessibilite(page, 'Keycloak : mot de passe oublié');

  await page.locator('#username').fill(utilisateur);
  await page
    .locator('#kc-reset-password-form button[type="submit"]')
    .first()
    .click();
  // Page d'information : un courriel est parti (même réponse pour un compte inconnu).
  await expect(
    page.locator('.kc-feedback-text, [class*="alert"]').first(),
  ).toBeVisible();
  await verifierAccessibilite(page, 'Keycloak : information après la demande');

  const { lien, sujet } = await lienDuCourriel(
    api,
    `${utilisateur}@exemple.test`,
  );
  expect(sujet.toLowerCase()).toContain('mot de passe');
  await page.goto(lien);

  await expect(page.locator('#password-new')).toBeVisible();
  await verifierAccessibilite(page, 'Keycloak : nouveau mot de passe');
  await page.locator('#password-new').fill('Nouveau-2026-secret');
  await page.locator('#password-confirm').fill('Nouveau-2026-secret');
  await page.locator('#kc-submit').click();
  // Mot de passe changé : l'utilisateur est renvoyé vers l'application ou une page d'information.
  await expect(page.locator('#password-new')).toHaveCount(0);
});

test('une demande de connexion invalide affiche une page d’erreur lisible', async ({
  page,
}) => {
  await page.goto(
    'http://localhost:8180/realms/ecole/protocol/openid-connect/auth?client_id=client-inconnu' +
      '&redirect_uri=http%3A%2F%2Flocalhost%3A4200%2F&response_type=code&scope=openid',
  );
  await expect(
    page
      .locator('#kc-error-message, .kc-feedback-text, [class*="alert"]')
      .first(),
  ).toBeVisible();
  await expect(page.locator('#ecole-plateforme')).toBeVisible();
  await capturer(page, 'keycloak-erreur-1440');
  await verifierAccessibilite(page, 'Keycloak : page d’erreur');
});
