package sn.ecole.etablissement;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Une année scolaire vient de passer EN_COURS. Événement de domaine, publié dans la transaction.
 */
public record AnneeScolaireOuverte(
    UUID tenantId, UUID anneeId, String libelle, LocalDate dateDebut, LocalDate dateFin) {}
