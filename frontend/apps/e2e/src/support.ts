import { APIRequestContext, expect, Page, test } from '@playwright/test';

/**
 * Prérequis : `docker compose -f infra/docker-compose.yml up -d` puis le backend en profil dev
 * (`./mvnw spring-boot:run`) sur le port 8080. Comptes fictifs du realm de dev (infra/keycloak) :
 * le mot de passe vient de E2E_MOT_DE_PASSE (par défaut, celui, public, du realm de dev).
 */
// `||` et non `??` : en CI, un secret non défini arrive sous forme de chaîne VIDE, pas absente.
const MOT_DE_PASSE = process.env['E2E_MOT_DE_PASSE'] || 'Passer-2026-dev';
const KEYCLOAK = process.env['E2E_KEYCLOAK'] || 'http://localhost:8180';
const API = process.env['E2E_API'] || 'http://localhost:8080';

/** Échoue tout de suite, avec un message clair, si l'infra n'est pas démarrée. */
export function exigerInfra(): void {
  test.beforeAll(async ({ request }) => {
    for (const [nom, url] of [
      ['Keycloak', `${KEYCLOAK}/realms/ecole`],
      ['API', `${API}/actuator/health`],
    ]) {
      const reponse = await request.get(url).catch(() => null);
      if (!reponse?.ok()) {
        throw new Error(
          `${nom} injoignable (${url}) : démarrez l'infra et le backend en profil dev.`,
        );
      }
    }
  });
}

export async function seConnecter(
  page: Page,
  utilisateur: string,
): Promise<void> {
  await page.goto('/');
  // Redirection vers Keycloak (code + PKCE).
  await page.waitForURL(/\/realms\/ecole\/protocol\/openid-connect\/auth/);
  await page.locator('#username').fill(utilisateur);
  await page.locator('#password').fill(MOT_DE_PASSE);
  await page.locator('#kc-login').click();
  // Retour vers l'application, sinon échec explicite (mot de passe refusé…) plutôt qu'un
  // « élément introuvable » plus loin dans le scénario.
  await page
    .waitForURL((url) => !url.pathname.startsWith('/realms/'), {
      timeout: 15_000,
    })
    .catch(async () => {
      const message = await page
        .locator('#input-error, .kc-feedback-text, [class*="alert"]')
        .first()
        .textContent()
        .catch(() => null);
      throw new Error(
        `Connexion Keycloak refusée pour « ${utilisateur} » : ${message?.trim() ?? 'aucun message'}` +
          ' (vérifier E2E_MOT_DE_PASSE).',
      );
    });
}

export async function choisirEcole(page: Page, nom: string): Promise<void> {
  await expect(
    page.getByRole('heading', { name: 'Choisissez votre école' }),
  ).toBeVisible({ timeout: 20_000 });
  await page.getByRole('button', { name: nom }).click();
}

export async function ouvrirMenuUtilisateur(
  page: Page,
  utilisateur: string,
): Promise<void> {
  // Coquille Material : bouton ; coquille légère : <summary>. Dans les deux cas, le nom visible.
  await page.getByText(utilisateur, { exact: true }).first().click();
}

/** « Changer d'école » depuis le menu (menuitem Material ou bouton de la coquille légère). */
export async function changerEcole(
  page: Page,
  utilisateur: string,
): Promise<void> {
  await ouvrirMenuUtilisateur(page, utilisateur);
  await page.getByText("Changer d'école", { exact: true }).click();
}

export async function seDeconnecter(
  page: Page,
  utilisateur: string,
): Promise<void> {
  await ouvrirMenuUtilisateur(page, utilisateur);
  await page.getByText('Se déconnecter', { exact: true }).click();
}

export async function verifierProfil(
  page: Page,
  ecole: string,
  roles: string[],
): Promise<void> {
  await expect(page.getByTestId('moi-ecole')).toHaveText(ecole);
  await expect(page.getByTestId('moi-roles').locator('li')).toHaveText(roles);
}

/** Adresses des trois applications en développement. */
export const APPS = {
  backoffice: /localhost:4200/,
  'vie-scolaire': /localhost:4202/,
  parent: /localhost:4201/,
} as const;

/** L'utilisateur est dans l'application de son rôle, et n'a vu aucune page « Accès refusé ». */
export async function verifierDansApp(
  page: Page,
  app: keyof typeof APPS,
): Promise<void> {
  await expect(page).toHaveURL(APPS[app]);
  await expect(page.getByRole('heading', { name: 'Accès refusé' })).toHaveCount(
    0,
  );
}

/** Identifiant de l'école A de dev (voir backend/src/main/resources/db/dev). */
export const ECOLE_A_ID = '01930000-0000-7000-8000-0000000000a1';

export const URL_API = API;

