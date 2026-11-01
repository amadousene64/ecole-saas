package sn.ecole.statistique.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import sn.ecole.etablissement.CodeCycle;
import sn.ecole.statistique.domain.PeriodeCourbe;

/** Tableau de bord de la direction : des nombres, aucun élève. */
public record TableauDeBordDto(
    UUID anneeId,
    String anneeLibelle,
    Instant calculeLe,
    PeriodeCourbe periode,
    CodeCycle cycle,
    long inscrits,
    List<EffectifCycleDto> parCycle,
    List<EffectifNiveauDto> parNiveau,
    List<EffectifStatutDto> parStatut,
    PartDto dossiers,
    PartDto familles,
    List<SemaineDto> courbe) {

  public record EffectifCycleDto(CodeCycle cycle, String libelle, long nombre) {}

  public record EffectifNiveauDto(
      UUID niveauId, String code, String libelle, CodeCycle cycle, long nombre) {}

  public record EffectifStatutDto(String statut, long nombre) {}

  public record PartDto(long total, long dont, int pourcentage) {}

  public record SemaineDto(LocalDate lundi, long nombre) {}
}
