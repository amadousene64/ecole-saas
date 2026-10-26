package sn.ecole;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** Frontières entre modules métier (Spring Modulith). Doit rester vert. */
class ModularityTests {

  private final ApplicationModules modules = ApplicationModules.of(EcoleApplication.class);

  @Test
  void lesModulesRespectentLeursFrontieres() {
    modules.verify();
  }

  @Test
  void documentationDesModules() {
    new Documenter(modules).writeDocumentation();
  }
}
