package sn.ecole.inscription.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import sn.ecole.inscription.domain.TypeLien;

/**
 * {@code responsableId} n'est lu qu'à la création. L'accès à l'espace parent ne se modifie pas ici
 * : voir {@code PUT .../acces-espace-parent} (directeur seul).
 */
public record LienParentalRequete(
    UUID responsableId,
    @NotNull TypeLien type,
    @NotNull Boolean responsableLegal,
    @NotNull Boolean payeur,
    @NotNull Boolean contactUrgence,
    @NotNull Boolean autoriseRecuperer,
    @NotNull Boolean recoitNotifications) {}
