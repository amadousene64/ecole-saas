// Le fichier est généré côté serveur (docs/api) : il sort volontairement de l'espace de travail Nx.
// eslint-disable-next-line @nx/enforce-module-boundaries
import serveur from '../../../../../../docs/api/droits-serveur.json';
import { Cible, COUVERTURE } from './couverture';
import { menuPour, pagePermise, peut } from './navigation';
import { APP_DU_ROLE, ORDRE_ROLES, RoleEcole } from './roles';
import { ACTIONS, PAGES } from './table';

/**
 * L'interface reflète EXACTEMENT les droits du serveur. Les droits du serveur sont relevés dans les
 * `@PreAuthorize` des services (docs/api/droits-serveur.json, régénéré par DroitsServeurTest) ; la
 * couverture dit quel écran porte chaque méthode. Ce test relie les deux, pour chaque rôle.
 */

interface MethodeServeur {
  roles: string[];
  ecriture: boolean;
  conditionnel: boolean;
  perimetre: boolean;
  expression: string;
}

const METHODES = serveur.methodes as Record<string, MethodeServeur>;
const CLES = Object.keys(METHODES).sort();

/** Pages d'accueil : elles n'appellent que /moi, ouvert à tout compte connecté de l'école. */
const PAGES_SANS_METHODE_SERVEUR = (id: string) => id.startsWith('accueil-');

/** Vrai si le rôle a l'écran (page et/ou action) qui porte cette méthode. */
function ecran(cible: Cible, role: RoleEcole): boolean {
  if (!cible.page && !cible.action) {
    return false;
  }
  const page = cible.page ? pagePermise(cible.page, [role]) : true;
  const action = cible.action ? peut(cible.action, [role]) : true;
  return page && action;
}

function autorise(cle: string, role: string): boolean {
  return METHODES[cle].roles.includes(role);
}

describe('droits d’interface = droits du serveur', () => {
  it('chaque méthode protégée du serveur a une couverture, et inversement', () => {
    const sansCouverture = CLES.filter((c) => !(c in COUVERTURE));
    expect(
      sansCouverture,
      'méthodes serveur sans entrée dans couverture.ts (écran ou exception documentée)',
    ).toEqual([]);
    const perimees = Object.keys(COUVERTURE).filter((c) => !(c in METHODES));
    expect(
      perimees,
      'entrées de couverture.ts sans méthode serveur (relancer DroitsServeurTest -Ddroits.maj=true ?)',
    ).toEqual([]);
  });

  it('les pages et actions citées existent dans la table', () => {
    const ids = new Set(PAGES.map((p) => p.id));
    for (const [cle, cible] of Object.entries(COUVERTURE)) {
      if (cible.page) {
        expect(ids.has(cible.page), `${cle} → page ${cible.page}`).toBe(true);
      }
      if (cible.action) {
        expect(cible.action in ACTIONS, `${cle} → action ${cible.action}`).toBe(
          true,
        );
      }
      // Une entrée sans écran est une exception, et seulement elle.
      expect(!!cible.exception, `${cle} : page/action OU exception`).toBe(
        !cible.page && !cible.action,
      );
    }
  });

  it('l’interface ne montre jamais ce que le serveur refuse (rôle par rôle)', () => {
    for (const [cle, cible] of Object.entries(COUVERTURE)) {
      for (const role of ORDRE_ROLES) {
        if (ecran(cible, role)) {
          expect(
            autorise(cle, role),
            `${role} voit ${JSON.stringify({ page: cible.page, action: cible.action })} mais le serveur refuse ${cle}`,
          ).toBe(true);
        }
      }
    }
  });

  it('aucun droit d’écriture du serveur n’est inaccessible par l’interface sans raison documentée', () => {
    const manques: string[] = [];
    for (const [cle, cible] of Object.entries(COUVERTURE)) {
      const methode = METHODES[cle];
      if (!methode?.ecriture || cible.lecture) {
        continue;
      }
      for (const role of methode.roles as RoleEcole[]) {
        if (ecran(cible, role)) {
          continue;
        }
        const exception = cible.exception;
        const documente =
          !!exception &&
          exception.raison.length >= 30 &&
          (!exception.sauf || exception.sauf.includes(role));
        if (!documente) {
          manques.push(
            `${role} peut écrire ${cle} côté serveur, sans écran ni exception`,
          );
        }
      }
    }
    expect(manques).toEqual([]);
  });

  it('les exceptions sont motivées et ne concernent que des rôles autorisés par le serveur', () => {
    for (const [cle, cible] of Object.entries(COUVERTURE)) {
      if (!cible.exception) {
        continue;
      }
      expect(
        cible.exception.raison.length,
        `${cle} : raison`,
      ).toBeGreaterThanOrEqual(30);
      for (const r of cible.exception.sauf ?? []) {
        expect(autorise(cle, r), `${cle} : exception pour ${r}`).toBe(true);
      }
    }
  });

  it('chaque page ouverte à un rôle est portée par au moins une méthode serveur qu’il peut appeler', () => {
    for (const role of ORDRE_ROLES) {
      for (const page of PAGES.filter((p) => pagePermise(p.id, [role]))) {
        if (PAGES_SANS_METHODE_SERVEUR(page.id)) {
          continue;
        }
        const portees = Object.entries(COUVERTURE).filter(
          ([, c]) => c.page === page.id,
        );
        expect(
          portees.length,
          `page ${page.id} sans méthode serveur`,
        ).toBeGreaterThan(0);
        // Au moins une lecture de la page est permise au rôle (il peut charger l'écran).
        // Une page de pure création (formulaire) n'a que des écritures : on exige alors l'une d'elles.
        const lectures = portees.filter(
          ([cle]) => !METHODES[cle].ecriture || COUVERTURE[cle].lecture,
        );
        const requises = lectures.length > 0 ? lectures : portees;
        expect(
          requises.some(([cle]) => autorise(cle, role)),
          `${role} ouvre la page ${page.id} mais le serveur lui refuse toute lecture`,
        ).toBe(true);
      }
    }
  });

  it('chaque entrée de menu d’un rôle correspond à un endpoint autorisé côté serveur', () => {
    for (const role of ORDRE_ROLES) {
      for (const entree of menuPour([role], APP_DU_ROLE[role])) {
        if (PAGES_SANS_METHODE_SERVEUR(entree.id)) {
          continue;
        }
        const autorisees = Object.entries(COUVERTURE).filter(
          ([cle, c]) => c.page === entree.id && autorise(cle, role),
        );
        expect(
          autorisees.length,
          `${role} → menu « ${entree.libelle} » (${entree.id}) : aucune méthode serveur autorisée`,
        ).toBeGreaterThan(0);
      }
    }
  });
});

