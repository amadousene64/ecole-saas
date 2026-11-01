-- Module inscription (3/5) : inscriptions annuelles (élève × classe × année) et options choisies.

CREATE TABLE inscription (
  id             UUID          PRIMARY KEY,
  tenant_id      UUID          NOT NULL REFERENCES tenant(id),
  eleve_id       UUID          NOT NULL,
  classe_id      UUID          NOT NULL,
  -- Année de la classe, recopiée pour l'unicité « une inscription active par année ».
  annee_id       UUID          NOT NULL,
  statut         VARCHAR(12)   NOT NULL,
  redoublant     BOOLEAN       NOT NULL DEFAULT FALSE,
  date_entree    DATE          NOT NULL,
  date_sortie    DATE,
  motif_sortie   VARCHAR(200),
  cree_le        TIMESTAMPTZ   NOT NULL DEFAULT now(),
  cree_par       UUID,
  modifie_le     TIMESTAMPTZ,
  modifie_par    UUID,
  supprime_le    TIMESTAMPTZ,
  version        INT           NOT NULL DEFAULT 0,
  CONSTRAINT uq_inscription_id UNIQUE (tenant_id, id),
  CONSTRAINT fk_inscription_eleve FOREIGN KEY (tenant_id, eleve_id) REFERENCES eleve (tenant_id, id),
  CONSTRAINT fk_inscription_classe FOREIGN KEY (tenant_id, classe_id) REFERENCES classe (tenant_id, id),
  CONSTRAINT fk_inscription_annee FOREIGN KEY (tenant_id, annee_id)
    REFERENCES annee_scolaire (tenant_id, id),
  CONSTRAINT ck_inscription_statut
    CHECK (statut IN ('PRE_INSCRIT', 'INSCRIT', 'TRANSFERE', 'RADIE', 'ABANDON')),
  CONSTRAINT ck_inscription_sortie CHECK (
    (statut IN ('PRE_INSCRIT', 'INSCRIT') AND date_sortie IS NULL)
    OR (statut IN ('TRANSFERE', 'RADIE', 'ABANDON') AND date_sortie IS NOT NULL))
);
-- Une seule inscription active (pré-inscrit ou inscrit) par élève et par année.
CREATE UNIQUE INDEX uq_inscription_active ON inscription (tenant_id, eleve_id, annee_id)
  WHERE statut IN ('PRE_INSCRIT', 'INSCRIT') AND supprime_le IS NULL;
CREATE INDEX ix_inscription_classe ON inscription (tenant_id, classe_id, statut);
CREATE INDEX ix_inscription_eleve ON inscription (tenant_id, eleve_id);

-- Rien ne s'inscrit ni ne change dans une année clôturée.
CREATE TRIGGER trg_inscription_annee_cloturee BEFORE INSERT OR UPDATE ON inscription
  FOR EACH ROW EXECUTE FUNCTION refuser_annee_cloturee();

CREATE TABLE inscription_option (
  id              UUID         PRIMARY KEY,
  tenant_id       UUID         NOT NULL REFERENCES tenant(id),
  inscription_id  UUID         NOT NULL,
  option_id       UUID         NOT NULL,
  cree_le         TIMESTAMPTZ  NOT NULL DEFAULT now(),
  cree_par        UUID,
  modifie_le      TIMESTAMPTZ,
  modifie_par     UUID,
  supprime_le     TIMESTAMPTZ,
  version         INT          NOT NULL DEFAULT 0,
  CONSTRAINT fk_inscription_option_inscription FOREIGN KEY (tenant_id, inscription_id)
    REFERENCES inscription (tenant_id, id),
  CONSTRAINT fk_inscription_option_option FOREIGN KEY (tenant_id, option_id)
    REFERENCES option_enseignement (tenant_id, id)
);
CREATE UNIQUE INDEX uq_inscription_option ON inscription_option (tenant_id, inscription_id, option_id)
  WHERE supprime_le IS NULL;
CREATE INDEX ix_inscription_option_option ON inscription_option (tenant_id, option_id);

ALTER TABLE inscription ENABLE ROW LEVEL SECURITY;
ALTER TABLE inscription FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON inscription
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE inscription_option ENABLE ROW LEVEL SECURITY;
ALTER TABLE inscription_option FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON inscription_option
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE ON inscription, inscription_option TO ecole_app;
