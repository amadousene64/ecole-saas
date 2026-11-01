package sn.ecole.inscription.api;

import java.util.UUID;
import sn.ecole.inscription.domain.TypeLien;

public record LienParentalDto(
    UUID id,
    Integer version,
    ResponsableDto responsable,
    TypeLien type,
    boolean responsableLegal,
    boolean payeur,
    boolean contactUrgence,
    boolean autoriseRecuperer,
    boolean recoitNotifications,
    boolean accesEspaceParent) {}
