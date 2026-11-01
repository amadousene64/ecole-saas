import type { NomIcone } from '@ecole/ui';
import type { IdApp, RoleEcole } from './roles';

/**
 * LA table des droits d'interface, source unique du menu, des gardes de route et de l'affichage
 * des boutons. Elle reflète les règles du serveur (qui reste l'autorité : chaque appel est
 * contrôlé côté serveur quoi que l'interface montre) ; la mettre à jour avec elles.
 *
 * Une page ou une action qui n'a pas encore de module derrière (`disponible: false`) n'apparaît
 * nulle part : ni menu, ni route.
 */

export interface PageDef {
  /** Identifiant stable, utilisé par les gardes (`data: { page: 'eleves' }`). */
  id: string;
  app: IdApp;
  chemin: string;
  libelle: string;
  /** Libellé propre à certains rôles (« Élèves et inscriptions » pour le secrétariat…). */
  libelles?: Partial<Record<RoleEcole, string>>;
  icone: NomIcone;
  /** Rôles qui peuvent ouvrir la page. */
  roles: readonly RoleEcole[];
  /** Rôles qui la voient dans le menu (par défaut : tous ceux de `roles`). */
  menu?: readonly RoleEcole[];
  /** Intitulé de section dans le menu, par rôle. */
  groupes?: Partial<Record<RoleEcole, string>>;
  disponible: boolean;
}

const PERSONNEL_BACKOFFICE = [
  'DIRECTEUR',
  'SECRETARIAT',
  'CENSEUR',
  'COMPTABLE',
] as const satisfies readonly RoleEcole[];

export const PAGES: readonly PageDef[] = [
  // --- Back-office ---
  {
    id: 'accueil-backoffice',
    app: 'backoffice',
    chemin: '/',
    libelle: 'Accueil',
    icone: 'accueil',
    roles: PERSONNEL_BACKOFFICE,
    disponible: true,
  },
  {
    id: 'eleves',
    app: 'backoffice',
    chemin: '/eleves',
    libelle: 'Élèves',
    libelles: {
      SECRETARIAT: 'Élèves et inscriptions',
      COMPTABLE: 'Élèves et payeurs',
    },
    icone: 'eleves',
    roles: PERSONNEL_BACKOFFICE,
    disponible: true,
  },
  {
    id: 'eleves-nouveau',
    app: 'backoffice',
    chemin: '/eleves/nouveau',
    libelle: 'Nouvel élève',
    icone: 'ajouter',
    roles: ['DIRECTEUR', 'SECRETARIAT'],
    menu: [],
    disponible: true,
  },
  {
    id: 'import',
    app: 'backoffice',
    chemin: '/eleves/import',
    libelle: 'Import',
    icone: 'import',
    roles: ['DIRECTEUR', 'SECRETARIAT'],
    // Le directeur y accède depuis la liste des élèves ; le secrétariat l'a dans son menu.
    menu: ['SECRETARIAT'],
    disponible: true,
  },
  {
    id: 'classes',
    app: 'backoffice',
    chemin: '/etablissement/classes',
    libelle: 'Classes et salles',
    // Le censeur lit les classes et les salles (LECTURE_PERSONNEL) sans pouvoir les modifier.
    libelles: { CENSEUR: 'Classes' },
    icone: 'classes',
    roles: ['DIRECTEUR', 'SECRETARIAT', 'CENSEUR'],
    groupes: { DIRECTEUR: 'Classes et années' },
    disponible: true,
  },
  {
    id: 'annees',
    app: 'backoffice',
    chemin: '/etablissement/annees',
    libelle: 'Années et périodes',
    // Le secrétariat gère années, périodes et calendrier ; l'ouverture et la clôture d'une année
    // sont au directeur seul (boutons masqués par l'action etablissement.direction).
    libelles: { SECRETARIAT: 'Années, périodes et calendrier' },
    icone: 'annees',
    roles: ['DIRECTEUR', 'SECRETARIAT'],
    groupes: {
      DIRECTEUR: 'Classes et années',
      SECRETARIAT: "Paramètres de l'école",
    },
    disponible: true,
  },
  {
    id: 'etablissement',
    app: 'backoffice',
    chemin: '/etablissement/parametres',
    libelle: 'Établissement',
    // Secrétariat : profil et logo seulement (cachet et signature : directeur seul).
    libelles: { SECRETARIAT: 'Profil et logo' },
    icone: 'parametres',
    roles: ['DIRECTEUR', 'SECRETARIAT'],
    groupes: { DIRECTEUR: 'Paramètres', SECRETARIAT: "Paramètres de l'école" },
    disponible: true,
  },
  {
    id: 'structure',
    app: 'backoffice',
    chemin: '/etablissement/structure',
    libelle: 'Structure pédagogique',
    // Secrétariat : niveaux et séries (les cycles et leurs paramètres de notation : directeur seul).
    libelles: { SECRETARIAT: 'Niveaux et séries' },
    icone: 'tableau',
    roles: ['DIRECTEUR', 'SECRETARIAT'],
    groupes: { DIRECTEUR: 'Paramètres', SECRETARIAT: "Paramètres de l'école" },
    disponible: true,
  },
  // Modules pas encore construits : déclarés pour que la table décrive la cible, mais invisibles.
  {
    id: 'vie-scolaire-direction',
    app: 'backoffice',
    chemin: '/vie-scolaire',
    libelle: 'Vie scolaire',
    icone: 'retard',
    roles: ['DIRECTEUR', 'CENSEUR'],
    disponible: false,
  },
  {
    id: 'notes-bulletins',
    app: 'backoffice',
    chemin: '/notes',
    libelle: 'Notes et bulletins',
    icone: 'notes',
    roles: ['DIRECTEUR', 'CENSEUR'],
    disponible: false,
  },
  {
    id: 'finances',
    app: 'backoffice',
    chemin: '/finances',
    libelle: 'Finances',
    icone: 'paiements',
    roles: ['DIRECTEUR', 'COMPTABLE'],
    disponible: false,
  },
  {
    id: 'communication',
    app: 'backoffice',
    chemin: '/communication',
    libelle: 'Communication',
    icone: 'messages',
    roles: ['DIRECTEUR', 'SECRETARIAT'],
    disponible: false,
  },
  // --- Vie scolaire ---
  {
    id: 'accueil-vie-scolaire',
    app: 'vie-scolaire',
    chemin: '/',
    libelle: 'Accueil',
    icone: 'accueil',
    roles: ['SURVEILLANT', 'ENSEIGNANT'],
    disponible: true,
  },
  // --- Espace parent ---
  {
    id: 'accueil-parent',
    app: 'parent',
    chemin: '/',
    libelle: 'Accueil',
    icone: 'accueil',
    roles: ['PARENT', 'ELEVE'],
    disponible: true,
  },
  {
    id: 'enfants',
    app: 'parent',
    chemin: '/enfants',
    libelle: 'Mes enfants',
    icone: 'eleves',
    // Le serveur ne répond qu'au parent (enfantsDuParentConnecte) : l'élève n'a pas cet écran.
    roles: ['PARENT'],
    // Déjà sur l'accueil : pas d'entrée de menu en double.
    menu: [],
    disponible: true,
  },
  {
    id: 'notes-parent',
    app: 'parent',
    chemin: '/notes',
    libelle: 'Notes',
    icone: 'notes',
    roles: ['PARENT', 'ELEVE'],
    disponible: false,
  },
  {
    id: 'paiements-parent',
    app: 'parent',
    chemin: '/paiements',
    libelle: 'Paiements',
    icone: 'paiements',
    roles: ['PARENT', 'ELEVE'],
    disponible: false,
  },
  {
    id: 'messages-parent',
    app: 'parent',
    chemin: '/messages',
    libelle: 'Messages',
    icone: 'messages',
    roles: ['PARENT', 'ELEVE'],
    disponible: false,
  },
];

