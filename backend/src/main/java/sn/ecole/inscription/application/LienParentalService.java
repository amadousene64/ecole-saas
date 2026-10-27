package sn.ecole.inscription.application;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.inscription.domain.Eleve;
import sn.ecole.inscription.domain.EleveRepository;
import sn.ecole.inscription.domain.LienParental;
import sn.ecole.inscription.domain.LienParentalRepository;
import sn.ecole.inscription.domain.Responsable;
import sn.ecole.inscription.domain.ResponsableRepository;
import sn.ecole.inscription.domain.TypeLien;
import sn.ecole.shared.application.JournalAudit;
import sn.ecole.shared.domain.RoleEcole;

/**
 * Liens entre élèves et responsables. Seuls la direction et le secrétariat créent un lien : un
 * parent ne peut jamais se lier lui-même à un élève.
 *
 * <p>Ce que voit chacun : direction et secrétariat, tous les responsables de l'élève ; comptable,
 * les payeurs seulement ; parent, son propre lien seulement (jamais l'autre parent, ses coordonnées
 * ni ses droits).
 */
@Service
@Transactional
public class LienParentalService implements UtilisationEleve {

  /** Un lien et son responsable. */
  public record VueLien(LienParental lien, Responsable responsable) {}

  static final String LECTURE_RESPONSABLES =
      "hasAnyRole('DIRECTEUR','SECRETARIAT','COMPTABLE')"
          + " or (hasRole('PARENT') and hasPermission(#eleveId, 'Eleve', 'lire'))";

  private final LienParentalRepository liens;
  private final ResponsableRepository responsables;
  private final EleveRepository eleves;
  private final JournalAudit journal;

  public LienParentalService(
      LienParentalRepository liens,
      ResponsableRepository responsables,
      EleveRepository eleves,
      JournalAudit journal) {
    this.liens = liens;
    this.responsables = responsables;
    this.eleves = eleves;
    this.journal = journal;
  }

  @PreAuthorize(LECTURE_RESPONSABLES)
  @Transactional(readOnly = true)
  public List<VueLien> lister(UUID eleveId) {
    chargerEleve(eleveId);
    List<LienParental> tous = liens.findByEleveId(eleveId);
    List<LienParental> visibles;
    if (Erreurs.aLeRole(RoleEcole.DIRECTEUR) || Erreurs.aLeRole(RoleEcole.SECRETARIAT)) {
      visibles = tous;
    } else if (Erreurs.aLeRole(RoleEcole.COMPTABLE)) {
      visibles = tous.stream().filter(LienParental::isPayeur).toList();
    } else {
      // Parent : seulement son lien, et seulement s'il a accès à l'espace parent.
      UUID moi = Erreurs.utilisateurCourant();
      Set<UUID> miens =
          responsables.findByUtilisateurId(moi).map(r -> Set.of(r.getId())).orElse(Set.of());
      visibles =
          tous.stream()
              .filter(l -> miens.contains(l.getResponsableId()) && l.isAccesEspaceParent())
              .toList();
    }
    return vues(visibles);
  }

  @PreAuthorize(Droits.ECRITURE)
  public VueLien ajouter(UUID eleveId, UUID responsableId, TypeLien type, LienParental.Droits d) {
    chargerEleve(eleveId);
    Responsable responsable = chargerResponsable(responsableId);
    if (liens.findByEleveIdAndResponsableId(eleveId, responsableId).isPresent()) {
      throw Erreurs.conflit("LIEN_EXISTANT", "Ce responsable est déjà lié à cet élève.");
    }
    LienParental lien = liens.save(LienParental.creer(eleveId, responsableId, type, d));
    journal.consigner(
        "LIEN_PARENTAL_CREE",
        "Eleve",
        eleveId,
        Map.of("lien", lien.getId(), "responsable", responsableId, "type", type));
    return new VueLien(lien, responsable);
  }

  @PreAuthorize(Droits.ECRITURE)
  public VueLien modifier(
      UUID eleveId, UUID lienId, int version, TypeLien type, LienParental.Droits d) {
    LienParental lien = chargerLien(eleveId, lienId);
    Erreurs.verifierVersion(version, lien);
    if (lien.isResponsableLegal() && !d.responsableLegal()) {
      verifierPasDernierResponsableLegal(lien);
    }
    lien.modifier(type, d);
    LienParental enregistre = liens.save(lien);
    journal.consigner(
        "LIEN_PARENTAL_MODIFIE", "Eleve", eleveId, Map.of("lien", lienId, "droits", d));
    return new VueLien(enregistre, chargerResponsable(lien.getResponsableId()));
  }

