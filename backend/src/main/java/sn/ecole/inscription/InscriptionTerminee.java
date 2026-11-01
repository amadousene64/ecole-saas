package sn.ecole.inscription;

import java.time.LocalDate;
import java.util.UUID;

/** Une inscription vient de se terminer : {@code statut} vaut TRANSFERE, RADIE ou ABANDON. */
public record InscriptionTerminee(
    UUID tenantId, UUID inscriptionId, UUID eleveId, String statut, LocalDate dateSortie) {}
