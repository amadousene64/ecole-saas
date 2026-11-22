-- Matières enseignées par l'école, et grille horaire (créneaux) de chaque cycle.
-- Les coefficients (niveau, série, matière) et le barème du primaire viendront dans une table à
-- part : une matière ne change pas quand ses coefficients changent (docs/bulletins, § 3).

CREATE TABLE matiere (
  id            UUID          PRIMARY KEY,
  tenant_id     UUID          NOT NULL REFERENCES tenant(id),
  code          VARCHAR(10)   NOT NULL,
  libelle       VARCHAR(80)   NOT NULL,
  -- Domaine ou discipline mère pour l'affichage (« Français » pour Dissertation…), facultatif.
  groupe        VARCHAR(60),
  -- Cycles où la matière est enseignée (au moins un).
  cycles        VARCHAR(12)[] NOT NULL,
  ordre         SMALLINT      NOT NULL DEFAULT 0,
  actif         BOOLEAN       NOT NULL DEFAULT TRUE,
  cree_le       TIMESTAMPTZ   NOT NULL DEFAULT now(),
  cree_par      UUID,
  modifie_le    TIMESTAMPTZ,
  modifie_par   UUID,
  supprime_le   TIMESTAMPTZ,
  version       INT           NOT NULL DEFAULT 0,
  CONSTRAINT uq_matiere_id UNIQUE (tenant_id, id),
  CONSTRAINT ck_matiere_cycles CHECK (
    cardinality(cycles) BETWEEN 1 AND 3
    AND cycles <@ ARRAY['ELEMENTAIRE', 'MOYEN', 'SECONDAIRE']::VARCHAR(12)[]),
  CONSTRAINT ck_matiere_ordre CHECK (ordre BETWEEN 0 AND 999)
);
CREATE UNIQUE INDEX uq_matiere_code ON matiere (tenant_id, code) WHERE supprime_le IS NULL;
CREATE INDEX ix_matiere_ordre ON matiere (tenant_id, ordre, libelle) WHERE supprime_le IS NULL;

-- Créneau de la grille horaire d'un cycle : heure de début et de fin, jours où il a lieu.
-- jours : lundi = 1, mardi = 2, mercredi = 4 … dimanche = 64 (somme des jours concernés).
CREATE TABLE creneau (
  id            UUID          PRIMARY KEY,
  tenant_id     UUID          NOT NULL REFERENCES tenant(id),
  cycle_id      UUID          NOT NULL,
  debut         TIME          NOT NULL,
  fin           TIME          NOT NULL,
  jours         SMALLINT      NOT NULL,
  type          VARCHAR(10)   NOT NULL,
  libelle       VARCHAR(30),
  cree_le       TIMESTAMPTZ   NOT NULL DEFAULT now(),
  cree_par      UUID,
  modifie_le    TIMESTAMPTZ,
  modifie_par   UUID,
  supprime_le   TIMESTAMPTZ,
  version       INT           NOT NULL DEFAULT 0,
  CONSTRAINT uq_creneau_id UNIQUE (tenant_id, id),
  CONSTRAINT fk_creneau_cycle FOREIGN KEY (tenant_id, cycle_id) REFERENCES cycle (tenant_id, id),
  CONSTRAINT ck_creneau_heures CHECK (debut >= TIME '06:00' AND fin <= TIME '20:00'
    AND fin >= debut + INTERVAL '15 minutes'),
  CONSTRAINT ck_creneau_jours CHECK (jours BETWEEN 1 AND 127),
  CONSTRAINT ck_creneau_type CHECK (type IN ('COURS', 'PAUSE'))
);
CREATE INDEX ix_creneau_cycle ON creneau (tenant_id, cycle_id, debut) WHERE supprime_le IS NULL;

ALTER TABLE matiere ENABLE ROW LEVEL SECURITY;
ALTER TABLE matiere FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON matiere
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE creneau ENABLE ROW LEVEL SECURITY;
ALTER TABLE creneau FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON creneau
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE ON matiere, creneau TO ecole_app;
