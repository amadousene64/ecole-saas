-- Paramètres de notation figés par année. Les cycles et niveaux sont un modèle que le directeur
-- modifie à tout moment ; à l'ouverture d'une année, leurs paramètres sont COPIÉS ici et ne bougent
-- plus : une année EN_COURS ou CLOTUREE garde les siens, quoi qu'il arrive au modèle ensuite.

-- Notation par coefficients = sur 20. Le modèle lui-même ne peut plus l'enfreindre.
ALTER TABLE cycle ADD CONSTRAINT ck_cycle_base_coefficient
  CHECK (mode_notation <> 'COEFFICIENT' OR base_moyenne = 20);

CREATE TABLE parametres_cycle_annee (
  id            UUID         PRIMARY KEY,
  tenant_id     UUID         NOT NULL REFERENCES tenant(id),
  annee_id      UUID         NOT NULL,
  cycle_id      UUID         NOT NULL,
  type_periode  VARCHAR(10)  NOT NULL,
  mode_notation VARCHAR(12)  NOT NULL,
  base_moyenne  INT          NOT NULL,
  cree_le       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  cree_par      UUID,
  modifie_le    TIMESTAMPTZ,
  modifie_par   UUID,
  supprime_le   TIMESTAMPTZ,
  version       INT          NOT NULL DEFAULT 0,
  CONSTRAINT uq_parametres_cycle_annee UNIQUE (tenant_id, annee_id, cycle_id),
  CONSTRAINT fk_parametres_cycle_annee_annee FOREIGN KEY (tenant_id, annee_id)
    REFERENCES annee_scolaire (tenant_id, id),
  CONSTRAINT fk_parametres_cycle_annee_cycle FOREIGN KEY (tenant_id, cycle_id)
    REFERENCES cycle (tenant_id, id),
  CONSTRAINT ck_parametres_cycle_annee_type CHECK (type_periode IN ('TRIMESTRE', 'SEMESTRE')),
  CONSTRAINT ck_parametres_cycle_annee_mode CHECK (mode_notation IN ('BAREME', 'COEFFICIENT')),
  CONSTRAINT ck_parametres_cycle_annee_base CHECK (base_moyenne IN (10, 20)),
  CONSTRAINT ck_parametres_cycle_annee_coefficient
    CHECK (mode_notation <> 'COEFFICIENT' OR base_moyenne = 20)
);

CREATE TABLE parametres_niveau_annee (
  id            UUID         PRIMARY KEY,
  tenant_id     UUID         NOT NULL REFERENCES tenant(id),
  annee_id      UUID         NOT NULL,
  niveau_id     UUID         NOT NULL,
  cycle_id      UUID         NOT NULL,
  -- Recopié du cycle au moment du figeage, pour pouvoir contraindre la base.
  mode_notation VARCHAR(12)  NOT NULL,
  -- Base effective : celle du niveau si elle surcharge celle du cycle, sinon celle du cycle.
  base_moyenne  INT          NOT NULL,
  cree_le       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  cree_par      UUID,
  modifie_le    TIMESTAMPTZ,
  modifie_par   UUID,
  supprime_le   TIMESTAMPTZ,
  version       INT          NOT NULL DEFAULT 0,
  CONSTRAINT uq_parametres_niveau_annee UNIQUE (tenant_id, annee_id, niveau_id),
  CONSTRAINT fk_parametres_niveau_annee_annee FOREIGN KEY (tenant_id, annee_id)
    REFERENCES annee_scolaire (tenant_id, id),
  CONSTRAINT fk_parametres_niveau_annee_niveau FOREIGN KEY (tenant_id, niveau_id)
    REFERENCES niveau (tenant_id, id),
  CONSTRAINT fk_parametres_niveau_annee_cycle FOREIGN KEY (tenant_id, cycle_id)
    REFERENCES cycle (tenant_id, id),
  CONSTRAINT ck_parametres_niveau_annee_mode CHECK (mode_notation IN ('BAREME', 'COEFFICIENT')),
  CONSTRAINT ck_parametres_niveau_annee_base CHECK (base_moyenne IN (10, 20)),
  CONSTRAINT ck_parametres_niveau_annee_coefficient
    CHECK (mode_notation <> 'COEFFICIENT' OR base_moyenne = 20)
);

-- Une copie figée ne se modifie ni ne se supprime, par personne (le rôle applicatif n'a d'ailleurs
-- que SELECT et INSERT).
CREATE FUNCTION refuser_modification_parametres() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'PARAMETRES_FIGES : les paramètres d''une année ouverte sont immuables'
    USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER trg_parametres_cycle_annee_immuable BEFORE UPDATE OR DELETE ON parametres_cycle_annee
  FOR EACH ROW EXECUTE FUNCTION refuser_modification_parametres();
CREATE TRIGGER trg_parametres_niveau_annee_immuable BEFORE UPDATE OR DELETE ON parametres_niveau_annee
  FOR EACH ROW EXECUTE FUNCTION refuser_modification_parametres();
CREATE TRIGGER trg_parametres_cycle_annee_cloturee BEFORE INSERT ON parametres_cycle_annee
  FOR EACH ROW EXECUTE FUNCTION refuser_annee_cloturee();
CREATE TRIGGER trg_parametres_niveau_annee_cloturee BEFORE INSERT ON parametres_niveau_annee
  FOR EACH ROW EXECUTE FUNCTION refuser_annee_cloturee();

ALTER TABLE parametres_cycle_annee ENABLE ROW LEVEL SECURITY;
ALTER TABLE parametres_cycle_annee FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON parametres_cycle_annee
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE parametres_niveau_annee ENABLE ROW LEVEL SECURITY;
ALTER TABLE parametres_niveau_annee FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON parametres_niveau_annee
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

GRANT SELECT, INSERT ON parametres_cycle_annee, parametres_niveau_annee TO ecole_app;
