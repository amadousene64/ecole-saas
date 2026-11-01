import type { IdAction } from './table';
import type { RoleEcole } from './roles';

/**
 * Pour chaque méthode de service protégée côté serveur (relevée dans `docs/api/droits-serveur.json`
 * par `DroitsServeurTest`), ce qui la porte dans l'interface :
 * - `page` : la page qui la charge ou la déclenche (menu et garde de route) ;
 * - `action` : le bouton, l'onglet ou le champ qui la déclenche ;
 * - les deux : il faut avoir la page ET l'action ;
 * - `exception` : la méthode n'est (volontairement ou provisoirement) portée par aucun écran, avec la
 *   raison. `sauf` limite l'exception à certains rôles (les autres doivent avoir l'écran).
 * - `lecture: true` : malgré l'absence de `@Transactional(readOnly = true)` (la lecture est
 *   journalisée), c'est une lecture, pas un droit d'écriture.
 *
 * `droits-serveur.spec.ts` vérifie, pour chaque rôle : (1) l'interface ne montre jamais ce que le
 * serveur refuse ; (2) tout droit d'ÉCRITURE du serveur est accessible par l'interface, sinon une
 * exception documentée ici ; (3) chaque page du menu est portée par au moins une méthode serveur
 * autorisée au rôle. Une méthode serveur ajoutée sans entrée ici fait échouer ce test.
 */
export interface Cible {
  page?: string;
  action?: IdAction;
  lecture?: boolean;
  exception?: { raison: string; sauf?: readonly RoleEcole[] };
}

const INTERNE = {
  raison:
    'API interne entre modules (ReferentielScolaire) : jamais appelée directement par un écran.',
};

