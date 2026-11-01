package sn.ecole.inscription.application;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.etablissement.ReferentielScolaire;
import sn.ecole.etablissement.ReferentielScolaire.ClasseScolaire;
import sn.ecole.inscription.domain.EleveRepository;
import sn.ecole.inscription.domain.IdentiteEleve;
import sn.ecole.inscription.domain.ImportEleves;
import sn.ecole.inscription.domain.ImportElevesRepository;
import sn.ecole.inscription.domain.LecteurTableur;
import sn.ecole.inscription.domain.LecteurTableur.Ligne;
import sn.ecole.inscription.domain.LienParental;
import sn.ecole.inscription.domain.Matricule;
import sn.ecole.inscription.domain.PolitiqueCapacite;
import sn.ecole.inscription.domain.Responsable;
import sn.ecole.inscription.domain.ResponsableRepository;
import sn.ecole.inscription.domain.Saisies;
import sn.ecole.inscription.domain.Sexe;
import sn.ecole.inscription.domain.StatutInscription;
import sn.ecole.inscription.domain.TypeLien;
import sn.ecole.shared.application.JournalAudit;
import sn.ecole.shared.domain.ErreurMetier;
import sn.ecole.shared.domain.TypeErreur;

/**
 * Import d'une liste d'élèves (reprise de données), en deux temps :
 *
 * <ol>
 *   <li>{@link #analyser} : validation à blanc, rien n'est écrit ; rapport ligne par ligne (erreurs
 *       bloquantes, avertissements) et empreinte SHA-256 du fichier ;
 *   <li>{@link #importer} : le même fichier (même empreinte), seulement s'il n'a aucune erreur ;
 *       tout ou rien dans une seule transaction ; un fichier n'est importé qu'une fois.
 * </ol>
 *
 * Colonnes : prenoms, nom, sexe, date_naissance, lieu_naissance (obligatoires) ; matricule,
 * nationalite (SN par défaut), identifiant_national, classe (avec l'année de l'import), et un
 * responsable facultatif : responsable_prenoms, responsable_nom, responsable_telephone,
 * responsable_lien (PERE, MERE, TUTEUR, AUTRE). Les messages ne recopient jamais les valeurs.
 */
@Service
@Transactional
public class ImportElevesService {

  public enum StatutLigne {
    OK,
    AVERTISSEMENT,
    ERREUR
  }

  public record LigneRapport(int numero, StatutLigne statut, List<String> messages) {}

  public record Rapport(
      String empreinte,
      int lignes,
      int erreurs,
      int avertissements,
      List<String> colonnesIgnorees,
      List<LigneRapport> details) {

    public boolean valide() {
      return erreurs == 0;
    }
  }

  /** Résultat d'un import : {@code applique} faux si le fichier avait des erreurs (rien écrit). */
  public record Resultat(
      Rapport rapport, boolean applique, int elevesCrees, int inscriptions, int responsables) {}

  private record ResponsablePrevu(String prenoms, String nom, String telephone, TypeLien lien) {}

  private record ElevePrevu(
      String matricule,
      IdentiteEleve identite,
      ClasseScolaire classe,
      ResponsablePrevu responsable) {}

  private record Analyse(Rapport rapport, List<ElevePrevu> eleves) {}

  // --- Colonnes ---

  static final String MATRICULE = "matricule";
  static final String PRENOMS = "prenoms";
  static final String NOM = "nom";
  static final String SEXE = "sexe";
  static final String NAISSANCE = "date_naissance";
  static final String LIEU = "lieu_naissance";
  static final String NATIONALITE = "nationalite";
  static final String IDENTIFIANT = "identifiant_national";
  static final String CLASSE = "classe";
  static final String R_PRENOMS = "responsable_prenoms";
  static final String R_NOM = "responsable_nom";
  static final String R_TELEPHONE = "responsable_telephone";
  static final String R_LIEN = "responsable_lien";

