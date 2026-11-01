-- Module inscription (2/5) : responsables (propres à chaque école), liens parentaux, codes
-- d'activation des comptes parent et élève.

CREATE TABLE responsable (
  id              UUID          PRIMARY KEY,
  tenant_id       UUID          NOT NULL REFERENCES tenant(id),
  prenoms         VARCHAR(100)  NOT NULL,
  nom             VARCHAR(60)   NOT NULL,
  telephone       VARCHAR(16)   NOT NULL,
  email           VARCHAR(254),
  profession      VARCHAR(100),
  nom_recherche   VARCHAR(200)  NOT NULL,
  -- Compte Keycloak relié par code d'activation ; jamais partagé entre écoles.
  utilisateur_id  UUID,
  cree_le         TIMESTAMPTZ   NOT NULL DEFAULT now(),
  cree_par        UUID,
  modifie_le      TIMESTAMPTZ,
  modifie_par     UUID,
  supprime_le     TIMESTAMPTZ,
  version         INT           NOT NULL DEFAULT 0,
  CONSTRAINT uq_responsable_id UNIQUE (tenant_id, id),
  CONSTRAINT ck_responsable_telephone CHECK (telephone ~ '^\+[1-9][0-9]{7,14}$')
);
CREATE INDEX ix_responsable_telephone ON responsable (tenant_id, telephone) WHERE supprime_le IS NULL;
CREATE INDEX ix_responsable_recherche ON responsable (tenant_id, nom_recherche, id)
  WHERE supprime_le IS NULL;
CREATE UNIQUE INDEX uq_responsable_utilisateur ON responsable (tenant_id, utilisateur_id)
  WHERE utilisateur_id IS NOT NULL AND supprime_le IS NULL;

CREATE TABLE lien_parental (
  id                    UUID         PRIMARY KEY,
  tenant_id             UUID         NOT NULL REFERENCES tenant(id),
  eleve_id              UUID         NOT NULL,
  responsable_id        UUID         NOT NULL,
  type                  VARCHAR(6)   NOT NULL,
  responsable_legal     BOOLEAN      NOT NULL,
  payeur                BOOLEAN      NOT NULL,
  contact_urgence       BOOLEAN      NOT NULL,
  autorise_recuperer    BOOLEAN      NOT NULL,
  recoit_notifications  BOOLEAN      NOT NULL,
  -- Faux (décision du directeur, ex. décision de justice) : ce responsable ne voit plus l'élève et
  -- ne reçoit aucune notification.
  acces_espace_parent   BOOLEAN      NOT NULL DEFAULT TRUE,
  cree_le               TIMESTAMPTZ  NOT NULL DEFAULT now(),
  cree_par              UUID,
  modifie_le            TIMESTAMPTZ,
  modifie_par           UUID,
  supprime_le           TIMESTAMPTZ,
  version               INT          NOT NULL DEFAULT 0,
  CONSTRAINT uq_lien_parental_id UNIQUE (tenant_id, id),
  CONSTRAINT fk_lien_parental_eleve FOREIGN KEY (tenant_id, eleve_id) REFERENCES eleve (tenant_id, id),
  CONSTRAINT fk_lien_parental_responsable FOREIGN KEY (tenant_id, responsable_id)
    REFERENCES responsable (tenant_id, id),
  CONSTRAINT ck_lien_parental_type CHECK (type IN ('PERE', 'MERE', 'TUTEUR', 'AUTRE')),
  CONSTRAINT ck_lien_parental_notifications CHECK (acces_espace_parent OR NOT recoit_notifications)
);
CREATE UNIQUE INDEX uq_lien_parental ON lien_parental (tenant_id, eleve_id, responsable_id)
  WHERE supprime_le IS NULL;
CREATE INDEX ix_lien_parental_responsable ON lien_parental (tenant_id, responsable_id)
  WHERE supprime_le IS NULL;

-- Codes d'activation : l'identifiant (4 caractères) sert à retrouver le code, le secret
-- (8 caractères) n'est stocké que sous forme d'empreinte HMAC (clé hors base).
CREATE TABLE code_activation (
  id           UUID          PRIMARY KEY,
  tenant_id    UUID          NOT NULL REFERENCES tenant(id),
  cible        VARCHAR(12)   NOT NULL,
  cible_id     UUID          NOT NULL,
  identifiant  VARCHAR(4)    NOT NULL,
  empreinte    BYTEA         NOT NULL,
  expire_le    TIMESTAMPTZ   NOT NULL,
  tentatives   INT           NOT NULL DEFAULT 0,
  statut       VARCHAR(8)    NOT NULL,
  utilise_le   TIMESTAMPTZ,
  utilise_par  UUID,
  cree_le      TIMESTAMPTZ   NOT NULL DEFAULT now(),
  cree_par     UUID,
  modifie_le   TIMESTAMPTZ,
  modifie_par  UUID,
  supprime_le  TIMESTAMPTZ,
  version      INT           NOT NULL DEFAULT 0,
  CONSTRAINT ck_code_activation_cible CHECK (cible IN ('RESPONSABLE', 'ELEVE')),
  CONSTRAINT ck_code_activation_statut CHECK (statut IN ('ACTIF', 'UTILISE', 'REVOQUE', 'EPUISE')),
  CONSTRAINT ck_code_activation_tentatives CHECK (tentatives BETWEEN 0 AND 5)
);
CREATE UNIQUE INDEX uq_code_activation_identifiant ON code_activation (tenant_id, identifiant)
  WHERE statut = 'ACTIF';
CREATE INDEX ix_code_activation_cible ON code_activation (tenant_id, cible, cible_id);

ALTER TABLE responsable ENABLE ROW LEVEL SECURITY;
ALTER TABLE responsable FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON responsable
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE lien_parental ENABLE ROW LEVEL SECURITY;
ALTER TABLE lien_parental FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON lien_parental
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE code_activation ENABLE ROW LEVEL SECURITY;
ALTER TABLE code_activation FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON code_activation
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE ON responsable, lien_parental, code_activation TO ecole_app;
