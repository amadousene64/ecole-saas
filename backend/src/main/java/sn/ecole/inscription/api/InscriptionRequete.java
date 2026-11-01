package sn.ecole.inscription.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import sn.ecole.inscription.domain.StatutInscription;

/** Statut initial : PRE_INSCRIT ou INSCRIT. Options : parmi celles de la classe, une par groupe. */
public record InscriptionRequete(
    @NotNull UUID eleveId,
    @NotNull UUID classeId,
    @NotNull StatutInscription statut,
    boolean redoublant,
    @Size(max = 10) List<@NotNull UUID> optionIds) {}
