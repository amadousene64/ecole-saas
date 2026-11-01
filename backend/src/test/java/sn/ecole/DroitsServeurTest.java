package sn.ecole;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Droits côté serveur, relevés dans les {@code @PreAuthorize} des services de l'école et de
 * l'inscription (la règle d'architecture : chaque méthode exposée porte son contrôle). Le résultat
 * est versionné dans {@code docs/api/droits-serveur.json} : le front-end s'en sert pour vérifier
 * que ses menus et ses boutons correspondent exactement à ces droits (voir {@code
 * droits-serveur.spec.ts}). Une méthode ajoutée ou un droit modifié côté serveur change ce fichier,
 * donc fait échouer ce test tant qu'il n'est pas régénéré : {@code ./mvnw test
 * -Dtest=DroitsServeurTest -Ddroits.maj=true}.
 */
class DroitsServeurTest {

  private static final Path FICHIER = Path.of("..", "docs", "api", "droits-serveur.json");

  private static final List<String> PAQUETS =
      List.of(
          "sn.ecole.etablissement.application",
          "sn.ecole.inscription.application",
          "sn.ecole.statistique.application");

  private static final List<String> ORDRE_ROLES =
      List.of(
          "DIRECTEUR",
          "SECRETARIAT",
          "CENSEUR",
          "COMPTABLE",
          "SURVEILLANT",
          "ENSEIGNANT",
          "PARENT",
          "ELEVE");

  private static final Pattern HAS_ROLE = Pattern.compile("hasRole\\('([A-Z_]+)'\\)");
  private static final Pattern HAS_ANY_ROLE = Pattern.compile("hasAnyRole\\(([^)]*)\\)");
  private static final Pattern NOM_ROLE = Pattern.compile("'([A-Z_]+)'");

  @Test
  void leFichierDesDroitsServeurCorrespondAuCode() throws Exception {
    String attendu = produire();
    if (Boolean.getBoolean("droits.maj")) {
      Files.writeString(FICHIER, attendu, StandardCharsets.UTF_8);
      return;
    }
    assertThat(Files.exists(FICHIER))
        .as(
            "docs/api/droits-serveur.json absent : ./mvnw test -Dtest=DroitsServeurTest -Ddroits.maj=true")
        .isTrue();
    String actuel = Files.readString(FICHIER, StandardCharsets.UTF_8);
    assertThat(actuel.replace("\r\n", "\n"))
        .as(
            "docs/api/droits-serveur.json périmé : relancez avec -Ddroits.maj=true, puis mettez à"
                + " jour la couverture du front-end (libs/auth/src/lib/droits/couverture.ts)")
        .isEqualTo(attendu);
  }

  private static List<Class<?>> classesDeService() throws ClassNotFoundException {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Service.class));
    List<Class<?>> classes = new ArrayList<>();
    for (String paquet : PAQUETS) {
      for (var def : scanner.findCandidateComponents(paquet)) {
        classes.add(Class.forName(def.getBeanClassName()));
      }
    }
    classes.sort((a, b) -> a.getSimpleName().compareTo(b.getSimpleName()));
    return classes;
  }

  static String produire() throws Exception {
    Map<String, Map<String, Object>> methodes = new TreeMap<>();
    for (Class<?> classe : classesDeService()) {
      boolean classeEnLecture = lectureSeule(classe.getAnnotation(Transactional.class));
      for (Method m : classe.getDeclaredMethods()) {
        PreAuthorize pa = AnnotatedElementUtils.findMergedAnnotation(m, PreAuthorize.class);
        if (pa == null || !java.lang.reflect.Modifier.isPublic(m.getModifiers())) {
          continue;
        }
        Transactional tx = m.getAnnotation(Transactional.class);
        boolean lecture = tx != null ? lectureSeule(tx) : classeEnLecture;
        String cle = classe.getSimpleName() + "." + m.getName();
        String expression = pa.value();
        Map<String, Object> entree = new LinkedHashMap<>();
        entree.put("roles", roles(expression));
        entree.put("ecriture", !lecture);
        entree.put("conditionnel", expression.contains("#") || expression.contains(" and "));
        entree.put("perimetre", expression.contains("hasPermission"));
        entree.put("expression", expression.replaceAll("\\s+", " ").strip());
        // Plusieurs méthodes de même nom : droits fusionnés (union des rôles), le plus permissif.
        methodes.merge(cle, entree, DroitsServeurTest::fusionner);
      }
    }
    Map<String, Object> racine = new LinkedHashMap<>();
    racine.put(
        "commentaire",
        "Généré par DroitsServeurTest (./mvnw test -Dtest=DroitsServeurTest -Ddroits.maj=true)."
            + " Ne pas modifier à la main.");
    racine.put("methodes", methodes);
    return JsonMapper.builder()
            .build()
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(racine)
            .replace("\r\n", "\n")
        + "\n";
  }

  private static boolean lectureSeule(Transactional tx) {
    return tx != null && tx.readOnly();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> fusionner(Map<String, Object> a, Map<String, Object> b) {
    List<String> roles = new ArrayList<>((List<String>) a.get("roles"));
    for (String r : (List<String>) b.get("roles")) {
      if (!roles.contains(r)) {
        roles.add(r);
      }
    }
    roles.sort((x, y) -> Integer.compare(ORDRE_ROLES.indexOf(x), ORDRE_ROLES.indexOf(y)));
    Map<String, Object> fusion = new LinkedHashMap<>(a);
    fusion.put("roles", roles);
    fusion.put("ecriture", (boolean) a.get("ecriture") || (boolean) b.get("ecriture"));
    fusion.put("conditionnel", (boolean) a.get("conditionnel") || (boolean) b.get("conditionnel"));
    fusion.put("perimetre", (boolean) a.get("perimetre") || (boolean) b.get("perimetre"));
    fusion.put("expression", a.get("expression") + " | " + b.get("expression"));
    return fusion;
  }

  /** Rôles nommés dans l'expression ({@code hasRole}, {@code hasAnyRole}), dans l'ordre usuel. */
  static List<String> roles(String expression) {
    List<String> trouves = new ArrayList<>();
    Matcher un = HAS_ROLE.matcher(expression);
    while (un.find()) {
      trouves.add(un.group(1));
    }
    Matcher plusieurs = HAS_ANY_ROLE.matcher(expression);
    while (plusieurs.find()) {
      Matcher nom = NOM_ROLE.matcher(plusieurs.group(1));
      while (nom.find()) {
        trouves.add(nom.group(1));
      }
    }
    return trouves.stream()
        .distinct()
        .sorted((x, y) -> Integer.compare(ORDRE_ROLES.indexOf(x), ORDRE_ROLES.indexOf(y)))
        .toList();
  }

  @Test
  void lExtractionDesRolesComprendLesFormesDExpression() {
    assertThat(roles("hasRole('DIRECTEUR')")).containsExactly("DIRECTEUR");
    assertThat(roles("hasAnyRole('SECRETARIAT','DIRECTEUR')"))
        .containsExactly("DIRECTEUR", "SECRETARIAT");
    assertThat(
            roles(
                "hasRole('DIRECTEUR') or (hasRole('SECRETARIAT') and #sortie != null)"
                    + " or hasAnyRole('CENSEUR')"))
        .containsExactly("DIRECTEUR", "SECRETARIAT", "CENSEUR");
    assertThat(roles("isAuthenticated()")).isEmpty();
  }
}