  private static final List<String> OBLIGATOIRES = List.of(PRENOMS, NOM, SEXE, NAISSANCE, LIEU);

  private static final Map<String, String> ALIAS = new HashMap<>();

  static {
    for (String c :
        List.of(
            MATRICULE,
            PRENOMS,
            NOM,
            SEXE,
            NAISSANCE,
            LIEU,
            NATIONALITE,
            IDENTIFIANT,
            CLASSE,
            R_PRENOMS,
            R_NOM,
            R_TELEPHONE,
            R_LIEN)) {
      ALIAS.put(c, c);
    }
    ALIAS.put("prenom", PRENOMS);
    ALIAS.put("date_de_naissance", NAISSANCE);
    ALIAS.put("ne_le", NAISSANCE);
    ALIAS.put("nee_le", NAISSANCE);
    ALIAS.put("lieu_de_naissance", LIEU);
    ALIAS.put("ne_a", LIEU);
    ALIAS.put("nin", IDENTIFIANT);
    ALIAS.put("responsable_prenom", R_PRENOMS);
    ALIAS.put("prenoms_responsable", R_PRENOMS);
    ALIAS.put("nom_responsable", R_NOM);
    ALIAS.put("telephone_responsable", R_TELEPHONE);
    ALIAS.put("telephone", R_TELEPHONE);
    ALIAS.put("lien", R_LIEN);
    ALIAS.put("lien_parental", R_LIEN);
  }

  private static final List<DateTimeFormatter> FORMATS_DATE =
      List.of("d/M/uuuu", "uuuu-M-d", "d-M-uuuu", "d.M.uuuu").stream()
          .map(m -> DateTimeFormatter.ofPattern(m).withResolverStyle(ResolverStyle.STRICT))
          .toList();

  private final LecteurTableur lecteur;
  private final EleveService eleves;
  private final EleveRepository depotEleves;
  private final ResponsableService responsables;
  private final ResponsableRepository depotResponsables;
  private final LienParentalService liens;
  private final InscriptionService inscriptions;
  private final ReferentielScolaire referentiel;
  private final ParametresInscriptionService parametres;
  private final ImportElevesRepository imports;
  private final JournalAudit journal;

  public ImportElevesService(
      LecteurTableur lecteur,
      EleveService eleves,
      EleveRepository depotEleves,
      ResponsableService responsables,
      ResponsableRepository depotResponsables,
      LienParentalService liens,
      InscriptionService inscriptions,
      ReferentielScolaire referentiel,
      ParametresInscriptionService parametres,
      ImportElevesRepository imports,
      JournalAudit journal) {
    this.lecteur = lecteur;
    this.eleves = eleves;
    this.depotEleves = depotEleves;
    this.responsables = responsables;
    this.depotResponsables = depotResponsables;
    this.liens = liens;
    this.inscriptions = inscriptions;
    this.referentiel = referentiel;
    this.parametres = parametres;
    this.imports = imports;
    this.journal = journal;
  }

  /** Validation à blanc : rien n'est écrit. */
  @PreAuthorize(Droits.ECRITURE)
  @Transactional(readOnly = true)
  public Rapport analyser(byte[] contenu, UUID anneeId) {
    return analyse(contenu, anneeId).rapport();
  }

