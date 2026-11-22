-- Module personnel : annuaire du personnel (noms tirés du compte à la connexion) et affectations
-- par année scolaire, avec historique (une affectation se termine, elle n'est jamais effacée).

CREATE TABLE membre_personnel (
  id              UUID          PRIMARY KEY,
  tenant_id       UUID          NOT NULL REFERENCES tenant(id),
  utilisateur_id  UUID          NOT NULL,
  prenoms         VARCHAR(100),
  nom             VARCHAR(60),
  -- Dernière présentation à l'école (GET /api/v1/moi), date de l'application.
  vu_le           TIMESTAMPTZ   NOT NULL,
  cree_le         TIMESTAMPTZ   NOT NULL DEFAULT now(),
  cree_par        UUID,
  modifie_le      TIMESTAMPTZ,
  modifie_par     UUID,
  supprime_le     TIMESTAMPTZ,
  version         INT           NOT NULL DEFAULT 0,
  CONSTRAINT uq_membre_personnel_id UNIQUE (tenant_id, id)
);
CREATE UNIQUE INDEX uq_membre_personnel_utilisateur ON membre_personnel (tenant_id, utilisateur_id)
  WHERE supprime_le IS NULL;

-- ENSEIGNEMENT : un enseignant, une matière dans une classe.
-- TITULAIRE    : le maître d'une classe de l'élémentaire, toutes matières.
-- SURVEILLANCE : un surveillant, toutes les classes d'un cycle.
-- Période [debut, fin) : fin est la première date où l'affectation ne vaut plus (nulle : en cours).
CREATE TABLE affectation (
  id              UUID          PRIMARY KEY,
  tenant_id       UUID          NOT NULL REFERENCES tenant(id),
  annee_id        UUID          NOT NULL,
  utilisateur_id  UUID          NOT NULL,
  type            VARCHAR(12)   NOT NULL,
  classe_id       UUID,
  matiere_id      UUID,
  code_cycle      VARCHAR(12),
  debut           DATE          NOT NULL,
  fin             DATE,
  cree_le         TIMESTAMPTZ   NOT NULL DEFAULT now(),
  cree_par        UUID,
  modifie_le      TIMESTAMPTZ,
  modifie_par     UUID,
  supprime_le     TIMESTAMPTZ,
  version         INT           NOT NULL DEFAULT 0,
  CONSTRAINT uq_affectation_id UNIQUE (tenant_id, id),
  CONSTRAINT fk_affectation_annee FOREIGN KEY (tenant_id, annee_id)
    REFERENCES annee_scolaire (tenant_id, id),
  CONSTRAINT fk_affectation_classe FOREIGN KEY (tenant_id, classe_id) REFERENCES classe (tenant_id, id),
  CONSTRAINT fk_affectation_matiere FOREIGN KEY (tenant_id, matiere_id)
    REFERENCES matiere (tenant_id, id),
  CONSTRAINT ck_affectation_type CHECK (
    (type = 'ENSEIGNEMENT' AND classe_id IS NOT NULL AND matiere_id IS NOT NULL
      AND code_cycle IS NULL)
    OR (type = 'TITULAIRE' AND classe_id IS NOT NULL AND matiere_id IS NULL AND code_cycle IS NULL)
    OR (type = 'SURVEILLANCE' AND classe_id IS NULL AND matiere_id IS NULL
      AND code_cycle IN ('ELEMENTAIRE', 'MOYEN', 'SECONDAIRE'))),
  CONSTRAINT ck_affectation_periode CHECK (fin IS NULL OR fin > debut)
);
CREATE INDEX ix_affectation_utilisateur ON affectation (tenant_id, utilisateur_id, debut)
  WHERE supprime_le IS NULL;
CREATE INDEX ix_affectation_annee ON affectation (tenant_id, annee_id) WHERE supprime_le IS NULL;
CREATE INDEX ix_affectation_classe ON affectation (tenant_id, classe_id) WHERE supprime_le IS NULL;

ALTER TABLE membre_personnel ENABLE ROW LEVEL SECURITY;
ALTER TABLE membre_personnel FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON membre_personnel
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE affectation ENABLE ROW LEVEL SECURITY;
ALTER TABLE affectation FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON affectation
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE ON membre_personnel, affectation TO ecole_app;
