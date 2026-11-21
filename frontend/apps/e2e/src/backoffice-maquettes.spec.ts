import { expect as expectBase, Page, test } from '@playwright/test';
import { BUREAU, capturerMaquette, TELEPHONE } from './maquettes';
import { COMPTES_E2E, exigerInfra, lienMenu, seConnecter } from './support';

/**
 * Écrans du back-office mis en regard des maquettes (docs/maquettes/, voir maquettes.ts). LECTURE
 * SEULE dans les écoles A et B : on ouvre les formulaires et les confirmations sans les envoyer.
 * Les assistants, qui enregistrent un brouillon à chaque étape, sont capturés dans l'école E2E et
 * leur brouillon est abandonné à la fin.
 */
exigerInfra();

const expect = expectBase.configure({ timeout: 15_000 });

async function ouvrirPremiereFiche(page: Page): Promise<void> {
  await page.goto('/eleves');
  await expect(page.getByTestId('table-eleves')).toBeVisible();
  await page
    .locator('[data-testid^="eleve-"]')
    .first()
    .getByRole('link')
    .click();
  await expect(page.getByTestId('titre-fiche')).toBeVisible();
}

test('15 · connexion (bureau)', async ({ page }) => {
  await page.setViewportSize(BUREAU);
  await page.goto('/');
  await expect(page.locator('#kc-login')).toBeVisible();
  await capturerMaquette(page, '15-connexion');
});

test('16 · connexion (téléphone)', async ({ page }) => {
  await page.setViewportSize(TELEPHONE);
  await page.goto('/');
  await expect(page.locator('#kc-login')).toBeVisible();
  await capturerMaquette(page, '16-connexion-telephone');
});

test('03, 04, 06 · secrétariat : liste, fiche, responsable', async ({
  page,
}) => {
  await page.setViewportSize(BUREAU);
  await seConnecter(page, 'secretariat.a');
  await lienMenu(page, 'Élèves et inscriptions').click();
  await expect(page.locator('[data-testid^="eleve-"]').first()).toBeVisible();
  await capturerMaquette(page, '03-liste-eleves');

  await ouvrirPremiereFiche(page);
  await capturerMaquette(page, '04-fiche-eleve');

  await page.getByRole('tab', { name: 'Responsables' }).click();
  await page.getByTestId('ouvrir-ajout-responsable').click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await capturerMaquette(page, '06-modale-responsable');
  await page.keyboard.press('Escape');

  await page.goto('/eleves/import');
  await expect(page.getByTestId('titre-etape')).toHaveText(
    'Importer des élèves',
  );
  await capturerMaquette(page, 'import-assistant');
});

test('05 · secrétariat sur téléphone : liste', async ({ page }) => {
  await page.setViewportSize(TELEPHONE);
  await seConnecter(page, 'secretariat.a');
  await page.goto('/eleves');
  await expect(page.locator('[data-testid^="eleve-"]').first()).toBeVisible();
  await capturerMaquette(page, '05-liste-eleves-telephone');
});