  /**
   * Import du fichier validé. Refusé si le fichier diffère de celui analysé (empreinte), s'il a
   * déjà été importé, ou s'il contient une erreur (rien n'est alors écrit).
   */
  @PreAuthorize(Droits.ECRITURE)
  public Resultat importer(byte[] contenu, String empreinte, UUID anneeId) {
    String calculee = sha256(contenu);
    if (empreinte == null || !calculee.equalsIgnoreCase(empreinte.strip())) {
      throw Erreurs.conflit(
          "EMPREINTE_DIFFERENTE",
          "Le fichier a changé depuis sa validation : validez-le de nouveau avant d'importer.");
    }
    if (imports.existsByEmpreinte(calculee)) {
      throw Erreurs.conflit("IMPORT_DEJA_FAIT", "Ce fichier a déjà été importé.");
    }
    Analyse analyse = analyse(contenu, anneeId);
    if (!analyse.rapport().valide()) {
      return new Resultat(analyse.rapport(), false, 0, 0, 0);
    }
    int inscrits = 0;
    Map<String, UUID> responsablesCrees = new HashMap<>();
    for (ElevePrevu prevu : analyse.eleves()) {
      UUID eleveId = eleves.creerAvecMatricule(prevu.matricule(), prevu.identite()).getId();
      if (prevu.responsable() != null) {
        UUID responsableId = responsable(prevu.responsable(), responsablesCrees);
        liens.ajouter(
            eleveId,
            responsableId,
            prevu.responsable().lien(),
            new LienParental.Droits(true, true, true, true, true));
      }
      if (prevu.classe() != null) {
        inscriptions.inscrire(
            eleveId, prevu.classe().id(), StatutInscription.INSCRIT, false, List.of());
        inscrits++;
      }
    }
    ImportEleves trace =
        imports.save(
            ImportEleves.enregistrer(
                calculee, anneeId, analyse.eleves().size(), inscrits, responsablesCrees.size()));
    journal.consigner(
        "IMPORT_ELEVES",
        "ImportEleves",
        trace.getId(),
        Map.of(
            "empreinte",
            calculee,
            "eleves",
            analyse.eleves().size(),
            "inscriptions",
            inscrits,
            "responsables",
            responsablesCrees.size()));
    return new Resultat(
        analyse.rapport(), true, analyse.eleves().size(), inscrits, responsablesCrees.size());
  }

  /** Responsable existant (même téléphone, même nom) réutilisé, sinon créé une fois par import. */
  private UUID responsable(ResponsablePrevu r, Map<String, UUID> crees) {
    String recherche = Saisies.normaliser(r.nom() + " " + r.prenoms());
    String cle = r.telephone() + "|" + recherche;
    UUID dejaCree = crees.get(cle);
    if (dejaCree != null) {
      return dejaCree;
    }
    for (Responsable existant :
        depotResponsables.findByTelephoneOrderByNomRecherche(r.telephone())) {
      if (existant.getNomRecherche().equals(recherche)) {
        return existant.getId();
      }
    }
    UUID id =
        responsables
            .creer(new ResponsableService.Saisie(r.prenoms(), r.nom(), r.telephone(), null, null))
            .getId();
    crees.put(cle, id);
    return id;
  }

  // --- Analyse ---

