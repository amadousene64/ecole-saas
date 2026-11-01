package sn.ecole.inscription.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Port de persistance : toujours limité à l'école courante (filtre Hibernate + RLS). */
public interface InscriptionOptionRepository {

  List<InscriptionOption> findByInscriptionId(UUID inscriptionId);

  List<InscriptionOption> findByInscriptionIdIn(Collection<UUID> inscriptionIds);

  /** Vrai si un élève de la classe (inscription active ou non) a choisi l'option. */
  boolean optionChoisieDansClasse(UUID classeId, UUID optionId);

  InscriptionOption save(InscriptionOption choix);
}
