package sn.ecole.statistique.domain;

import java.util.Optional;
import java.util.UUID;
import sn.ecole.etablissement.CodeCycle;

/**
 * Cache du tableau de bord, par école, année, période et cycle. Un cache vide ou en panne n'est
 * jamais une erreur : on recalcule depuis la base.
 */
public interface CacheTableauDeBord {

  Optional<TableauDeBord> lire(UUID tenantId, UUID anneeId, PeriodeCourbe periode, CodeCycle cycle);

  void ecrire(
      UUID tenantId, UUID anneeId, PeriodeCourbe periode, CodeCycle cycle, TableauDeBord tableau);
}
