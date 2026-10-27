-- Module etablissement (3/3) : salles et classes.

CREATE TABLE salle (
  id           UUID         PRIMARY KEY,
  tenant_id    UUID         NOT NULL REFERENCES tenant(id),
  nom          VARCHAR(60)  NOT NULL,
  capacite     INT          NOT NULL,
  actif        BOOLEAN      NOT NULL DEFAULT TRUE,
  cree_le      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  cree_par     UUID,
  modifie_le   TIMESTAMPTZ,
  modifie_par  UUID,
  supprime_le  TIMESTAMPTZ,
  version      INT          NOT NULL DEFAULT 0,
  CONSTRAINT uq_salle_id UNIQUE (tenant_id, id),
  CONSTRAINT ck_salle_capacite CHECK (capacite BETWEEN 1 AND 500)
);
CREATE UNIQUE INDEX uq_salle_nom ON salle (tenant_id, upper(nom)) WHERE supprime_le IS NULL;

CREATE TABLE classe (
  id           UUID         PRIMARY KEY,
  tenant_id    UUID         NOT NULL REFERENCES tenant(id),
  annee_id     UUID         NOT NULL,
  niveau_id    UUID         NOT NULL,
  -- NULL pour un niveau sans série. La cohérence niveau/série est vérifiée par le domaine.
  serie_id     UUID,
  nom          VARCHAR(30)  NOT NULL,
  capacite     INT          NOT NULL,
  salle_id     UUID,
  cree_le      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  cree_par     UUID,
  modifie_le   TIMESTAMPTZ,
  modifie_par  UUID,
  supprime_le  TIMESTAMPTZ,
  version      INT          NOT NULL DEFAULT 0,
  CONSTRAINT uq_classe_id UNIQUE (tenant_id, id),
  CONSTRAINT fk_classe_annee FOREIGN KEY (tenant_id, annee_id) REFERENCES annee_scolaire (tenant_id, id),
  CONSTRAINT fk_classe_niveau FOREIGN KEY (tenant_id, niveau_id) REFERENCES niveau (tenant_id, id),
  CONSTRAINT fk_classe_serie FOREIGN KEY (tenant_id, serie_id) REFERENCES serie (tenant_id, id),
  CONSTRAINT fk_classe_salle FOREIGN KEY (tenant_id, salle_id) REFERENCES salle (tenant_id, id),
  CONSTRAINT ck_classe_capacite CHECK (capacite BETWEEN 1 AND 500)
);
CREATE UNIQUE INDEX uq_classe_nom ON classe (tenant_id, annee_id, upper(nom)) WHERE supprime_le IS NULL;
CREATE INDEX ix_classe_niveau ON classe (tenant_id, niveau_id, serie_id);
CREATE INDEX ix_classe_salle ON classe (tenant_id, salle_id);

CREATE TRIGGER trg_classe_annee_cloturee BEFORE INSERT OR UPDATE ON classe
  FOR EACH ROW EXECUTE FUNCTION refuser_annee_cloturee();

ALTER TABLE salle ENABLE ROW LEVEL SECURITY;
ALTER TABLE salle FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON salle
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE classe ENABLE ROW LEVEL SECURITY;
ALTER TABLE classe FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON classe
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE ON salle, classe TO ecole_app;
