package sn.ecole.inscription.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.etablissement.ReferentielScolaire;
import sn.ecole.etablissement.ReferentielScolaire.ClasseScolaire;
import sn.ecole.etablissement.ReferentielScolaire.OptionScolaire;
import sn.ecole.etablissement.VerificateurUtilisationClasse;
import sn.ecole.inscription.InscriptionTerminee;
import sn.ecole.inscription.InscriptionValidee;
import sn.ecole.inscription.domain.Eleve;
import sn.ecole.inscription.domain.EleveRepository;
import sn.ecole.inscription.domain.Inscription;
import sn.ecole.inscription.domain.InscriptionOption;
import sn.ecole.inscription.domain.InscriptionOptionRepository;
import sn.ecole.inscription.domain.InscriptionRepository;
import sn.ecole.inscription.domain.PolitiqueCapacite;
import sn.ecole.inscription.domain.StatutInscription;
import sn.ecole.inscription.domain.VerrouClasse;
import sn.ecole.shared.application.JournalAudit;
import sn.ecole.shared.domain.ContexteTenant;

/**
 * Inscriptions annuelles : un élève dans une classe d'une année non clôturée, une seule inscription
 * active par élève et par année, options choisies parmi celles de la classe (une par groupe),
 * capacité contrôlée selon la politique de l'école (avertir ou bloquer).
 *
 * <p>Les opérations sur une classe prennent son verrou ({@link VerrouClasse}) : deux inscriptions
 * simultanées ne dépassent pas la capacité, et la classe ne peut pas être supprimée pendant qu'on y
 * inscrit un élève.
 */
@Service
@Transactional
public class InscriptionService implements VerificateurUtilisationClasse, UtilisationEleve {

  /** Inscription, nom de sa classe, options choisies et avertissements éventuels. */
  public record VueInscription(
      Inscription inscription, String classe, List<UUID> options, List<String> avertissements) {}

  private final InscriptionRepository inscriptions;
  private final InscriptionOptionRepository choix;
  private final EleveRepository eleves;
  private final ReferentielScolaire referentiel;
  private final ParametresInscriptionService parametres;
  private final VerrouClasse verrou;
  private final ApplicationEventPublisher evenements;
  private final JournalAudit journal;

  public InscriptionService(
      InscriptionRepository inscriptions,
      InscriptionOptionRepository choix,
      EleveRepository eleves,
      ReferentielScolaire referentiel,
      ParametresInscriptionService parametres,
      VerrouClasse verrou,
      ApplicationEventPublisher evenements,
      JournalAudit journal) {
    this.inscriptions = inscriptions;
    this.choix = choix;
    this.eleves = eleves;
    this.referentiel = referentiel;
    this.parametres = parametres;
    this.verrou = verrou;
    this.evenements = evenements;
    this.journal = journal;
  }

  /**
   * @param statut PRE_INSCRIT ou INSCRIT
   */
  @PreAuthorize(Droits.ECRITURE)
  public VueInscription inscrire(
      UUID eleveId,
      UUID classeId,
      StatutInscription statut,
      boolean redoublant,
      Collection<UUID> options) {
    eleves.findById(eleveId).orElseThrow(Erreurs::eleveIntrouvable);
    ClasseScolaire classe = classeOuverte(classeId);
    verrou.verrouiller(classeId);
    if (inscriptions
        .findFirstByEleveIdAndAnneeIdAndStatutIn(
            eleveId, classe.anneeId(), StatutInscription.ACTIFS)
        .isPresent()) {
      throw Erreurs.conflit(
          "INSCRIPTION_ACTIVE_EXISTANTE",
          "Cet élève a déjà une inscription active pour cette année.");
    }
    Set<UUID> optionsChoisies = verifierOptions(classe, options);
    List<String> avertissements = controlerCapacite(classe);
    Inscription inscription =
        Inscription.creer(
            eleveId,
            classeId,
            classe.anneeId(),
            statut,
            redoublant,
            LocalDate.now(EleveService.FUSEAU));
    try {
      inscription = inscriptions.saveAndFlush(inscription);
    } catch (DataIntegrityViolationException e) {
      // Course perdue contre une inscription simultanée du même élève dans une autre classe.
      throw Erreurs.conflit(
          "INSCRIPTION_ACTIVE_EXISTANTE",
          "Cet élève a déjà une inscription active pour cette année.");
    }
    for (UUID option : optionsChoisies) {
      choix.save(InscriptionOption.choisir(inscription.getId(), option));
    }
    journal.consigner(
        "ELEVE_INSCRIT",
        "Eleve",
        eleveId,
        Map.of("inscription", inscription.getId(), "classe", classeId, "statut", statut));
    if (statut == StatutInscription.INSCRIT) {
      publierValidation(inscription);
    }
    return new VueInscription(
        inscription, classe.nom(), List.copyOf(optionsChoisies), avertissements);
  }

  @PreAuthorize(Droits.SCOLARITE)
  @Transactional(readOnly = true)
  public VueInscription lire(UUID inscriptionId) {
    return vue(charger(inscriptionId), List.of());
  }

