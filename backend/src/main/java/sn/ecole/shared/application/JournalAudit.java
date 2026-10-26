package sn.ecole.shared.application;

import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.shared.domain.EntreeJournalAudit;
import sn.ecole.shared.domain.JournalAuditRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consigne une action sensible dans le journal d'audit, dans la transaction métier en cours : pas
 * d'action validée sans sa trace, pas de trace pour une action annulée.
 */
@Component
public class JournalAudit {

  private final JournalAuditRepository journal;
  private final JsonMapper json;

  public JournalAudit(JournalAuditRepository journal, JsonMapper json) {
    this.journal = journal;
    this.json = json;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public EntreeJournalAudit consigner(
      String action, String typeCible, UUID idCible, Map<String, ?> details) {
    return journal.save(
        new EntreeJournalAudit(
            acteurCourant(), action, typeCible, idCible, json.writeValueAsString(details)));
  }

  private static UUID acteurCourant() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      throw new IllegalStateException("Action auditée sans utilisateur authentifié.");
    }
    try {
      return UUID.fromString(auth.getName());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("Identifiant d'utilisateur invalide pour l'audit.", e);
    }
  }
}
