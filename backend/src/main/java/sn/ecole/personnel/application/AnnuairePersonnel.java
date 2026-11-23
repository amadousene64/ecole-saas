package sn.ecole.personnel.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import sn.ecole.personnel.domain.MembrePersonnel;
import sn.ecole.personnel.domain.MembrePersonnelRepository;
import sn.ecole.shared.application.JournalAudit;
import sn.ecole.shared.application.UtilisateurIdentifie;
import sn.ecole.shared.domain.Appartenance;
import sn.ecole.shared.domain.AppartenanceRepository;
import sn.ecole.shared.domain.RoleEcole;

/**
 * Annuaire du personnel : enseignants et surveillants de l'école, avec les prénoms et nom de leur
 * compte, relevés à chaque présentation à l'école ({@link UtilisateurIdentifie}). Un compte qui ne
 * s'est jamais connecté apparaît sans nom : la direction le reconnaît à son identifiant.
 */
@Service
public class AnnuairePersonnel {

  private static final Logger LOG = LoggerFactory.getLogger(AnnuairePersonnel.class);

  /** Rôles désignables dans une affectation. */
  static final Set<RoleEcole> AFFECTABLES = EnumSet.of(RoleEcole.ENSEIGNANT, RoleEcole.SURVEILLANT);

  /** Membre affectable, ses rôles affectables et son nom s'il s'est déjà connecté. */
  public record Membre(UUID utilisateurId, String prenoms, String nom, Set<RoleEcole> roles) {

    public Membre {
      roles = Set.copyOf(roles);
    }
  }

  private final MembrePersonnelRepository membres;
  private final AppartenanceRepository appartenances;
  private final TransactionTemplate nouvelleTransaction;
  private final Clock horloge;
  private final JournalAudit journal;

  public AnnuairePersonnel(
      MembrePersonnelRepository membres,
      AppartenanceRepository appartenances,
      PlatformTransactionManager transactions,
      Clock horloge,
      JournalAudit journal) {
    this.membres = membres;
    this.appartenances = appartenances;
    this.nouvelleTransaction = new TransactionTemplate(transactions);
    this.nouvelleTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.horloge = horloge;
    this.journal = journal;
  }

  /** Enseignants et surveillants de l'école, par nom (les comptes jamais connectés en dernier). */
  @PreAuthorize(Droits.GESTION)
  @Transactional(readOnly = true)
  public List<Membre> affectables() {
    Map<UUID, Set<RoleEcole>> roles = new LinkedHashMap<>();
    for (Appartenance a : appartenances.findByRoleIn(AFFECTABLES)) {
      roles
          .computeIfAbsent(a.getUtilisateurId(), k -> EnumSet.noneOf(RoleEcole.class))
          .add(a.getRole());
    }
    Map<UUID, MembrePersonnel> parUtilisateur = new HashMap<>();
    membres
        .findByUtilisateurIdIn(roles.keySet())
        .forEach(m -> parUtilisateur.put(m.getUtilisateurId(), m));
    List<Membre> liste = new ArrayList<>();
    roles.forEach(
        (utilisateur, sesRoles) -> {
          MembrePersonnel m = parUtilisateur.get(utilisateur);
          liste.add(
              new Membre(
                  utilisateur,
                  m == null ? null : m.getPrenoms(),
                  m == null ? null : m.getNom(),
                  sesRoles));
        });
    liste.sort(
        Comparator.comparing(
                (Membre m) -> m.nom() == null ? "￿" : m.nom().toLowerCase(java.util.Locale.ROOT))
            .thenComparing(m -> m.prenoms() == null ? "" : m.prenoms())
            .thenComparing(m -> m.utilisateurId().toString()));
    return liste;
  }

  /**
   * Relève les noms du compte d'un membre affectable qui se présente à son école. Dans sa propre
   * transaction, et sans jamais faire échouer la requête : l'annuaire n'est qu'un confort
   * d'affichage.
   */
  @EventListener
  public void identifie(UtilisateurIdentifie evenement) {
    if (evenement.roles().stream().noneMatch(AFFECTABLES::contains)) {
      return;
    }
    try {
      nouvelleTransaction.executeWithoutResult(
          statut -> {
            Instant maintenant = Instant.now(horloge);
            membres
                .findByUtilisateurId(evenement.utilisateurId())
                .ifPresentOrElse(
                    m -> {
                      // Une écriture par jour au plus, ou dès que le compte change de nom.
                      if (!m.aJour(evenement.prenoms(), evenement.nom(), maintenant)) {
                        if (m.renseigner(evenement.prenoms(), evenement.nom(), maintenant)) {
                          // Un compte qui change de nom est tracé (jamais le nom lui-même) : la
                          // direction choisit les personnes à affecter d'après ces noms.
                          journal.consigner(
                              "ANNUAIRE_NOM_MODIFIE",
                              "Utilisateur",
                              evenement.utilisateurId(),
                              Map.of());
                        }
                        membres.save(m);
                      }
                    },
                    () ->
                        membres.save(
                            MembrePersonnel.creer(
                                evenement.utilisateurId(),
                                evenement.prenoms(),
                                evenement.nom(),
                                maintenant)));
          });
    } catch (RuntimeException e) {
      // Ni nom ni identifiant de personne dans le journal applicatif.
      LOG.warn("Annuaire du personnel non mis à jour ({})", e.getClass().getSimpleName());
    }
  }
}
