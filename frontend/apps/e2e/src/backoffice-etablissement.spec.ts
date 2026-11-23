import { expect as expectBase, Page, test } from '@playwright/test';
import { verifierAccessibilite } from './accessibilite';
import {
  COMPTES_E2E,
  exigerInfra,
  lienMenu,
  NOM_ECOLE_E2E,
  seConnecter,
  verifierProfil,
} from './support';

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
  // Modification du profil : dans l'école E2E, jamais dans l'école A.
  await seConnecter(page, COMPTES_E2E.directeur);
  await verifierProfil(page, NOM_ECOLE_E2E, ['Directeur']);

  await allerA(page, 'Établissement');
  await expect(page.getByTestId('champ-nom')).toHaveValue(NOM_ECOLE_E2E);
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
  await seConnecter(page, COMPTES_E2E.secretariat);
  await verifierProfil(page, NOM_ECOLE_E2E, ['Secrétariat']);

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

test('le censeur lit les classes sans pouvoir les modifier ; pas de paramètres de l’école', async ({
  page,
}) => {
  await seConnecter(page, 'censeur.a');
  await verifierProfil(page, 'Lycée Exemple A', ['Censeur']);
  const liens = page
    .getByRole('navigation', { name: 'Navigation principale' })
    .getByRole('link');
  await expect(liens).toHaveText([
    'Accueil',
    'Élèves',
    'Classes',
    'Matières et horaires',
    'Affectations',
  ]);

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
  await expect(page.getByTestId('preparer-annee')).toBeVisible();
  await expect(page.getByTestId('ouvrir')).toHaveCount(0);
  await expect(page.getByTestId('cloturer')).toHaveCount(0);
});

test('le directeur voit les années et peut en préparer une', async ({
  page,
}) => {
  await seConnecter(page, 'directeur.a');
  await allerA(page, 'Années et périodes');
  await expect(page.getByTestId('annee-2026-2027')).toBeVisible();
  await expect(page.getByTestId('preparer-annee')).toBeVisible();
  await expect(
    page.getByTestId('ouvrir').or(page.getByTestId('cloturer')).first(),
  ).toBeVisible();
});

test("l'assistant de préparation crée une année, ses périodes et son calendrier (école E2E)", async ({
  page,
}) => {
  // Libellé unique par exécution ; l'année est supprimée à la fin (rien ne reste).
  const an = 2100 + (Date.now() % 800);
  const libelle = `${an}-${an + 1}`;
  await seConnecter(page, COMPTES_E2E.directeur);
  await page.goto('/etablissement/annees');
  await page.getByTestId('preparer-annee').click();
  await expect(page.getByTestId('titre-etape')).toHaveText("L'année scolaire");
  await expect(
    page.getByRole('navigation', { name: 'Navigation principale' }),
  ).toHaveCount(0);
  await page.getByTestId('champ-annee-libelle').fill(libelle);
  await page.getByTestId('champ-annee-debut').fill(`${an}-10-05`);
  await page.getByTestId('champ-annee-fin').fill(`${an + 1}-07-02`);
  await verifierAccessibilite(page, 'Préparation de l’année : année');
  await page.getByTestId('etape-suivante').click();

  await expect(page.getByTestId('titre-etape')).toHaveText('Périodes');
  await expect(page).toHaveURL(
    /\/etablissement\/annees\/preparation\/[0-9a-f-]{36}$/,
  );
  await verifierAccessibilite(page, 'Préparation de l’année : périodes');
  await page.getByTestId('etape-suivante').click();

  await expect(page.getByTestId('titre-etape')).toHaveText('Calendrier');
  await verifierAccessibilite(page, 'Préparation de l’année : calendrier');
  await page.getByTestId('etape-suivante').click();

  await expect(page.getByTestId('titre-etape')).toHaveText(
    "Ouverture de l'année",
  );
  await expect(page.getByTestId('recap-annee')).toContainText(libelle);
  await expect(page.getByTestId('ouvrir-annee')).toBeVisible();
  await verifierAccessibilite(page, 'Préparation de l’année : ouverture');
  await page.getByTestId('garder-preparation').click();

  // L'année reste en préparation ; nettoyage : suppression.
  const ligne = page.getByTestId(`annee-${libelle}`);
  await expect(ligne).toContainText('En préparation');
  await ligne.getByTestId('supprimer-annee').click();
  await page.getByTestId('confirmer-action').click();
  await expect(ligne).toHaveCount(0);
});

