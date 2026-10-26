package sn.ecole;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import sn.ecole.shared.domain.ContexteTenant;

/** Règles de couches de CLAUDE.md (api → application → domain ← infrastructure). */
class CouchesTests {

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("sn.ecole");

  @Test
  void leDomaineNeDependNiDuWebNiDesRepositoriesNiDesAutresCouches() {
    noClasses()
        .that()
        .resideInAPackage("sn.ecole..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework.web..",
            "jakarta.servlet..",
            "org.springframework.data.repository..",
            "org.springframework.data.jpa.repository..",
            "sn.ecole..application..",
            "sn.ecole..api..",
            "sn.ecole..infrastructure..")
        .check(CLASSES);
  }

  @Test
  void lApplicationNeDependNiDeLApiNiDeLInfrastructure() {
    noClasses()
        .that()
        .resideInAPackage("sn.ecole..application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("sn.ecole..api..", "sn.ecole..infrastructure..")
        .check(CLASSES);
  }

  @Test
  void lInfrastructureNeDependPasDeLApi() {
    noClasses()
        .that()
        .resideInAPackage("sn.ecole..infrastructure..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("sn.ecole..api..")
        .check(CLASSES);
  }

  @Test
  void seulsLeFiltreHttpEtExecutionPourTenantOuvrentUnContexteDEcole() {
    noClasses()
        .that()
        .doNotHaveFullyQualifiedName("sn.ecole.shared.api.FiltreTenant")
        .and()
        .doNotHaveFullyQualifiedName("sn.ecole.shared.application.ExecutionPourTenant")
        .should()
        .callMethodWhere(
            DescribedPredicate.describe(
                "ContexteTenant.executer",
                appel ->
                    appel.getTarget().getOwner().isEquivalentTo(ContexteTenant.class)
                        && appel.getTarget().getName().equals("executer")))
        .check(CLASSES);
  }
}
