import { expect as expectBase, test } from '@playwright/test';
import {
  exigerInfra,
  garantirClasseE2E,
  lienMenu,
  seConnecter,
  COMPTES_E2E,
  creerEleveE2E,
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

  // Élève existant sans inscription (l'assistant, lui, inscrit dès la création).
  const eleve = await creerEleveE2E(request, `Classe${suffixe}`);
  await seConnecter(page, COMPTES_E2E.secretariat);
  await lienMenu(page, 'Élèves', false).click();
  await expect(page.getByTestId('table-eleves')).toBeVisible();

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
  await page.goto(`/eleves/${eleve}`);
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
  // L'alerte disparaît dès le début du rechargement : attendre les classes elles-mêmes
  // (mat-select ne s'ouvre pas tant que la liste est vide).
  await expect(page.getByTestId('chargement-classes')).toHaveCount(0);
  await page.getByTestId('champ-classe').click();
  await expect(
    page.getByRole('option', { name: classe, exact: true }),
  ).toBeVisible();
});
