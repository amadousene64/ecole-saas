package sn.ecole.inscription.api;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** {@code classeId} absent : même classe. Les options remplacent les précédentes. */
public record ModificationInscriptionRequete(
    UUID classeId, boolean redoublant, @Size(max = 10) List<UUID> optionIds) {}
