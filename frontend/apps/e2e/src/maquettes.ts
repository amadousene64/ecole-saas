import { Page } from '@playwright/test';
import { mkdirSync } from 'node:fs';

/**
 * Captures à mettre en regard des maquettes de référence (docs/maquettes/). Une capture par écran,
 * nommée d'après la maquette qu'elle reproduit (« 03-liste-eleves »), à la largeur de la maquette.
 * Elles ne sont pas comparées au pixel (données et rendu des polices varient) : elles servent à la
 * relecture, image contre image. Dossier : E2E_DOSSIER_MAQUETTES, sinon dist/.playwright/maquettes.
 */
export const DOSSIER_MAQUETTES =
  process.env['E2E_DOSSIER_MAQUETTES'] || 'dist/.playwright/maquettes';

/** Largeurs des maquettes : bureau (1440 × 900) et téléphone (390 × 844). */
export const BUREAU = { width: 1440, height: 900 } as const;
export const TELEPHONE = { width: 390, height: 844 } as const;

export async function capturerMaquette(page: Page, nom: string): Promise<void> {
  mkdirSync(DOSSIER_MAQUETTES, { recursive: true });
  // Rendu stable : polices chargées, page remontée en haut (sinon la barre du haut, collante,
  // apparaît au milieu d'une capture pleine page), aucune animation en cours.
  await page.evaluate(async () => {
    await document.fonts.ready;
    window.scrollTo(0, 0);
    document.querySelectorAll('*').forEach((e) => {
      if (e.scrollTop) {
        e.scrollTop = 0;
      }
    });
  });
  await page.screenshot({
    path: `${DOSSIER_MAQUETTES}/${nom}.png`,
    fullPage: true,
    animations: 'disabled',
  });
}
