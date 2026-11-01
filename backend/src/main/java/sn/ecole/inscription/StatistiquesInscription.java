package sn.ecole.inscription;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Agrégats d'inscription pour le tableau de bord de la direction. Que des nombres : aucun nom,
 * aucune donnée de santé ni de famille ne sort de ce module par cette interface. Réservé au
 * directeur.
 */
public interface StatistiquesInscription {

  /**
   * Nombre d'inscriptions de l'année par classe et par statut (les combinaisons à zéro sont
   * absentes).
   */
  List<EffectifClasse> effectifs(UUID anneeId);

  /**
   * Suivi des élèves ayant une inscription active (PRE_INSCRIT ou INSCRIT) dans ces classes, pour
   * cette année. Sans classe : tout à zéro.
   */
  SuiviClasses suivi(UUID anneeId, Collection<UUID> classeIds);

  /**
   * Inscriptions dont la date d'entrée tombe entre ces deux dates (incluses), dans ces classes et
   * cette année, par semaine (lundi de la semaine), toutes les semaines de la plage présentes, y
   * compris à zéro. Toutes les inscriptions comptent, même celles qui ont pris fin depuis : c'est
   * un flux d'inscriptions, pas un effectif.
   */
  List<InscriptionsSemaine> inscriptionsParSemaine(
      UUID anneeId, Collection<UUID> classeIds, LocalDate debut, LocalDate fin);

  record EffectifClasse(UUID classeId, String statut, long nombre) {}

  /**
   * @param eleves élèves avec inscription active
   * @param dossiersComplets parmi eux, ceux dont toutes les pièces exigées sont déposées (une pièce
   *     bloquée par l'antivirus ne compte pas)
   * @param familles responsables liés à ces élèves avec accès à l'espace parent
   * @param famillesConnectees parmi elles, celles dont le compte est activé
   */
  record SuiviClasses(long eleves, long dossiersComplets, long familles, long famillesConnectees) {}

  record InscriptionsSemaine(LocalDate lundi, long nombre) {}
}
