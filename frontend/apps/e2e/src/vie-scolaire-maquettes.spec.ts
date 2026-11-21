import { expect as expectBase, test } from '@playwright/test';
import { BUREAU, capturerMaquette } from './maquettes';
import { exigerInfra, seConnecter } from './support';

/** Poste de vie scolaire mis en regard de la maquette 17 (voir maquettes.ts). Lecture seule. */
exigerInfra();

const expect = expectBase.configure({ timeout: 15_000 });

test('17 · accueil du surveillant', async ({ page }) => {
  await page.setViewportSize(BUREAU);
  await seConnecter(page, 'surveillant.a');
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  await capturerMaquette(page, '17-vie-scolaire');
});
