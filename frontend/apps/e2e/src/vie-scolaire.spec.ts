import { test } from '@playwright/test';
import {
  changerEcole,
  choisirEcole,
  exigerInfra,
  seConnecter,
  verifierDansApp,
  verifierProfil,
} from './support';

// Vie scolaire : surveillants et enseignants. Les autres rôles sont renvoyés dans leur application.
exigerInfra();

test('un surveillant accède à la vie scolaire', async ({ page }) => {
  await seConnecter(page, 'surveillant.b');
  await verifierDansApp(page, 'vie-scolaire');
  await verifierProfil(page, 'École Exemple B', ['Surveillant']);
});

test('le censeur et la direction sont renvoyés vers le back-office', async ({
  page,
}) => {
  await seConnecter(page, 'censeur.a');
  await verifierDansApp(page, 'backoffice');
  await verifierProfil(page, 'Lycée Exemple A', ['Censeur']);
});

test('enseignant en A (vie scolaire), parent en B (espace parent)', async ({
  page,
}) => {
  await seConnecter(page, 'enseignant.parent');
  await choisirEcole(page, 'Lycée Exemple A');
  await verifierDansApp(page, 'vie-scolaire');
  await verifierProfil(page, 'Lycée Exemple A', ['Enseignant']);

  await changerEcole(page, 'enseignant.parent');
  await choisirEcole(page, 'École Exemple B');
  await verifierDansApp(page, 'parent');
  await verifierProfil(page, 'École Exemple B', ['Parent']);
});

test('un élève est renvoyé vers l’espace parent', async ({ page }) => {
  await seConnecter(page, 'eleve.b');
  await verifierDansApp(page, 'parent');
});
