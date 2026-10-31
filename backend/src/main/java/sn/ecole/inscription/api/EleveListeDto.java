package sn.ecole.inscription.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import sn.ecole.inscription.domain.PieceDossier;
import sn.ecole.inscription.domain.Sexe;
import sn.ecole.inscription.domain.StatutInscription;

/**
 * Ligne de la liste des élèves du personnel. {@code classe} : classe actuelle, absente si non
 * inscrit ; {@code statut} : celui de l'inscription la plus récente, absent si l'élève n'en a
 * jamais eu ; {@code dossier} : absent pour qui ne peut pas lire les pièces.
 */
public record EleveListeDto(
    UUID id,
    String matricule,
    String prenoms,
    String nom,
    Sexe sexe,
    LocalDate dateNaissance,
    String classe,
    StatutInscription statut,
    DossierDto dossier) {

  /**
   * État du dossier au regard des pièces exigées : types sans pièce valable, nombre de pièces en
   * analyse et bloquées par l'antivirus.
   */
  public record DossierDto(List<PieceDossier.Type> manquantes, int enAnalyse, int refusees) {}

  @Override
  public String toString() {
    return "EleveListeDto[" + id + "]";
  }
}
