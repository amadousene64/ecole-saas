import { ICONES } from '@ecole/ui';
import {
  ACTIONS,
  PAGES,
  appAAtteindre,
  appPour,
  menuPour,
  pagePermise,
  peut,
  rolePrincipal,
  rolesConnus,
  APP_DU_ROLE,
  IdApp,
  ORDRE_ROLES,
  RoleEcole,
} from '../../index';

/**
 * Matrice rôle × menu × route : la table des droits est la source unique du menu, des gardes et
 * des boutons ; ces tests vérifient qu'elle est cohérente avec elle-même, pour chaque rôle.
 */

const APPS: readonly IdApp[] = ['backoffice', 'vie-scolaire', 'parent'];

describe('table des droits : cohérence', () => {
  it('chaque page a un identifiant unique, un chemin absolu et une icône connue', () => {
    const ids = PAGES.map((p) => p.id);
    expect(new Set(ids).size).toBe(ids.length);
    for (const p of PAGES) {
      expect(p.chemin.startsWith('/'), p.id).toBe(true);
      expect(Object.keys(ICONES), p.id).toContain(p.icone);
      expect(p.roles.length, p.id).toBeGreaterThan(0);
    }
  });

  it("chaque page n'est réservée qu'à des rôles de SON application", () => {
    for (const p of PAGES) {
      for (const role of p.roles) {
        expect(APP_DU_ROLE[role], `${p.id} pour ${role}`).toBe(p.app);
      }
      // Les rôles du menu sont des rôles de la page.
      for (const role of p.menu ?? []) {
        expect(p.roles, `${p.id} menu ${role}`).toContain(role);
      }
    }
  });

  it("chaque rôle a un accueil dans son application, et c'est la route « / »", () => {
    for (const role of ORDRE_ROLES) {
      const accueil = PAGES.filter(
        (p) =>
          p.app === APP_DU_ROLE[role] &&
          p.chemin === '/' &&
          p.disponible &&
          p.roles.includes(role),
      );
      expect(accueil, `accueil de ${role}`).toHaveLength(1);
    }
  });

  it('deux pages disponibles de la même application ne partagent pas un chemin', () => {
    for (const app of APPS) {
      const chemins = PAGES.filter((p) => p.app === app && p.disponible).map(
        (p) => p.chemin,
      );
      expect(new Set(chemins).size, app).toBe(chemins.length);
    }
  });

  it("chaque action n'est ouverte qu'à des rôles du personnel", () => {
    for (const [action, roles] of Object.entries(ACTIONS)) {
      for (const r of roles as readonly RoleEcole[]) {
        expect(APP_DU_ROLE[r], `${action} pour ${r}`).toBe('backoffice');
      }
    }
  });
});

describe('menus par rôle', () => {
  const libelles = (role: RoleEcole, app: IdApp) =>
    menuPour([role], app).map((e) => e.libelle);

  it('directeur', () => {
    expect(libelles('DIRECTEUR', 'backoffice')).toEqual([
      'Accueil',
      'Élèves',
      'Classes et salles',
      'Années et périodes',
      'Établissement',
      'Structure pédagogique',
    ]);
    const sections = menuPour(['DIRECTEUR'], 'backoffice').map((e) => e.groupe);
    expect(sections).toEqual([
      null,
      null,
      'Classes et années',
      'Classes et années',
      'Paramètres',
      'Paramètres',
    ]);
  });

  it('secrétariat', () => {
    expect(libelles('SECRETARIAT', 'backoffice')).toEqual([
      'Accueil',
      'Élèves et inscriptions',
      'Import',
      'Classes et salles',
    ]);
  });

  it('censeur', () => {
    expect(libelles('CENSEUR', 'backoffice')).toEqual(['Accueil', 'Élèves']);
  });

  it('comptable', () => {
    expect(libelles('COMPTABLE', 'backoffice')).toEqual([
      'Accueil',
      'Élèves et payeurs',
    ]);
  });

  it('surveillant et enseignant : accueil de la vie scolaire seulement', () => {
    expect(libelles('SURVEILLANT', 'vie-scolaire')).toEqual(['Accueil']);
    expect(libelles('ENSEIGNANT', 'vie-scolaire')).toEqual(['Accueil']);
  });

  it("parent et élève : un seul accueil (la barre du bas n'apparaît pas)", () => {
    expect(libelles('PARENT', 'parent')).toEqual(['Accueil']);
    expect(libelles('ELEVE', 'parent')).toEqual(['Accueil']);
  });

  it("les modules pas encore construits n'apparaissent dans aucun menu", () => {
    const interdits = [
      'Vie scolaire',
      'Notes et bulletins',
      'Finances',
      'Communication',
      'Notes',
      'Paiements',
      'Messages',
    ];
    for (const role of ORDRE_ROLES) {
      const tous = menuPour([role], APP_DU_ROLE[role]).map((e) => e.libelle);
      for (const interdit of interdits) {
        expect(tous, `${role} voit ${interdit}`).not.toContain(interdit);
      }
    }
  });

  it("un rôle n'a jamais de menu dans une application qui n'est pas la sienne", () => {
    for (const role of ORDRE_ROLES) {
      for (const app of APPS.filter((a) => a !== APP_DU_ROLE[role])) {
        expect(menuPour([role], app), `${role} dans ${app}`).toEqual([]);
      }
    }
  });
});

