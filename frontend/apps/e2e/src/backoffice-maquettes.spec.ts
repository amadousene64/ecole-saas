import { expect as expectBase, Page, test } from '@playwright/test';
import { BUREAU, capturerMaquette, TELEPHONE } from './maquettes';
import { exigerInfra, lienMenu, seConnecter } from './support';

/**
 * Écrans du back-office mis en regard des maquettes (docs/maquettes/, voir maquettes.ts). LECTURE
 * SEULE : on ouvre les formulaires et les confirmations sans jamais les envoyer, rien n'est créé.
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

test('03, 04, 06, 08 · secrétariat : liste, fiche, responsable, nouvel élève', async ({
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
  await page.getByTestId('ajouter-responsable').click();
  await capturerMaquette(page, '06-modale-responsable');

  await page.goto('/eleves/nouveau');
  await expect(page.getByTestId('champ-prenoms')).toBeVisible();
  await capturerMaquette(page, '08-nouvel-eleve');
});

test('05, 10 · secrétariat sur téléphone : liste et nouvel élève', async ({
  page,
}) => {
  await page.setViewportSize(TELEPHONE);
  await seConnecter(page, 'secretariat.a');
  await page.goto('/eleves');
  await expect(page.locator('[data-testid^="eleve-"]').first()).toBeVisible();
  await capturerMaquette(page, '05-liste-eleves-telephone');
  await page.goto('/eleves/nouveau');
  await expect(page.getByTestId('champ-prenoms')).toBeVisible();
  await capturerMaquette(page, '10-nouvel-eleve-telephone');
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
