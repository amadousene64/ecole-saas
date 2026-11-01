package sn.ecole.inscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import sn.ecole.shared.domain.TenantAwareEntity;

/** Trace d'un import de liste d'élèves ; l'empreinte du fichier empêche de l'importer deux fois. */
@Entity
@Table(name = "import_eleves")
public class ImportEleves extends TenantAwareEntity {

  @Column(nullable = false, updatable = false, length = 64)
  private String empreinte;

  @Column(name = "annee_id", updatable = false)
  private UUID anneeId;

  @Column(name = "eleves_crees", nullable = false, updatable = false)
  private int elevesCrees;

  @Column(nullable = false, updatable = false)
  private int inscriptions;

  @Column(nullable = false, updatable = false)
  private int responsables;

  protected ImportEleves() {}

  public static ImportEleves enregistrer(
      String empreinte, UUID anneeId, int elevesCrees, int inscriptions, int responsables) {
    ImportEleves i = new ImportEleves();
    i.empreinte = Objects.requireNonNull(empreinte);
    i.anneeId = anneeId;
    i.elevesCrees = elevesCrees;
    i.inscriptions = inscriptions;
    i.responsables = responsables;
    return i;
  }

  public String getEmpreinte() {
    return empreinte;
  }

  public int getElevesCrees() {
    return elevesCrees;
  }

  public int getInscriptions() {
    return inscriptions;
  }

  public int getResponsables() {
    return responsables;
  }
}