export const COUVERTURE: Readonly<Record<string, Cible>> = {
  // --- Établissement : profil, images, années, périodes, calendrier ---
  'EtablissementService.lire': { page: 'etablissement' },
  'EtablissementService.modifier': {
    page: 'etablissement',
    action: 'etablissement.ecrire',
  },
  // Logo : directeur et secrétariat. Cachet et signature : directeur seul (masqués au secrétariat).
  'EtablissementService.deposerImage': {
    page: 'etablissement',
    action: 'etablissement.ecrire',
  },
  'EtablissementService.supprimerImage': {
    page: 'etablissement',
    action: 'etablissement.ecrire',
  },
  'AnneeScolaireService.lister': { page: 'annees' },
  'AnneeScolaireService.lire': { page: 'annees' },
  'AnneeScolaireService.enCours': { page: 'annees' },
  'AnneeScolaireService.creer': {
    page: 'annees',
    action: 'etablissement.ecrire',
  },
  'AnneeScolaireService.modifier': {
    page: 'annees',
    action: 'etablissement.ecrire',
  },
  'AnneeScolaireService.supprimer': {
    page: 'annees',
    action: 'etablissement.ecrire',
  },
  // Ouverture et clôture d'une année : directeur seul.
  'AnneeScolaireService.ouvrir': {
    page: 'annees',
    action: 'etablissement.direction',
  },
  'AnneeScolaireService.cloturer': {
    page: 'annees',
    action: 'etablissement.direction',
  },
  'CalendrierService.lister': { page: 'annees' },
  'CalendrierService.ajouter': {
    page: 'annees',
    action: 'etablissement.ecrire',
  },
  'CalendrierService.modifier': {
    page: 'annees',
    action: 'etablissement.ecrire',
  },
  'CalendrierService.supprimer': {
    page: 'annees',
    action: 'etablissement.ecrire',
  },
  'PeriodeService.lister': { page: 'annees' },
  'PeriodeService.generer': {
    page: 'annees',
    action: 'etablissement.ecrire',
  },
  'PeriodeService.modifier': {
    page: 'annees',
    action: 'etablissement.ecrire',
  },

  // --- Établissement : structure (cycles, niveaux, séries, options) ---
  'CycleService.lister': { page: 'structure' },
  // Paramètres de notation des cycles : directeur seul.
  'CycleService.modifier': {
    page: 'structure',
    action: 'etablissement.direction',
  },
  'NiveauService.lister': { page: 'structure' },
  'NiveauService.lire': { page: 'structure' },
  'NiveauService.creer': { page: 'structure', action: 'etablissement.ecrire' },
  'NiveauService.modifier': {
    page: 'structure',
    action: 'etablissement.ecrire',
  },
  'NiveauService.supprimer': {
    page: 'structure',
    action: 'etablissement.ecrire',
  },
  'NiveauService.definirSeries': {
    page: 'structure',
    action: 'etablissement.ecrire',
  },
  'SerieService.lister': { page: 'structure' },
  'SerieService.lire': { page: 'structure' },
  'SerieService.creer': { page: 'structure', action: 'etablissement.ecrire' },
  'SerieService.modifier': {
    page: 'structure',
    action: 'etablissement.ecrire',
  },
  'SerieService.supprimer': {
    page: 'structure',
    action: 'etablissement.ecrire',
  },
  'OptionEnseignementService.lister': { page: 'classes' },
  'OptionEnseignementService.creer': {
    exception: {
      raison:
        "Pas encore d'écran des options d'enseignement (elles servent aux emplois du temps et aux notes, modules à venir).",
    },
  },
  'OptionEnseignementService.modifier': {
    exception: {
      raison:
        "Pas encore d'écran des options d'enseignement (modules à venir).",
    },
  },
  'OptionEnseignementService.supprimer': {
    exception: {
      raison:
        "Pas encore d'écran des options d'enseignement (modules à venir).",
    },
  },

  // --- Établissement : classes et salles ---
  'ClasseService.lister': { page: 'classes' },
  'ClasseService.lire': { page: 'classes' },
  'ClasseService.options': { page: 'classes' },
  'ClasseService.creer': { page: 'classes', action: 'etablissement.ecrire' },
  'ClasseService.modifier': { page: 'classes', action: 'etablissement.ecrire' },
  'ClasseService.supprimer': {
    page: 'classes',
    action: 'etablissement.ecrire',
  },
  'ClasseService.definirOptions': {
    exception: {
      raison:
        "Pas encore d'écran des options d'une classe (liées aux options d'enseignement, modules à venir).",
    },
  },
  'SalleService.lister': { page: 'classes' },
  'SalleService.lire': { page: 'classes' },
  'SalleService.creer': { page: 'classes', action: 'etablissement.ecrire' },
  'SalleService.modifier': { page: 'classes', action: 'etablissement.ecrire' },
  'SalleService.supprimer': { page: 'classes', action: 'etablissement.ecrire' },
  'ReferentielScolaireService.annee': { exception: INTERNE },
  'ReferentielScolaireService.anneeEnCours': { exception: INTERNE },
  'ReferentielScolaireService.classe': { exception: INTERNE },
  'ReferentielScolaireService.classes': { exception: INTERNE },
  'ReferentielScolaireService.nomsClasses': { exception: INTERNE },
  'ReferentielScolaireService.parametresNotation': { exception: INTERNE },
  'ReferentielScolaireService.periodes': { exception: INTERNE },
  'ReferentielScolaireService.niveaux': { exception: INTERNE },

  // --- Tableau de bord de la direction (accueil du directeur) ---
  'TableauDeBordService.direction': {
    page: 'accueil-backoffice',
    action: 'statistiques.voir',
  },
  'StatistiquesInscriptionService.effectifs': {
    page: 'accueil-backoffice',
    action: 'statistiques.voir',
  },
  'StatistiquesInscriptionService.suivi': {
    page: 'accueil-backoffice',
    action: 'statistiques.voir',
  },
  'StatistiquesInscriptionService.inscriptionsParSemaine': {
    page: 'accueil-backoffice',
    action: 'statistiques.voir',
  },

  // --- Élèves et inscriptions ---
  'EleveService.rechercher': { page: 'eleves' },
  'EleveService.lire': { page: 'eleves' },
  'EleveService.creer': { page: 'eleves-nouveau', action: 'eleves.creer' },
  'EleveService.modifier': { page: 'eleves', action: 'eleves.ecrire' },
  'EleveService.deposerPhoto': {
    exception: {
      raison:
        "Pas encore d'écran pour la photo de l'élève (la fiche ne l'affiche pas encore).",
    },
  },
  'EleveService.supprimerPhoto': {
    exception: {
      raison:
        "Pas encore d'écran pour la photo de l'élève (la fiche ne l'affiche pas encore).",
    },
  },
  'EleveService.supprimer': {
    exception: {
      raison:
        "Suppression logique d'un élève créé par erreur (directeur seul) : rare, pas d'écran dédié pour l'instant.",
    },
  },
  'EleveService.moi': {
    exception: {
      raison:
        "Fiche de l'élève connecté : écran à venir dans l'espace parent (l'élève n'a pas encore de fiche).",
    },
  },
  'ImportElevesService.analyser': { page: 'import', action: 'eleves.importer' },
  'ImportElevesService.importer': { page: 'import', action: 'eleves.importer' },
  'InscriptionService.inscrire': { page: 'eleves', action: 'eleves.ecrire' },
  'InscriptionService.valider': { page: 'eleves', action: 'eleves.ecrire' },
  'InscriptionService.modifier': { page: 'eleves', action: 'eleves.ecrire' },
  // Transfert, abandon : secrétariat et directeur ; radiation : directeur seul (action dédiée).
  'InscriptionService.sortir': { page: 'eleves', action: 'eleves.ecrire' },
  'InscriptionService.historique': {
    page: 'eleves',
    action: 'eleves.scolarite.voir',
  },
  'InscriptionService.lire': {
    page: 'eleves',
    action: 'eleves.scolarite.voir',
  },
  'InscriptionService.elevesDeLaClasse': {
    page: 'eleves',
    action: 'eleves.scolarite.voir',
  },

  // --- Responsables, liens, codes d'activation ---
  'LienParentalService.lister': {
    page: 'eleves',
    action: 'eleves.responsables.voir',
  },
  'LienParentalService.fratrie': {
    page: 'eleves',
    action: 'eleves.responsables.voir',
  },
  'LienParentalService.ajouter': { page: 'eleves', action: 'eleves.ecrire' },
  'LienParentalService.modifier': { page: 'eleves', action: 'eleves.ecrire' },
  'LienParentalService.supprimer': { page: 'eleves', action: 'eleves.ecrire' },
  'LienParentalService.definirAccesEspaceParent': {
    page: 'eleves',
    action: 'eleves.acces-parent',
  },
  'LienParentalService.enfantsDuParentConnecte': { page: 'enfants' },
  'ResponsableService.lire': { page: 'eleves', action: 'eleves.ecrire' },
  'ResponsableService.rechercher': { page: 'eleves', action: 'eleves.ecrire' },
  'ResponsableService.creer': { page: 'eleves', action: 'eleves.ecrire' },
  'ResponsableService.modifier': { page: 'eleves', action: 'eleves.ecrire' },
  'ResponsableService.delierCompte': {
    exception: {
      raison:
        "Délier le compte d'un responsable (directeur seul) : sera porté par la page « Comptes parents » (issue #44).",
    },
  },
  'ActivationService.emettrePourEleve': {
    page: 'eleves',
    action: 'eleves.ecrire',
  },
  'ActivationService.emettrePourResponsable': {
    page: 'eleves',
    action: 'eleves.ecrire',
  },
  'ActivationService.activer': {
    exception: {
      raison:
        "Activation par le parent ou l'élève lui-même, avec le code de l'école : écran de l'espace parent, ouvert à tout compte connecté (aucun rôle exigé).",
    },
  },

  // --- Dossier : données sensibles, pièces ---
  // Lecture du contenu : journalisée (donc non « readOnly ») mais c'est une lecture.
  'DossierSensibleService.lire': {
    page: 'eleves',
    action: 'eleves.sensible.lire',
    lecture: true,
  },
  'DossierSensibleService.etat': {
    page: 'eleves',
    action: 'eleves.sensible.etat',
  },
  'DossierSensibleService.modifier': {
    page: 'eleves',
    action: 'eleves.ecrire',
  },
  'PieceService.lister': { page: 'eleves', action: 'eleves.pieces.voir' },
  'PieceService.deposer': { page: 'eleves', action: 'eleves.ecrire' },
  'PieceService.supprimer': { page: 'eleves', action: 'eleves.ecrire' },
  // Ouverture d'une pièce : journalisée ; le certificat médical se limite au directeur et au
  // censeur (action dédiée dans la fiche).
  'PieceService.url': {
    page: 'eleves',
    action: 'eleves.pieces.voir',
    lecture: true,
  },

  // --- Paramètres d'inscription ---
  'ParametresInscriptionService.lire': {
    exception: {
      raison:
        "Pas encore d'écran du format du matricule et de la politique de capacité (à venir dans les paramètres).",
    },
  },
  'ParametresInscriptionService.modifier': {
    exception: {
      raison:
        "Pas encore d'écran du format du matricule et de la politique de capacité (directeur seul, à venir dans les paramètres).",
    },
  },
};
