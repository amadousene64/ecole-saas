-- Module etablissement (1/3) : profil de l'école, images officielles, cycles, niveaux et séries.
-- Les clés étrangères sont composites (tenant_id, id) : une ligne ne peut référencer que des lignes
-- de la même école, même en cas d'erreur applicative. L'application n'a pas le droit DELETE.

CREATE TABLE etablissement (
  id           UUID         PRIMARY KEY,
  tenant_id    UUID         NOT NULL REFERENCES tenant(id),
  nom          VARCHAR(200) NOT NULL,
  code         VARCHAR(40)  NOT NULL,
  statut       VARCHAR(30)  NOT NULL,
  ia           VARCHAR(120),
  ief          VARCHAR(120),
  adresse      VARCHAR(300),
  telephone    VARCHAR(16),
  email        VARCHAR(254),
  fuseau       VARCHAR(40)  NOT NULL DEFAULT 'Africa/Dakar',
  devise       VARCHAR(3)   NOT NULL DEFAULT 'XOF',
  cree_le      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  cree_par     UUID,
  modifie_le   TIMESTAMPTZ,
  modifie_par  UUID,
  supprime_le  TIMESTAMPTZ,
  version      INT          NOT NULL DEFAULT 0,
  CONSTRAINT uq_etablissement_tenant UNIQUE (tenant_id),
  CONSTRAINT ck_etablissement_statut CHECK (statut IN ('PUBLIC', 'PRIVE_LAIQUE', 'PRIVE_CONFESSIONNEL')),
  CONSTRAINT ck_etablissement_telephone CHECK (telephone IS NULL OR telephone ~ '^\+[1-9][0-9]{7,14}$')
);

-- Logo, cachet et signature : le fichier est dans MinIO, la base ne garde que sa clé d'objet.
CREATE TABLE etablissement_image (
  id            UUID         PRIMARY KEY,
  tenant_id     UUID         NOT NULL REFERENCES tenant(id),
  type          VARCHAR(10)  NOT NULL,
  cle           VARCHAR(200) NOT NULL,
  content_type  VARCHAR(30)  NOT NULL,
  taille        INT          NOT NULL,
  cree_le       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  cree_par      UUID,
  modifie_le    TIMESTAMPTZ,
  modifie_par   UUID,
  supprime_le   TIMESTAMPTZ,
  version       INT          NOT NULL DEFAULT 0,
  CONSTRAINT ck_etablissement_image_type CHECK (type IN ('LOGO', 'CACHET', 'SIGNATURE')),
  CONSTRAINT ck_etablissement_image_taille CHECK (taille > 0)
);
CREATE UNIQUE INDEX uq_etablissement_image_type
  ON etablissement_image (tenant_id, type) WHERE supprime_le IS NULL;

CREATE TABLE cycle (
  id            UUID         PRIMARY KEY,
  tenant_id     UUID         NOT NULL REFERENCES tenant(id),
  code          VARCHAR(20)  NOT NULL,
  libelle       VARCHAR(80)  NOT NULL,
  type_periode  VARCHAR(10)  NOT NULL,
  mode_notation VARCHAR(12)  NOT NULL,
  base_moyenne  INT          NOT NULL,
  cree_le       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  cree_par      UUID,
  modifie_le    TIMESTAMPTZ,
  modifie_par   UUID,
  supprime_le   TIMESTAMPTZ,
  version       INT          NOT NULL DEFAULT 0,
  CONSTRAINT uq_cycle_id UNIQUE (tenant_id, id),
  CONSTRAINT uq_cycle_code UNIQUE (tenant_id, code),
  CONSTRAINT ck_cycle_code CHECK (code IN ('ELEMENTAIRE', 'MOYEN', 'SECONDAIRE')),
  CONSTRAINT ck_cycle_type_periode CHECK (type_periode IN ('TRIMESTRE', 'SEMESTRE')),
  CONSTRAINT ck_cycle_mode_notation CHECK (mode_notation IN ('BAREME', 'COEFFICIENT')),
  CONSTRAINT ck_cycle_base_moyenne CHECK (base_moyenne IN (10, 20))
);

