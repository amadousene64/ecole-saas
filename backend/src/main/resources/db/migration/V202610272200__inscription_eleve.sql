-- Module inscription (1/5) : paramètres, matricules, élèves et données sensibles chiffrées.

-- Paramètres d'inscription de l'école (une ligne au plus ; valeurs par défaut sinon).
CREATE TABLE parametres_inscription (
  id                  UUID         PRIMARY KEY,
  tenant_id           UUID         NOT NULL REFERENCES tenant(id),
  format_matricule    VARCHAR(60)  NOT NULL,
  politique_capacite  VARCHAR(10)  NOT NULL,
  cree_le             TIMESTAMPTZ  NOT NULL DEFAULT now(),
  cree_par            UUID,
  modifie_le          TIMESTAMPTZ,
  modifie_par         UUID,
  supprime_le         TIMESTAMPTZ,
  version             INT          NOT NULL DEFAULT 0,
  CONSTRAINT ck_parametres_inscription_capacite CHECK (politique_capacite IN ('AVERTIR', 'BLOQUER'))
);
CREATE UNIQUE INDEX uq_parametres_inscription ON parametres_inscription (tenant_id)
  WHERE supprime_le IS NULL;

-- Compteurs des matricules : une ligne par clé (l'année si le format la contient, « * » sinon).
-- Incrémentés par INSERT ... ON CONFLICT DO UPDATE (atomique, sans trou sous concurrence).
CREATE TABLE sequence_matricule (
  id           UUID         PRIMARY KEY,
  tenant_id    UUID         NOT NULL REFERENCES tenant(id),
  cle          VARCHAR(10)  NOT NULL,
  valeur       BIGINT       NOT NULL,
  cree_le      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  cree_par     UUID,
  modifie_le   TIMESTAMPTZ,
  modifie_par  UUID,
  supprime_le  TIMESTAMPTZ,
  version      INT          NOT NULL DEFAULT 0,
  CONSTRAINT uq_sequence_matricule UNIQUE (tenant_id, cle)
);

CREATE TABLE eleve (
  id                    UUID          PRIMARY KEY,
  tenant_id             UUID          NOT NULL REFERENCES tenant(id),
  matricule             VARCHAR(30)   NOT NULL,
  prenoms               VARCHAR(100)  NOT NULL,
  nom                   VARCHAR(60)   NOT NULL,
  sexe                  CHAR(1)       NOT NULL,
  date_naissance        DATE          NOT NULL,
  lieu_naissance        VARCHAR(100)  NOT NULL,
  nationalite           VARCHAR(2)    NOT NULL,
  identifiant_national  VARCHAR(30),
  photo_cle             VARCHAR(200),
  photo_content_type    VARCHAR(30),
  -- « nom prénoms » en minuscules sans accents : recherche et tri.
  nom_recherche         VARCHAR(200)  NOT NULL,
  -- Compte Keycloak de l'élève, relié par code d'activation (collège et lycée).
  utilisateur_id        UUID,
  cree_le               TIMESTAMPTZ   NOT NULL DEFAULT now(),
  cree_par              UUID,
  modifie_le            TIMESTAMPTZ,
  modifie_par           UUID,
  supprime_le           TIMESTAMPTZ,
  version               INT           NOT NULL DEFAULT 0,
  CONSTRAINT uq_eleve_id UNIQUE (tenant_id, id),
  -- Un matricule n'est jamais réutilisé, même après suppression.
  CONSTRAINT uq_eleve_matricule UNIQUE (tenant_id, matricule),
  CONSTRAINT ck_eleve_sexe CHECK (sexe IN ('M', 'F')),
  CONSTRAINT ck_eleve_nationalite CHECK (nationalite ~ '^[A-Z]{2}$')
);
CREATE INDEX ix_eleve_recherche ON eleve (tenant_id, nom_recherche, id) WHERE supprime_le IS NULL;
CREATE UNIQUE INDEX uq_eleve_identifiant_national ON eleve (tenant_id, identifiant_national)
  WHERE identifiant_national IS NOT NULL AND supprime_le IS NULL;
CREATE UNIQUE INDEX uq_eleve_utilisateur ON eleve (tenant_id, utilisateur_id)
  WHERE utilisateur_id IS NOT NULL AND supprime_le IS NULL;

-- Données sensibles (santé, allergies, situation familiale), chiffrées par l'application
-- (AES-256-GCM, clé hors base) : la base ne voit jamais le texte clair.
CREATE TABLE eleve_dossier_sensible (
  id                   UUID         PRIMARY KEY,
  tenant_id            UUID         NOT NULL REFERENCES tenant(id),
  eleve_id             UUID         NOT NULL,
  sante                BYTEA,
  allergies            BYTEA,
  situation_familiale  BYTEA,
  cree_le              TIMESTAMPTZ  NOT NULL DEFAULT now(),
  cree_par             UUID,
  modifie_le           TIMESTAMPTZ,
  modifie_par          UUID,
  supprime_le          TIMESTAMPTZ,
  version              INT          NOT NULL DEFAULT 0,
  CONSTRAINT fk_eleve_dossier_sensible_eleve FOREIGN KEY (tenant_id, eleve_id)
    REFERENCES eleve (tenant_id, id)
);
CREATE UNIQUE INDEX uq_eleve_dossier_sensible ON eleve_dossier_sensible (tenant_id, eleve_id)
  WHERE supprime_le IS NULL;

ALTER TABLE parametres_inscription ENABLE ROW LEVEL SECURITY;
ALTER TABLE parametres_inscription FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON parametres_inscription
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE sequence_matricule ENABLE ROW LEVEL SECURITY;
ALTER TABLE sequence_matricule FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON sequence_matricule
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE eleve ENABLE ROW LEVEL SECURITY;
ALTER TABLE eleve FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON eleve
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE eleve_dossier_sensible ENABLE ROW LEVEL SECURITY;
ALTER TABLE eleve_dossier_sensible FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON eleve_dossier_sensible
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE ON parametres_inscription, sequence_matricule, eleve,
  eleve_dossier_sensible TO ecole_app;
