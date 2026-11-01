package sn.ecole.inscription.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import sn.ecole.inscription.domain.StatutInscription;

/**
 * {@code avertissements} : par exemple dépassement de capacité quand l'école a choisi d'avertir
 * plutôt que de bloquer (vide en lecture).
 */
public record InscriptionDto(
    UUID id,
    Integer version,
    UUID eleveId,
    UUID classeId,
    String classe,
    UUID anneeId,
    StatutInscription statut,
    boolean redoublant,
    LocalDate dateEntree,
    LocalDate dateSortie,
    String motifSortie,
    List<UUID> optionIds,
    List<String> avertissements) {}
