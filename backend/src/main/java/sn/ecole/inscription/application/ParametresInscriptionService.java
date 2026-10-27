package sn.ecole.inscription.application;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.inscription.domain.ParametresInscription;
import sn.ecole.inscription.domain.ParametresInscriptionRepository;
import sn.ecole.inscription.domain.PolitiqueCapacite;
import sn.ecole.shared.application.JournalAudit;

/** Réglages d'inscription de l'école (format des matricules, politique de capacité). */
@Service
@Transactional
public class ParametresInscriptionService {

  private final ParametresInscriptionRepository parametres;
  private final JournalAudit journal;

  public ParametresInscriptionService(
      ParametresInscriptionRepository parametres, JournalAudit journal) {
    this.parametres = parametres;
    this.journal = journal;
  }

  @PreAuthorize(Droits.ECRITURE)
  @Transactional(readOnly = true)
  public ParametresInscription lire() {
    return courants();
  }

  /** Ne change que les prochains matricules : ceux déjà attribués restent. */
  @PreAuthorize(Droits.DIRECTION)
  public ParametresInscription modifier(
      int version, String formatMatricule, PolitiqueCapacite politique) {
    ParametresInscription p = parametres.findFirstBy().orElseGet(ParametresInscription::parDefaut);
    if (version != versionExposee(p)) {
      throw Erreurs.conflit(
          "CONFLIT_VERSION",
          "La donnée a été modifiée entre-temps : rechargez-la puis recommencez.");
    }
    p.modifier(formatMatricule, politique);
    ParametresInscription enregistres = parametres.save(p);
    journal.consigner(
        "PARAMETRES_INSCRIPTION_MODIFIES",
        "ParametresInscription",
        enregistres.getId(),
        Map.of("formatMatricule", p.getFormatMatricule(), "politiqueCapacite", politique));
    return enregistres;
  }

  /**
   * Version vue par le client : 0 tant que l'école garde les valeurs par défaut (rien en base),
   * puis la version en base plus un. Distingue « jamais enregistrés » de « enregistrés une fois ».
   */
  public static int versionExposee(ParametresInscription p) {
    return p.getVersion() == null ? 0 : p.getVersion() + 1;
  }

  /** Paramètres enregistrés, ou valeurs par défaut (non enregistrées) ; sans contrôle de droits. */
  ParametresInscription courants() {
    return parametres.findFirstBy().orElseGet(ParametresInscription::parDefaut);
  }
}