  /** Historique de l'élève, de la plus récente à la plus ancienne. */
  @PreAuthorize(Droits.SCOLARITE_ELEVE)
  @Transactional(readOnly = true)
  public List<VueInscription> historique(UUID eleveId) {
    eleves.findById(eleveId).orElseThrow(Erreurs::eleveIntrouvable);
    return vues(inscriptions.findByEleveIdOrderByDateEntreeDesc(eleveId));
  }

  /** Élèves pré-inscrits ou inscrits dans la classe, par nom. */
  @PreAuthorize(Droits.SCOLARITE)
  @Transactional(readOnly = true)
  public List<ResumeEleve> elevesDeLaClasse(UUID classeId) {
    referentiel.classe(classeId);
    List<UUID> ids =
        inscriptions.findByClasseIdAndStatutIn(classeId, StatutInscription.ACTIFS).stream()
            .map(Inscription::getEleveId)
            .toList();
    String nom = referentiel.nomsClasses(List.of(classeId)).get(classeId);
    return eleves.findByIdIn(ids).stream()
        .sorted(java.util.Comparator.comparing(Eleve::getNomRecherche))
        .map(e -> new ResumeEleve(e, nom))
        .toList();
  }

  /** PRE_INSCRIT → INSCRIT. */
  @PreAuthorize(Droits.ECRITURE)
  public VueInscription valider(UUID inscriptionId, int version) {
    Inscription inscription = charger(inscriptionId);
    Erreurs.verifierVersion(version, inscription);
    classeOuverte(inscription.getClasseId());
    inscription.valider();
    Inscription enregistree = inscriptions.save(inscription);
    journal.consigner(
        "INSCRIPTION_VALIDEE",
        "Eleve",
        inscription.getEleveId(),
        Map.of("inscription", inscriptionId));
    publierValidation(enregistree);
    return vue(enregistree, List.of());
  }

  /**
   * Change la classe (même année), le redoublement ou les options d'une inscription active. Les
   * options sont revérifiées contre la classe (nouvelle ou non).
   */
  @PreAuthorize(Droits.ECRITURE)
  public VueInscription modifier(
      UUID inscriptionId,
      int version,
      UUID classeId,
      boolean redoublant,
      Collection<UUID> options) {
    Inscription inscription = charger(inscriptionId);
    Erreurs.verifierVersion(version, inscription);
    UUID cible = classeId == null ? inscription.getClasseId() : classeId;
    ClasseScolaire classe = classeOuverte(cible);
    List<String> avertissements = List.of();
    if (!cible.equals(inscription.getClasseId())) {
      if (!classe.anneeId().equals(inscription.getAnneeId())) {
        throw Erreurs.regle(
            "ANNEE_DIFFERENTE", "Changement de classe possible seulement dans la même année.");
      }
      // Verrous toujours pris dans le même ordre : pas d'interblocage entre deux changements.
      List<UUID> aVerrouiller = new ArrayList<>(List.of(inscription.getClasseId(), cible));
      aVerrouiller.sort(null);
      aVerrouiller.forEach(verrou::verrouiller);
      avertissements = controlerCapacite(classe);
      inscription.changerClasse(cible);
    }
    inscription.definirRedoublant(redoublant);
    Set<UUID> voulues = verifierOptions(classe, options);
    remplacerOptions(inscriptionId, voulues);
    Inscription enregistree = inscriptions.save(inscription);
    journal.consigner(
        "INSCRIPTION_MODIFIEE",
        "Eleve",
        inscription.getEleveId(),
        Map.of("inscription", inscriptionId, "classe", cible, "redoublant", redoublant));
    return vue(enregistree, avertissements);
  }

  /** Sortie : transfert, radiation ou abandon. La radiation est une décision du directeur seul. */
  @PreAuthorize(
      "hasRole('DIRECTEUR') or (hasRole('SECRETARIAT') and #sortie != null"
          + " && #sortie.name() != 'RADIE')")
  public VueInscription sortir(
      UUID inscriptionId, int version, StatutInscription sortie, LocalDate date, String motif) {
    Inscription inscription = charger(inscriptionId);
    Erreurs.verifierVersion(version, inscription);
    classeOuverte(inscription.getClasseId());
    inscription.sortir(sortie, date, motif);
    Inscription enregistree = inscriptions.save(inscription);
    journal.consigner(
        "INSCRIPTION_TERMINEE",
        "Eleve",
        inscription.getEleveId(),
        Map.of("inscription", inscriptionId, "statut", sortie, "date", date.toString()));
    evenements.publishEvent(
        new InscriptionTerminee(
            ContexteTenant.exiger(), inscriptionId, inscription.getEleveId(), sortie.name(), date));
    return vue(enregistree, List.of());
  }

