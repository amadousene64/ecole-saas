package sn.ecole.inscription.infrastructure;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import sn.ecole.inscription.StatistiquesInscription.EffectifClasse;
import sn.ecole.inscription.StatistiquesInscription.SuiviClasses;
import sn.ecole.inscription.domain.PieceDossier;
import sn.ecole.inscription.domain.StatistiquesInscriptionRepository;
import sn.ecole.inscription.domain.StatutInscription;

/**
 * JPQL paramétré, en agrégats uniquement (jamais de ligne d'élève). L'école courante est appliquée
 * par le filtre Hibernate et la RLS, sur chaque entité interrogée.
 */
@Component
class StatistiquesInscriptionJpa implements StatistiquesInscriptionRepository {

  /** Élèves ayant une inscription active dans ces classes, cette année. */
  private static final String ELEVES_ACTIFS =
      "select i.eleveId from Inscription i where i.anneeId = :annee and i.classeId in :classes"
          + " and i.statut in :actifs";

  private final EntityManager em;

  StatistiquesInscriptionJpa(EntityManager em) {
    this.em = em;
  }

  @Override
  public List<EffectifClasse> effectifs(UUID anneeId) {
    return em
        .createQuery(
            "select i.classeId, i.statut, count(i) from Inscription i where i.anneeId = :annee"
                + " group by i.classeId, i.statut",
            Object[].class)
        .setParameter("annee", anneeId)
        .getResultList()
        .stream()
        .map(
            l ->
                new EffectifClasse(
                    (UUID) l[0], ((StatutInscription) l[1]).name(), ((Number) l[2]).longValue()))
        .toList();
  }

  @Override
  public SuiviClasses suivi(UUID anneeId, Collection<UUID> classeIds) {
    long eleves =
        compter(
            "select count(distinct i.eleveId) from Inscription i where i.anneeId = :annee"
                + " and i.classeId in :classes and i.statut in :actifs",
            anneeId,
            classeIds);
    // Dossier complet : chaque pièce exigée a au moins une pièce déposée non bloquée.
    long complets =
        em.createQuery(
                "select count(distinct i.eleveId) from Inscription i where i.anneeId = :annee"
                    + " and i.classeId in :classes and i.statut in :actifs"
                    + " and (select count(distinct p.type) from PieceDossier p"
                    + " where p.eleveId = i.eleveId and p.type in :exigees"
                    + " and p.statut <> :infecte) = :nombreExigees",
                Long.class)
            .setParameter("annee", anneeId)
            .setParameter("classes", classeIds)
            .setParameter("actifs", StatutInscription.ACTIFS)
            .setParameter("exigees", PieceDossier.EXIGEES)
            .setParameter("infecte", PieceDossier.Statut.INFECTE)
            .setParameter("nombreExigees", (long) PieceDossier.EXIGEES.size())
            .getSingleResult();
    long familles =
        compter(
            "select count(distinct l.responsableId) from LienParental l"
                + " where l.accesEspaceParent = true and l.eleveId in ("
                + ELEVES_ACTIFS
                + ")",
            anneeId,
            classeIds);
    long connectees =
        compter(
            "select count(distinct l.responsableId) from LienParental l, Responsable r"
                + " where r.id = l.responsableId and r.utilisateurId is not null"
                + " and l.accesEspaceParent = true and l.eleveId in ("
                + ELEVES_ACTIFS
                + ")",
            anneeId,
            classeIds);
    return new SuiviClasses(eleves, complets, familles, connectees);
  }

  private long compter(String jpql, UUID anneeId, Collection<UUID> classeIds) {
    return em.createQuery(jpql, Long.class)
        .setParameter("annee", anneeId)
        .setParameter("classes", classeIds)
        .setParameter("actifs", StatutInscription.ACTIFS)
        .getSingleResult();
  }

  @Override
  public List<DateEtNombre> entreesParJour(
      UUID anneeId, Collection<UUID> classeIds, LocalDate debut, LocalDate fin) {
    return em
        .createQuery(
            "select i.dateEntree, count(i) from Inscription i where i.anneeId = :annee"
                + " and i.classeId in :classes and i.dateEntree between :debut and :fin"
                + " group by i.dateEntree",
            Object[].class)
        .setParameter("annee", anneeId)
        .setParameter("classes", classeIds)
        .setParameter("debut", debut)
        .setParameter("fin", fin)
        .getResultList()
        .stream()
        .map(l -> new DateEtNombre((LocalDate) l[0], ((Number) l[1]).longValue()))
        .toList();
  }
}
