package sn.ecole.inscription.api;

import sn.ecole.shared.domain.RoleEcole;

/** Rôle obtenu dans l'école : PARENT ou ELEVE. */
public record ActivationDto(RoleEcole role) {}
