import { expect as expectBase, test } from '@playwright/test';
import { verifierAccessibilite } from './accessibilite';
import { exigerInfra, seConnecter } from './support';

/**
 * Import d'élèves : rapport ligne par ligne avant toute écriture. Ce scénario n'importe rien (les
 * fichiers contiennent des erreurs) : il ne laisse aucune donnée et se rejoue à volonté.
 */
exigerInfra();

// Premier chargement des écrans paresseux sur un serveur de développement : plus patient que 5 s.
const expect = expectBase.configure({ timeout: 15_000 });

const FICHIER_AVEC_ERREURS = [
  'prenoms;nom;sexe;date_naissance;lieu_naissance',
  'Awa;Import-Fictif;F;2012-05-14;Ville Fictive',
  'Bintou;Import-Fictif;X;pas-une-date;Ville Fictive',
].join('\n');

test('le rapport détaille chaque ligne, et un fichier en erreur ne peut pas être importé', async ({
  page,
}) => {
  await seConnecter(page, 'secretariat.a');
  await page.getByRole('link', { name: 'Élèves', exact: true }).click();
  await page.getByTestId('lien-import').click();
  await expect(
    page.getByRole('heading', { name: 'Importer des élèves' }),
  ).toBeVisible();
  await verifierAccessibilite(page, "Import d'élèves (avant dépôt)");

  await page.getByTestId('champ-fichier-import').setInputFiles({
    name: 'eleves.csv',
    mimeType: 'text/csv',
    buffer: Buffer.from(FICHIER_AVEC_ERREURS, 'utf-8'),
  });
  await page.getByTestId('verifier-import').click();

  await expect(page.getByTestId('table-rapport')).toBeVisible();
  await expect(page.getByTestId('ligne-3')).toContainText('Erreur');
  await expect(page.getByTestId('synthese-import')).toContainText(
    'Corrigez le fichier',
  );
  await expect(page.getByTestId('confirmer-import')).toBeDisabled();
  await verifierAccessibilite(page, "Import d'élèves (rapport avec erreurs)");

  // Un mauvais format est refusé avant tout envoi.
  await page.getByTestId('champ-fichier-import').setInputFiles({
    name: 'eleves.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.4'),
  });
  await expect(page.getByTestId('erreur-import')).toContainText('CSV');
});

test("le censeur lit la liste mais n'a ni création ni import", async ({
  page,
}) => {
  await seConnecter(page, 'censeur.a');
  await page.getByRole('link', { name: 'Élèves', exact: true }).click();
  await expect(page.getByTestId('table-eleves')).toBeVisible();
  await expect(page.getByTestId('nouvel-eleve')).toHaveCount(0);
  await expect(page.getByTestId('lien-import')).toHaveCount(0);
  await verifierAccessibilite(page, 'Liste des élèves (censeur)');
});
