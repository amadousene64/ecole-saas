package sn.ecole.etablissement.api;

import java.util.Set;
import java.util.UUID;
import sn.ecole.etablissement.CodeCycle;

/** Matière de l'école ; {@code groupe} : domaine ou discipline mère, pour l'affichage. */
public record MatiereDto(
    UUID id,
    Integer version,
    String code,
    String libelle,
    String groupe,
    Set<CodeCycle> cycles,
    int ordre,
    boolean actif) {}
