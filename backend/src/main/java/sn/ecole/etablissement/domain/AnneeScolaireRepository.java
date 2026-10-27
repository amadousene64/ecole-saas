package sn.ecole.etablissement.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port de persistance : toujours limité à l'école courante (filtre Hibernate + RLS). */
public interface AnneeScolaireRepository {
  List<AnneeScolaire> findAllByOrderByDateDebutDesc();

  Optional<AnneeScolaire> findById(UUID id);

  Optional<AnneeScolaire> findByStatut(StatutAnnee statut);

  Optional<AnneeScolaire> findByLibelle(String libelle);

  boolean existsByStatutIn(Collection<StatutAnnee> statuts);

  AnneeScolaire save(AnneeScolaire annee);

  /** Écrit tout de suite : une violation d'unicité remonte dans l'appel, pas à la validation. */
  AnneeScolaire saveAndFlush(AnneeScolaire annee);
}
