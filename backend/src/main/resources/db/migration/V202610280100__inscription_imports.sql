-- Module inscription (5/5) : imports de listes d'élèves (reprise de données). Une ligne par fichier
-- importé : son empreinte empêche d'importer deux fois le même fichier.

CREATE TABLE import_eleves (
  id            UUID         PRIMARY KEY,
  tenant_id     UUID         NOT NULL REFERENCES tenant(id),
  empreinte     VARCHAR(64)  NOT NULL,
  annee_id      UUID,
  eleves_crees  INT          NOT NULL,
  inscriptions  INT          NOT NULL,
  responsables  INT          NOT NULL,
  cree_le       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  cree_par      UUID,
  modifie_le    TIMESTAMPTZ,
  modifie_par   UUID,
  supprime_le   TIMESTAMPTZ,
  version       INT          NOT NULL DEFAULT 0,
  CONSTRAINT uq_import_eleves_empreinte UNIQUE (tenant_id, empreinte),
  CONSTRAINT fk_import_eleves_annee FOREIGN KEY (tenant_id, annee_id)
    REFERENCES annee_scolaire (tenant_id, id),
  CONSTRAINT ck_import_eleves_compteurs CHECK (eleves_crees >= 0 AND inscriptions >= 0 AND responsables >= 0)
);

ALTER TABLE import_eleves ENABLE ROW LEVEL SECURITY;
ALTER TABLE import_eleves FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON import_eleves
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

GRANT SELECT, INSERT ON import_eleves TO ecole_app;
