# Mise en production : liste de contrôle

À dérouler avant chaque mise en service (école pilote comprise), puis à chaque mise à jour de
l'infrastructure. Chaque ligne se vérifie, elle ne se suppose pas.

## Comptes et secrets

- [ ] **Aucun compte de dev dans Keycloak.** Le realm versionné (`infra/keycloak/realm-ecole.json`)
      n'en contient aucun (vérifié par `RealmSansComptesDeDevTest`). Les comptes fictifs et leur mot
      de passe de dev sont dans `infra/keycloak/dev/ecole-users-0.json`, monté **seulement** par
      `infra/docker-compose.yml` (dev). En production :
      - ce fichier n'est ni copié ni monté dans `/opt/keycloak/data/import` ;
      - la liste des utilisateurs du realm `ecole` ne contient aucun des noms de
        `ecole-users-0.json` (`directeur.a`, `secretariat.e2e`, `enseignant.e2e`, `parent.demo`…) ;
      - aucune identité n'utilise un identifiant `01930000-0000-7000-8000-…` (identifiants fictifs).
- [ ] **Aucun mot de passe par défaut** : ni le mot de passe de dev des comptes fictifs, ni
      `admin-dev` (administrateur Keycloak), ni `redis-dev`, ni les mots de passe PostgreSQL de
      `infra/postgres/init` ; tous fournis par l'environnement, distincts, générés.
- [ ] **Scripts de dev non exécutés** : `creer-comptes-dev.sh`, `configurer-realm-dev.sh` et
      `durcir-clients-dev.sh` refusent un Keycloak non local ; ne jamais les lancer avec
      `FORCER_DEV=1` contre la production.
- [ ] **Profil `dev` désactivé** côté backend : ni `DonneesDev`, ni `DonneesDevInscription`, ni la
      migration répétable `db/dev/R__dev_donnees_fictives.sql` (écoles et appartenances fictives).
- [ ] Client Keycloak `dev-cli` absent ou désactivé ; `clients-autorises` limité à `backoffice`,
      `parent`, `vie-scolaire`.
- [ ] Clés de chiffrement et HMAC propres à la production, hors dépôt, sauvegardées (les perdre rend
      les données sensibles illisibles) ; jamais celles d'un `cles-dev.env`.

## Données

- [ ] Aucune école fictive (`LYC-A`, `ECO-B`, `E2E`) ni donnée de test dans la base.
- [ ] Sauvegardes chiffrées et testées (restauration réelle), durées de conservation actives
      (purges quotidiennes : brouillons d'inscription, puis absences après clôture des bulletins).

## Suivi

Compléter cette liste à chaque PR qui ajoute un compte, un secret ou une donnée de démonstration.
