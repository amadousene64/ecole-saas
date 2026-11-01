import { test } from '@playwright/test';
import {
  changerEcole,
  choisirEcole,
  exigerInfra,
  seConnecter,
  verifierDansApp,
  verifierProfil,
} from './support';

// Espace parent (coquille légère, sans Material) : rôles PARENT et ELEVE.
exigerInfra();

test('un élève accède à l’espace parent (une seule école)', async ({
  page,
}) => {
  await seConnecter(page, 'eleve.b');
  await verifierDansApp(page, 'parent');
  await verifierProfil(page, 'École Exemple B', ['Élève']);
});

test('parent dans une école, enseignant dans l’autre : chaque école mène à son application', async ({
  page,
}) => {
  await seConnecter(page, 'enseignant.parent');

  // École B : parent, l’espace parent est ouvert.
  await choisirEcole(page, 'École Exemple B');
  await verifierDansApp(page, 'parent');
  await verifierProfil(page, 'École Exemple B', ['Parent']);

  // École A : simple enseignant, renvoyé vers la vie scolaire (jamais une page « Accès refusé »).
  await changerEcole(page, 'enseignant.parent');
  await choisirEcole(page, 'Lycée Exemple A');
  await verifierDansApp(page, 'vie-scolaire');
  await verifierProfil(page, 'Lycée Exemple A', ['Enseignant']);
});

test('la direction est renvoyée vers le back-office', async ({ page }) => {
  await seConnecter(page, 'directeur.a');
  await verifierDansApp(page, 'backoffice');
  await verifierProfil(page, 'Lycée Exemple A', ['Directeur']);
});
