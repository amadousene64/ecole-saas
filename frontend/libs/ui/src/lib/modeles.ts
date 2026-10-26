/** École proposée au choix. Indépendant du contrat d'API : la lib `ui` ne dépend pas d'`auth`. */
export interface EcoleChoix {
  id: string;
  code: string;
  nom: string;
}

export interface LienNavigation {
  chemin: string;
  libelle: string;
}
