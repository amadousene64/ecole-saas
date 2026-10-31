import { expect, test } from '@playwright/test';
import { verifierAccessibilite } from './accessibilite';
import { exigerInfra, seConnecter, verifierProfil } from './support';

// Accessibilité de l'espace parent (axe, WCAG 2.1 AA, dont les contrastes), sur téléphone : c'est
// son usage principal. Barre de navigation basse à quatre entrées.
exigerInfra();

test.use({ viewport: { width: 390, height: 844 } });

test("l'espace parent respecte WCAG 2.1 AA sur téléphone", async ({ page }) => {
  await seConnecter(page, 'eleve.b');
  await verifierProfil(page, 'École Exemple B', ['Élève']);
  await verifierAccessibilite(page, 'Accueil');

  const barre = page.getByRole('navigation', { name: 'Navigation principale' });
  await expect(barre.getByRole('link')).toHaveText([
    'Accueil',
    'Notes',
    'Paiements',
    'Messages',
  ]);
  for (const entree of ['Notes', 'Paiements', 'Messages']) {
    await barre.getByRole('link', { name: entree }).click();
    await expect(
      page.getByRole('heading', { level: 1, name: entree }),
    ).toBeVisible();
    await expect(barre.getByRole('link', { name: entree })).toHaveAttribute(
      'aria-current',
      'page',
    );
    await verifierAccessibilite(page, entree);
  }
});