describe('rôle × page : aucune route interdite atteignable', () => {
  it('chaque entrée de menu est une page permise au rôle, et inversement pour les gardes', () => {
    for (const role of ORDRE_ROLES) {
      const app = APP_DU_ROLE[role];
      for (const entree of menuPour([role], app)) {
        expect(pagePermise(entree.id, [role]), `${role} → ${entree.id}`).toBe(
          true,
        );
      }
      for (const p of PAGES) {
        const permise = pagePermise(p.id, [role]);
        // Permise ⇒ disponible, dans son application, et un de ses rôles.
        if (permise) {
          expect(p.disponible).toBe(true);
          expect(p.app).toBe(app);
          expect(p.roles).toContain(role);
        }
        // Une page absente du menu ou de la table n'est jamais permise.
        if (!p.disponible || !p.roles.includes(role)) {
          expect(permise, `${role} ↛ ${p.id}`).toBe(false);
        }
      }
    }
  });

  it('une page inconnue est refusée à tous', () => {
    for (const role of ORDRE_ROLES) {
      expect(pagePermise('inexistante', [role])).toBe(false);
    }
  });

  it('les actions de la table sont cohérentes avec les pages : pas de bouton sans page', () => {
    // Créer / importer un élève suppose d'avoir la page correspondante.
    for (const role of ORDRE_ROLES) {
      if (peut('eleves.creer', [role])) {
        expect(pagePermise('eleves-nouveau', [role])).toBe(true);
      }
      if (peut('eleves.importer', [role])) {
        expect(pagePermise('import', [role])).toBe(true);
      }
      if (
        peut('eleves.ecrire', [role]) ||
        peut('eleves.sensible.lire', [role])
      ) {
        expect(pagePermise('eleves', [role]), role).toBe(true);
      }
      if (peut('etablissement.direction', [role])) {
        expect(pagePermise('annees', [role])).toBe(true);
      }
    }
  });
});

describe("redirection vers l'application du rôle", () => {
  const cas: [RoleEcole, IdApp][] = [
    ['DIRECTEUR', 'backoffice'],
    ['SECRETARIAT', 'backoffice'],
    ['CENSEUR', 'backoffice'],
    ['COMPTABLE', 'backoffice'],
    ['SURVEILLANT', 'vie-scolaire'],
    ['ENSEIGNANT', 'vie-scolaire'],
    ['PARENT', 'parent'],
    ['ELEVE', 'parent'],
  ];

  it.each(cas)('%s travaille dans %s', (role, app) => {
    expect(appPour([role])).toBe(app);
    for (const autre of APPS.filter((a) => a !== app)) {
      expect(appAAtteindre([role], autre)).toBe(app);
    }
    expect(appAAtteindre([role], app)).toBeNull();
  });

  it('plusieurs rôles : le plus prioritaire décide', () => {
    expect(rolePrincipal(['ENSEIGNANT', 'DIRECTEUR'])).toBe('DIRECTEUR');
    expect(appPour(['PARENT', 'ENSEIGNANT'])).toBe('vie-scolaire');
    expect(rolesConnus(['PARENT', 'INCONNU', 'CENSEUR'])).toEqual([
      'CENSEUR',
      'PARENT',
    ]);
  });

  it("un utilisateur qui a un rôle dans l'application où il se trouve y reste", () => {
    // Directeur et parent dans la même école : il peut ouvrir l'espace parent.
    expect(appAAtteindre(['DIRECTEUR', 'PARENT'], 'parent')).toBeNull();
    expect(appAAtteindre(['DIRECTEUR', 'PARENT'], 'backoffice')).toBeNull();
    // Mais pas la vie scolaire, où il n'a aucun rôle : il est renvoyé vers son application principale.
    expect(appAAtteindre(['DIRECTEUR', 'PARENT'], 'vie-scolaire')).toBe(
      'backoffice',
    );
    expect(
      menuPour(['DIRECTEUR', 'PARENT'], 'parent').map((e) => e.libelle),
    ).toEqual(['Accueil']);
  });

  it('aucun rôle connu : aucune application', () => {
    expect(appPour([])).toBeNull();
    expect(appPour(['AUTRE'])).toBeNull();
    expect(appAAtteindre([], 'backoffice')).toBeNull();
  });
});

describe('actions', () => {
  it('radier et donner accès à l’espace parent : directeur seul', () => {
    for (const role of ORDRE_ROLES) {
      expect(peut('inscription.radier', [role])).toBe(role === 'DIRECTEUR');
      expect(peut('eleves.acces-parent', [role])).toBe(role === 'DIRECTEUR');
    }
  });

  it('le secrétariat ne lit pas le contenu sensible, mais en voit l’état', () => {
    expect(peut('eleves.sensible.lire', ['SECRETARIAT'])).toBe(false);
    expect(peut('eleves.sensible.etat', ['SECRETARIAT'])).toBe(true);
  });

  it('le censeur lit le sensible et ouvre le certificat, sans écrire', () => {
    expect(peut('eleves.sensible.lire', ['CENSEUR'])).toBe(true);
    expect(peut('eleves.certificat.ouvrir', ['CENSEUR'])).toBe(true);
    expect(peut('eleves.ecrire', ['CENSEUR'])).toBe(false);
    expect(peut('eleves.pieces.voir', ['CENSEUR'])).toBe(true);
  });

  it('le comptable voit les responsables (payeurs), ni les pièces ni le sensible', () => {
    expect(peut('eleves.responsables.voir', ['COMPTABLE'])).toBe(true);
    expect(peut('eleves.pieces.voir', ['COMPTABLE'])).toBe(false);
    expect(peut('eleves.sensible.etat', ['COMPTABLE'])).toBe(false);
  });
});
