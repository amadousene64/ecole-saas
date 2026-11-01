import { expect as expectBase, test } from '@playwright/test';
import {
  exigerInfra,
  garantirClasseE2E,
  lienMenu,
  seConnecter,
} from './support';

/**
 * Formulaire d'inscription quand les classes ne se chargent pas : le secrétariat doit voir
 * pourquoi la liste est vide (et pouvoir réessayer), au lieu d'une liste qui ne s'ouvre pas.
 */
exigerInfra();

const expect = expectBase.configure({ timeout: 15_000 });

test('classes indisponibles : le message reste affiché, puis « Réessayer » charge la liste', async ({
  page,
  request,
}) => {
  const classe = await garantirClasseE2E(request);
  const suffixe = Date.now()
    .toString(36)
    .toUpperCase()
    .replace(/\d/g, (c) => 'GHIJKLMNOP'[Number(c)]);

  await seConnecter(page, 'secretariat.a');
  await lienMenu(page, 'Élèves', false).click();
  await expect(page.getByTestId('table-eleves')).toBeVisible();
  await page.getByTestId('nouvel-eleve').click();
  await page.getByTestId('champ-prenoms').fill(`Classe${suffixe}`);
  await page.getByTestId('champ-nom').fill('Testeur-Fictif');
  await page.getByTestId('champ-naissance').fill('2012-05-14');
  await page.getByTestId('champ-lieu').fill('Ville Fictive');

  // Le serveur des classes tombe en panne.
  let panne = true;
  await page.route('**/api/v1/classes', async (route) => {
    if (panne) {
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ detail: 'Erreur simulée.' }),
      });
    } else {
      await route.continue();
    }
  });
  await page.getByTestId('creer-eleve').click();
  await expect(page.getByTestId('titre-fiche')).toContainText(
    `Classe${suffixe}`,
  );
  await page.getByRole('tab', { name: 'Scolarité' }).click();
  await page.getByTestId('action-inscrire').click();

  const alerte = page.getByTestId('erreur-classes');
  await expect(alerte).toBeVisible();
  await expect(alerte).toContainText("Les classes n'ont pas pu être chargées");
  await expect(alerte).toHaveAttribute('role', 'alert');

  // La panne cesse : « Réessayer » charge la liste, qui s'ouvre enfin.
  panne = false;
  await page.getByTestId('reessayer-classes').click();
  await expect(alerte).toHaveCount(0);
  await page.getByTestId('champ-classe').click();
  await expect(
    page.getByRole('option', { name: classe, exact: true }),
  ).toBeVisible();
});
