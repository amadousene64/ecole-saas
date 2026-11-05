package sn.ecole.etablissement.api;

/**
 * Marque publique d'une école (page de connexion) : que du public. Tout est nul pour une marque
 * neutre (école inconnue, hôte commun). {@code logoVersion} change à chaque remplacement du logo :
 * le client l'ajoute à l'adresse du logo pour contourner son cache.
 */
public record MarqueDto(String nom, String ville, String phraseAccueil, String logoVersion) {}