/** Assistant d'inscription : étape 1 remplie, un responsable ajouté, puis étape 3 atteinte. */
async function assistantJusquaScolarite(page: Page): Promise<void> {
  await page.goto('/eleves/nouveau');
  await expect(page).toHaveURL(/\/eleves\/brouillons\//);
  await page.getByTestId('champ-prenoms').fill('Aminata');
  await page.getByTestId('champ-nom').fill('Fictive');
  await page.getByTestId('champ-naissance').fill('2013-03-14');
  await page.getByTestId('champ-lieu').fill('Thiès');
  await page.getByTestId('etape-suivante').click();
  await expect(page.getByTestId('titre-etape')).toHaveText(
    "Responsables de l'élève",
  );
  await page.getByTestId('ouvrir-ajout-responsable').click();
  await page.getByTestId('resp-telephone').fill('+221700000088');
  await page.getByTestId('resp-prenoms').fill('Fatou');
  await page.getByTestId('resp-nom').fill('Fictive');
  await page.getByTestId('ajouter-responsable').click();
  await expect(page.getByTestId('responsable-0')).toBeVisible();
}

/**
 * Nettoyage : « Enregistrer et quitter », puis abandon du brouillon ouvert par CE test seulement
 * (les captures tournent en parallèle dans l'école E2E : un autre brouillon peut être en cours).
 */
async function abandonnerBrouillons(page: Page): Promise<void> {
  const id = page.url().split('/').pop() ?? '';
  expect(id).toMatch(/^[0-9a-f-]{36}$/);
  await page.getByTestId('enregistrer-quitter').click();
  await expect(page.getByTestId('table-eleves')).toBeVisible();
  const ligne = page
    .getByTestId('brouillons-en-cours')
    .locator('li')
    .filter({ has: page.locator(`a[href$="/eleves/brouillons/${id}"]`) });
  // Liste paginée : on charge la suite tant que ce brouillon n'est pas affiché.
  const suite = page.getByTestId('brouillons-suite');
  await expect(page.getByTestId('brouillons-en-cours')).toBeVisible();
  const lignes = page.getByTestId('brouillons-en-cours').locator('li');
  while ((await ligne.count()) === 0 && (await suite.count()) > 0) {
    const avant = await lignes.count();
    await suite.click();
    await expect(lignes).not.toHaveCount(avant);
  }
  await ligne.getByRole('button', { name: /Abandonner/ }).click();
  await page.getByTestId('confirmer-action').click();
  await expect(page.getByTestId('confirmer-abandon')).toHaveCount(0);
  await expect(ligne).toHaveCount(0);
}

test("08, 09 · assistant d'inscription (bureau, école E2E)", async ({
  page,
}) => {
  await page.setViewportSize(BUREAU);
  await seConnecter(page, COMPTES_E2E.secretariat);
  await assistantJusquaScolarite(page);
  await capturerMaquette(page, '08-assistant-etape2');
  await page.getByTestId('etape-suivante').click();
  await page.getByTestId('champ-classe').click();
  await page.getByRole('option').first().click();
  await page.getByTestId('etape-suivante').click();
  await expect(page.getByTestId('titre-etape')).toHaveText('Pièces du dossier');
  await page.getByTestId('etape-suivante').click();
  await expect(page.getByTestId('titre-etape')).toHaveText(
    "Vérifiez avant d'inscrire",
  );
  await page.getByTestId('generer-code').click();
  await capturerMaquette(page, '09-assistant-verification');
  await abandonnerBrouillons(page);
});

test("10 · assistant d'inscription (téléphone, école E2E)", async ({
  page,
}) => {
  await page.setViewportSize(TELEPHONE);
  await seConnecter(page, COMPTES_E2E.secretariat);
  await assistantJusquaScolarite(page);
  await page.getByTestId('etape-suivante').click();
  await expect(page.getByTestId('titre-etape')).toHaveText('Classe et options');
  await page.getByTestId('champ-classe').click();
  await page.getByRole('option').first().click();
  await capturerMaquette(page, '10-assistant-telephone');
  // Sur téléphone, « Enregistrer et quitter » est dans l'en-tête masqué : la croix fait de même.
  const brouillon = page.url();
  await page.getByTestId('fermer-assistant').click();
  await expect(page.getByTestId('table-eleves')).toBeVisible();
  await page.setViewportSize(BUREAU);
  await page.goto(brouillon);
  await expect(page.getByTestId('titre-etape')).toHaveText('Classe et options');
  await abandonnerBrouillons(page);
});

test("préparation d'une année : première étape (école E2E, rien n'est créé)", async ({
  page,
}) => {
  await page.setViewportSize(BUREAU);
  await seConnecter(page, COMPTES_E2E.directeur);
  await page.goto('/etablissement/annees/preparation');
  await expect(page.getByTestId('titre-etape')).toHaveText("L'année scolaire");
  await capturerMaquette(page, 'annees-preparation');
});

test('11, 07 · direction : tableau de bord, changement de statut, classes et salles, paramètres', async ({
  page,
}) => {
  await page.setViewportSize(BUREAU);
  await seConnecter(page, 'directeur.a');
  await expect(page.locator('canvas, svg').first()).toBeVisible();
  // Graphiques dessinés (ECharts anime l'entrée).
  await page.waitForTimeout(1_000);
  await capturerMaquette(page, '11-tableau-de-bord-direction');

  // Le directeur seul peut radier : formulaire ouvert, jamais envoyé.
  await ouvrirPremiereFiche(page);
  await page.getByRole('tab', { name: 'Scolarité' }).click();
  await page.getByTestId('action-sortie').click();
  await capturerMaquette(page, '07-confirmation-radiation');

  await page.goto('/etablissement/classes');
  await expect(page.getByRole('tab', { name: 'Classes' })).toBeVisible();
  await expect(page.getByTestId('table-classes')).toBeVisible();
  await capturerMaquette(page, 'classes');
  await page.getByRole('tab', { name: 'Salles' }).click();
  await expect(page.getByTestId('table-salles')).toBeVisible();
  await capturerMaquette(page, 'salles');

  await page.goto('/etablissement/annees');
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  await capturerMaquette(page, 'annees');

  await page.goto('/etablissement/parametres');
  await expect(page.getByTestId('champ-nom')).toBeVisible();
  await capturerMaquette(page, 'parametres');

  await page.goto('/etablissement/structure');
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  await capturerMaquette(page, 'structure');

  await page.goto('/eleves/import');
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  await capturerMaquette(page, 'import');
});

for (const [compte, nom] of [
  ['comptable.a', '12-tableau-de-bord-comptabilite'],
  ['censeur.a', '13-tableau-de-bord-censeur'],
] as const) {
  test(`${nom.slice(0, 2)} · accueil de ${compte}`, async ({ page }) => {
    await page.setViewportSize(BUREAU);
    await seConnecter(page, compte);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await page.waitForTimeout(1_000);
    await capturerMaquette(page, nom);
  });
}
