import { expect as expectBase, test } from '@playwright/test';
import { capturerMaquette, TELEPHONE } from './maquettes';
import { exigerInfra, seConnecter } from './support';

/** Espace parent mis en regard des maquettes 19 et 20 (voir maquettes.ts). Lecture seule. */
exigerInfra();

const expect = expectBase.configure({ timeout: 15_000 });

test('19 · fil du parent de démo (téléphone)', async ({ page }) => {
  await page.setViewportSize(TELEPHONE);
  await seConnecter(page, 'parent.demo');
  await expect(page.getByTestId('enfant')).toHaveCount(2);
  await capturerMaquette(page, '19-parent-fil');
});
