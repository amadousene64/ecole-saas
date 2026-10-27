package sn.ecole.etablissement.api;

import java.time.LocalDate;
import java.util.UUID;
import sn.ecole.etablissement.domain.StatutAnnee;

public record AnneeScolaireDto(
    UUID id,
    Integer version,
    String libelle,
    LocalDate dateDebut,
    LocalDate dateFin,
    StatutAnnee statut) {}
