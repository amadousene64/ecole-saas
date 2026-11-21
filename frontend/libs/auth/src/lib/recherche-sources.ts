import { InjectionToken } from '@angular/core';

/**
 * Contrat des sources de la recherche globale. Fichier à part : les applications y ajoutent leurs
 * sources au démarrage, alors que l'écran de recherche (et la boîte de dialogue de Material) n'est
 * chargé qu'à la première ouverture (Ctrl+K), hors du lot initial.
 */
/** Un résultat de la recherche globale. */
export interface ResultatRecherche {
  id: string;
  libelle: string;
  detail?: string;
  /** Chemin interne vers lequel aller. */
  chemin: string;
}

/**
 * Source de résultats ajoutée par une application (par exemple les élèves). Le terme est envoyé à
 * la source dans le CORPS d'une requête, jamais dans une URL.
 */
export interface SourceRecherche {
  id: string;
  titre: string;
  /** Vrai si l'utilisateur a le droit de chercher dans cette source. */
  disponible(): boolean;
  rechercher(terme: string): Promise<ResultatRecherche[]>;
}

export const SOURCES_RECHERCHE = new InjectionToken<SourceRecherche[]>(
  'SOURCES_RECHERCHE',
);
