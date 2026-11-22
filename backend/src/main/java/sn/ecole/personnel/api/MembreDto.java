package sn.ecole.personnel.api;

import java.util.Set;
import java.util.UUID;
import sn.ecole.shared.domain.RoleEcole;

/** Membre affectable ; prénoms et nom absents tant que le compte ne s'est jamais connecté. */
public record MembreDto(UUID utilisateurId, String prenoms, String nom, Set<RoleEcole> roles) {}
