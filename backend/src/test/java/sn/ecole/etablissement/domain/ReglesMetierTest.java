package sn.ecole.etablissement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import sn.ecole.etablissement.StatutEtablissement;
import sn.ecole.shared.domain.ErreurMetier;
import sn.ecole.shared.domain.TypeErreur;

/** Série/niveau, calendrier, téléphone, profil et paramètres de notation. */
class ReglesMetierTest {

  private static final UUID L1 = UUID.randomUUID();
  private static final UUID S2 = UUID.randomUUID();
  private static final AnneeScolaire ANNEE =
      AnneeScolaire.creer("2026-2027", LocalDate.of(2026, 10, 5), LocalDate.of(2027, 7, 2));

  private static void assertCode(Runnable action, String code) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(ErreurMetier.class, e -> assertThat(e.code()).isEqualTo(code));
  }

  // --- Série obligatoire seulement si le niveau en a ---

  @Test
  void unNiveauAvecSeriesExigeUneSerie() {
    assertCode(() -> ReglesClasse.verifierSerie(Set.of(L1, S2), null), "SERIE_REQUISE");
  }

  @Test
  void unNiveauSansSerieRefuseUneSerie() {
    assertCode(() -> ReglesClasse.verifierSerie(Set.of(), S2), "SERIE_INTERDITE");
  }

  @Test
  void uneSerieHorsDuNiveauEstRefusee() {
    assertCode(() -> ReglesClasse.verifierSerie(Set.of(L1), S2), "SERIE_INTERDITE");
  }

  @Test
  void leCasNominalPasse() {
    assertThatCode(() -> ReglesClasse.verifierSerie(Set.of(L1, S2), S2)).doesNotThrowAnyException();
    assertThatCode(() -> ReglesClasse.verifierSerie(Set.of(), null)).doesNotThrowAnyException();
  }

  // --- Classe et salle ---

  @Test
  void laCapaciteEstBornee() {
    assertCode(() -> Salle.creer("Salle 1", 0), "CAPACITE_INVALIDE");
    assertCode(() -> Salle.creer("Salle 1", 501), "CAPACITE_INVALIDE");
    assertCode(() -> Salle.creer("  ", 30), "CHAMP_INVALIDE");
    assertThat(Salle.creer(" Salle 1 ", 40).getNom()).isEqualTo("Salle 1");
  }

  @Test
  void uneClasseGardeSonAnneeEtAccepteUneSerieNulle() {
    UUID annee = UUID.randomUUID();
    Classe classe = Classe.creer(annee, UUID.randomUUID(), null, "CE1A", 45, null);
    classe.modifier(classe.getNiveauId(), null, "CE1B", 50, null);
    assertThat(classe.getAnneeId()).isEqualTo(annee);
    assertThat(classe.getNom()).isEqualTo("CE1B");
    assertThat(classe.getSerieId()).isNull();
  }

  // --- Calendrier ---

  private static EvenementCalendrier vacances(String debut, String fin) {
    return EvenementCalendrier.creer(
        UUID.randomUUID(),
        TypeEvenement.VACANCES,
        "Vacances",
        LocalDate.parse(debut),
        LocalDate.parse(fin));
  }

  @Test
  void unEvenementDoitResterDansLAnnee() {
    assertCode(
        () ->
            ReglesCalendrier.verifier(
                ANNEE,
                TypeEvenement.FERIE,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 1),
                List.of()),
        "EVENEMENT_HORS_ANNEE");
  }

  @Test
  void deuxVacancesNeSeChevauchentPasMaisUnFerieSeSuperposeAuxVacances() {
    List<EvenementCalendrier> autres = List.of(vacances("2026-12-19", "2027-01-03"));
    assertCode(
        () ->
            ReglesCalendrier.verifier(
                ANNEE,
                TypeEvenement.VACANCES,
                LocalDate.of(2027, 1, 3),
                LocalDate.of(2027, 1, 10),
                autres),
        "VACANCES_CHEVAUCHANTES");
    assertThatCode(
            () ->
                ReglesCalendrier.verifier(
                    ANNEE,
                    TypeEvenement.FERIE,
                    LocalDate.of(2026, 12, 25),
                    LocalDate.of(2026, 12, 25),
                    autres))
        .doesNotThrowAnyException();
  }

  @Test
  void unEvenementNePeutPasFinirAvantDeCommencer() {
    assertCode(() -> vacances("2027-01-10", "2027-01-03"), "EVENEMENT_INVALIDE");
  }

  // --- Téléphone E.164, +221 par défaut ---

  @Test
  void leTelephoneSenegalaisLocalDevientInternational() {
    assertThat(Telephone.normaliser("77 123 45 67")).isEqualTo("+221771234567");
    assertThat(Telephone.normaliser("33.823.45.67")).isEqualTo("+221338234567");
  }

  @Test
  void leTelephoneInternationalEstConserve() {
    assertThat(Telephone.normaliser("+221 77 123 45 67")).isEqualTo("+221771234567");
    assertThat(Telephone.normaliser("0033 1 23 45 67 89")).isEqualTo("+33123456789");
  }

  @Test
  void leTelephoneVideDevientNull() {
    assertThat(Telephone.normaliser("  ")).isNull();
    assertThat(Telephone.normaliser(null)).isNull();
  }

  @Test
  void unTelephoneInvalideEstRefuse() {
    assertCode(() -> Telephone.normaliser("abc"), "TELEPHONE_INVALIDE");
    assertCode(() -> Telephone.normaliser("12345"), "TELEPHONE_INVALIDE");
    assertCode(() -> Telephone.normaliser("+0123456789"), "TELEPHONE_INVALIDE");
  }

  // --- Profil de l'établissement ---

  private static Etablissement etablissement() {
    return Etablissement.creer("Lycée Exemple", "LYC-001", StatutEtablissement.PUBLIC);
  }

  @Test
  void leProfilANeParDefautLeFuseauEtLaDeviseDuSenegal() {
    Etablissement e = etablissement();
    assertThat(e.getFuseau()).isEqualTo("Africa/Dakar");
    assertThat(e.getDevise()).isEqualTo("XOF");
  }

  @Test
  void leProfilValideEtNormaliseSesChamps() {
    Etablissement e = etablissement();
    e.modifier(
        "  Lycée Exemple A ",
        "LYC-001",
        StatutEtablissement.PRIVE_LAIQUE,
        "IA Dakar",
        "IEF Pikine",
        "Rue 1",
        "77 123 45 67",
        "contact@exemple.test",
        "Africa/Dakar",
        "XOF");
    assertThat(e.getNom()).isEqualTo("Lycée Exemple A");
    assertThat(e.getTelephone()).isEqualTo("+221771234567");
    assertThat(e.getStatut()).isEqualTo(StatutEtablissement.PRIVE_LAIQUE);
  }

  @Test
  void leFuseauEtLaDeviseInconnusSontRefuses() {
    Etablissement e = etablissement();
    assertCode(
        () ->
            e.modifier(
                "A",
                "B",
                StatutEtablissement.PUBLIC,
                null,
                null,
                null,
                null,
                null,
                "Mars/Olympus",
                "XOF"),
        "FUSEAU_INVALIDE");
    assertCode(
        () ->
            e.modifier(
                "A",
                "B",
                StatutEtablissement.PUBLIC,
                null,
                null,
                null,
                null,
                null,
                "Africa/Dakar",
                "ZZZ9"),
        "DEVISE_INVALIDE");
  }

  @Test
  void leFuseauEtLaDeviseVidesRetombentSurLesDefauts() {
    Etablissement e = etablissement();
    e.modifier("A", "B", StatutEtablissement.PUBLIC, null, null, null, null, null, " ", null);
    assertThat(e.getFuseau()).isEqualTo("Africa/Dakar");
    assertThat(e.getDevise()).isEqualTo("XOF");
  }

  @Test
  void unEmailOuUnCodeInvalideSontRefuses() {
    Etablissement e = etablissement();
    assertCode(
        () ->
            e.modifier(
                "A",
                "B",
                StatutEtablissement.PUBLIC,
                null,
                null,
                null,
                null,
                "pas-un-email",
                null,
                null),
        "EMAIL_INVALIDE");
    assertCode(
        () ->
            e.modifier(
                "A", "B C", StatutEtablissement.PUBLIC, null, null, null, null, null, null, null),
        "CHAMP_INVALIDE");
    assertCode(
        () ->
            e.modifier(
                "", "B", StatutEtablissement.PUBLIC, null, null, null, null, null, null, null),
        "CHAMP_INVALIDE");
  }

  // --- Cycles : paramètres de notation et base du niveau ---

  @Test
  void laBaseDeLaMoyenneVaut10Ou20() {
    Cycle cycle =
        Cycle.creer(CodeCycle.MOYEN, "Moyen", TypePeriode.SEMESTRE, ModeNotation.COEFFICIENT, 20);
    assertCode(
        () -> cycle.parametrer(TypePeriode.SEMESTRE, ModeNotation.COEFFICIENT, 15),
        "BASE_MOYENNE_INVALIDE");
    cycle.parametrer(TypePeriode.TRIMESTRE, ModeNotation.BAREME, 10);
    assertThat(cycle.aLesParametres(TypePeriode.TRIMESTRE, ModeNotation.BAREME, 10)).isTrue();
    assertThat(cycle.aLesParametres(TypePeriode.SEMESTRE, ModeNotation.BAREME, 10)).isFalse();
  }

  @Test
  void leNiveauSurchargeLaBaseDeSonCycleSinon_ilLaSuit() {
    Cycle cycle =
        Cycle.creer(
            CodeCycle.ELEMENTAIRE, "Élémentaire", TypePeriode.TRIMESTRE, ModeNotation.BAREME, 10);
    Niveau suit = Niveau.creer(UUID.randomUUID(), "cp", "CP", 2, null);
    Niveau surcharge = Niveau.creer(UUID.randomUUID(), "CM2", "CM2", 6, 20);

    assertThat(suit.getCode()).isEqualTo("CP");
    assertThat(suit.baseMoyenneEffective(cycle)).isEqualTo(10);
    assertThat(surcharge.baseMoyenneEffective(cycle)).isEqualTo(20);
    assertCode(() -> Niveau.creer(UUID.randomUUID(), "X", "X", 1, 12), "BASE_MOYENNE_INVALIDE");
    assertCode(() -> Niveau.creer(UUID.randomUUID(), "X Y", "X", 1, null), "CHAMP_INVALIDE");
    assertCode(() -> Niveau.creer(UUID.randomUUID(), "X", "X", 0, null), "CHAMP_INVALIDE");
  }

  // --- Modèle sénégalais ---

  @Test
  void leModeleSenegalaisEstCoherent() {
    assertThat(ModeleSenegalais.CYCLES)
        .extracting(c -> c.code())
        .containsExactly(CodeCycle.values());
    assertThat(ModeleSenegalais.NIVEAUX)
        .extracting(n -> n.code())
        .hasSize(13)
        .doesNotHaveDuplicates();
    assertThat(ModeleSenegalais.NIVEAUX).extracting(n -> n.ordre()).doesNotHaveDuplicates();
    var codesNiveaux = ModeleSenegalais.NIVEAUX.stream().map(n -> n.code()).toList();
    var codesSeries = ModeleSenegalais.SERIES.stream().map(s -> s.code()).toList();
    assertThat(ModeleSenegalais.SERIES_PAR_NIVEAU.keySet()).isSubsetOf(codesNiveaux);
    ModeleSenegalais.SERIES_PAR_NIVEAU
        .values()
        .forEach(s -> assertThat(codesSeries).containsAll(s));
    assertThat(ModeleSenegalais.SERIES_PAR_NIVEAU.get("2NDE")).containsExactly("L", "S");
    assertThat(ModeleSenegalais.SERIES_PAR_NIVEAU.get("TLE"))
        .containsExactly("L1", "L2", "S1", "S2", "S3");
    ModeleSenegalais.CYCLES.forEach(c -> assertThat(c.baseMoyenne()).isIn(10, 20));
  }

  @Test
  void toutesLesErreursDuDomaineSontDesErreursMetierTypees() {
    assertThatThrownBy(() -> Telephone.normaliser("x"))
        .isInstanceOfSatisfying(
            ErreurMetier.class, e -> assertThat(e.type()).isEqualTo(TypeErreur.INVALIDE));
  }
}
