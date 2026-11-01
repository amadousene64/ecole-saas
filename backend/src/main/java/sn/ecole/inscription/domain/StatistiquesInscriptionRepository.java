package sn.ecole.inscription.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import sn.ecole.inscription.StatistiquesInscription.EffectifClasse;
import sn.ecole.inscription.StatistiquesInscription.SuiviClasses;

/**
 * Port des agrégats d'inscription : toujours limité à l'école courante (filtre Hibernate + RLS).
 */
public interface StatistiquesInscriptionRepository {

  List<EffectifClasse> effectifs(UUID anneeId);

  /** {@code classeIds} n'est jamais vide. */
  SuiviClasses suivi(UUID anneeId, Collection<UUID> classeIds);

  /** {@code classeIds} n'est jamais vide ; un nombre d'inscriptions par date d'entrée. */
  List<DateEtNombre> entreesParJour(
      UUID anneeId, Collection<UUID> classeIds, LocalDate debut, LocalDate fin);

  record DateEtNombre(LocalDate date, long nombre) {}
}
