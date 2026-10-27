-- Module etablissement (2/3) : année scolaire, périodes et calendrier.

CREATE TABLE annee_scolaire (
  id           UUID         PRIMARY KEY,
  tenant_id    UUID         NOT NULL REFERENCES tenant(id),
  libelle      VARCHAR(9)   NOT NULL,
  date_debut   DATE         NOT NULL,
  date_fin     DATE         NOT NULL,
  statut       VARCHAR(12)  NOT NULL DEFAULT 'PREPARATION',
  cree_le      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  cree_par     UUID,
  modifie_le   TIMESTAMPTZ,
  modifie_par  UUID,
  supprime_le  TIMESTAMPTZ,
  version      INT          NOT NULL DEFAULT 0,
  CONSTRAINT uq_annee_scolaire_id UNIQUE (tenant_id, id),
  CONSTRAINT ck_annee_scolaire_statut CHECK (statut IN ('PREPARATION', 'EN_COURS', 'CLOTUREE')),
  CONSTRAINT ck_annee_scolaire_dates CHECK (date_debut < date_fin),
  CONSTRAINT ck_annee_scolaire_libelle CHECK (libelle ~ '^[0-9]{4}-[0-9]{4}$')
);
CREATE UNIQUE INDEX uq_annee_scolaire_libelle
  ON annee_scolaire (tenant_id, libelle) WHERE supprime_le IS NULL;
-- Une seule année en cours par école, même si deux ouvertures arrivent en même temps.
CREATE UNIQUE INDEX uq_annee_scolaire_en_cours
  ON annee_scolaire (tenant_id) WHERE statut = 'EN_COURS' AND supprime_le IS NULL;

CREATE TABLE periode (
  id           UUID         PRIMARY KEY,
  tenant_id    UUID         NOT NULL REFERENCES tenant(id),
  annee_id     UUID         NOT NULL,
  cycle_id     UUID         NOT NULL,
  numero       INT          NOT NULL,
  libelle      VARCHAR(40)  NOT NULL,
  date_debut   DATE         NOT NULL,
  date_fin     DATE         NOT NULL,
  cree_le      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  cree_par     UUID,
  modifie_le   TIMESTAMPTZ,
  modifie_par  UUID,
  supprime_le  TIMESTAMPTZ,
  version      INT          NOT NULL DEFAULT 0,
  CONSTRAINT fk_periode_annee FOREIGN KEY (tenant_id, annee_id) REFERENCES annee_scolaire (tenant_id, id),
  CONSTRAINT fk_periode_cycle FOREIGN KEY (tenant_id, cycle_id) REFERENCES cycle (tenant_id, id),
  CONSTRAINT ck_periode_numero CHECK (numero BETWEEN 1 AND 4),
  CONSTRAINT ck_periode_dates CHECK (date_debut <= date_fin)
);
CREATE UNIQUE INDEX uq_periode_numero
  ON periode (tenant_id, annee_id, cycle_id, numero) WHERE supprime_le IS NULL;

CREATE TABLE evenement_calendrier (
  id           UUID         PRIMARY KEY,
  tenant_id    UUID         NOT NULL REFERENCES tenant(id),
  annee_id     UUID         NOT NULL,
  type         VARCHAR(10)  NOT NULL,
  libelle      VARCHAR(120) NOT NULL,
  date_debut   DATE         NOT NULL,
  date_fin     DATE         NOT NULL,
  cree_le      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  cree_par     UUID,
  modifie_le   TIMESTAMPTZ,
  modifie_par  UUID,
  supprime_le  TIMESTAMPTZ,
  version      INT          NOT NULL DEFAULT 0,
  CONSTRAINT fk_evenement_calendrier_annee FOREIGN KEY (tenant_id, annee_id)
    REFERENCES annee_scolaire (tenant_id, id),
  CONSTRAINT ck_evenement_calendrier_type CHECK (type IN ('FERIE', 'VACANCES')),
  CONSTRAINT ck_evenement_calendrier_dates CHECK (date_debut <= date_fin)
);
CREATE INDEX ix_evenement_calendrier_annee ON evenement_calendrier (tenant_id, annee_id, date_debut);

-- Année clôturée = lecture seule : filet de sécurité de la règle appliquée par le domaine.
-- Fonction ordinaire (droits de l'appelant, donc soumise à la RLS) : pas de SECURITY DEFINER.
CREATE FUNCTION refuser_annee_cloturee() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  statut_annee VARCHAR(12);
BEGIN
  IF TG_TABLE_NAME = 'annee_scolaire' THEN
    statut_annee := OLD.statut;
  ELSE
    SELECT statut INTO statut_annee FROM annee_scolaire WHERE id = NEW.annee_id;
  END IF;
  IF statut_annee = 'CLOTUREE' THEN
    RAISE EXCEPTION 'ANNEE_CLOTUREE : l''année scolaire est clôturée' USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_annee_scolaire_cloturee BEFORE UPDATE ON annee_scolaire
  FOR EACH ROW EXECUTE FUNCTION refuser_annee_cloturee();
CREATE TRIGGER trg_periode_annee_cloturee BEFORE INSERT OR UPDATE ON periode
  FOR EACH ROW EXECUTE FUNCTION refuser_annee_cloturee();
CREATE TRIGGER trg_evenement_calendrier_annee_cloturee BEFORE INSERT OR UPDATE ON evenement_calendrier
  FOR EACH ROW EXECUTE FUNCTION refuser_annee_cloturee();

ALTER TABLE annee_scolaire ENABLE ROW LEVEL SECURITY;
ALTER TABLE annee_scolaire FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON annee_scolaire
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE periode ENABLE ROW LEVEL SECURITY;
ALTER TABLE periode FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON periode
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE evenement_calendrier ENABLE ROW LEVEL SECURITY;
ALTER TABLE evenement_calendrier FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON evenement_calendrier
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE ON annee_scolaire, periode, evenement_calendrier TO ecole_app;
