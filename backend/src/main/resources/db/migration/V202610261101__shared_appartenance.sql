-- Rôles d'un utilisateur (sub Keycloak) dans une école. Source de vérité des droits :
-- aucun rôle métier n'est porté par le realm Keycloak.
CREATE TABLE appartenance (
  id             UUID        PRIMARY KEY,
  tenant_id      UUID        NOT NULL REFERENCES tenant(id),
  utilisateur_id UUID        NOT NULL,
  role           VARCHAR(20) NOT NULL,
  cree_le        TIMESTAMPTZ NOT NULL DEFAULT now(),
  cree_par       UUID,
  modifie_le     TIMESTAMPTZ,
  modifie_par    UUID,
  supprime_le    TIMESTAMPTZ,
  version        INT         NOT NULL DEFAULT 0,
  CONSTRAINT ck_appartenance_role CHECK (role IN
    ('DIRECTEUR', 'SECRETARIAT', 'SURVEILLANT', 'ENSEIGNANT', 'COMPTABLE', 'PARENT', 'ELEVE')),
  CONSTRAINT uq_appartenance UNIQUE (tenant_id, utilisateur_id, role)
);
-- L'unicité (tenant_id, utilisateur_id, role) fournit l'index de recherche par utilisateur.

ALTER TABLE appartenance ENABLE ROW LEVEL SECURITY;
ALTER TABLE appartenance FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON appartenance
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- DELETE autorisé ici uniquement : c'est une donnée de droits, pas une donnée scolaire.
GRANT SELECT, INSERT, UPDATE, DELETE ON appartenance TO ecole_app;
