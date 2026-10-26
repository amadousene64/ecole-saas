package sn.ecole.shared.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;
import sn.ecole.shared.domain.EntreeJournalAudit;
import sn.ecole.shared.domain.JournalAuditRepository;

/** Implémentation Spring Data du port {@link JournalAuditRepository}. */
interface JournalAuditJpaRepository
    extends Repository<EntreeJournalAudit, UUID>, JournalAuditRepository {}
