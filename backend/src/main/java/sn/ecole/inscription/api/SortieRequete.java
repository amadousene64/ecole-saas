package sn.ecole.inscription.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import sn.ecole.inscription.domain.StatutInscription;

/** Sortie : TRANSFERE, RADIE (directeur seul) ou ABANDON. */
public record SortieRequete(
    @NotNull StatutInscription statut, @NotNull LocalDate date, @Size(max = 200) String motif) {}