/**
 * Actions (boutons, champs, onglets) et rôles qui les voient. Chaque entrée a son pendant
 * `@PreAuthorize` côté serveur.
 */
export const ACTIONS = {
  // Élèves
  'eleves.creer': ['DIRECTEUR', 'SECRETARIAT'],
  'eleves.importer': ['DIRECTEUR', 'SECRETARIAT'],
  'eleves.ecrire': ['DIRECTEUR', 'SECRETARIAT'],
  'eleves.responsables.voir': ['DIRECTEUR', 'SECRETARIAT', 'COMPTABLE'],
  'eleves.scolarite.voir': ['DIRECTEUR', 'SECRETARIAT', 'CENSEUR'],
  'eleves.pieces.voir': ['DIRECTEUR', 'SECRETARIAT', 'CENSEUR'],
  'eleves.certificat.ouvrir': ['DIRECTEUR', 'CENSEUR'],
  'eleves.sensible.lire': ['DIRECTEUR', 'CENSEUR'],
  'eleves.sensible.etat': ['DIRECTEUR', 'SECRETARIAT', 'CENSEUR'],
  'eleves.acces-parent': ['DIRECTEUR'],
  'inscription.radier': ['DIRECTEUR'],
  // Établissement
  // Profil, logo, années, périodes, calendrier, niveaux, séries, classes, salles.
  'etablissement.ecrire': ['DIRECTEUR', 'SECRETARIAT'],
  // Cachet, signature, paramètres de notation des cycles, ouverture et clôture d'une année.
  'etablissement.direction': ['DIRECTEUR'],
  // Tableau de bord de la direction (accueil du directeur) : effectifs, dossiers, familles.
  'statistiques.voir': ['DIRECTEUR'],
} as const satisfies Record<string, readonly RoleEcole[]>;

export type IdAction = keyof typeof ACTIONS;
