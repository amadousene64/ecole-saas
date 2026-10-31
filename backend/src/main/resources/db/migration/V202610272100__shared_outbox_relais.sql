-- Relais de l'outbox : nouvelles tentatives espacées après un échec.
ALTER TABLE outbox_event ADD COLUMN prochaine_tentative_le TIMESTAMPTZ NOT NULL DEFAULT now();

-- Index du relais : événements non traités d'une école, par date de prochaine tentative.
DROP INDEX ix_outbox_event_tenant_a_traiter;
CREATE INDEX ix_outbox_event_tenant_a_traiter ON outbox_event (tenant_id, prochaine_tentative_le)
  WHERE traite_le IS NULL;
