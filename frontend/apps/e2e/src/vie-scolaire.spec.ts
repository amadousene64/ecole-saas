import { test } from '@playwright/test';
import {
  changerEcole,
  choisirEcole,
  exigerInfra,
  seConnecter,
  verifierAccesRefuse,
  verifierProfil,
} from './support';

// Vie scolaire : direction, censeur, enseignants, surveillants.
exigerInfra();

test('un surveillant accède à la vie scolaire', async ({ page }) => {
  await seConnecter(page, 'surveillant.b');
  await verifierProfil(page, 'École Exemple B', ['Surveillant']);
});

test('le censeur et la direction y ont accès', async ({ page }) => {
  await seConnecter(page, 'censeur.a');
  await verifierProfil(page, 'Lycée Exemple A', ['Censeur']);
});

test('enseignant en A (accès), parent en B (refus)', async ({ page }) => {
  await seConnecter(page, 'enseignant.parent');
  await choisirEcole(page, 'Lycée Exemple A');
  await verifierProfil(page, 'Lycée Exemple A', ['Enseignant']);

  await changerEcole(page, 'enseignant.parent');
  await choisirEcole(page, 'École Exemple B');
  await verifierAccesRefuse(page);
});

test('un élève n’a pas accès à la vie scolaire', async ({ page }) => {
  await seConnecter(page, 'eleve.b');
  await verifierAccesRefuse(page);
});
