package sn.ecole.inscription.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import sn.ecole.inscription.domain.BrouillonInscription;
import sn.ecole.shared.domain.ContexteTenant;

/**
 * JPQL paramétré ; l'école courante est appliquée par le filtre Hibernate et la RLS, les brouillons
 * clos sont exclus par la restriction de l'entité.
 */
class PageBrouillonsImpl implements PageBrouillons {

  private final EntityManager em;

  PageBrouillonsImpl(EntityManager em) {
    this.em = em;
  }

  @Override
  public List<BrouillonInscription> page(Instant apresModifieLe, UUID apresId, int limite) {
    String apres =
        apresModifieLe == null
            ? ""
            : " where b.modifieLe < :t or (b.modifieLe = :t and b.id < :id)";
    TypedQuery<BrouillonInscription> requete =
        em.createQuery(
                "select b from BrouillonInscription b"
                    + apres
                    + " order by b.modifieLe desc, b.id desc",
                BrouillonInscription.class)
            .setMaxResults(limite);
    if (apresModifieLe != null) {
      requete.setParameter("t", apresModifieLe).setParameter("id", apresId);
    }
    return requete.getResultList();
  }

  /** Verrou consultatif de transaction, propre à l'école et à l'auteur ; ne lit aucune donnée. */
  @Override
  public void verrouillerAuteur(UUID auteurId) {
    em.createNativeQuery(
            "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(:cle, 0))) verrou")
        .setParameter("cle", "brouillons:" + ContexteTenant.exiger() + ":" + auteurId)
        .getSingleResult();
  }
}
