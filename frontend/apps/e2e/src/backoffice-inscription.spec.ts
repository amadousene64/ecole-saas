import {
  BrowserContext,
  expect as expectBase,
  Page,
  request,
  test,
} from '@playwright/test';
import { verifierAccessibilite } from './accessibilite';
import {
  creerUtilisateurSansEcole,
  ECOLE_A_ID,
  exigerInfra,
  seConnecter,
  URL_API,
} from './support';

/**
 * Module inscription, de bout en bout (Keycloak, API et deux applications réelles) :
 *   1. le secrétariat inscrit un élève, lui ajoute un responsable et génère un code d'activation ;
 *   2. le parent active son compte avec ce code et ne voit que son enfant ;
 *   3. le censeur consulte les données sensibles (une requête de lecture, seulement au clic) ;
 *   4. le directeur bloque l'accès du parent : sa requête suivante est refusée, sans reconnexion.
 *
 * Les étapes se suivent (un seul élève, un seul parent) : mode série. Noms uniques par exécution.
 */
exigerInfra();

// Premier chargement des écrans paresseux sur un serveur de développement : plus patient que 5 s.
const expect = expectBase.configure({ timeout: 15_000 });

// Les noms ne contiennent que des lettres (règle du serveur) : chiffres remplacés par des lettres.
const suffixe = Date.now()
  .toString(36)
  .toUpperCase()
  .replace(/\d/g, (c) => 'GHIJKLMNOP'[Number(c)]);
const PRENOM_ELEVE = `Awa${suffixe}`;
const NOM_ELEVE = 'Testeur-Fictif';
// Le serveur met les noms de famille en majuscules.
const NOM_RESPONSABLE = `Parent${suffixe}`.toUpperCase();
const UTILISATEUR_PARENT = `parent.e2e.${suffixe.toLowerCase()}`;
const SANTE = `Asthme fictif ${suffixe}`;
const URL_PARENT = 'http://localhost:4201';

test.describe.configure({ mode: 'serial' });

let code = '';
let eleveId = '';
let contextParent: BrowserContext;
let pageParent: Page;
/** En-têtes réellement envoyés par l'application parent (jeton et école), pour la dernière étape. */
let entetesParent: Record<string, string> = {};

test.afterAll(async () => {
  await contextParent?.close();
});

/** Toutes les adresses appelées par les navigateurs du scénario (API et pages de l'application). */
const urlsAppelees: string[] = [];

function surveillerUrls(page: Page): void {
  page.on('request', (req) => urlsAppelees.push(req.url()));
  page.on('framenavigated', (frame) => urlsAppelees.push(frame.url()));
}

/** Aucun nom d'élève ni de responsable dans une URL : ni requête d'API, ni adresse de l'application. */
function verifierAucunNomDansLesUrls(): void {
  expect(urlsAppelees.length).toBeGreaterThan(20);
  const interdits = [PRENOM_ELEVE, NOM_ELEVE, NOM_RESPONSABLE];
  for (const url of urlsAppelees) {
    const lisible = decodeURIComponent(url).toLowerCase();
    for (const nom of interdits) {
      expect(lisible, `nom dans l'URL ${url}`).not.toContain(nom.toLowerCase());
    }
  }
}

async function ouvrirFiche(page: Page): Promise<void> {
  await page.getByRole('link', { name: 'Élèves', exact: true }).click();
  await page.getByTestId('filtre-recherche').fill(PRENOM_ELEVE);
  await expect(
    page.getByRole('link', { name: new RegExp(PRENOM_ELEVE) }),
  ).toBeVisible();
  await page.getByRole('link', { name: new RegExp(PRENOM_ELEVE) }).click();
  await expect(page.getByTestId('titre-fiche')).toContainText(PRENOM_ELEVE);
  // Le terme cherché n'est jamais reporté dans l'adresse de l'application.
  expect(decodeURIComponent(page.url()).toLowerCase()).not.toContain(
    PRENOM_ELEVE.toLowerCase(),
  );
  eleveId = page.url().split('/').pop() ?? '';
}