  private Analyse analyse(byte[] contenu, UUID anneeId) {
    String empreinte = sha256(contenu);
    List<Ligne> lignes = lecteur.lire(contenu);
    Map<String, String> colonnes = new LinkedHashMap<>();
    List<String> ignorees = new ArrayList<>();
    if (!lignes.isEmpty()) {
      for (String entete : lignes.getFirst().valeurs().keySet()) {
        String connue = ALIAS.get(cle(entete));
        if (connue == null) {
          ignorees.add(entete);
        } else {
          colonnes.putIfAbsent(connue, entete);
        }
      }
      List<String> manquantes =
          OBLIGATOIRES.stream().filter(c -> !colonnes.containsKey(c)).toList();
      if (!manquantes.isEmpty()) {
        throw new ErreurMetier(
            "COLONNES_MANQUANTES",
            TypeErreur.INVALIDE,
            "Colonnes obligatoires absentes : " + String.join(", ", manquantes) + ".");
      }
    }
    Map<String, ClasseScolaire> classes = classesDeLAnnee(anneeId);
    Map<UUID, Long> effectifs = new HashMap<>();
    boolean bloquer = parametres.courants().getPolitiqueCapacite() == PolitiqueCapacite.BLOQUER;
    LocalDate aujourdhui = LocalDate.now(EleveService.FUSEAU);

    Set<String> matricules = new HashSet<>();
    Set<String> identifiants = new HashSet<>();
    Set<String> personnes = new HashSet<>();
    List<LigneRapport> details = new ArrayList<>();
    List<ElevePrevu> prevus = new ArrayList<>();
    int erreurs = 0;
    int avertissements = 0;

    for (Ligne ligne : lignes) {
      List<String> problemes = new ArrayList<>();
      List<String> remarques = new ArrayList<>();
      Map<String, String> v = new HashMap<>();
      colonnes.forEach((c, entete) -> v.put(c, ligne.valeurs().getOrDefault(entete, "")));

      // Identité
      IdentiteEleve identite = null;
      Sexe sexe = sexe(v.get(SEXE));
      LocalDate naissance = date(v.get(NAISSANCE));
      if (sexe == null) {
        problemes.add("Colonne sexe : M ou F attendu.");
      }
      if (naissance == null) {
        problemes.add("Colonne date_naissance : date attendue (JJ/MM/AAAA).");
      }
      String nationalite = v.getOrDefault(NATIONALITE, "");
      if (nationalite.isBlank()) {
        nationalite = "SN";
        remarques.add("Nationalité absente : SN par défaut.");
      }
      if (sexe != null && naissance != null) {
        try {
          identite =
              IdentiteEleve.valider(
                  v.get(PRENOMS),
                  v.get(NOM),
                  sexe,
                  naissance,
                  v.get(LIEU),
                  nationalite,
                  v.getOrDefault(IDENTIFIANT, ""),
                  aujourdhui);
        } catch (ErreurMetier e) {
          problemes.add(e.getMessage());
        }
      }

      // Matricule
      String matricule = v.getOrDefault(MATRICULE, "");
      if (!matricule.isBlank()) {
        try {
          matricule = Matricule.verifier(matricule);
          if (!matricules.add(matricule)) {
            problemes.add("Matricule en double dans le fichier.");
          } else if (depotEleves.matriculeExiste(matricule)) {
            problemes.add("Matricule déjà attribué dans l'école.");
          }
        } catch (ErreurMetier e) {
          problemes.add(e.getMessage());
        }
      }

      if (identite != null) {
        String id = identite.identifiantNational();
        if (id != null) {
          if (!identifiants.add(id)) {
            problemes.add("Identifiant national en double dans le fichier.");
          } else if (depotEleves.findByIdentifiantNational(id).isPresent()) {
            problemes.add("Identifiant national déjà connu dans l'école.");
          }
        }
        String personne =
            Saisies.normaliser(identite.nom() + " " + identite.prenoms()) + "|" + naissance;
        if (!personnes.add(personne)) {
          remarques.add("Même nom et même date de naissance qu'une ligne précédente : doublon ?");
        } else if (depotEleves.existsByNomRechercheAndDateNaissance(
            Saisies.normaliser(identite.nom() + " " + identite.prenoms()), naissance)) {
          remarques.add("Un élève de même nom et même date de naissance existe déjà : doublon ?");
        }
      }

      // Classe
      ClasseScolaire classe = null;
      String nomClasse = v.getOrDefault(CLASSE, "");
      if (!nomClasse.isBlank()) {
        if (anneeId == null) {
          problemes.add("Classe indiquée : choisissez l'année scolaire de l'import.");
        } else {
          classe = classes.get(nomClasse.strip().toUpperCase(Locale.ROOT));
          if (classe == null) {
            problemes.add("Colonne classe : classe inconnue pour cette année.");
          } else {
            long effectif =
                effectifs.merge(
                    classe.id(), inscriptions.effectif(classe.id()) + 1, (a, b) -> a + 1);
            if (effectif > classe.capacite()) {
              String message = "La classe dépasse sa capacité (" + classe.capacite() + " places).";
              if (bloquer) {
                problemes.add(message);
              } else {
                remarques.add(message);
              }
            }
          }
        }
      }

      // Responsable
      ResponsablePrevu responsable = null;
      if (!v.getOrDefault(R_PRENOMS, "").isBlank()
          || !v.getOrDefault(R_NOM, "").isBlank()
          || !v.getOrDefault(R_TELEPHONE, "").isBlank()
          || !v.getOrDefault(R_LIEN, "").isBlank()) {
        try {
          TypeLien lien = lien(v.getOrDefault(R_LIEN, ""));
          if (lien == null) {
            problemes.add("Colonne responsable_lien : PERE, MERE, TUTEUR ou AUTRE attendu.");
          } else {
            responsable =
                new ResponsablePrevu(
                    Saisies.nomPersonne(
                        v.getOrDefault(R_PRENOMS, ""), "Les prénoms du responsable", 100),
                    Saisies.nomPersonne(v.getOrDefault(R_NOM, ""), "Le nom du responsable", 60)
                        .toUpperCase(Locale.ROOT),
                    Responsable.telephone(v.getOrDefault(R_TELEPHONE, "")),
                    lien);
          }
        } catch (ErreurMetier e) {
          problemes.add(e.getMessage());
        }
      }

      StatutLigne statut;
      List<String> messages = new ArrayList<>(problemes);
      messages.addAll(remarques);
      if (!problemes.isEmpty()) {
        statut = StatutLigne.ERREUR;
        erreurs++;
      } else {
        statut = remarques.isEmpty() ? StatutLigne.OK : StatutLigne.AVERTISSEMENT;
        if (!remarques.isEmpty()) {
          avertissements++;
        }
        prevus.add(
            new ElevePrevu(matricule.isBlank() ? null : matricule, identite, classe, responsable));
      }
      details.add(new LigneRapport(ligne.numero(), statut, List.copyOf(messages)));
    }
    Rapport rapport =
        new Rapport(
            empreinte, lignes.size(), erreurs, avertissements, List.copyOf(ignorees), details);
    return new Analyse(rapport, prevus);
  }

