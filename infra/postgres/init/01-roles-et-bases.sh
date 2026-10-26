#!/usr/bin/env bash
# Exécuté une seule fois, à la création du volume PostgreSQL (dev local).
# - ecole_owner : propriétaire du schéma, applique les migrations Flyway.
# - ecole_app   : rôle de l'application, NON propriétaire => soumis à la RLS.
# - keycloak    : base séparée pour Keycloak.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -v owner_pw="$ECOLE_OWNER_PASSWORD" -v app_pw="$ECOLE_APP_PASSWORD" -v kc_pw="$KEYCLOAK_DB_PASSWORD" <<'SQL'
CREATE ROLE ecole_owner LOGIN PASSWORD :'owner_pw' NOSUPERUSER NOBYPASSRLS NOCREATEROLE;
CREATE ROLE ecole_app   LOGIN PASSWORD :'app_pw'   NOSUPERUSER NOBYPASSRLS NOCREATEROLE NOCREATEDB;
CREATE ROLE keycloak    LOGIN PASSWORD :'kc_pw'    NOSUPERUSER NOCREATEROLE;
CREATE DATABASE ecole    OWNER ecole_owner;
CREATE DATABASE keycloak OWNER keycloak;
SQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname ecole <<'SQL'
REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON DATABASE ecole FROM PUBLIC;
GRANT CONNECT ON DATABASE ecole TO ecole_app;
CREATE SCHEMA ecole AUTHORIZATION ecole_owner;
GRANT USAGE ON SCHEMA ecole TO ecole_app;
SQL
