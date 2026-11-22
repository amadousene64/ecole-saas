package sn.ecole.inscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import sn.ecole.shared.domain.TenantAwareEntity;

/**
 * Brouillon de l'assistant d'inscription (5 étapes), enregistré côté serveur à chaque étape. Le
 * contenu saisi (identité, responsables, scolarité) n'est manipulé ici que CHIFFRÉ : le service
 * applicatif le sérialise, le chiffre et le déchiffre. À l'abandon ou à la finalisation, le contenu
 * est effacé et le brouillon marqué supprimé : rien de lisible ne reste.
 */
@Entity
@Table(name = "brouillon_inscription")
public class BrouillonInscription extends TenantAwareEntity {

  public static final int ETAPES = 5;

  /** Un brouillon non terminé est supprimé après 30 jours sans modification. */
  public static final Duration CONSERVATION = Duration.ofDays(30);

  /** Brouillons en cours par auteur : au-delà, il faut en terminer ou en abandonner un. */
  public static final int MAX_PAR_AUTEUR = 50;

  @Column(name = "annee_id", nullable = false, updatable = false)
  private UUID anneeId;

  @Column(name = "auteur_id", nullable = false, updatable = false)
  private UUID auteurId;

  @Column(nullable = false)
  private short etape = 1;

  @Column(name = "contenu")
  private byte[] contenu;

  protected BrouillonInscription() {}

  /** Le contenu chiffré est posé ensuite ({@link #remplacer}) : son contexte inclut l'id. */
  public static BrouillonInscription ouvrir(UUID anneeId, UUID auteurId) {
    BrouillonInscription b = new BrouillonInscription();
    b.anneeId = Objects.requireNonNull(anneeId);
    b.auteurId = Objects.requireNonNull(auteurId);
    return b;
  }

  public void remplacer(byte[] contenuChiffre, int etapeAtteinte) {
    if (estSupprime()) {
      throw Saisies.conflit("BROUILLON_CLOS", "Ce brouillon a déjà été finalisé ou abandonné.");
    }
    if (etapeAtteinte < 1 || etapeAtteinte > ETAPES) {
      throw Saisies.invalide("ETAPE_INVALIDE", "L'étape va de 1 à " + ETAPES + ".");
    }
    contenu = Objects.requireNonNull(contenuChiffre);
    etape = (short) etapeAtteinte;
  }

  /** Date de suppression automatique si le brouillon n'est pas terminé d'ici là. */
  public Instant expireLe() {
    Instant reference = getModifieLe() != null ? getModifieLe() : getCreeLe();
    return reference == null ? null : reference.plus(CONSERVATION);
  }

  /** Abandon ou finalisation : le contenu est effacé, la ligne marquée supprimée. */
  public void clore(Instant quand) {
    contenu = null;
    marquerSupprime(Objects.requireNonNull(quand));
  }

  public UUID getAnneeId() {
    return anneeId;
  }

  public UUID getAuteurId() {
    return auteurId;
  }

  public int getEtape() {
    return etape;
  }

  public byte[] getContenu() {
    return contenu;
  }
}
