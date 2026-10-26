-- Écoles d'un utilisateur, pour le sélecteur d'école (GET /api/v1/moi/ecoles) : à ce stade aucune
-- école n'est encore choisie, donc la RLS d'appartenance (par école courante) ne laisse rien voir.
-- On n'ouvre pas la table : seule cette fonction SECURITY DEFINER lit les appartenances d'UN
-- utilisateur, sans parcourir les écoles. L'identifiant est celui du `sub` du jeton, fourni par la
-- couche application ; il n'est jamais un paramètre de l'API.

-- La table est en FORCE ROW LEVEL SECURITY : le propriétaire (qui exécute la fonction) y est soumis.
-- On lui donne donc la lecture, pour lui seul ; ecole_app n'est pas concerné par cette politique.
CREATE POLICY lecture_proprietaire ON appartenance FOR SELECT TO ecole_owner USING (true);

-- Recherche par utilisateur, hors école (l'index unique existant commence par tenant_id).
CREATE INDEX ix_appartenance_utilisateur ON appartenance (utilisateur_id) WHERE supprime_le IS NULL;

CREATE FUNCTION ecoles_de_utilisateur(p_utilisateur UUID)
RETURNS TABLE (ecole_id UUID, ecole_code VARCHAR(40), ecole_nom VARCHAR(200))
LANGUAGE sql STABLE SECURITY DEFINER
-- search_path figé : une fonction SECURITY DEFINER ne doit pas dépendre de celui de l'appelant.
SET search_path = pg_catalog, pg_temp
AS $$
  SELECT DISTINCT t.id, t.code, t.nom
  FROM ecole.appartenance a
  JOIN ecole.tenant t ON t.id = a.tenant_id
  WHERE a.utilisateur_id = p_utilisateur
    AND a.supprime_le IS NULL
    AND t.actif
    AND t.supprime_le IS NULL
  ORDER BY t.nom, t.id
$$;

REVOKE ALL ON FUNCTION ecoles_de_utilisateur(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION ecoles_de_utilisateur(UUID) TO ecole_app;
