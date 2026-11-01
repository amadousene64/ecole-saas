import { expect as expectBase, Page, test } from '@playwright/test';
import { mkdirSync } from 'node:fs';
import { verifierAccessibilite } from './accessibilite';
import { exigerInfra, seConnecter } from './support';

/**
 * Tableau de bord de la direction (accueil du directeur) sur les vraies données de développement :
 * chiffres clés, trois graphiques ECharts réellement dessinés, alternative en tableau, filtres,
 * clavier, mobile, axe. Les montants exacts dépendent des autres tests (qui inscrivent des
 * élèves) : on vérifie la cohérence interne plutôt que des valeurs figées.
 */
exigerInfra();

const expect = expectBase.configure({ timeout: 20_000 });
const DOSSIER = 'dist/.playwright/captures';

const nombre = (texte: string | null) =>
  Number((texte ?? '').replace(/\D+/g, '') || NaN);

async function attendreTableau(page: Page): Promise<void> {
  await expect(
    page.getByRole('heading', { name: 'Tableau de bord' }),
  ).toBeVisible();
  await expect(page.getByTestId('kpi-inscrits')).toBeVisible();
  // ECharts est chargé à la demande puis dessine un canvas par graphique.
  for (const i of [0, 1, 2]) {
    await expect(
      page.locator(`[data-testid="graphique-${i}"] canvas`),
    ).toHaveCount(1);
  }
}

test('le directeur voit ses chiffres clés et trois graphiques dessinés', async ({
  page,
}) => {
  await seConnecter(page, 'directeur.a');
  await attendreTableau(page);

  const inscrits = nombre(
    await page.locator('[data-testid="kpi-inscrits"] .valeur').textContent(),
  );
  expect(inscrits).toBeGreaterThan(0);
  await expect(page.getByTestId('kpi-dossiers')).toContainText('%');
  await expect(page.getByTestId('kpi-familles')).toContainText(
    'familles ont activé leur compte',
  );
  // Les trois cycles ont leur ligne, avec leur nom (la couleur n'est jamais seule).
  for (const cycle of ['Primaire', 'Collège', 'Lycée']) {
    await expect(page.getByTestId('kpi-inscrits')).toContainText(cycle);
  }

  // Un lecteur d'écran lit le résumé de chaque graphique.
  const resumes = page.locator('[data-testid^="graphique-"] [role="img"]');
  await expect(resumes).toHaveCount(3);
  await expect(resumes.first()).toHaveAttribute(
    'aria-label',
    /élèves? inscrits?/,
  );

  // L'accueil garde le profil du compte, sous le tableau de bord.
  await expect(page.getByTestId('moi-roles')).toBeVisible();

  mkdirSync(DOSSIER, { recursive: true });
  await page.screenshot({
    path: `${DOSSIER}/tableau-de-bord-1440.png`,
    fullPage: true,
  });
  await verifierAccessibilite(page, 'Tableau de bord (directeur, 1440 px)');
});

test('« Voir les données » donne le même contenu en tableau, au clavier aussi', async ({
  page,
}) => {
  await seConnecter(page, 'directeur.a');
  await attendreTableau(page);
  const inscrits = nombre(
    await page.locator('[data-testid="kpi-inscrits"] .valeur').textContent(),
  );

  const bouton = page.getByTestId('voir-donnees-0');
  await bouton.focus();
  await expect(bouton).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(bouton).toHaveAttribute('aria-expanded', 'true');

  const table = page.getByTestId('donnees-0');
  await expect(table).toBeVisible();
  const valeurs = await table.locator('tbody td.num').allTextContents();
  const somme = valeurs.reduce((s, v) => s + Number(v), 0);
  expect(somme, "la somme des niveaux est le nombre d'inscrits").toBe(inscrits);
  await expect(table.locator('thead th[scope="col"]')).toHaveCount(2);

  await verifierAccessibilite(
    page,
    'Tableau de bord, données du graphique ouvertes',
  );
  await page.keyboard.press('Enter');
  await expect(table).toHaveCount(0);
});

test('les filtres de cycle et de période relancent la demande avec les bons paramètres', async ({
  page,
}) => {
  await seConnecter(page, 'directeur.a');
  await attendreTableau(page);
  const total = nombre(
    await page.locator('[data-testid="kpi-inscrits"] .valeur').textContent(),
  );

  const demande = page.waitForRequest(
    (r) =>
      r.url().includes('/api/v1/statistiques/direction') &&
      r.url().includes('cycle=MOYEN') &&
      r.url().includes('periode=JOURS_90'),
  );
  await page.getByTestId('periode-JOURS_90').click();
  await page.getByTestId('cycle-MOYEN').click();
  await demande;

  const carte = page.locator('[data-testid="kpi-inscrits"]');
  await expect(carte).toContainText('Collège');
  await expect(carte).not.toContainText('Lycée');
  const moyen = nombre(await carte.locator('.valeur').textContent());
  expect(moyen).toBeLessThanOrEqual(total);

  await page.getByTestId('cycle-TOUS').click();
  await expect(carte).toContainText('Lycée');
});

test('à 390 px : pas de défilement horizontal, graphiques empilés, axe', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await seConnecter(page, 'directeur.a');
  await attendreTableau(page);

  const debord = await page.evaluate(
    () =>
      document.documentElement.scrollWidth -
      document.documentElement.clientWidth,
  );
  expect(debord, 'défilement horizontal de la page').toBeLessThanOrEqual(0);
  const a = await page.getByTestId('graphique-0').boundingBox();
  const b = await page.getByTestId('graphique-1').boundingBox();
  expect(b!.y, 'les graphiques sont empilés').toBeGreaterThan(
    a!.y + a!.height - 1,
  );
  expect(a!.width).toBeLessThanOrEqual(390);

  mkdirSync(DOSSIER, { recursive: true });
  await page.screenshot({
    path: `${DOSSIER}/tableau-de-bord-390.png`,
    fullPage: true,
  });
  await verifierAccessibilite(page, 'Tableau de bord (directeur, 390 px)');
});

test('le secrétariat garde l’accueil simple et ne demande aucune statistique', async ({
  page,
}) => {
  const appels: string[] = [];
  page.on('request', (r) => appels.push(r.url()));
  await seConnecter(page, 'secretariat.a');
  await expect(page.getByTestId('moi-roles')).toBeVisible();

  await expect(page.getByTestId('raccourcis')).toBeVisible();
  await expect(page.getByTestId('kpi-inscrits')).toHaveCount(0);
  expect(appels.filter((u) => u.includes('/api/v1/statistiques'))).toEqual([]);
});