  /** Classe actuelle (inscription active la plus récente) de chaque élève, pour les listes. */
  Map<UUID, String> classesActuelles(Collection<UUID> eleveIds) {
    if (eleveIds.isEmpty()) {
      return Map.of();
    }
    Map<UUID, Inscription> derniere = new HashMap<>();
    for (Inscription i :
        inscriptions.findByEleveIdInAndStatutIn(eleveIds, StatutInscription.ACTIFS)) {
      derniere.merge(
          i.getEleveId(), i, (a, b) -> a.getDateEntree().isAfter(b.getDateEntree()) ? a : b);
    }
    Map<UUID, String> noms =
        referentiel.nomsClasses(
            derniere.values().stream().map(Inscription::getClasseId).collect(Collectors.toSet()));
    Map<UUID, String> resultat = new HashMap<>();
    derniere.forEach((eleve, i) -> resultat.put(eleve, noms.get(i.getClasseId())));
    return resultat;
  }

  /** Inscriptions actives de l'élève (pour l'éligibilité à un compte élève). */
  List<Inscription> actives(UUID eleveId) {
    return inscriptions.findByEleveIdInAndStatutIn(List.of(eleveId), StatutInscription.ACTIFS);
  }

  // --- Ports implémentés ---

  /** Issue #3 : toute inscription, quel que soit son statut, rend la classe utilisée. */
  @Override
  public boolean classeUtilisee(UUID classeId) {
    verrou.verrouiller(classeId);
    return inscriptions.existsByClasseId(classeId);
  }

  @Override
  public boolean optionUtilisee(UUID classeId, UUID optionId) {
    verrou.verrouiller(classeId);
    return choix.optionChoisieDansClasse(classeId, optionId);
  }

  @Override
  public boolean eleveUtilise(UUID eleveId) {
    return inscriptions.existsByEleveId(eleveId);
  }

  // --- Règles ---

  private ClasseScolaire classeOuverte(UUID classeId) {
    ClasseScolaire classe = referentiel.classe(classeId);
    if (referentiel.annee(classe.anneeId()).cloturee()) {
      throw Erreurs.conflit("ANNEE_CLOTUREE", "L'année scolaire est clôturée : lecture seule.");
    }
    return classe;
  }

  private static Set<UUID> verifierOptions(ClasseScolaire classe, Collection<UUID> options) {
    Set<UUID> voulues = new LinkedHashSet<>(options == null ? List.of() : options);
    Map<UUID, String> groupes =
        classe.options().stream()
            .collect(Collectors.toMap(OptionScolaire::id, OptionScolaire::groupe));
    InscriptionOption.verifierChoix(voulues, groupes);
    return voulues;
  }

  /** À appeler sous le verrou de la classe, avant d'y ajouter l'élève. */
  private List<String> controlerCapacite(ClasseScolaire classe) {
    long occupees = inscriptions.countByClasseIdAndStatutIn(classe.id(), StatutInscription.ACTIFS);
    if (occupees < classe.capacite()) {
      return List.of();
    }
    if (parametres.courants().getPolitiqueCapacite() == PolitiqueCapacite.BLOQUER) {
      throw Erreurs.conflit(
          "CAPACITE_ATTEINTE",
          "La classe " + classe.nom() + " est complète (" + classe.capacite() + " places).");
    }
    return List.of(
        "CAPACITE_DEPASSEE : la classe "
            + classe.nom()
            + " compte désormais "
            + (occupees + 1)
            + " élèves pour "
            + classe.capacite()
            + " places.");
  }

  private void remplacerOptions(UUID inscriptionId, Set<UUID> voulues) {
    for (InscriptionOption actuel : choix.findByInscriptionId(inscriptionId)) {
      if (!voulues.remove(actuel.getOptionId())) {
        actuel.retirer(Instant.now());
        choix.save(actuel);
      }
    }
    for (UUID option : voulues) {
      choix.save(InscriptionOption.choisir(inscriptionId, option));
    }
  }

  private void publierValidation(Inscription i) {
    evenements.publishEvent(
        new InscriptionValidee(
            ContexteTenant.exiger(), i.getId(), i.getEleveId(), i.getClasseId(), i.getAnneeId()));
  }

  private Inscription charger(UUID id) {
    return inscriptions
        .findById(id)
        .orElseThrow(
            () -> Erreurs.introuvable("INSCRIPTION_INTROUVABLE", "Inscription introuvable."));
  }

  private VueInscription vue(Inscription i, List<String> avertissements) {
    return new VueInscription(
        i,
        referentiel.nomsClasses(List.of(i.getClasseId())).get(i.getClasseId()),
        choix.findByInscriptionId(i.getId()).stream().map(InscriptionOption::getOptionId).toList(),
        avertissements);
  }

  private List<VueInscription> vues(List<Inscription> liste) {
    Map<UUID, String> noms =
        referentiel.nomsClasses(
            liste.stream().map(Inscription::getClasseId).collect(Collectors.toSet()));
    Map<UUID, List<UUID>> options = new HashMap<>();
    choix
        .findByInscriptionIdIn(liste.stream().map(Inscription::getId).toList())
        .forEach(
            c ->
                options
                    .computeIfAbsent(c.getInscriptionId(), k -> new ArrayList<>())
                    .add(c.getOptionId()));
    return liste.stream()
        .map(
            i ->
                new VueInscription(
                    i,
                    noms.get(i.getClasseId()),
                    options.getOrDefault(i.getId(), List.of()),
                    List.of()))
        .toList();
  }
}
