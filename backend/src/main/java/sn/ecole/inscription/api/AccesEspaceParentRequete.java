package sn.ecole.inscription.api;

import jakarta.validation.constraints.NotNull;

/** Faux : le responsable ne voit plus l'élève et ne reçoit plus aucune notification. */
public record AccesEspaceParentRequete(@NotNull Boolean acces) {}
