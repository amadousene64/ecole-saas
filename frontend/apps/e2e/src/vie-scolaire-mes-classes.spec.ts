import { expect as expectBase, test } from '@playwright/test';
import { verifierAccessibilite } from './accessibilite';
import {
  COMPTES_E2E,
  exigerInfra,
  garantirClasseE2E,
  garantirMatieresE2E,
  lienMenu,
  seConnecter,
} from './support';

// Affectations et périmètre (école E2E) : la direction affecte enseignant.e2e à une matière d'une
// classe ; il la voit aussitôt dans « Mes classes » ; la fin d'affectation la lui retire aussitôt.
exigerInfra();

const expect = expectBase.configure({ timeout: 15_000 });

test('la direction affecte un enseignant : « Mes classes » suit l’affectation, sa fin vaut aussitôt', async ({
  page,
  browser,
  request,
}) => {
  const classe = await garantirClasseE2E(request);
  await garantirMatieresE2E(request);

  // L'enseignant se présente une première fois : sans affectation, aucune classe.
  await seConnecter(page, COMPTES_E2E.enseignant);
  await lienMenu(page, 'Mes classes').click();
  await expect(page.getByTestId('aucune-classe')).toBeVisible();
  await verifierAccessibilite(page, 'Mes classes (aucune)');

  // La direction l'affecte (backoffice, second navigateur).
  const contexteDirection = await browser.newContext({
    baseURL: 'http://localhost:4200',
  });
  const direction = await contexteDirection.newPage();
  try {
    await seConnecter(direction, COMPTES_E2E.directeur);
    await lienMenu(direction, 'Affectations').click();
    await expect(direction.getByTestId('table-affectations')).toBeVisible();
    await verifierAccessibilite(direction, 'Affectations');

    await direction.getByTestId('nouvelle-affectation').click();
    await expect(direction.getByTestId('modale-affectation')).toBeVisible();
    await direction.getByTestId('champ-personne').click();
    // Il s'est déjà connecté : l'annuaire porte son nom.
    await direction
      .getByRole('option', { name: /FICTIF E2E Enseignant/ })
      .click();
    await direction.getByTestId('champ-classe-affectation').click();
    await direction.getByRole('option', { name: classe, exact: true }).click();
    await direction.getByTestId('champ-matiere-affectation').click();
    await direction.getByRole('option', { name: 'Mathématiques' }).click();
    await verifierAccessibilite(direction, 'Modale : nouvelle affectation');
    await direction.getByTestId('enregistrer-affectation').click();
    const ligne = direction.getByTestId(
      `affectation-Mathématiques · ${classe}`,
    );
    await expect(ligne).toContainText('En cours');
    await expect(ligne).toContainText('FICTIF E2E Enseignant');

    // Aussitôt : la classe apparaît chez l'enseignant, avec ses élèves.
    await page.reload();
    const carte = page.getByTestId(`voir-eleves-${classe}`);
    await expect(carte).toBeVisible();
    await carte.click();
    await expect(page.getByTestId('table-eleves-classe')).toBeVisible();
    await verifierAccessibilite(page, 'Mes classes : élèves');

    // Fin d'affectation confirmée (commencée aujourd'hui : annulée, sans trace dans la liste).
    await ligne.getByRole('button', { name: /Terminer/ }).click();
    await verifierAccessibilite(
      direction,
      'Confirmation : terminer une affectation',
    );
    await direction.getByTestId('confirmer-action').click();
    await expect(ligne).toHaveCount(0);
  } finally {
    await contexteDirection.close();
  }

  // Aussitôt : plus de classe, et l'API refuse la liste des élèves.
  await page.reload();
  await expect(page.getByTestId('aucune-classe')).toBeVisible();
});
