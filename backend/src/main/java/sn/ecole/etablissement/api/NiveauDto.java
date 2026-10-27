package sn.ecole.etablissement.api;

import java.util.List;
import java.util.UUID;

/**
 * Un niveau d'études. {@code baseMoyenne} est la surcharge propre au niveau (nulle : il suit son
 * cycle) ; {@code baseMoyenneEffective} est la base qui s'applique réellement.
 */
public record NiveauDto(
    UUID id,
    Integer version,
    UUID cycleId,
    String code,
    String libelle,
    int ordre,
    Integer baseMoyenne,
    int baseMoyenneEffective,
    boolean actif,
    List<UUID> serieIds) {}
