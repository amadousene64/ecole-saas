import { expect as expectBase, Page, test } from '@playwright/test';
import { mkdirSync } from 'node:fs';
import { verifierAccessibilite } from './accessibilite';
import { exigerInfra, seConnecter, lienMenu } from './support';

/**
 * Responsive du back-office aux trois largeurs de référence (téléphone 390, tablette 768, bureau
 * 1440) : mode de la navigation, tableaux en cartes, bouton d'action flottant, aucun défilement
 * horizontal, axe sans violation. Les captures d'écran sont enregistrées pour la relecture
 * (dist/.playwright/captures) ; elles ne sont pas comparées au pixel (rendu variable d'un poste à
 * l'autre) : ce sont les vérifications de mise en page ci-dessous qui font foi.
 */
exigerInfra();

const expect = expectBase.configure({ timeout: 15_000 });

const DOSSIER = 'dist/.playwright/captures';
const LARGEURS = [
  { nom: '390', largeur: 390, hauteur: 844 },
  { nom: '768', largeur: 768, hauteur: 1024 },
  { nom: '1440', largeur: 1440, hauteur: 900 },
] as const;

async function capturer(page: Page, nom: string): Promise<void> {
  mkdirSync(DOSSIER, { recursive: true });
  await page.screenshot({ path: `${DOSSIER}/${nom}.png`, fullPage: true });
}

async function pasDeDefilementHorizontal(page: Page): Promise<void> {
  const debord = await page.evaluate(
    () =>
      document.documentElement.scrollWidth -
      document.documentElement.clientWidth,
  );
  expect(debord, 'défilement horizontal de la page').toBeLessThanOrEqual(0);
}

for (const { nom, largeur, hauteur } of LARGEURS) {
  test(`secrétariat à ${nom} px : navigation, cartes, bouton flottant, axe`, async ({
    page,
  }) => {
    await page.setViewportSize({ width: largeur, height: hauteur });
    await seConnecter(page, 'secretariat.a');
    await expect(page.getByTestId('moi-roles')).toBeVisible();

    // --- Accueil ---
    await pasDeDefilementHorizontal(page);
    const tiroir = page.getByTestId('ouvrir-tiroir');
    const barre = page.locator('mat-sidenav');
    if (largeur < 600) {
      await expect(tiroir).toBeVisible();
      await expect(barre).toBeHidden();
    } else {
      await expect(tiroir).toHaveCount(0);
      await expect(barre).toBeVisible();
      const largeurBarre = (await barre.boundingBox())?.width ?? 0;
      if (largeur < 1024) {
        expect(largeurBarre, 'barre réduite aux icônes').toBeLessThan(100);
      } else {
        expect(largeurBarre, 'barre complète').toBeGreaterThan(200);
      }
    }
    await capturer(page, `secretariat-accueil-${nom}`);
    await verifierAccessibilite(page, `Accueil (secrétariat, ${nom} px)`);

    // Le tiroir s'ouvre, montre le menu, et se referme à la navigation.
    if (largeur < 600) {
      await tiroir.click();
      await expect(lienMenu(page, 'Élèves et inscriptions')).toBeVisible();
      await capturer(page, `secretariat-tiroir-${nom}`);
      await verifierAccessibilite(page, `Tiroir de navigation (${nom} px)`);
      await lienMenu(page, 'Élèves et inscriptions').click();
    } else {
      await lienMenu(page, 'Élèves et inscriptions').click();
    }

    // --- Liste des élèves : tableau (>= 600) ou cartes (< 600) ---
    await expect(page.getByTestId('table-eleves')).toBeVisible();
    await expect(page.locator('[data-testid^="eleve-"]').first()).toBeVisible();
    await pasDeDefilementHorizontal(page);
    const ligne = page.locator('[data-testid^="eleve-"]').first();
    const affichage = await ligne.evaluate((e) => getComputedStyle(e).display);
    if (largeur < 600) {
      expect(affichage, 'ligne affichée en carte').toBe('block');
      // Chaque cellule répète son intitulé.
      await expect(ligne.locator('td[data-label="Matricule"]')).toHaveCount(1);
      // Le bouton d'action principale est flottant.
      const action = page.getByTestId('nouvel-eleve');
      await expect(action).toBeVisible();
      const position = await action
        .locator('xpath=ancestor::ecole-action-principale')
        .evaluate((e) => getComputedStyle(e).position);
      expect(position).toBe('fixed');
    } else {
      expect(affichage, 'ligne de tableau').toBe('table-row');
      const position = await page
        .getByTestId('nouvel-eleve')
        .locator('xpath=ancestor::ecole-action-principale')
        .evaluate((e) => getComputedStyle(e).position);
      expect(position).not.toBe('fixed');
    }
    await capturer(page, `secretariat-eleves-${nom}`);
    await verifierAccessibilite(page, `Liste des élèves (${nom} px)`);

    // --- Fiche : onglets, formulaires ---
    await ligne.getByRole('link').click();
    await expect(page.getByTestId('titre-fiche')).toBeVisible();
    await pasDeDefilementHorizontal(page);
    await capturer(page, `secretariat-fiche-${nom}`);
    await verifierAccessibilite(page, `Fiche élève (${nom} px)`);
  });
}

test('directeur à 390 px : sections du menu et accès aux paramètres', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await seConnecter(page, 'directeur.a');
  await page.getByTestId('ouvrir-tiroir').click();
  await expect(page.getByText('Classes et années')).toBeVisible();
  await expect(page.getByText('Paramètres', { exact: true })).toBeVisible();
  await lienMenu(page, 'Établissement').click();
  await expect(page.getByTestId('champ-nom')).toBeVisible();
  await pasDeDefilementHorizontal(page);
  await capturer(page, 'directeur-parametres-390');
  await verifierAccessibilite(page, 'Paramètres (directeur, 390 px)');
});
