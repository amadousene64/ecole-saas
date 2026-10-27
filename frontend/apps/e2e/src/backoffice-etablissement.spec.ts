import { expect, Page, test } from '@playwright/test';
import { exigerInfra, seConnecter, verifierProfil } from './support';

// Back-office, module établissement : profil, classes, droits en lecture seule. Données de dev :
// école A avec l'année 2026-2027 en cours (profil dev du backend).
exigerInfra();

async function allerA(page: Page, lien: string): Promise<void> {
  await page.getByRole('link', { name: lien, exact: true }).click();
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
  // Le secrétariat voit le logo mais pas le cachet ni la signature (page Établissement).
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

test('le secrétariat ne voit ni le cachet ni la signature, mais dépose le logo', async ({
  page,
}) => {
  await seConnecter(page, 'secretariat.a');
  await allerA(page, 'Établissement');

  await expect(page.getByTestId('image-LOGO')).toBeVisible();
  await expect(page.getByTestId('fichier-LOGO')).toBeVisible();
  await expect(page.getByTestId('image-CACHET')).toHaveCount(0);
  await expect(page.getByTestId('image-SIGNATURE')).toHaveCount(0);
});

test('le censeur lit la structure sans pouvoir rien modifier', async ({
  page,
}) => {
  await seConnecter(page, 'censeur.a');
  await verifierProfil(page, 'Lycée Exemple A', ['Censeur']);

  await allerA(page, 'Établissement');
  await expect(page.getByTestId('lecture-seule')).toBeVisible();
  await expect(page.getByTestId('champ-nom')).toBeDisabled();
  await expect(page.getByTestId('enregistrer')).toHaveCount(0);
  await expect(page.getByTestId('image-CACHET')).toHaveCount(0);

  await allerA(page, 'Années et périodes');
  await expect(page.getByTestId('table-annees')).toBeVisible();
  await expect(page.getByTestId('annee-2026-2027')).toBeVisible();
  await expect(page.getByTestId('form-annee')).toHaveCount(0);
  await expect(page.getByTestId('ouvrir')).toHaveCount(0);
  await expect(page.getByTestId('cloturer')).toHaveCount(0);

  await allerA(page, 'Classes et salles');
  await expect(page.getByTestId('table-classes')).toBeVisible();
  await expect(page.getByTestId('form-classe')).toHaveCount(0);
});

test("l'ouverture et la clôture d'une année sont réservées au directeur", async ({
  page,
}) => {
  await seConnecter(page, 'secretariat.a');
  await allerA(page, 'Années et périodes');

  await expect(page.getByTestId('annee-2026-2027')).toBeVisible();
  await expect(page.getByTestId('form-annee')).toBeVisible();
  await expect(page.getByTestId('ouvrir')).toHaveCount(0);
  await expect(page.getByTestId('cloturer')).toHaveCount(0);
});
