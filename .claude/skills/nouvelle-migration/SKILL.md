---
name: nouvelle-migration
description: Écrit une migration Flyway PostgreSQL conforme (tenant_id, RLS, index, audit, version). À utiliser pour toute création ou modification de table.
---

# Nouvelle migration Flyway

Arguments : `$ARGUMENTS` (module et description).

1. Nom du fichier : `backend/src/main/resources/db/migration/V<AAAAMMJJHHMM>__<module>_<description>.sql`
   (horodatage courant, minuscules, underscores). **Ne modifie jamais une migration existante.**
2. Chaque table métier suit ce gabarit :

```sql
CREATE TABLE <table> (
  id           UUID PRIMARY KEY,
  tenant_id    UUID        NOT NULL REFERENCES tenant(id),
  -- colonnes métier ...
  cree_le      TIMESTAMPTZ NOT NULL DEFAULT now(),
  cree_par     UUID,
  modifie_le   TIMESTAMPTZ,
  modifie_par  UUID,
  supprime_le  TIMESTAMPTZ,
  version      INT         NOT NULL DEFAULT 0
);
CREATE INDEX ix_<table>_tenant ON <table> (tenant_id /* , colonnes de recherche */);
ALTER TABLE <table> ENABLE ROW LEVEL SECURITY;
ALTER TABLE <table> FORCE ROW LEVEL SECURITY;
CREATE POLICY isolement_ecole ON <table>
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
GRANT SELECT, INSERT, UPDATE ON <table> TO ecole_app;  -- jamais DELETE sur les données scolaires
```

   Pourquoi `NULLIF(current_setting('app.tenant_id', true), '')` : hors transaction (ou après
   un `set_config(..., true)` terminé) la variable vaut `''` ou n'existe pas ; `''::uuid`
   lèverait une erreur. Avec `NULLIF`, la comparaison vaut `NULL` : **aucune ligne visible,
   aucune insertion possible**, sans erreur. Ne jamais écrire `current_setting('app.tenant_id')::uuid`.

   Les migrations tournent avec le rôle propriétaire `ecole_owner` ; l'application se connecte
   avec `ecole_app`, qui n'est pas propriétaire et reste donc soumis à la RLS. Toute nouvelle
   table doit donner ses droits à `ecole_app` (le `GRANT` ci-dessus).

3. Types : montants `BIGINT` (FCFA), notes `NUMERIC(5,2)` pour les valeurs figées et
   `NUMERIC(12,6)` pour les valeurs exactes, dates `DATE`, horodatages `TIMESTAMPTZ`,
   énumérations `VARCHAR` + `CHECK`.
4. Contraintes d'unicité toujours préfixées par `tenant_id`.
5. Changement destructif (suppression ou renommage de colonne) : en deux migrations
   (ajout + bascule, puis suppression dans une version ultérieure).
6. Vérifie avec `./mvnw -q test -Dtest=MigrationsTest` (Testcontainers applique toutes les migrations).
