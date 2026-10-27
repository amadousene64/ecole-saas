package sn.ecole.etablissement;

import java.time.LocalDate;
import java.util.UUID;

/** Une année scolaire vient d'être clôturée (lecture seule désormais). */
public record AnneeScolaireCloturee(
    UUID tenantId, UUID anneeId, String libelle, LocalDate dateDebut, LocalDate dateFin) {}
