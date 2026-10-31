package sn.ecole.inscription.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import sn.ecole.inscription.domain.Eleve;
import sn.ecole.inscription.domain.RechercheEleves;
import sn.ecole.inscription.domain.StatutInscription;

/**
 * JPQL construit selon les critères présents, toujours paramétré. L'école courante est appliquée
 * par le filtre Hibernate et la RLS.
 */
@Component
class RechercheElevesJpa implements RechercheEleves {

  private final EntityManager em;

  RechercheElevesJpa(EntityManager em) {
    this.em = em;
  }

  @Override
  public List<Eleve> rechercher(
      String terme,
      UUID classeId,
      StatutInscription statut,
      String apresNom,
      UUID apresId,
      int limite) {
    StringBuilder jpql = new StringBuilder("select e from Eleve e where 1 = 1");
    Map<String, Object> parametres = new LinkedHashMap<>();
    if (terme != null && !terme.isBlank()) {
      jpql.append(" and (e.nomRecherche like :motif escape '\\' or e.matricule = :matricule)");
      parametres.put("motif", "%" + echapper(terme) + "%");
      parametres.put("matricule", terme.strip().toUpperCase(Locale.ROOT));
    }
    if (classeId != null) {
      jpql.append(
          " and exists (select 1 from Inscription i where i.eleveId = e.id"
              + " and i.classeId = :classe and i.statut in :actifs)");
      parametres.put("classe", classeId);
      parametres.put("actifs", StatutInscription.ACTIFS);
    }
    if (statut != null) {
      // Statut de l'inscription la plus récente (date d'entrée) : celle qui décrit la situation.
      jpql.append(
          " and exists (select 1 from Inscription i where i.eleveId = e.id and i.statut = :statut"
              + " and i.dateEntree = (select max(j.dateEntree) from Inscription j"
              + " where j.eleveId = e.id))");
      parametres.put("statut", statut);
    }
    if (apresNom != null && apresId != null) {
      jpql.append(
          " and (e.nomRecherche > :apresNom or (e.nomRecherche = :apresNom and e.id > :apresId))");
      parametres.put("apresNom", apresNom);
      parametres.put("apresId", apresId);
    }
    jpql.append(" order by e.nomRecherche, e.id");
    TypedQuery<Eleve> requete = em.createQuery(jpql.toString(), Eleve.class);
    parametres.forEach(requete::setParameter);
    return requete.setMaxResults(limite).getResultList();
  }

  private static String echapper(String terme) {
    return terme.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
