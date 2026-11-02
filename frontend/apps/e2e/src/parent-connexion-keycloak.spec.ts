import { expect as expectBase, Page, request, test } from '@playwright/test';
import { mkdirSync } from 'node:fs';
import { verifierAccessibilite } from './accessibilite';
import {
  exigerInfra,
  exigerMailpit,
  garantirLogoE2E,
  lienDuCourriel,
} from './support';

/**
 * Pages de connexion Keycloak de l'espace parent (thème « ecole », client « parent ») : encart
 * « Espace parent » et lien de création de compte réservés à ce client, inscription d'un parent,
 * vérification de l'adresse par courriel (Mailpit), axe à chaque page à 1440 et 390 px.
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

test('le parent voit l’encart « Espace parent » et le lien de création de compte', async ({
  page,
}) => {
  await ouvrirConnexion(page);

  await expect(page.locator('#ecole-nom')).toHaveText('Lycée Exemple A');
  const encart = page.locator('#ecole-encart-parent');
  await expect(encart).toBeVisible();
  await expect(encart).toContainText('Espace parent');
  await expect(encart).toContainText("code d'activation");
  await expect(
    page.getByRole('link', { name: 'Créer mon compte parent' }),
  ).toBeVisible();

  await capturer(page, 'keycloak-connexion-parent-1440');
  await verifierAccessibilite(page, 'Keycloak : connexion parent (1440 px)');
});

test('sur téléphone : bandeau, encart replié, formulaire en vue, sans défilement horizontal', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await ouvrirConnexion(page);
  await expect(page.locator('#ecole-nom')).toHaveText('Lycée Exemple A');

  expect(
    (await page.locator('#ecole-marque').boundingBox())!.height,
  ).toBeLessThan(120);
  await expect(page.locator('#ecole-encart-parent')).toBeHidden();
  const bouton = await page.locator('#kc-login').boundingBox();
  expect(bouton!.y + bouton!.height).toBeLessThanOrEqual(844);
  const debord = await page.evaluate(
    () =>
      document.documentElement.scrollWidth -
      document.documentElement.clientWidth,
  );
  expect(debord).toBeLessThanOrEqual(0);

  await capturer(page, 'keycloak-connexion-parent-390');
  await verifierAccessibilite(page, 'Keycloak : connexion parent (390 px)');
});

test('inscription d’un parent → vérification de l’adresse par courriel', async ({
  page,
  request: api,
}) => {
  const suffixe = Date.now().toString(36);
  const utilisateur = `parent.inscrit.${suffixe}`;
  const courriel = `${utilisateur}@exemple.test`;
  await ouvrirConnexion(page);

  await page.getByRole('link', { name: 'Créer mon compte parent' }).click();
  await expect(page.locator('#kc-register-form')).toBeVisible();
  await expect(page.locator('#ecole-nom')).toHaveText('Lycée Exemple A');
  await verifierAccessibilite(page, 'Keycloak : inscription (1440 px)');

  await page.locator('#firstName').fill('Mame');
  await page.locator('#lastName').fill('Parent-Fictif');
  await page.locator('#email').fill(courriel);
  await page.locator('#username').fill(utilisateur);
  await page.locator('#kc-register-form input[type="submit"]').click();

  // Adresse à vérifier : page d'information, puis lien reçu par courriel.
  await expect(page.locator('#kc-page-title')).toContainText(
    'Vérification du courriel',
  );
  await expect(page.locator('#ecole-nom')).toHaveText('Lycée Exemple A');
  await expect(page.locator('#kc-register-form')).toHaveCount(0);
  await verifierAccessibilite(page, 'Keycloak : vérification de l’adresse');

  const { lien } = await lienDuCourriel(api, courriel);
  await page.goto(lien);
  // Adresse confirmée : Keycloak demande le mot de passe du nouveau compte (action requise).
  await expect(page.locator('#password-new')).toBeVisible();
  await verifierAccessibilite(
    page,
    'Keycloak : choix du mot de passe du nouveau compte',
  );
  await page.locator('#password-new').fill(MOT_DE_PASSE);
  await page.locator('#password-confirm').fill(MOT_DE_PASSE);
  await page.locator('#kc-submit').click();
  await expect(page.locator('#password-new')).toHaveCount(0);
});

test('erreurs de saisie à l’inscription : messages liés aux champs, lisibles et accessibles', async ({
  page,
}) => {
  await ouvrirConnexion(page);
  await page.getByRole('link', { name: 'Créer mon compte parent' }).click();
  await expect(page.locator('#kc-register-form')).toBeVisible();

  await page.locator('#kc-register-form input[type="submit"]').click();
  await expect(page.locator('#kc-register-form')).toBeVisible();
  await expect(
    page.locator('[id^="input-error"], .pf-m-error').first(),
  ).toBeVisible();
  await verifierAccessibilite(page, 'Keycloak : inscription avec erreurs');
});
