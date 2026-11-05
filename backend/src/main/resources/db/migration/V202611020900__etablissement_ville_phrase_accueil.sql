-- Marque de l'école sur les pages de connexion : la ville et une phrase d'accueil, affichées avec le
-- nom et le logo (jamais le cachet ni la signature). Colonnes facultatives ; la table porte déjà
-- tenant_id, son index et sa politique RLS.
ALTER TABLE etablissement
  ADD COLUMN ville          VARCHAR(100),
  ADD COLUMN phrase_accueil VARCHAR(200);
