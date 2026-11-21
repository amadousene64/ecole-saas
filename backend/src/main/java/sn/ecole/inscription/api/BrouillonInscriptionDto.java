package sn.ecole.inscription.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import sn.ecole.inscription.domain.Sexe;
import sn.ecole.inscription.domain.TypeLien;

/**
 * Brouillon de l'assistant d'inscription, déchiffré. Tous les champs du contenu sont facultatifs
 * pendant la saisie ; la finalisation contrôle le tout. La version est aussi dans l'ETag.
 */
public record BrouillonInscriptionDto(
    UUID id,
    UUID anneeId,
    int etape,
    @Valid ContenuDto contenu,
    UUID auteurId,
    Instant modifieLe,
    Instant expireLe,
    int version) {

  /** Contenu saisi : identité, responsables, scolarité. */
  public record ContenuDto(
      @Valid EleveSaisiDto eleve,
      @Valid @Size(max = 4) List<ResponsableSaisiDto> responsables,
      @Valid ScolariteDto scolarite) {

    @Override
    public String toString() {
      return "ContenuDto[***]";
    }
  }

  public record EleveSaisiDto(
      @Size(max = 100) String prenoms,
      @Size(max = 60) String nom,
      Sexe sexe,
      LocalDate dateNaissance,
      @Size(max = 100) String lieuNaissance,
      @Size(max = 2) String nationalite,
      @Size(max = 40) String identifiantNational) {

    @Override
    public String toString() {
      return "EleveSaisiDto[***]";
    }
  }

  /** Responsable existant ({@code responsableId}) ou à créer (identité). */
  public record ResponsableSaisiDto(
      UUID responsableId,
      @Size(max = 100) String prenoms,
      @Size(max = 60) String nom,
      @Size(max = 30) String telephone,
      @Email @Size(max = 254) String email,
      TypeLien type,
      boolean responsableLegal,
      boolean payeur,
      boolean contactUrgence,
      boolean autoriseRecuperer,
      boolean recoitNotifications) {

    @Override
    public String toString() {
      return "ResponsableSaisiDto[***]";
    }
  }

  public record ScolariteDto(
      UUID classeId, @Size(max = 10) List<UUID> options, boolean redoublant) {}
}
