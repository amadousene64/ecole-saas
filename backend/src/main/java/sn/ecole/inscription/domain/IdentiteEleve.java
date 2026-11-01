package sn.ecole.inscription.domain;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * État civil d'un élève, validé et normalisé. Seules les données nécessaires à la scolarité :
 * l'identifiant national est facultatif.
 */
public record IdentiteEleve(
    String prenoms,
    String nom,
    Sexe sexe,
    LocalDate dateNaissance,
    String lieuNaissance,
    String nationalite,
    String identifiantNational) {

  private static final Pattern PAYS = Pattern.compile("[A-Z]{2}");
  private static final Pattern IDENTIFIANT = Pattern.compile("[A-Z0-9-]{1,30}");

  /** Âges admis à la date de saisie : de la maternelle au lycée, redoublements compris. */
  static final int AGE_MIN = 2;

  static final int AGE_MAX = 30;

  /**
   * @param aujourdhui date de référence du contrôle de vraisemblance de la date de naissance
   */
  public static IdentiteEleve valider(
      String prenoms,
      String nom,
      Sexe sexe,
      LocalDate dateNaissance,
      String lieuNaissance,
      String nationalite,
      String identifiantNational,
      LocalDate aujourdhui) {
    if (sexe == null) {
      throw Saisies.invalide("CHAMP_INVALIDE", "Le sexe est obligatoire.");
    }
    if (dateNaissance == null
        || dateNaissance.isAfter(aujourdhui.minusYears(AGE_MIN))
        || dateNaissance.isBefore(aujourdhui.minusYears(AGE_MAX))) {
      throw Saisies.invalide(
          "DATE_NAISSANCE_INVALIDE",
          "La date de naissance doit correspondre à un âge de "
              + AGE_MIN
              + " à "
              + AGE_MAX
              + " ans.");
    }
    String pays = Saisies.exiger(nationalite, "La nationalité", 2).toUpperCase(Locale.ROOT);
    if (!PAYS.matcher(pays).matches()) {
      throw Saisies.invalide(
          "CHAMP_INVALIDE", "La nationalité est un code pays à deux lettres (ex. SN).");
    }
    String identifiant = Saisies.optionnel(identifiantNational, "L'identifiant national", 30);
    if (identifiant != null) {
      identifiant = identifiant.toUpperCase(Locale.ROOT).replace(" ", "");
      if (!IDENTIFIANT.matcher(identifiant).matches()) {
        throw Saisies.invalide(
            "CHAMP_INVALIDE", "L'identifiant national ne contient que des lettres et chiffres.");
      }
    }
    return new IdentiteEleve(
        Saisies.nomPersonne(prenoms, "Les prénoms", 100),
        Saisies.nomPersonne(nom, "Le nom", 60).toUpperCase(Locale.ROOT),
        sexe,
        dateNaissance,
        Saisies.exiger(lieuNaissance, "Le lieu de naissance", 100),
        pays,
        identifiant);
  }

  public IdentiteEleve {
    Objects.requireNonNull(prenoms);
    Objects.requireNonNull(nom);
    Objects.requireNonNull(sexe);
    Objects.requireNonNull(dateNaissance);
  }

  /** Jamais de nom ni de date de naissance dans une trace. */
  @Override
  public String toString() {
    return "IdentiteEleve[***]";
  }
}
