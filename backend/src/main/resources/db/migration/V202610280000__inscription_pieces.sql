-- Module inscription (4/5) : pièces du dossier de l'élève (stockées dans MinIO), analysées par
-- ClamAV avant toute mise à disposition.

CREATE TABLE piece_dossier (
  id                UUID          PRIMARY KEY,
  tenant_id         UUID          NOT NULL REFERENCES tenant(id),
  eleve_id          UUID          NOT NULL,
  type              VARCHAR(20)   NOT NULL,
  cle               VARCHAR(200)  NOT NULL,
  content_type      VARCHAR(30)   NOT NULL,
  taille            INT           NOT NULL,
  empreinte_sha256  VARCHAR(64)   NOT NULL,
  -- EN_ANALYSE à l'arrivée ; SAIN ou INFECTE après l'analyse antivirus. Inaccessible tant que
  -- la pièce n'est pas SAIN.
  statut            VARCHAR(10)   NOT NULL,
  analyse_le        TIMESTAMPTZ,
  cree_le           TIMESTAMPTZ   NOT NULL DEFAULT now(),
  cree_par          UUID,
  modifie_le        TIMESTAMPTZ,
  modifie_par       UUID,
  supprime_le       TIMESTAMPTZ,
  version           INT           NOT NULL DEFAULT 0,
  CONSTRAINT uq_piece_dossier_id UNIQUE (tenant_id, id),
  CONSTRAINT fk_piece_dossier_eleve FOREIGN KEY (tenant_id, eleve_id) REFERENCES eleve (tenant_id, id),
  CONSTRAINT ck_piece_dossier_type CHECK (type IN ('ACTE_NAISSANCE', 'CERTIFICAT', 'PHOTO_IDENTITE')),
  CONSTRAINT ck_piece_dossier_statut CHECK (statut IN ('EN_ANALYSE', 'SAIN', 'INFECTE')),
  CONSTRAINT ck_piece_dossier_content_type
    CHECK (content_type IN ('application/pdf', 'image/jpeg', 'image/png')),
  CONSTRAINT ck_piece_dossier_taille CHECK (taille BETWEEN 1 AND 5000000)
);
CREATE INDEX ix_piece_dossier_eleve ON piece_dossier (tenant_id, eleve_id) WHERE supprime_le IS NULL;

ALTER TABLE piece_dossier ENABLE ROW LEVEL SECURITY;
ALTER TABLE piece_dossier FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON piece_dossier
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE ON piece_dossier TO ecole_app;