test("le secrétariat inscrit un élève et génère un code d'activation", async ({
  page,
}) => {
  surveillerUrls(page);
  await seConnecter(page, 'secretariat.a');
  await page.getByRole('link', { name: 'Élèves', exact: true }).click();
  await expect(page.getByTestId('table-eleves')).toBeVisible();
  await verifierAccessibilite(page, 'Liste des élèves');

  // Création de l'élève.
  await page.getByTestId('nouvel-eleve').click();
  await page.getByTestId('champ-prenoms').fill(PRENOM_ELEVE);
  await page.getByTestId('champ-nom').fill(NOM_ELEVE);
  await page.getByTestId('champ-naissance').fill('2012-05-14');
  await page.getByTestId('champ-lieu').fill('Ville Fictive');
  await verifierAccessibilite(page, 'Nouvel élève');
  await page.getByTestId('creer-eleve').click();
  await expect(page.getByTestId('titre-fiche')).toContainText(PRENOM_ELEVE);
  eleveId = page.url().split('/').pop() ?? '';
  expect(eleveId).toMatch(/^[0-9a-f-]{36}$/);

  // Inscription dans une classe de l'année en cours.
  await page.getByRole('tab', { name: 'Scolarité' }).click();
  await page.getByTestId('action-inscrire').click();
  await page.getByTestId('champ-classe').click();
  await page.getByRole('option').first().click();
  await page.getByTestId('confirmer-inscription').click();
  await expect(page.getByText('Élève inscrit.')).toBeVisible();
  await expect(page.getByTestId('table-inscriptions')).toContainText('Inscrit');

  // Données sensibles : le secrétariat saisit mais ne peut pas relire.
  await page.getByRole('tab', { name: 'Identité' }).click();
  await expect(page.getByTestId('afficher-sante')).toHaveCount(0);
  await page.getByTestId('saisie-sante').fill(SANTE);
  await page.getByTestId('enregistrer-sensibles').click();
  await expect(page.getByTestId('etat-sensibles')).toContainText(
    'Santé : renseigné',
  );
  await expect(page.getByText(SANTE)).toHaveCount(0);
  await verifierAccessibilite(
    page,
    'Fiche élève, onglet Identité (secrétariat)',
  );

  // Responsable, puis code d'activation affiché une seule fois.
  await page.getByRole('tab', { name: 'Responsables' }).click();
  await page.getByTestId('resp-prenoms').fill('Mame');
  await page.getByTestId('resp-nom').fill(NOM_RESPONSABLE);
  await page.getByTestId('resp-telephone').fill('+221700000099');
  await page.getByTestId('ajouter-responsable').click();
  await expect(page.getByTestId(`lien-${NOM_RESPONSABLE}`)).toBeVisible();
  await verifierAccessibilite(page, 'Fiche élève, onglet Responsables');

  await page.getByTestId(`code-${NOM_RESPONSABLE}`).click();
  const valeur = page.getByTestId('code-valeur');
  await expect(valeur).toBeVisible();
  code = ((await valeur.textContent()) ?? '').trim();
  expect(code).toMatch(/^[0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4}$/);
  await verifierAccessibilite(page, "Dialogue du code d'activation");
  await page.getByTestId('fermer-code').click();
  // Une fois fermé, le code n'est plus nulle part dans la page.
  await expect(page.getByText(code)).toHaveCount(0);

  // Pièces : dépôt avec statut d'analyse (la vérification antivirus se termine hors du test).
  await page.getByRole('tab', { name: 'Pièces' }).click();
  await page.getByTestId('champ-fichier-piece').setInputFiles({
    name: 'acte.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.4\n% acte fictif e2e\n%%EOF'),
  });
  await page.getByTestId('deposer-piece').click();
  await expect(page.getByTestId('piece-ACTE_NAISSANCE')).toContainText(
    /Analyse en cours|Vérifiée/,
  );
  await verifierAccessibilite(page, 'Fiche élève, onglet Pièces');
});

test('le parent active son compte avec le code et ne voit que son enfant', async ({
  browser,
}) => {
  test.skip(!code, "pas de code : l'étape précédente a échoué");
  const api = await request.newContext();
  await creerUtilisateurSansEcole(api, UTILISATEUR_PARENT);
  await api.dispose();

  contextParent = await browser.newContext({ baseURL: URL_PARENT });
  pageParent = await contextParent.newPage();
  pageParent.on('request', async (req) => {
    if (req.url().includes('/api/v1/')) {
      const entetes = await req.allHeaders();
      if (entetes['authorization']) {
        entetesParent = {
          Authorization: entetes['authorization'],
          ...(entetes['x-tenant-id']
            ? { 'X-Tenant-Id': entetes['x-tenant-id'] }
            : {}),
        };
      }
    }
  });

  surveillerUrls(pageParent);
  await seConnecter(pageParent, UTILISATEUR_PARENT);
  // Aucune école : l'espace parent mène à l'activation.
  await expect(
    pageParent.getByRole('heading', { name: 'Activer mon compte' }),
  ).toBeVisible();
  await verifierAccessibilite(pageParent, 'Activation du compte parent');

  // Un mauvais code est refusé sans détail.
  await pageParent.getByTestId('champ-ecole').fill(ECOLE_A_ID);
  await pageParent.getByTestId('champ-code').fill('0000-0000-0000');
  await pageParent.getByTestId('activer').click();
  await expect(pageParent.getByTestId('erreur-activation')).toContainText(
    'pas valable',
  );

  await pageParent.getByTestId('champ-code').fill(code.toLowerCase());
  await pageParent.getByTestId('activer').click();

  await expect(pageParent).toHaveURL(/\/enfants$/);
  const ecole = pageParent.getByTestId('ecole-LYCEE-A');
  await expect(ecole).toContainText(PRENOM_ELEVE);
  // Un seul enfant : pas les élèves de démonstration d'un autre responsable, ni ceux de l'école.
  await expect(pageParent.getByTestId('enfant')).toHaveCount(1);
  await expect(pageParent.getByText('Élève Démo')).toHaveCount(0);
  await verifierAccessibilite(pageParent, 'Mes enfants');

  // L'enfant est lisible par son parent, avec le jeton que l'application vient d'utiliser.
  expect(entetesParent['Authorization']).toMatch(/^Bearer /);
  const lecture = await contextParent.request.get(
    `${URL_API}/api/v1/eleves/${eleveId}`,
    {
      headers: entetesParent,
    },
  );
  expect(lecture.status(), "lecture de l'enfant par son parent").toBe(200);
});

