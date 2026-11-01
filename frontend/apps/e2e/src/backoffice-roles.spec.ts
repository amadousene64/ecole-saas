import { expect as expectBase, Page, test } from '@playwright/test';
import { choisirEcole, exigerInfra, seConnecter } from './support';

/**
 * Connexion de chaque rôle : on ouvre TOUJOURS le back-office (4200), comme un utilisateur qui
 * tape l'adresse de l'école, et chacun doit arriver dans l'application de son rôle, sur le bon
 * accueil et avec le bon menu, sans jamais passer par une page « Accès refusé ».
 */
exigerInfra();

const expect = expectBase.configure({ timeout: 15_000 });

const APPS = {
  backoffice: /localhost:4200/,
  'vie-scolaire': /localhost:4202/,
  parent: /localhost:4201/,
} as const;

async function menu(page: Page): Promise<string[]> {
  const liens = page
    .getByRole('navigation', { name: 'Navigation principale' })
    .getByRole('link');
  await expect(liens.first()).toBeVisible();
  return (await liens.allTextContents()).map((t) => t.trim());
}

async function aucunAccesRefuse(page: Page): Promise<void> {
  await expect(page.getByRole('heading', { name: 'Accès refusé' })).toHaveCount(
    0,
  );
}

const PERSONNEL = [
  {
    utilisateur: 'directeur.a',
    role: 'Directeur',
    menu: [
      'Accueil',
      'Élèves',
      'Classes et salles',
      'Années et périodes',
      'Établissement',
      'Structure pédagogique',
    ],
  },
  {
    utilisateur: 'secretariat.a',
    role: 'Secrétariat',
    menu: [
      'Accueil',
      'Élèves et inscriptions',
      'Import',
      'Classes et salles',
      'Années, périodes et calendrier',
      'Profil et logo',
      'Niveaux et séries',
    ],
  },
  {
    utilisateur: 'censeur.a',
    role: 'Censeur',
    menu: ['Accueil', 'Élèves', 'Classes'],
  },
  {
    utilisateur: 'comptable.a',
    role: 'Comptable',
    menu: ['Accueil', 'Élèves et payeurs'],
  },
] as const;

for (const cas of PERSONNEL) {
  test(`${cas.role} : arrive au back-office avec son menu`, async ({
    page,
  }) => {
    await seConnecter(page, cas.utilisateur);

    await expect(page).toHaveURL(APPS.backoffice);
    await expect(page.getByTestId('moi-roles').locator('li')).toHaveText([
      cas.role,
    ]);
    expect(await menu(page)).toEqual([...cas.menu]);
    await expect(page.getByTestId('menu-compte')).toContainText(cas.role);
    await aucunAccesRefuse(page);
  });
}

test('surveillant : renvoyé automatiquement vers la vie scolaire', async ({
  page,
}) => {
  await seConnecter(page, 'surveillant.a');

  await expect(page).toHaveURL(APPS['vie-scolaire']);
  await expect(page.getByTestId('moi-roles').locator('li')).toHaveText([
    'Surveillant',
  ]);
  expect(await menu(page)).toEqual(['Accueil']);
  await aucunAccesRefuse(page);
  // L'adresse ne garde ni l'école ni l'origine : elles sont consommées à l'arrivée.
  expect(new URL(page.url()).search).toBe('');
});

test('parent de démo : renvoyé vers l’espace parent, voit ses deux enfants', async ({
  page,
}) => {
  await seConnecter(page, 'parent.demo');

  await expect(page).toHaveURL(APPS.parent);
  await expect(page.getByTestId('enfant')).toHaveCount(2);
  await expect(page.getByTestId('moi-roles').locator('li')).toHaveText([
    'Parent',
  ]);
  // Une seule entrée de menu : pas de barre du bas.
  await expect(page.locator('nav.barre-basse')).toHaveCount(0);
  await aucunAccesRefuse(page);
});

test('élève (une seule école) : renvoyé vers l’espace parent', async ({
  page,
}) => {
  await seConnecter(page, 'eleve.b');
  await expect(page).toHaveURL(APPS.parent);
  await expect(page.getByTestId('moi-roles').locator('li')).toHaveText([
    'Élève',
  ]);
  await aucunAccesRefuse(page);
});

test('enseignant en A, parent en B : chaque école mène à la bonne application', async ({
  page,
}) => {
  await seConnecter(page, 'enseignant.parent');

  await choisirEcole(page, 'Lycée Exemple A');
  await expect(page).toHaveURL(APPS['vie-scolaire']);
  await expect(page.getByTestId('moi-roles').locator('li')).toHaveText([
    'Enseignant',
  ]);

  // « Changer d'école » depuis la vie scolaire, puis l'école B : parent, donc l'espace parent.
  await page.getByTestId('menu-compte').click();
  await page.getByRole('menuitem', { name: "Changer d'école" }).click();
  await choisirEcole(page, 'École Exemple B');
  await expect(page).toHaveURL(APPS.parent);
  await expect(page.getByTestId('moi-roles').locator('li')).toHaveText([
    'Parent',
  ]);
  await aucunAccesRefuse(page);
});

