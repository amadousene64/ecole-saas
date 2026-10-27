package sn.ecole;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import sn.ecole.etablissement.InitialisationEcole;

/**
 * {@link InitialisationEcole} reçoit un identifiant d'école arbitraire et n'a pas de contrôle de
 * droits : c'est un appel système. Seul l'amorçage du profil dev et son implémentation peuvent s'en
 * servir. Le futur back-office du super-administrateur s'ajoutera ici, délibérément, avec son
 * propre contrôle d'accès.
 */
class AppelantsInitialisationEcoleTest {

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("sn.ecole");

  @Test
  void seulsLAmorcageDevEtSonImplementationUtilisentInitialisationEcole() {
    noClasses()
        .that()
        .resideOutsideOfPackages(
            "sn.ecole.etablissement.dev..", "sn.ecole.etablissement.application..")
        .should()
        .dependOnClassesThat()
        .areAssignableTo(InitialisationEcole.class)
        .because(
            "InitialisationEcole est un appel système sans contrôle de droits : jamais depuis un"
                + " endpoint ni un autre module sans décision explicite")
        .check(CLASSES);
  }
}
