package sn.ecole.shared.domain;

import java.util.List;
import java.util.UUID;

/** Port du journal d'audit ; ajout seul, toujours limité à l'école courante. */
public interface JournalAuditRepository {

  EntreeJournalAudit save(EntreeJournalAudit entree);

  List<EntreeJournalAudit> findByTypeCibleAndIdCibleOrderByCreeLeAsc(
      String typeCible, UUID idCible);
}
