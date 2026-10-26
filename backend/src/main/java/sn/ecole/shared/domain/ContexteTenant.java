package sn.ecole.shared.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * École (tenant) courante, portée par une {@link ScopedValue} : elle ne vaut que pendant
 * l'exécution du bloc et ne peut pas « fuir » d'une requête à l'autre.
 *
 * <p>Seuls le filtre HTTP (après vérification de l'appartenance) et {@code ExecutionPourTenant}
 * ouvrent un contexte ; une règle ArchUnit l'impose.
 */
public final class ContexteTenant {

  /** Valeur utilisée quand aucune école n'est positionnée : ne correspond à aucune ligne. */
  public static final UUID AUCUN = new UUID(0L, 0L);

  private static final ScopedValue<UUID> COURANT = ScopedValue.newInstance();

  private ContexteTenant() {}

  public static Optional<UUID> courant() {
    return COURANT.isBound() ? Optional.of(COURANT.get()) : Optional.empty();
  }

  public static UUID exiger() {
    return courant()
        .orElseThrow(
            () ->
                new ErreurMetier(
                    "TENANT_ABSENT", TypeErreur.INVALIDE, "Aucune école n'est sélectionnée."));
  }

  public static <T, X extends Throwable> T executer(
      UUID tenantId, ScopedValue.CallableOp<? extends T, X> operation) throws X {
    Objects.requireNonNull(tenantId, "tenantId");
    return ScopedValue.where(COURANT, tenantId).call(operation);
  }
}