CREATE TABLE niveau (
  id            UUID         PRIMARY KEY,
  tenant_id     UUID         NOT NULL REFERENCES tenant(id),
  cycle_id      UUID         NOT NULL,
  code          VARCHAR(10)  NOT NULL,
  libelle       VARCHAR(80)  NOT NULL,
  ordre         INT          NOT NULL,
  -- NULL : le niveau suit la base de son cycle ; sinon elle la remplace (ex. CI en base 10).
  base_moyenne  INT,
  actif         BOOLEAN      NOT NULL DEFAULT TRUE,
  cree_le       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  cree_par      UUID,
  modifie_le    TIMESTAMPTZ,
  modifie_par   UUID,
  supprime_le   TIMESTAMPTZ,
  version       INT          NOT NULL DEFAULT 0,
  CONSTRAINT uq_niveau_id UNIQUE (tenant_id, id),
  CONSTRAINT fk_niveau_cycle FOREIGN KEY (tenant_id, cycle_id) REFERENCES cycle (tenant_id, id),
  CONSTRAINT ck_niveau_base_moyenne CHECK (base_moyenne IS NULL OR base_moyenne IN (10, 20))
);
CREATE UNIQUE INDEX uq_niveau_code ON niveau (tenant_id, code) WHERE supprime_le IS NULL;
CREATE INDEX ix_niveau_cycle ON niveau (tenant_id, cycle_id, ordre);

CREATE TABLE serie (
  id            UUID         PRIMARY KEY,
  tenant_id     UUID         NOT NULL REFERENCES tenant(id),
  code          VARCHAR(10)  NOT NULL,
  libelle       VARCHAR(80)  NOT NULL,
  actif         BOOLEAN      NOT NULL DEFAULT TRUE,
  cree_le       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  cree_par      UUID,
  modifie_le    TIMESTAMPTZ,
  modifie_par   UUID,
  supprime_le   TIMESTAMPTZ,
  version       INT          NOT NULL DEFAULT 0,
  CONSTRAINT uq_serie_id UNIQUE (tenant_id, id)
);
CREATE UNIQUE INDEX uq_serie_code ON serie (tenant_id, code) WHERE supprime_le IS NULL;

-- Séries autorisées pour un niveau. Un niveau sans ligne ici n'a pas de série.
CREATE TABLE niveau_serie (
  id            UUID         PRIMARY KEY,
  tenant_id     UUID         NOT NULL REFERENCES tenant(id),
  niveau_id     UUID         NOT NULL,
  serie_id      UUID         NOT NULL,
  cree_le       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  cree_par      UUID,
  modifie_le    TIMESTAMPTZ,
  modifie_par   UUID,
  supprime_le   TIMESTAMPTZ,
  version       INT          NOT NULL DEFAULT 0,
  CONSTRAINT fk_niveau_serie_niveau FOREIGN KEY (tenant_id, niveau_id) REFERENCES niveau (tenant_id, id),
  CONSTRAINT fk_niveau_serie_serie FOREIGN KEY (tenant_id, serie_id) REFERENCES serie (tenant_id, id)
);
CREATE UNIQUE INDEX uq_niveau_serie
  ON niveau_serie (tenant_id, niveau_id, serie_id) WHERE supprime_le IS NULL;
CREATE INDEX ix_niveau_serie_serie ON niveau_serie (tenant_id, serie_id);

ALTER TABLE etablissement ENABLE ROW LEVEL SECURITY;
ALTER TABLE etablissement FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON etablissement
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE etablissement_image ENABLE ROW LEVEL SECURITY;
ALTER TABLE etablissement_image FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON etablissement_image
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE cycle ENABLE ROW LEVEL SECURITY;
ALTER TABLE cycle FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON cycle
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE niveau ENABLE ROW LEVEL SECURITY;
ALTER TABLE niveau FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON niveau
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE serie ENABLE ROW LEVEL SECURITY;
ALTER TABLE serie FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON serie
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE niveau_serie ENABLE ROW LEVEL SECURITY;
ALTER TABLE niveau_serie FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON niveau_serie
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE ON etablissement, etablissement_image, cycle, niveau, serie, niveau_serie
  TO ecole_app;