  private Map<String, ClasseScolaire> classesDeLAnnee(UUID anneeId) {
    if (anneeId == null) {
      return Map.of();
    }
    if (referentiel.annee(anneeId).cloturee()) {
      throw Erreurs.conflit("ANNEE_CLOTUREE", "L'année scolaire est clôturée : lecture seule.");
    }
    Map<String, ClasseScolaire> parNom = new HashMap<>();
    for (ClasseScolaire c : referentiel.classes(anneeId)) {
      parNom.put(c.nom().toUpperCase(Locale.ROOT), c);
    }
    return parNom;
  }

  /** En-tête normalisé : minuscules sans accents, mots séparés par « _ ». */
  static String cle(String entete) {
    return Saisies.normaliser(entete).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
  }

  static Sexe sexe(String valeur) {
    return switch (Saisies.normaliser(valeur == null ? "" : valeur)) {
      case "m", "masculin", "g", "garcon", "h", "homme" -> Sexe.M;
      case "f", "feminin", "fille", "femme" -> Sexe.F;
      default -> null;
    };
  }

  static LocalDate date(String valeur) {
    String propre = valeur == null ? "" : valeur.strip();
    for (DateTimeFormatter format : FORMATS_DATE) {
      try {
        return LocalDate.parse(propre, format);
      } catch (DateTimeParseException e) {
        // format suivant
      }
    }
    return null;
  }

  static TypeLien lien(String valeur) {
    return switch (Saisies.normaliser(valeur == null ? "" : valeur)) {
      case "pere" -> TypeLien.PERE;
      case "mere" -> TypeLien.MERE;
      case "tuteur", "tutrice" -> TypeLien.TUTEUR;
      case "autre" -> TypeLien.AUTRE;
      default -> null;
    };
  }

  static String sha256(byte[] contenu) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenu));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