  @PreAuthorize(Droits.ECRITURE)
  public void supprimer(UUID eleveId, UUID lienId) {
    LienParental lien = chargerLien(eleveId, lienId);
    if (lien.isResponsableLegal()) {
      verifierPasDernierResponsableLegal(lien);
    }
    lien.supprimer(Instant.now());
    liens.save(lien);
    journal.consigner("LIEN_PARENTAL_SUPPRIME", "Eleve", eleveId, Map.of("lien", lienId));
  }

  /**
   * Accès du responsable à l'espace parent (décision du directeur seul, par exemple sur décision de
   * justice) : sans accès, il ne voit plus l'élève et ne reçoit plus aucune notification.
   */
  @PreAuthorize(Droits.DIRECTION)
  public VueLien definirAccesEspaceParent(UUID eleveId, UUID lienId, int version, boolean acces) {
    LienParental lien = chargerLien(eleveId, lienId);
    Erreurs.verifierVersion(version, lien);
    lien.definirAccesEspaceParent(acces);
    LienParental enregistre = liens.save(lien);
    journal.consigner(
        "ACCES_ESPACE_PARENT_MODIFIE",
        "Eleve",
        eleveId,
        Map.of("lien", lienId, "responsable", lien.getResponsableId(), "acces", acces));
    return new VueLien(enregistre, chargerResponsable(lien.getResponsableId()));
  }

  /** Frères et sœurs : élèves ayant au moins un responsable en commun (sans table dédiée). */
  @PreAuthorize("hasAnyRole('DIRECTEUR','SECRETARIAT','COMPTABLE')")
  @Transactional(readOnly = true)
  public List<Eleve> fratrie(UUID eleveId) {
    chargerEleve(eleveId);
    Set<UUID> responsablesDeLEleve =
        liens.findByEleveId(eleveId).stream()
            .map(LienParental::getResponsableId)
            .collect(Collectors.toSet());
    if (responsablesDeLEleve.isEmpty()) {
      return List.of();
    }
    Set<UUID> freres = new LinkedHashSet<>();
    liens.findByResponsableIdIn(responsablesDeLEleve).stream()
        .map(LienParental::getEleveId)
        .filter(id -> !id.equals(eleveId))
        .forEach(freres::add);
    return eleves.findByIdIn(freres).stream()
        .sorted(Comparator.comparing(Eleve::getNomRecherche))
        .toList();
  }

  /** Enfants du parent connecté, dans l'école courante, auxquels il a accès. */
  @PreAuthorize("hasRole('PARENT')")
  @Transactional(readOnly = true)
  public List<Eleve> enfantsDuParentConnecte() {
    return responsables
        .findByUtilisateurId(Erreurs.utilisateurCourant())
        .map(
            r ->
                eleves
                    .findByIdIn(
                        liens.findByResponsableId(r.getId()).stream()
                            .filter(LienParental::isAccesEspaceParent)
                            .map(LienParental::getEleveId)
                            .toList())
                    .stream()
                    .sorted(Comparator.comparing(Eleve::getNomRecherche))
                    .toList())
        .orElse(List.of());
  }

  @Override
  public boolean eleveUtilise(UUID eleveId) {
    return liens.existsByEleveId(eleveId);
  }

  private void verifierPasDernierResponsableLegal(LienParental lien) {
    boolean autreLegal =
        liens.findByEleveId(lien.getEleveId()).stream()
            .anyMatch(l -> !l.getId().equals(lien.getId()) && l.isResponsableLegal());
    if (!autreLegal) {
      throw Erreurs.conflit(
          "DERNIER_RESPONSABLE_LEGAL",
          "L'élève doit garder au moins un responsable légal : désignez-en un autre d'abord.");
    }
  }

  private List<VueLien> vues(List<LienParental> liste) {
    Map<UUID, Responsable> parId =
        responsables
            .findByIdIn(liste.stream().map(LienParental::getResponsableId).toList())
            .stream()
            .collect(Collectors.toMap(Responsable::getId, Function.identity()));
    return liste.stream()
        .filter(l -> parId.containsKey(l.getResponsableId()))
        .map(l -> new VueLien(l, parId.get(l.getResponsableId())))
        .sorted(
            Comparator.comparing((VueLien v) -> !v.lien().isResponsableLegal())
                .thenComparing(v -> v.responsable().getNomRecherche()))
        .toList();
  }

  private Eleve chargerEleve(UUID eleveId) {
    return eleves.findById(eleveId).orElseThrow(Erreurs::eleveIntrouvable);
  }

  private Responsable chargerResponsable(UUID id) {
    return responsables
        .findById(id)
        .orElseThrow(
            () -> Erreurs.introuvable("RESPONSABLE_INTROUVABLE", "Responsable introuvable."));
  }

  private LienParental chargerLien(UUID eleveId, UUID lienId) {
    return liens
        .findById(lienId)
        .filter(l -> Objects.equals(l.getEleveId(), eleveId))
        .orElseThrow(() -> Erreurs.introuvable("LIEN_INTROUVABLE", "Lien introuvable."));
  }
}