const ADMIN_KC = process.env['E2E_KEYCLOAK_ADMIN'] || 'admin';
const ADMIN_KC_MOT_DE_PASSE =
  process.env['E2E_KEYCLOAK_ADMIN_MOT_DE_PASSE'] || 'admin-dev';

/**
 * Crée dans le realm de dev un compte tout neuf, sans aucune appartenance : celui d'un parent qui
 * n'a pas encore activé son compte. Un nom unique par exécution rend le scénario rejouable.
 */
export async function creerUtilisateurSansEcole(
  request: APIRequestContext,
  utilisateur: string,
): Promise<void> {
  const jeton = await request.post(
    `${KEYCLOAK}/realms/master/protocol/openid-connect/token`,
    {
      form: {
        grant_type: 'password',
        client_id: 'admin-cli',
        username: ADMIN_KC,
        password: ADMIN_KC_MOT_DE_PASSE,
      },
    },
  );
  expect(jeton.ok(), 'Jeton administrateur Keycloak (dev)').toBe(true);
  const { access_token: acces } = (await jeton.json()) as {
    access_token: string;
  };
  const creation = await request.post(`${KEYCLOAK}/admin/realms/ecole/users`, {
    headers: { Authorization: `Bearer ${acces}` },
    data: {
      username: utilisateur,
      email: `${utilisateur}@exemple.test`,
      firstName: 'Parent',
      lastName: 'Fictif E2E',
      enabled: true,
      emailVerified: true,
      credentials: [
        { type: 'password', value: MOT_DE_PASSE, temporary: false },
      ],
    },
  });
  expect(creation.status(), 'Création du compte parent fictif').toBe(201);
}

/** Lien du menu principal (et non un raccourci de l'accueil qui porte le même intitulé). */
export function lienMenu(page: Page, nom: string, exact = true) {
  return page
    .getByRole('navigation', { name: 'Navigation principale' })
    .getByRole('link', { name: nom, exact });
}

/** Classe réservée au parcours d'inscription : toujours la même, créée si elle manque. */
export const CLASSE_E2E_INSCRIPTION = 'E2E Inscription';

/**
 * Garantit l'existence, dans l'année en cours de l'école A, d'une classe dédiée à l'e2e (grande
 * capacité). Le parcours d'inscription ne dépend ainsi ni des classes du jeu de données de dev, ni de
 * leur remplissage (chaque exécution inscrit un élève de plus). Idempotent : une seule classe, jamais
 * de doublon. Utilise le compte directeur.a avec le client de dev `dev-cli`.
 */
export async function garantirClasseE2E(
  request: APIRequestContext,
): Promise<string> {
  const jeton = await request.post(
    `${KEYCLOAK}/realms/ecole/protocol/openid-connect/token`,
    {
      form: {
        grant_type: 'password',
        client_id: 'dev-cli',
        scope: 'openid',
        username: 'directeur.a',
        password: MOT_DE_PASSE,
      },
    },
  );
  expect(jeton.ok(), 'Jeton directeur.a (dev-cli)').toBe(true);
  const { access_token: acces } = (await jeton.json()) as {
    access_token: string;
  };
  const entetes = {
    Authorization: `Bearer ${acces}`,
    'X-Tenant-Id': ECOLE_A_ID,
  };

  const classes = await request.get(`${API}/api/v1/classes`, {
    headers: entetes,
  });
  expect(classes.ok(), 'Liste des classes').toBe(true);
  const existante = ((await classes.json()) as { nom: string }[]).find(
    (c) => c.nom === CLASSE_E2E_INSCRIPTION,
  );
  if (existante) {
    return CLASSE_E2E_INSCRIPTION;
  }

  const annee = await request.get(`${API}/api/v1/annees-scolaires/en-cours`, {
    headers: entetes,
  });
  expect(annee.ok(), 'Année en cours').toBe(true);
  const niveaux = await request.get(`${API}/api/v1/niveaux`, {
    headers: entetes,
  });
  const cp = ((await niveaux.json()) as { id: string; code: string }[]).find(
    (n) => n.code === 'CP',
  );
  expect(cp, 'Niveau CP (sans série)').toBeTruthy();
  const creation = await request.post(`${API}/api/v1/classes`, {
    headers: entetes,
    data: {
      anneeId: ((await annee.json()) as { id: string }).id,
      niveauId: cp!.id,
      nom: CLASSE_E2E_INSCRIPTION,
      capacite: 500,
    },
  });
  expect(creation.status(), 'Création de la classe e2e').toBe(201);
  return CLASSE_E2E_INSCRIPTION;
}

// --- Pages de connexion Keycloak (thème « ecole ») ---

const MAILPIT = process.env['E2E_MAILPIT'] || 'http://localhost:8025';

