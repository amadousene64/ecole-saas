package sn.ecole.inscription.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import sn.ecole.inscription.api.BrouillonInscriptionDto.ContenuDto;
import sn.ecole.inscription.domain.StatutInscription;

/** Requêtes et réponses annexes de l'assistant d'inscription. */
public final class BrouillonInscriptionRequetes {

  private BrouillonInscriptionRequetes() {}

  /** Ouverture : année facultative (année en cours par défaut). */
  public record OuvertureBrouillon(UUID anneeId) {}

  /** Enregistrement d'une étape : le contenu complet, et l'étape atteinte (1 à 5). */
  public record EnregistrementBrouillon(
      @Min(1) @Max(5) int etape, @NotNull @Valid ContenuDto contenu) {}

  /**
   * Finalisation : statut de l'inscription et, facultatif, le rang (à partir de 0) du responsable
   * du brouillon pour qui émettre un code d'activation.
   */
  public record FinalisationBrouillon(
      @NotNull StatutInscription statut, @Min(0) @Max(3) Integer codePour) {}

  public record BrouillonResumeDto(
      UUID id, String libelle, int etape, UUID auteurId, Instant modifieLe, int version) {}

  /** {@code codeActivation} : présent si demandé ; affiché une seule fois, jamais relisible. */
  public record BrouillonFinaliseDto(
      UUID eleveId,
      UUID inscriptionId,
      List<String> avertissements,
      CodeActivationDto codeActivation) {}
}
