-- Référentiel des écoles de la plateforme. Pas de tenant_id ni de RLS : c'est le tenant lui-même.
-- L'application (ecole_app) ne peut que le lire ; la création d'écoles relève du super-administrateur.
GRANT USAGE ON SCHEMA ecole TO ecole_app;

CREATE TABLE tenant (
  id           UUID         PRIMARY KEY,
  code         VARCHAR(40)  NOT NULL,
  nom          VARCHAR(200) NOT NULL,
  sous_domaine VARCHAR(63)  NOT NULL,
  actif        BOOLEAN      NOT NULL DEFAULT TRUE,
  cree_le      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  cree_par     UUID,
  modifie_le   TIMESTAMPTZ,
  modifie_par  UUID,
  supprime_le  TIMESTAMPTZ,
  version      INT          NOT NULL DEFAULT 0,
  CONSTRAINT uq_tenant_code UNIQUE (code),
  CONSTRAINT uq_tenant_sous_domaine UNIQUE (sous_domaine),
  CONSTRAINT ck_tenant_sous_domaine CHECK (sous_domaine ~ '^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$')
);

GRANT SELECT ON tenant TO ecole_app;
