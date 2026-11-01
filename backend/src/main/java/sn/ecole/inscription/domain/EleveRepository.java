package sn.ecole.inscription.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port de persistance : toujours limité à l'école courante (filtre Hibernate + RLS). */
public interface EleveRepository {

  Optional<Eleve> findById(UUID id);

  List<Eleve> findByIdIn(Collection<UUID> ids);

  Optional<Eleve> findByUtilisateurId(UUID utilisateurId);

  Optional<Eleve> findByIdentifiantNational(String identifiantNational);

  /** Doublon probable : même nom et prénoms (normalisés) et même date de naissance. */
  boolean existsByNomRechercheAndDateNaissance(String nomRecherche, java.time.LocalDate naissance);

  /** Compte aussi les élèves supprimés : un matricule n'est jamais réutilisé. */
  boolean matriculeExiste(String matricule);

  Eleve save(Eleve eleve);
}
