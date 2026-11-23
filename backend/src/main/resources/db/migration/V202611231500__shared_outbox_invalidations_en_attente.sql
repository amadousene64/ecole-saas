-- Invalidations de droits en attente d'un utilisateur (Redis injoignable au commit) : consultées
-- à chaque lecture des rôles et du périmètre pour ignorer le cache. Index partiel, presque vide.
CREATE INDEX ix_outbox_event_tenant_invalidation_en_attente
  ON outbox_event (tenant_id, id_agregat)
  WHERE traite_le IS NULL AND type_evenement = 'DROITS_A_INVALIDER';
