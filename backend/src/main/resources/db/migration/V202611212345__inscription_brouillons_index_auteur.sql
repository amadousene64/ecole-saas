-- Plafond de brouillons en cours par auteur : comptage indexé (école, auteur), brouillons ouverts.
CREATE INDEX ix_brouillon_inscription_auteur ON brouillon_inscription (tenant_id, auteur_id)
  WHERE supprime_le IS NULL;
