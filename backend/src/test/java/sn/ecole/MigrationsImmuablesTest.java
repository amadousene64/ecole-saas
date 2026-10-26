package sn.ecole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Une migration Flyway déjà présente sur la branche de référence ne doit jamais être modifiée ni
 * supprimée (CLAUDE.md, règle 8) : on en crée une nouvelle.
 *
 * <p>Référence : {@code main} (ou {@code origin/main} si la branche locale n'existe pas, cas d'un
 * clone CI) ; surchargeable par {@code -Dmigrations.reference=...}. Sans référence disponible le
 * test échoue au lieu d'être ignoré : en CI, faire {@code git fetch origin main}.
 */
class MigrationsImmuablesTest {

  private static final String DOSSIER = "backend/src/main/resources/db/migration/";

  private static String git(Path racine, String... arguments) {
    List<String> commande = new ArrayList<>(List.of("git", "-C", racine.toString()));
    commande.addAll(List.of(arguments));
    try {
      Process processus = new ProcessBuilder(commande).redirectErrorStream(false).start();
      byte[] sortie = processus.getInputStream().readAllBytes();
      if (!processus.waitFor(30, TimeUnit.SECONDS) || processus.exitValue() != 0) {
        return null;
      }
      return new String(sortie, StandardCharsets.UTF_8);
    } catch (IOException e) {
      return null;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    }
  }

  /** Comparaison insensible aux fins de ligne (Windows / Linux), comme la somme de Flyway. */
  private static String normaliser(String contenu) {
    return contenu.replace("\r\n", "\n").replace("\r", "\n");
  }

  @Test
  void aucuneMigrationDeLaReferenceNAEteModifieeNiSupprimee() throws IOException {
    String racineTexte = git(Path.of("."), "rev-parse", "--show-toplevel");
    if (racineTexte == null) {
      fail("Dépôt git introuvable : impossible de vérifier l'immuabilité des migrations.");
    }
    Path racine = Path.of(racineTexte.strip());

    String reference = System.getProperty("migrations.reference");
    if (reference == null) {
      reference = git(racine, "rev-parse", "--verify", "--quiet", "main") != null ? "main" : null;
    }
    if (reference == null
        && git(racine, "rev-parse", "--verify", "--quiet", "origin/main") != null) {
      reference = "origin/main";
    }
    if (reference == null) {
      fail("Branche de référence introuvable (main, origin/main) : faites git fetch origin main.");
    }

    String liste = git(racine, "ls-tree", "-r", "--name-only", reference, "--", DOSSIER);
    assertThat(liste).as("migrations sur %s", reference).isNotNull();

    List<String> ecarts = new ArrayList<>();
    for (String chemin : liste.lines().filter(l -> l.endsWith(".sql")).toList()) {
      Path fichier = racine.resolve(chemin);
      if (!Files.exists(fichier)) {
        ecarts.add("supprimée : " + chemin);
        continue;
      }
      String attendu = git(racine, "show", reference + ":" + chemin);
      String actuel = Files.readString(fichier, StandardCharsets.UTF_8);
      if (attendu == null || !normaliser(attendu).equals(normaliser(actuel))) {
        ecarts.add("modifiée : " + chemin);
      }
    }
    assertThat(ecarts)
        .as(
            "Migrations déjà présentes sur %s modifiées ou supprimées : créez une nouvelle"
                + " migration (skill /nouvelle-migration)",
            reference)
        .isEmpty();
  }
}