/** Adresse de l'API publique de marque, côté page de connexion (même valeur que le thème). */
export const URL_MARQUE = `${API}/api/v1/public/marque`;

/** Échoue clairement si la boîte de réception de développement (Mailpit) n'est pas démarrée. */
export async function exigerMailpit(request: APIRequestContext): Promise<void> {
  const reponse = await request.get(`${MAILPIT}/api/v1/info`).catch(() => null);
  if (!reponse?.ok()) {
    throw new Error(
      `Mailpit injoignable (${MAILPIT}) : docker compose -f infra/docker-compose.yml up -d.`,
    );
  }
}

/** PNG uni, valide, de taille donnée (logo de test sans fichier à versionner). */
export function pngUni(largeur: number, hauteur: number): Buffer {
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const zlib = require('node:zlib') as typeof import('node:zlib');
  const crc = (octets: Buffer): number => {
    let c = ~0;
    for (const o of octets) {
      c ^= o;
      for (let k = 0; k < 8; k++) {
        c = c & 1 ? (c >>> 1) ^ 0xedb88320 : c >>> 1;
      }
    }
    return ~c >>> 0;
  };
  const bloc = (type: string, donnees: Buffer): Buffer => {
    const tete = Buffer.alloc(4);
    tete.writeUInt32BE(donnees.length);
    const corps = Buffer.concat([Buffer.from(type, 'ascii'), donnees]);
    const somme = Buffer.alloc(4);
    somme.writeUInt32BE(crc(corps));
    return Buffer.concat([tete, corps, somme]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(largeur, 0);
  ihdr.writeUInt32BE(hauteur, 4);
  ihdr[8] = 8; // 8 bits
  ihdr[9] = 2; // RVB
  const ligne = Buffer.concat([
    Buffer.from([0]),
    Buffer.from(Array.from({ length: largeur }, () => [31, 107, 79]).flat()),
  ]);
  const brut = Buffer.concat(Array.from({ length: hauteur }, () => ligne));
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    bloc('IHDR', ihdr),
    bloc('IDAT', zlib.deflateSync(brut)),
    bloc('IEND', Buffer.alloc(0)),
  ]);
}

/**
 * Donne un logo à l'école A si elle n'en a pas (le directeur le dépose par l'API) : les pages de
 * connexion affichent alors une vraie marque, quel que soit l'état du jeu de données.
 */
export async function garantirLogoE2E(
  request: APIRequestContext,
): Promise<void> {
  const marque = await request.get(`${URL_MARQUE}?ecole=${ECOLE_A_ID}`);
  expect(marque.ok(), 'Marque publique').toBe(true);
  if (((await marque.json()) as { logoVersion?: string | null }).logoVersion) {
    return;
  }
  const jeton = await request.post(
    `${KEYCLOAK}/realms/ecole/protocol/openid-connect/token`,
    {
      form: {
        grant_type: 'password',
        client_id: 'dev-cli',
        scope: 'openid',
        username: 'directeur.a',
        password: MOT_DE_PASSE,
      },
    },
  );
  const { access_token: acces } = (await jeton.json()) as {
    access_token: string;
  };
  const depot = await request.put(`${API}/api/v1/etablissement/images/LOGO`, {
    headers: { Authorization: `Bearer ${acces}`, 'X-Tenant-Id': ECOLE_A_ID },
    multipart: {
      fichier: {
        name: 'logo.png',
        mimeType: 'image/png',
        buffer: pngUni(64, 64),
      },
    },
  });
  expect(depot.ok(), 'Dépôt du logo de test').toBe(true);
}

/**
 * Dernier courriel reçu par {@code adresse} (Mailpit), attendu jusqu'à 20 s : texte de la
 * première URL de la forme `…/login-actions/action-token…`.
 */
export async function lienDuCourriel(
  request: APIRequestContext,
  adresse: string,
): Promise<{ lien: string; sujet: string }> {
  for (let essai = 0; essai < 40; essai++) {
    const liste = await request.get(
      `${MAILPIT}/api/v1/search?query=${encodeURIComponent('to:' + adresse)}`,
    );
    const messages = (
      (await liste.json()) as { messages?: { ID: string; Subject: string }[] }
    ).messages;
    if (messages?.length) {
      const detail = (await (
        await request.get(`${MAILPIT}/api/v1/message/${messages[0].ID}`)
      ).json()) as { Text: string };
      const lien = /https?:\/\/[^\s"<>]*action-token[^\s"<>]*/.exec(
        detail.Text,
      )?.[0];
      if (lien) {
        return {
          lien: lien.replace(/&amp;/g, '&'),
          sujet: messages[0].Subject,
        };
      }
    }
    await new Promise((resolu) => setTimeout(resolu, 500));
  }
  throw new Error(`Aucun courriel avec lien reçu par ${adresse} (Mailpit).`);
}
