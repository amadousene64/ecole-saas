package sn.ecole;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Données fictives communes aux tests (aucune donnée réelle). */
public final class Fixtures {

  public static final UUID ECOLE_A = UUID.fromString("01930000-0000-7000-8000-0000000000a1");
  public static final UUID ECOLE_B = UUID.fromString("01930000-0000-7000-8000-0000000000b2");
  public static final UUID ECOLE_INACTIVE = UUID.fromString("01930000-0000-7000-8000-0000000000c3");

  public static final UUID DIRECTEUR_A = UUID.fromString("01930000-0000-7000-8000-000000000101");

  /** Enseignant dans A, parent dans B. */
  public static final UUID ENSEIGNANT_A_PARENT_B =
      UUID.fromString("01930000-0000-7000-8000-000000000102");

  public static final UUID SURVEILLANT_B = UUID.fromString("01930000-0000-7000-8000-000000000103");

  /** Membre seulement de l'école inactive. */
  public static final UUID MEMBRE_INACTIVE =
      UUID.fromString("01930000-0000-7000-8000-000000000104");

  private Fixtures() {}

  /** Insertion par le superutilisateur (qui n'est pas soumis à la RLS) ; idempotente. */
  public static void installer(JdbcTemplate superUtilisateur) {
    superUtilisateur.update(
        """
        INSERT INTO ecole.tenant (id, code, nom, sous_domaine, actif) VALUES
          (?, 'LYCEE-A', 'Lycée Exemple A', 'lycee-a', true),
          (?, 'ECOLE-B', 'École Exemple B', 'ecole-b', true),
          (?, 'ECOLE-C', 'École Fermée C', 'ecole-c', false)
        ON CONFLICT (id) DO NOTHING""",
        ECOLE_A,
        ECOLE_B,
        ECOLE_INACTIVE);
    appartenance(superUtilisateur, ECOLE_A, DIRECTEUR_A, "DIRECTEUR");
    appartenance(superUtilisateur, ECOLE_A, ENSEIGNANT_A_PARENT_B, "ENSEIGNANT");
    appartenance(superUtilisateur, ECOLE_B, ENSEIGNANT_A_PARENT_B, "PARENT");
    appartenance(superUtilisateur, ECOLE_B, SURVEILLANT_B, "SURVEILLANT");
    appartenance(superUtilisateur, ECOLE_INACTIVE, MEMBRE_INACTIVE, "DIRECTEUR");
  }

  public static void appartenance(
      JdbcTemplate superUtilisateur, UUID ecole, UUID utilisateur, String role) {
    superUtilisateur.update(
        """
        INSERT INTO ecole.appartenance (id, tenant_id, utilisateur_id, role)
        VALUES (gen_random_uuid(), ?, ?, ?)
        ON CONFLICT (tenant_id, utilisateur_id, role) DO NOTHING""",
        ecole,
        utilisateur,
        role);
  }
}
