package sn.ecole.inscription.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.inscription.domain.Responsable;
import sn.ecole.inscription.domain.ResponsableRepository;
import sn.ecole.inscription.domain.Saisies;
import sn.ecole.shared.application.AppartenanceService;
import sn.ecole.shared.application.JournalAudit;
import sn.ecole.shared.domain.RoleEcole;

/** Responsables (parents, tuteurs) de l'école : saisie par le secrétariat ou la direction. */
@Service
@Transactional
public class ResponsableService {

  public record Saisie(
      String prenoms, String nom, String telephone, String email, String profession) {

    @Override
    public String toString() {
      return "Saisie[***]";
    }
  }

  private final ResponsableRepository responsables;
  private final AppartenanceService appartenances;
  private final JournalAudit journal;

  public ResponsableService(
      ResponsableRepository responsables, AppartenanceService appartenances, JournalAudit journal) {
    this.responsables = responsables;
    this.appartenances = appartenances;
    this.journal = journal;
  }

  /**
   * Recherche par téléphone (pour éviter les doublons à la saisie) ou par nom. Un critère est
   * obligatoire : pas d'export de tous les responsables d'un coup.
   */
  @PreAuthorize(Droits.ECRITURE)
  @Transactional(readOnly = true)
  public List<Responsable> rechercher(String telephone, String nom) {
    if (telephone != null && !telephone.isBlank()) {
      return responsables.findByTelephoneOrderByNomRecherche(Responsable.telephone(telephone));
    }
    if (nom != null && nom.strip().length() >= 2) {
      return responsables.findTop50ByNomRechercheContainingOrderByNomRecherche(
          Saisies.normaliser(nom));
    }
    throw Erreurs.invalide(
        "CRITERE_REQUIS", "Indiquez un téléphone ou au moins deux lettres du nom.");
  }

  @PreAuthorize(Droits.ECRITURE)
  @Transactional(readOnly = true)
  public Responsable lire(UUID id) {
    return charger(id);
  }

  @PreAuthorize(Droits.ECRITURE)
  public Responsable creer(Saisie s) {
    Responsable r =
        responsables.save(
            Responsable.creer(s.prenoms(), s.nom(), s.telephone(), s.email(), s.profession()));
    journal.consigner("RESPONSABLE_CREE", "Responsable", r.getId(), Map.of());
    return r;
  }

  @PreAuthorize(Droits.ECRITURE)
  public Responsable modifier(UUID id, int version, Saisie s) {
    Responsable r = charger(id);
    Erreurs.verifierVersion(version, r);
    r.modifier(s.prenoms(), s.nom(), s.telephone(), s.email(), s.profession());
    Responsable enregistre = responsables.save(r);
    journal.consigner("RESPONSABLE_MODIFIE", "Responsable", id, Map.of());
    return enregistre;
  }

  /**
   * Détache le compte relié (téléphone perdu, compte compromis, récupération sans e-mail) et lui
   * retire le rôle PARENT dans l'école. Un nouveau code pourra ensuite être émis.
   */
  @PreAuthorize(Droits.DIRECTION)
  public Responsable delierCompte(UUID id) {
    Responsable r = charger(id);
    UUID ancien = r.delierCompte();
    if (ancien == null) {
      throw Erreurs.introuvable("COMPTE_NON_RELIE", "Aucun compte n'est relié à ce responsable.");
    }
    Responsable enregistre = responsables.save(r);
    appartenances.retirer(ancien, RoleEcole.PARENT);
    journal.consigner("COMPTE_PARENT_DELIE", "Responsable", id, Map.of());
    return enregistre;
  }

  Responsable charger(UUID id) {
    return responsables
        .findById(id)
        .orElseThrow(
            () -> Erreurs.introuvable("RESPONSABLE_INTROUVABLE", "Responsable introuvable."));
  }
}
