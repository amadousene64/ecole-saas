-- Journal d'audit : qui (acteur_id), quand (cree_le), quoi (action, cible, détails), où (tenant_id).
-- Ajout seul : l'application peut écrire et lire, jamais modifier ni supprimer.
CREATE TABLE journal_audit (
  id           UUID         PRIMARY KEY,
  tenant_id    UUID         NOT NULL REFERENCES tenant(id),
  acteur_id    UUID         NOT NULL,
  action       VARCHAR(60)  NOT NULL,
  type_cible   VARCHAR(60)  NOT NULL,
  id_cible     UUID         NOT NULL,
  details      JSONB        NOT NULL DEFAULT '{}',
  cree_le      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  cree_par     UUID,
  modifie_le   TIMESTAMPTZ,
  modifie_par  UUID,
  supprime_le  TIMESTAMPTZ,
  version      INT          NOT NULL DEFAULT 0
);
CREATE INDEX ix_journal_audit_tenant_cible ON journal_audit (tenant_id, type_cible, id_cible, cree_le);

ALTER TABLE journal_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE journal_audit FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON journal_audit
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

GRANT SELECT, INSERT ON journal_audit TO ecole_app;
