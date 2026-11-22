package sn.ecole.personnel.domain;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import sn.ecole.etablissement.CodeCycle;

/**
 * Cache du périmètre d'un utilisateur, pour un jour donné, versionné par la génération des droits
 * de l'utilisateur (la même que celle des rôles) : toute modification de ses affectations passe à
 * une nouvelle génération, et l'ancienne entrée n'est plus jamais lue. Un cache vide ou en panne
 * n'est jamais une source de droits : on relit les affectations.
 */
public interface CachePerimetre {

  Optional<Calcule> lire(UUID tenantId, UUID utilisateurId, long generation, LocalDate jour);

  void ecrire(UUID tenantId, UUID utilisateurId, long generation, LocalDate jour, Calcule calcule);

  /**
   * Classes affectées (enseignement, titulaire) et cycles surveillés, en vigueur ce jour-là ; un
   * cycle n'est surveillé que pour l'année de son affectation.
   */
  record Calcule(Set<UUID> classes, Set<CycleSurveille> cycles) {

    public static final Calcule AUCUN = new Calcule(Set.of(), Set.of());

    public Calcule {
      classes = Set.copyOf(classes);
      cycles = Set.copyOf(cycles);
    }
  }

  record CycleSurveille(UUID anneeId, CodeCycle codeCycle) {}
}
