-- Conteneur de test : mêmes rôles qu'en dev (infra/postgres/init), mots de passe de test.
CREATE ROLE ecole_owner LOGIN PASSWORD 'owner-test' NOSUPERUSER NOBYPASSRLS NOCREATEROLE;
CREATE ROLE ecole_app   LOGIN PASSWORD 'app-test'   NOSUPERUSER NOBYPASSRLS NOCREATEROLE NOCREATEDB;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA ecole AUTHORIZATION ecole_owner;