test('le censeur organise les matières et la grille horaire (école E2E)', async ({
  page,
}) => {
  await seConnecter(page, COMPTES_E2E.censeur);
  await allerA(page, 'Matières et horaires');
  await expect(page.getByTestId('table-matieres')).toBeVisible();
  await verifierAccessibilite(page, 'Matières et horaires : matières');

  // Matières usuelles : ajoutées une fois, sans doublon ensuite.
  await page.getByTestId('matieres-usuelles').click();
  await expect(page.getByTestId('info-matieres')).toBeVisible();
  await page.getByTestId('matieres-usuelles').click();
  await expect(page.getByTestId('info-matieres')).toHaveText(
    'Toutes les matières usuelles sont déjà là.',
  );
  await expect(page.getByTestId('matiere-MATHS')).toBeVisible();

  // Ajout en modale (un cycle au moins), puis suppression confirmée.
  await page.getByTestId('ajouter-matiere').click();
  await expect(page.getByTestId('modale-matiere')).toBeVisible();
  await page.getByTestId('champ-matiere-code').fill('E2EDISS');
  await page.getByTestId('champ-matiere-libelle').fill('Dissertation E2E');
  await page.getByTestId('champ-matiere-groupe').fill('Français');
  await page.getByTestId('enregistrer-matiere').click();
  await expect(page.getByText('Choisissez au moins un cycle.')).toBeVisible();
  await page.getByTestId('cycle-SECONDAIRE').getByRole('checkbox').check();
  await verifierAccessibilite(page, 'Modale : ajouter une matière');
  await page.getByTestId('enregistrer-matiere').click();
  const ligne = page.getByTestId('matiere-E2EDISS');
  await expect(ligne).toContainText('Secondaire');
  await ligne.getByRole('button', { name: /Supprimer/ }).click();
  await verifierAccessibilite(page, 'Confirmation : supprimer une matière');
  await page.getByTestId('confirmer-action').click();
  await expect(ligne).toHaveCount(0);

  // Grille horaire : un créneau du dimanche soir (aucun autre ne l'occupe), chevauchement refusé.
  await page.getByRole('tab', { name: 'Grille horaire' }).click();
  await expect(page.getByTestId('table-creneaux')).toBeVisible();
  await verifierAccessibilite(page, 'Matières et horaires : grille');
  const ajouterCreneau = async (debut: string, fin: string) => {
    await page.getByTestId('ajouter-creneau').click();
    await expect(page.getByTestId('modale-creneau')).toBeVisible();
    await page.getByTestId('champ-creneau-debut').fill(debut);
    await page.getByTestId('champ-creneau-fin').fill(fin);
    for (const jour of [
      'MONDAY',
      'TUESDAY',
      'WEDNESDAY',
      'THURSDAY',
      'FRIDAY',
    ]) {
      await page.getByTestId(`jour-${jour}`).getByRole('checkbox').uncheck();
    }
    await page.getByTestId('jour-SUNDAY').getByRole('checkbox').check();
  };
  await ajouterCreneau('18:00', '19:00');
  await page.getByTestId('champ-creneau-libelle').fill('E2E');
  await verifierAccessibilite(page, 'Modale : ajouter un créneau');
  await page.getByTestId('enregistrer-creneau').click();
  const creneau = page.getByTestId('creneau-18:00');
  await expect(creneau).toContainText('18:00 – 19:00');
  await expect(creneau).toContainText('dimanche');

  await ajouterCreneau('18:30', '19:30');
  await page.getByTestId('enregistrer-creneau').click();
  await expect(page.getByTestId('erreur-creneau')).toContainText('chevauche');
  await page
    .getByTestId('modale-creneau')
    .getByRole('button', { name: 'Annuler' })
    .click();
  await expect(page.getByTestId('modale-creneau')).toHaveCount(0);

  await creneau.getByRole('button', { name: /Supprimer/ }).click();
  await page.getByTestId('confirmer-action').click();
  await expect(creneau).toHaveCount(0);
});
