-- Module inscription : brouillon d'inscription (assistant en 5 étapes, enregistré à chaque étape).
-- Le contenu saisi (identité de l'élève, responsables, scolarité) est un document JSON CHIFFRÉ
-- (AES-GCM, clé hors base, contexte lié à l'école et au brouillon) : la base n'en voit rien.
-- À l'abandon ou à la finalisation, le contenu est effacé (NULL) et la ligne marquée supprimée.

CREATE TABLE brouillon_inscription (
  id           UUID          PRIMARY KEY,
  tenant_id    UUID          NOT NULL REFERENCES tenant(id),
  annee_id     UUID          NOT NULL,
  auteur_id    UUID          NOT NULL,
  -- Dernière étape atteinte (1 à 5) : l'assistant y reprend.
  etape        SMALLINT      NOT NULL DEFAULT 1,
  contenu      BYTEA,
  cree_le      TIMESTAMPTZ   NOT NULL DEFAULT now(),
  cree_par     UUID,
  modifie_le   TIMESTAMPTZ,
  modifie_par  UUID,
  supprime_le  TIMESTAMPTZ,
  version      INT           NOT NULL DEFAULT 0,
  CONSTRAINT uq_brouillon_inscription_id UNIQUE (tenant_id, id),
  CONSTRAINT fk_brouillon_inscription_annee FOREIGN KEY (tenant_id, annee_id)
    REFERENCES annee_scolaire (tenant_id, id),
  CONSTRAINT ck_brouillon_inscription_etape CHECK (etape BETWEEN 1 AND 5),
  -- Un brouillon actif a toujours un contenu ; un brouillon supprimé n'en a plus.
  CONSTRAINT ck_brouillon_inscription_contenu
    CHECK ((supprime_le IS NULL) = (contenu IS NOT NULL))
);
CREATE INDEX ix_brouillon_inscription_tenant
  ON brouillon_inscription (tenant_id, modifie_le DESC) WHERE supprime_le IS NULL;

ALTER TABLE brouillon_inscription ENABLE ROW LEVEL SECURITY;
ALTER TABLE brouillon_inscription FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON brouillon_inscription
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE ON brouillon_inscription TO ecole_app;

-- Pièces déposées pendant l'assistant : rattachées au brouillon, puis à l'élève créé à la
-- finalisation. Exactement un des deux rattachements.
ALTER TABLE piece_dossier ALTER COLUMN eleve_id DROP NOT NULL;
ALTER TABLE piece_dossier ADD COLUMN brouillon_id UUID;
ALTER TABLE piece_dossier ADD CONSTRAINT fk_piece_dossier_brouillon
  FOREIGN KEY (tenant_id, brouillon_id) REFERENCES brouillon_inscription (tenant_id, id);
ALTER TABLE piece_dossier ADD CONSTRAINT ck_piece_dossier_rattachement
  CHECK ((eleve_id IS NULL) <> (brouillon_id IS NULL));
CREATE INDEX ix_piece_dossier_brouillon ON piece_dossier (tenant_id, brouillon_id)
  WHERE supprime_le IS NULL AND brouillon_id IS NOT NULL;
