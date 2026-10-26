import { test } from '@playwright/test';
import {
  changerEcole,
  choisirEcole,
  exigerInfra,
  seConnecter,
  verifierAccesRefuse,
  verifierProfil,
} from './support';

// Espace parent (coquille légère, sans Material) : rôles PARENT et ELEVE.
exigerInfra();

test('un élève accède à l’espace parent (une seule école)', async ({
  page,
}) => {
  await seConnecter(page, 'eleve.b');
  await verifierProfil(page, 'École Exemple B', ['Élève']);
});

test('parent dans une école, enseignant dans l’autre : accès selon l’école choisie', async ({
  page,
}) => {
  await seConnecter(page, 'enseignant.parent');

  // École A : simple enseignant, l’espace parent est fermé.
  await choisirEcole(page, 'Lycée Exemple A');
  await verifierAccesRefuse(page);

  // École B : parent, l’accès est ouvert.
  await changerEcole(page, 'enseignant.parent');
  await choisirEcole(page, 'École Exemple B');
  await verifierProfil(page, 'École Exemple B', ['Parent']);
});

test('la direction n’a pas accès à l’espace parent', async ({ page }) => {
  await seConnecter(page, 'directeur.a');
  await verifierAccesRefuse(page);
});
