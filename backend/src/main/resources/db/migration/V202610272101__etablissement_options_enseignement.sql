-- Module etablissement : catalogue des options d'enseignement (LV2, arts…) et options proposées
-- par chaque classe. Un élève choisit au plus une option par groupe à son inscription.

CREATE TABLE option_enseignement (
  id           UUID         PRIMARY KEY,
  tenant_id    UUID         NOT NULL REFERENCES tenant(id),
  code         VARCHAR(10)  NOT NULL,
  libelle      VARCHAR(80)  NOT NULL,
  -- Groupe de choix exclusifs (ex. LV2 : espagnol, arabe, allemand…).
  groupe       VARCHAR(20)  NOT NULL,
  actif        BOOLEAN      NOT NULL DEFAULT TRUE,
  cree_le      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  cree_par     UUID,
  modifie_le   TIMESTAMPTZ,
  modifie_par  UUID,
  supprime_le  TIMESTAMPTZ,
  version      INT          NOT NULL DEFAULT 0,
  CONSTRAINT uq_option_enseignement_id UNIQUE (tenant_id, id),
  CONSTRAINT ck_option_enseignement_code CHECK (code ~ '^[A-Z0-9]{1,10}$'),
  CONSTRAINT ck_option_enseignement_groupe CHECK (groupe ~ '^[A-Z0-9_]{1,20}$')
);
CREATE UNIQUE INDEX uq_option_enseignement_code
  ON option_enseignement (tenant_id, code) WHERE supprime_le IS NULL;

CREATE TABLE classe_option (
  id           UUID         PRIMARY KEY,
  tenant_id    UUID         NOT NULL REFERENCES tenant(id),
  classe_id    UUID         NOT NULL,
  option_id    UUID         NOT NULL,
  cree_le      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  cree_par     UUID,
  modifie_le   TIMESTAMPTZ,
  modifie_par  UUID,
  supprime_le  TIMESTAMPTZ,
  version      INT          NOT NULL DEFAULT 0,
  CONSTRAINT uq_classe_option_id UNIQUE (tenant_id, id),
  CONSTRAINT fk_classe_option_classe FOREIGN KEY (tenant_id, classe_id) REFERENCES classe (tenant_id, id),
  CONSTRAINT fk_classe_option_option FOREIGN KEY (tenant_id, option_id)
    REFERENCES option_enseignement (tenant_id, id)
);
CREATE UNIQUE INDEX uq_classe_option
  ON classe_option (tenant_id, classe_id, option_id) WHERE supprime_le IS NULL;
CREATE INDEX ix_classe_option_option ON classe_option (tenant_id, option_id);

ALTER TABLE option_enseignement ENABLE ROW LEVEL SECURITY;
ALTER TABLE option_enseignement FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON option_enseignement
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE classe_option ENABLE ROW LEVEL SECURITY;
ALTER TABLE classe_option FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON classe_option
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE ON option_enseignement, classe_option TO ecole_app;
