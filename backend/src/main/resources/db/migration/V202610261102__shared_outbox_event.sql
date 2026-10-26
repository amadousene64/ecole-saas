-- Outbox : événements à relayer (SMS, paiement, PDF…), écrits dans la transaction métier.
CREATE TABLE outbox_event (
  id              UUID         PRIMARY KEY,
  tenant_id       UUID         NOT NULL REFERENCES tenant(id),
  type_agregat    VARCHAR(100) NOT NULL,
  id_agregat      UUID         NOT NULL,
  type_evenement  VARCHAR(150) NOT NULL,
  charge          JSONB        NOT NULL,
  traite_le       TIMESTAMPTZ,
  tentatives      INT          NOT NULL DEFAULT 0,
  derniere_erreur TEXT,
  cree_le         TIMESTAMPTZ  NOT NULL DEFAULT now(),
  cree_par        UUID,
  modifie_le      TIMESTAMPTZ,
  modifie_par     UUID,
  supprime_le     TIMESTAMPTZ,
  version         INT          NOT NULL DEFAULT 0
);
-- Index du relais : événements non traités d'une école, du plus ancien au plus récent.
CREATE INDEX ix_outbox_event_tenant_a_traiter ON outbox_event (tenant_id, cree_le)
  WHERE traite_le IS NULL;

ALTER TABLE outbox_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox_event FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON outbox_event
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE ON outbox_event TO ecole_app;
