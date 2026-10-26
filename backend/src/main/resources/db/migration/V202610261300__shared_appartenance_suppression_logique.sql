-- Les appartenances ne sont plus supprimées physiquement : un retrait renseigne supprime_le,
-- ce qui garde l'historique des droits. L'unicité ne porte plus que sur les rôles actifs,
-- pour permettre de redonner un rôle retiré.
ALTER TABLE appartenance DROP CONSTRAINT uq_appartenance;
CREATE UNIQUE INDEX uq_appartenance_active ON appartenance (tenant_id, utilisateur_id, role)
  WHERE supprime_le IS NULL;

REVOKE DELETE ON appartenance FROM ecole_app;