test('le censeur consulte des données sensibles : une lecture, seulement au clic', async ({
  page,
}) => {
  test.skip(!eleveId, "pas d'élève : l'étape précédente a échoué");
  const lectures: string[] = [];
  page.on('request', (req) => {
    if (
      req.method() === 'GET' &&
      /\/donnees-sensibles$/.test(new URL(req.url()).pathname)
    ) {
      lectures.push(req.url());
    }
  });

  surveillerUrls(page);
  await seConnecter(page, 'censeur.a');
  await ouvrirFiche(page);
  await expect(page.getByTestId('mention-journal')).toContainText('journal');
  await expect(page.getByTestId('bloc-sensibles')).toContainText(
    'Valeur masquée',
  );
  await expect(page.getByText(SANTE)).toHaveCount(0);
  expect(lectures, 'aucune lecture avant le clic').toHaveLength(0);
  await verifierAccessibilite(
    page,
    'Fiche élève, données sensibles masquées (censeur)',
  );

  const reponse = page.waitForResponse(
    (r) =>
      r.request().method() === 'GET' &&
      /\/donnees-sensibles$/.test(new URL(r.url()).pathname),
  );
  await page.getByTestId('afficher-sante').click();
  expect((await reponse).status()).toBe(200);
  expect(lectures, 'une seule lecture, au clic').toHaveLength(1);
  await expect(page.getByTestId('bloc-sensibles')).toContainText(SANTE);
  await verifierAccessibilite(
    page,
    'Fiche élève, données sensibles affichées (censeur)',
  );

  // Le censeur ne dépose rien et n'agit pas sur les responsables.
  await expect(page.getByTestId('enregistrer-sensibles')).toHaveCount(0);
  await expect(page.getByRole('tab', { name: 'Responsables' })).toHaveCount(0);
});

test("le directeur bloque l'accès d'un parent : sa requête suivante est refusée", async ({
  page,
}) => {
  test.skip(
    !eleveId || !pageParent,
    "pas de parent actif : l'étape précédente a échoué",
  );
  surveillerUrls(page);
  await seConnecter(page, 'directeur.a');
  await ouvrirFiche(page);
  await page.getByRole('tab', { name: 'Responsables' }).click();

  const interrupteur = page
    .getByTestId(`acces-${NOM_RESPONSABLE}`)
    .getByRole('switch');
  await expect(interrupteur).toBeEnabled();
  await interrupteur.click();
  await expect(page.getByTestId('acces-bloque')).toContainText('Accès bloqué');

  // Effet immédiat : le parent, toujours connecté avec le même jeton, est refusé tout de suite.
  const refus = await contextParent.request.get(
    `${URL_API}/api/v1/eleves/${eleveId}`,
    {
      headers: entetesParent,
    },
  );
  expect(refus.status(), "lecture de l'enfant après le blocage").toBe(403);

  await pageParent.goto('/enfants');
  await expect(pageParent.getByTestId('aucun-enfant')).toBeVisible();
  await expect(pageParent.getByText(PRENOM_ELEVE)).toHaveCount(0);

  // Le directeur peut rétablir l'accès, et l'effet est tout aussi immédiat.
  await interrupteur.click();
  await expect(page.getByTestId('acces-bloque')).toHaveCount(0);
  verifierAucunNomDansLesUrls();
  const retabli = await contextParent.request.get(
    `${URL_API}/api/v1/eleves/${eleveId}`,
    {
      headers: entetesParent,
    },
  );
  expect(retabli.status()).toBe(200);
});
