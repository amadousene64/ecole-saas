import { expect as expectBase, Page, test } from '@playwright/test';
import { exigerInfra, seConnecter, verifierProfil, lienMenu } from './support';

// Back-office, module établissement : profil, classes, droits en lecture seule. Données de dev :
// école A avec l'année 2026-2027 en cours (profil dev du backend).
exigerInfra();

// Premier chargement des écrans paresseux sur un serveur de développement : plus patient que 5 s.
const expect = expectBase.configure({ timeout: 15_000 });

async function allerA(page: Page, lien: string): Promise<void> {
  await lienMenu(page, lien).click();
}

test("le directeur modifie le profil de l'école et voit le cachet et la signature", async ({
  page,
}) => {
  await seConnecter(page, 'directeur.a');
  await verifierProfil(page, 'Lycée Exemple A', ['Directeur']);

  await allerA(page, 'Établissement');
  await expect(page.getByTestId('champ-nom')).toHaveValue('Lycée Exemple A');
  await expect(page.getByTestId('image-CACHET')).toBeVisible();
  await expect(page.getByTestId('image-SIGNATURE')).toBeVisible();

  // Un numéro local de 9 chiffres devient un numéro international (+221).
  await page.getByTestId('champ-telephone').fill('77 123 45 67');
  await page.getByTestId('enregistrer').click();
  await expect(page.getByTestId('confirmation')).toContainText('enregistrés');
  await expect(page.getByTestId('champ-telephone')).toHaveValue(
    '+221771234567',
  );

  await page.reload();
  await expect(page.getByTestId('champ-telephone')).toHaveValue(
    '+221771234567',
  );
});

test('le secrétariat crée puis supprime une classe sur un niveau sans série', async ({
  page,
}) => {
  const nom = `E2E-${Date.now() % 100000}`;
  await seConnecter(page, 'secretariat.a');
  await verifierProfil(page, 'Lycée Exemple A', ['Secrétariat']);

  await allerA(page, 'Classes et salles');
  await expect(page.getByTestId('table-classes')).toBeVisible();
  await page.getByTestId('champ-classe-nom').fill(nom);
  await page.getByTestId('champ-classe-niveau').click();
  await page.getByRole('option', { name: 'CP', exact: true }).click();
  // CP n'a pas de série : le champ n'apparaît pas.
  await expect(page.getByTestId('champ-classe-serie')).toHaveCount(0);
  await page.getByTestId('enregistrer-classe').click();
  await expect(page.getByTestId(`classe-${nom}`)).toBeVisible();

  // Une série devient obligatoire pour un niveau qui en a (Terminale).
  await page.getByTestId('champ-classe-nom').fill(`${nom}B`);
  await page.getByTestId('champ-classe-niveau').click();
  await page.getByRole('option', { name: 'Tle', exact: true }).click();
  await expect(page.getByTestId('champ-classe-serie')).toBeVisible();
  await expect(page.getByTestId('enregistrer-classe')).toBeDisabled();

  // Nettoyage : l'e2e se rejoue sans laisser de classe.
  await page
    .getByTestId(`classe-${nom}`)
    .getByRole('button', { name: 'Supprimer' })
    .click();
  await expect(page.getByTestId(`classe-${nom}`)).toHaveCount(0);
});

test('le secrétariat a « Paramètres de l’école » : profil et logo, sans cachet ni signature', async ({
  page,
}) => {
  await seConnecter(page, 'secretariat.a');
  await expect(lienMenu(page, 'Classes et salles')).toBeVisible();
  await expect(lienMenu(page, 'Profil et logo')).toBeVisible();
  await expect(lienMenu(page, 'Niveaux et séries')).toBeVisible();
  await expect(lienMenu(page, 'Années, périodes et calendrier')).toBeVisible();

  await allerA(page, 'Profil et logo');
  await expect(page.getByTestId('champ-nom')).toHaveValue('Lycée Exemple A');
  await expect(page.getByTestId('image-LOGO')).toBeVisible();
  await expect(page.getByTestId('image-CACHET')).toHaveCount(0);
  await expect(page.getByTestId('image-SIGNATURE')).toHaveCount(0);

  // Structure : niveaux et séries, mais pas l'onglet des cycles (notation : directeur seul).
  await allerA(page, 'Niveaux et séries');
  await expect(page.getByRole('tab', { name: 'Niveaux' })).toBeVisible();
  await expect(page.getByRole('tab', { name: 'Cycles' })).toHaveCount(0);
});

test('le censeur lit les classes sans pouvoir les modifier ; pas de pages de paramètres', async ({
  page,
}) => {
  await seConnecter(page, 'censeur.a');
  await verifierProfil(page, 'Lycée Exemple A', ['Censeur']);
  const liens = page
    .getByRole('navigation', { name: 'Navigation principale' })
    .getByRole('link');
  await expect(liens).toHaveText(['Accueil', 'Élèves', 'Classes']);

  await allerA(page, 'Classes');
  await expect(page.getByTestId('table-classes')).toBeVisible();
  await expect(page.getByTestId('champ-classe-nom')).toHaveCount(0);
  await expect(page.getByTestId('enregistrer-classe')).toHaveCount(0);

  for (const chemin of [
    '/etablissement/parametres',
    '/etablissement/structure',
    '/etablissement/annees',
  ]) {
    await page.goto(chemin);
    await expect(page).toHaveURL('http://localhost:4200/');
    await expect(page.getByTestId('moi-roles')).toBeVisible();
  }
});

test('l’ouverture et la clôture d’une année sont proposées au directeur seul', async ({
  page,
}) => {
  // Le secrétariat gère années, périodes et calendrier, mais ne peut ni ouvrir ni clôturer.
  await seConnecter(page, 'secretariat.a');
  await allerA(page, 'Années, périodes et calendrier');
  await expect(page.getByTestId('annee-2026-2027')).toBeVisible();
  await expect(page.getByTestId('form-annee')).toBeVisible();
  await expect(page.getByTestId('ouvrir')).toHaveCount(0);
  await expect(page.getByTestId('cloturer')).toHaveCount(0);
});

test('le directeur voit les années avec leur formulaire', async ({ page }) => {
  await seConnecter(page, 'directeur.a');
  await allerA(page, 'Années et périodes');
  await expect(page.getByTestId('annee-2026-2027')).toBeVisible();
  await expect(page.getByTestId('form-annee')).toBeVisible();
  await expect(
    page.getByTestId('ouvrir').or(page.getByTestId('cloturer')).first(),
  ).toBeVisible();
});
