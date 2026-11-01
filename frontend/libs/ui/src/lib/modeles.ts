import { NomIcone } from './icones/icone';

/** École proposée au choix. Indépendant du contrat d'API : la lib `ui` ne dépend pas d'`auth`. */
export interface EcoleChoix {
  id: string;
  code: string;
  nom: string;
}

export interface LienNavigation {
  chemin: string;
  libelle: string;
  /** Icône de l'entrée (barre latérale, barre de navigation basse de l'app parent). */
  icone?: NomIcone;
  /** Intitulé de section sous lequel l'entrée est rangée dans la barre latérale. */
  groupe?: string | null;
}