describe('ajustements demandés', () => {
  const methode = (cle: string) => METHODES[cle];

  it('le secrétariat gère profil, logo, structure, années, périodes et calendrier…', () => {
    const attendues = [
      'EtablissementService.modifier',
      'EtablissementService.deposerImage',
      'NiveauService.creer',
      'SerieService.creer',
      'AnneeScolaireService.creer',
      'AnneeScolaireService.modifier',
      'PeriodeService.generer',
      'PeriodeService.modifier',
      'CalendrierService.ajouter',
    ];
    for (const cle of attendues) {
      expect(methode(cle).roles, cle).toContain('SECRETARIAT');
      expect(ecran(COUVERTURE[cle], 'SECRETARIAT'), `écran de ${cle}`).toBe(
        true,
      );
    }
  });

  it('…mais ni cachet et signature, ni paramètres de notation des cycles, ni ouverture/clôture d’année', () => {
    for (const cle of [
      'CycleService.modifier',
      'AnneeScolaireService.ouvrir',
      'AnneeScolaireService.cloturer',
    ]) {
      expect(methode(cle).roles, cle).not.toContain('SECRETARIAT');
      expect(ecran(COUVERTURE[cle], 'SECRETARIAT'), cle).toBe(false);
      expect(ecran(COUVERTURE[cle], 'DIRECTEUR'), cle).toBe(true);
    }
    // Cachet et signature : conditionnés au type d'image côté serveur, masqués par l'action
    // directeur côté interface (carte-image) ; le logo reste au secrétariat.
    expect(methode('EtablissementService.deposerImage').conditionnel).toBe(
      true,
    );
    expect(peut('etablissement.direction', ['SECRETARIAT'])).toBe(false);
  });

  it('le menu du secrétariat porte « Paramètres de l’école » avec ses trois pages', () => {
    const menu = menuPour(['SECRETARIAT'], 'backoffice');
    const section = menu.filter(
      (e) => e.groupe === 'Paramètres de l’école'.replace('’', "'"),
    );
    expect(section.map((e) => e.libelle)).toEqual([
      'Années, périodes et calendrier',
      'Profil et logo',
      'Niveaux et séries',
    ]);
    // Aucune page réservée au directeur dans ce menu.
    expect(menu.map((e) => e.chemin)).not.toContain(
      '/etablissement/annees-direction',
    );
  });

  it('le censeur a « Classes » en lecture seule', () => {
    const menu = menuPour(['CENSEUR'], 'backoffice');
    expect(menu.map((e) => e.libelle)).toEqual([
      'Accueil',
      'Élèves',
      'Classes',
    ]);
    for (const cle of [
      'ClasseService.creer',
      'ClasseService.modifier',
      'SalleService.creer',
    ]) {
      expect(ecran(COUVERTURE[cle], 'CENSEUR'), cle).toBe(false);
    }
    for (const cle of ['ClasseService.lister', 'SalleService.lister']) {
      expect(ecran(COUVERTURE[cle], 'CENSEUR'), cle).toBe(true);
      expect(autorise(cle, 'CENSEUR'), cle).toBe(true);
    }
  });

  it('le surveillant est inchangé : aucune page d’élèves dans la vie scolaire', () => {
    expect(
      menuPour(['SURVEILLANT'], 'vie-scolaire').map((e) => e.libelle),
    ).toEqual(['Accueil']);
  });

  it('l’élève n’a pas « Mes enfants » (le serveur ne répond qu’au parent)', () => {
    expect(pagePermise('enfants', ['PARENT'])).toBe(true);
    expect(pagePermise('enfants', ['ELEVE'])).toBe(false);
  });
});
