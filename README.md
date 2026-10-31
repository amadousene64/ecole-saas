# Plateforme de gestion scolaire

SaaS multi-écoles (primaire → lycée, système sénégalais). Règles du projet : `CLAUDE.md`.
Conception : `docs/conception/`. Contrat d'API : `docs/api/openapi.json`.

## Démarrer en local

Prérequis : JDK 25, Docker, `curl` et `jq` (pour le script de vérification).

```bash
# 1. Infra : PostgreSQL 17, Keycloak, RabbitMQ, Redis, MinIO, ClamAV
docker compose -f infra/docker-compose.yml up -d --wait

# 2. Backend (profil dev : données fictives, école choisie par l'en-tête X-Tenant-Id).
#    Clés de chiffrement et HMAC : obligatoires, générées pour la session (jamais versionnées).
eval "$(infra/scripts/generer-cles-dev.sh)"
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # PowerShell : .\mvnw.cmd ...

# 3. Vérification de bout en bout (Git Bash)
./infra/scripts/verifier-connexion.sh
```

| Service | URL locale | Identifiants (dev) |
| --- | --- | --- |
| API | http://localhost:8080 (Swagger : `/swagger-ui.html`) | jeton Keycloak |
| Keycloak | http://localhost:8180 (realm `ecole`) | admin / admin-dev |
| RabbitMQ | http://localhost:15672 | ecole / rabbit-dev |
| MinIO | http://localhost:9001 | ecole / minio-dev-123 |
| ClamAV (clamd) | localhost:3310 | — |
| PostgreSQL | localhost:**55432**, base `ecole` | ecole_app / app-dev |
| Redis | localhost:**56379** | redis-dev |

Les ports 55432 et 56379 évitent les conflits avec un PostgreSQL ou un Redis déjà présents sur le poste.

Utilisateurs fictifs du realm (mot de passe `Passer-2026-dev`) :

| Utilisateur | École A (Lycée Exemple A) | École B (École Exemple B) |
| --- | --- | --- |
| `directeur.a` | DIRECTEUR | — |
| `enseignant.parent` | ENSEIGNANT | PARENT |
| `surveillant.b` | — | SURVEILLANT |
| `censeur.a` | CENSEUR | — |
| `secretariat.a` | SECRETARIAT | — |
| `eleve.b` | — | ELEVE |

L'import du realm ne se rejoue pas sur un Keycloak existant : pour récupérer de nouveaux utilisateurs,
recréez le volume (`docker compose -f infra/docker-compose.yml down -v`) ou ajoutez-les par l'import
partiel de la console d'administration.

## Commandes backend

```bash
./mvnw verify                                   # build + tous les tests (Docker requis)
./mvnw test -Dtest=NomDuTest                    # un seul test
./mvnw spotless:apply                           # formatage
./mvnw test -Dtest=ContratOpenApiTest -Dopenapi.maj=true   # mettre à jour docs/api/openapi.json
```

**Avant `./mvnw verify`, arrêtez la pile compose** : `docker compose -f infra/docker-compose.yml stop`
(les données sont conservées ; `up -d --wait` pour repartir). Les tests démarrent leurs propres
conteneurs (PostgreSQL, Redis, MinIO, ClamAV) ; avec la pile de dev en plus, la mémoire ne suffit
pas sur un poste de 16 Go et des tests échouent au hasard (connexions perdues, conteneurs tués).
Ne lancez jamais la pile compose pendant un `verify`.

De même, ne compilez pas dans `backend/` pendant qu'un `spring-boot:run` y tourne : il charge ses
classes depuis `backend/target/classes` au fil de l'eau, et un build qui les réécrit lui fait
mélanger deux versions du code (erreurs 500 incompréhensibles). Arrêtez-le d'abord, ou travaillez
dans un autre `git worktree`.

## Frontend

Espace de travail Nx 24 / Angular 22 dans `frontend/` (détails : `frontend/README.md`). Prérequis :
Node 24+, l'infra ci-dessus et le backend en profil dev.

```bash
cd frontend && npm install
npx nx serve backoffice        # http://localhost:4200 (parent : 4201, vie-scolaire : 4202)
npx nx run-many -t test lint build
npx nx e2e e2e                 # trois apps, connexion réelle via Keycloak (infra + backend démarrés)
npm run api:generate           # régénère le client d'API depuis docs/api/openapi.json
```

## Variables d'environnement

Infra (`docker compose`, valeurs de dev par défaut) : `POSTGRES_PASSWORD`, `ECOLE_OWNER_PASSWORD`,
`ECOLE_APP_PASSWORD`, `KEYCLOAK_DB_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD`, `RABBITMQ_PASSWORD`,
`REDIS_PASSWORD`, `MINIO_PASSWORD`. Elles peuvent aussi être mises dans `infra/.env`, non versionné :
`cp infra/env.example infra/.env` puis modifiez les valeurs.
Ces valeurs ne servent qu'à la création du volume PostgreSQL : après un changement,
`docker compose -f infra/docker-compose.yml down -v` (efface les données locales).

Backend (production, sans profil) : `ECOLE_DB_URL`, `ECOLE_DB_APP_USER`, `ECOLE_DB_APP_PASSWORD`,
`ECOLE_DB_OWNER_USER`, `ECOLE_DB_OWNER_PASSWORD`, `ECOLE_OIDC_ISSUER`, `ECOLE_REDIS_HOST`,
`ECOLE_REDIS_PORT`, `ECOLE_REDIS_PASSWORD`, `ECOLE_DOMAINE_RACINE`.

## Principes du socle multi-tenant

- **Keycloak = authentification seulement.** Aucun rôle métier dans le realm. Seuls les jetons
  des clients listés dans `ecole.securite.jeton.clients-autorises` (revendication `azp`) sont
  acceptés ; `dev-cli` ne l'est qu'en profil dev.
- **Rôles par école** dans la table `appartenance` (source de vérité), mis en cache dans Redis (5 min).
  Pour chaque appel `/api/**`, `FiltreTenant` vérifie que l'utilisateur est membre de l'école
  demandée et ne charge que ses rôles dans CETTE école.
- **Double barrière** : filtre Hibernate `@TenantId` + RLS PostgreSQL. L'application se connecte
  avec `ecole_app`, qui n'est pas propriétaire des tables et ne peut pas contourner la RLS.
- `app.tenant_id` est positionné au début de chaque transaction (`set_config(..., true)`) ; sans
  école, aucune ligne n'est visible. Hors requête HTTP (relais outbox, tâches planifiées), passer
  par `ExecutionPourTenant` (`executer`, `lire`, `pourChaqueEcole`).