test('arrivé dans la mauvaise application, on est renvoyé dans la sienne', async ({
  page,
}) => {
  await seConnecter(page, 'directeur.a');
  await expect(page).toHaveURL(APPS.backoffice);

  // Le directeur ouvre l'adresse de l'espace parent à la main.
  await page.goto('http://localhost:4201/');
  await expect(page).toHaveURL(APPS.backoffice);
  await expect(page.getByTestId('moi-roles').locator('li')).toHaveText([
    'Directeur',
  ]);
  await aucunAccesRefuse(page);
});

test('une URL interdite tapée à la main ramène à l’accueil avec un message discret', async ({
  page,
}) => {
  await seConnecter(page, 'comptable.a');
  await expect(page.getByTestId('moi-roles')).toBeVisible();

  await page.goto('/eleves/import');

  await expect(page).toHaveURL(/localhost:4200\/$/);
  await expect(page.getByTestId('moi-roles')).toBeVisible();
  await expect(page.getByTestId('notification-info')).toContainText(
    'pas disponible pour votre rôle',
  );
  await aucunAccesRefuse(page);
});

test('une page dont le module n’existe pas encore ramène à l’accueil', async ({
  page,
}) => {
  await seConnecter(page, 'directeur.a');
  await page.goto('/finances');
  await expect(page).toHaveURL(/localhost:4200\/$/);
  await aucunAccesRefuse(page);
});

test('le comptable n’a pas Paramètres : l’adresse tapée à la main le ramène à l’accueil', async ({
  page,
}) => {
  await seConnecter(page, 'comptable.a');
  await page.goto('/etablissement/parametres');
  await expect(page).toHaveURL(/localhost:4200\/$/);
  await expect(page.getByTestId('notification-info')).toBeVisible();
});

test('recherche globale (Ctrl+K) : pages et élèves selon le rôle', async ({
  page,
}) => {
  await seConnecter(page, 'secretariat.a');
  await expect(page.getByTestId('moi-roles')).toBeVisible();

  await page.keyboard.press('Control+k');
  const champ = page.getByTestId('terme-recherche-globale');
  await expect(champ).toBeFocused();

  // Les pages du menu (jamais une page interdite).
  await champ.fill('impo');
  await expect(page.getByTestId('resultat-page-import')).toBeVisible();
  await champ.fill('param');
  await expect(page.getByText('Aucun résultat')).toBeVisible();

  // Les élèves : le terme part dans le corps d'un POST, jamais dans une URL.
  const urls: string[] = [];
  page.on('request', (r) => urls.push(r.url()));
  const requete = page.waitForRequest(
    (r) =>
      r.url().endsWith('/api/v1/eleves/recherche') && r.method() === 'POST',
  );
  await champ.fill('Fictif Un');
  expect((await requete).postDataJSON()).toMatchObject({
    recherche: 'Fictif Un',
  });
  await expect(page.getByText('Fictif Un Élève Démo').first()).toBeVisible();
  expect(urls.some((u) => decodeURIComponent(u).includes('Fictif'))).toBe(
    false,
  );

  await page.getByText('Fictif Un Élève Démo').first().click();
  await expect(page).toHaveURL(/\/eleves\/[0-9a-f-]{36}$/);
});

test('menu du compte : ouvert au clavier, focus sur le premier élément, Échap le referme', async ({
  page,
}) => {
  await seConnecter(page, 'directeur.a');
  // Le menu se referme à chaque navigation : on attend la fin des redirections de connexion.
  await expect(page.getByTestId('moi-roles')).toBeVisible();
  const compte = page.getByTestId('menu-compte');
  await expect(compte).toBeVisible();
  await compte.focus();
  await page.keyboard.press('Enter');

  await expect(compte).toHaveAttribute('aria-expanded', 'true');
  const sortie = page.getByRole('menuitem', { name: 'Se déconnecter' });
  await expect(sortie).toBeFocused();

  await page.keyboard.press('Escape');
  await expect(page.getByRole('menu')).toHaveCount(0);
  await expect(compte).toBeFocused();
  await expect(compte).toHaveAttribute('aria-expanded', 'false');

  // Un clic ailleurs referme aussi le menu.
  await compte.click();
  await expect(page.getByRole('menu')).toBeVisible();
  await page.getByRole('heading', { level: 1 }).click();
  await expect(page.getByRole('menu')).toHaveCount(0);
});
