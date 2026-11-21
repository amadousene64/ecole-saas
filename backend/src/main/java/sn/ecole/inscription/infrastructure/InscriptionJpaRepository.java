package sn.ecole.inscription.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import sn.ecole.inscription.domain.Inscription;
import sn.ecole.inscription.domain.InscriptionRepository;
import sn.ecole.inscription.domain.StatutInscription;

/** Implémentation Spring Data du port {@link InscriptionRepository}. */
interface InscriptionJpaRepository extends Repository<Inscription, UUID>, InscriptionRepository {

  @Override
  @Query(
      "select i.classeId as classeId, count(i) as nombre from Inscription i"
          + " where i.anneeId = :annee and i.statut in :statuts group by i.classeId")
  List<EffectifClasse> compterParClasse(
      @Param("annee") UUID anneeId, @Param("statuts") Collection<StatutInscription> statuts);
}
