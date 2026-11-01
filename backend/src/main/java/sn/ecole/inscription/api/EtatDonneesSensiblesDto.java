package sn.ecole.inscription.api;

/** Quels champs sensibles sont renseignés, sans leur contenu. */
public record EtatDonneesSensiblesDto(
    boolean santeRenseignee, boolean allergiesRenseignees, boolean situationFamilialeRenseignee) {}
