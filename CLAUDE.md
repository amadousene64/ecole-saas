# Plateforme de gestion scolaire — mémoire du projet

Logiciel SaaS multi-écoles de gestion scolaire (primaire → lycée, système sénégalais) :
inscriptions, vie scolaire, **billets d'entrée en classe**, notes, **bulletins**, finances,
espace parent. Le dossier de conception complet est dans `docs/conception/`.

## Stack

- Backend : Java 25, Spring Boot 4.x, Spring Modulith, Spring Security (resource server OIDC),
  Spring Data JPA / Hibernate, Flyway, MapStruct, PostgreSQL 17, RabbitMQ, Redis, MinIO.
- Frontend : Angular 22 (standalone components, Signals, zoneless), espace de travail Nx,
  Angular Material, PWA pour l'espace parent.
- Identité : Keycloak. Tests : JUnit 5, Testcontainers, ArchUnit/Modulith, Vitest, Playwright.

## Commandes

```bash
# Backend (dossier backend/)
./mvnw verify                      # build + tous les tests (obligatoire avant de conclure)
./mvnw test -Dtest=NomDuTest       # un seul test
./mvnw spotless:apply              # formatage Java
# Frontend (dossier frontend/)
npx nx test <projet>               # tests unitaires
npx nx lint <projet>
npx nx e2e <projet>-e2e            # Playwright
# Infra locale
docker compose -f infra/docker-compose.yml up -d   # postgres, keycloak, rabbitmq, redis, minio
```

## Règles d'architecture (non négociables)

1. **Monolithe modulaire.** Un package racine par module métier sous `sn.ecole` :
   `etablissement` (structure de l'école : profil, cycles, niveaux, séries, années, périodes,
   calendrier, **classes et salles**), `inscription`, `personnel`, `emploidutemps`, `viescolaire`,
   `evaluation`, `bulletin`, `finance`, `communication`, `statistique`, `shared`.
   Un module n'accède à un autre que par les types de son package racine (API publique)
   ou par événement de domaine. `ModularityTests` doit rester vert.
2. **Couches par module** : `api` (contrôleurs, DTO) → `application` (cas d'usage) →
   `domain` (entités, règles, événements) ← `infrastructure` (JPA, clients externes).
   `domain` ne dépend ni de Spring Web ni de JPA Repository.
3. **Multi-tenant partout.** Toute table métier a `tenant_id UUID NOT NULL`, un index commençant
   par `tenant_id`, et une politique RLS. Toute entité étend `TenantAwareEntity`.
   Jamais de requête native sans filtre tenant. Chaque nouvel endpoint a un test
   « l'école A ne voit pas les données de l'école B ».
4. **Sécurité côté serveur** : `@PreAuthorize` sur chaque méthode de service exposée +
   vérification de périmètre (`PermissionEvaluator`). Ne jamais se fier à l'interface.
5. **Jamais d'entité JPA dans une réponse d'API** : DTO + mapper MapStruct.
6. **Argent et notes** : montants en `long` (FCFA). Notes et moyennes en `BigDecimal`,
   jamais `double`. Voir `docs/bulletins/regles-bulletins.md`.
7. **Événements fiables** : effet externe (SMS, paiement, PDF) = événement écrit dans l'outbox
   dans la même transaction, traité par un worker. Endpoints de création sensibles idempotents
   (`Idempotency-Key`). Deux exceptions actées :
   - **Stockage d'objets (MinIO)** : non transactionnel, il est écrit pendant la transaction avec
     compensation (`ApresTransaction` : fichier retiré si la base est annulée, ancien fichier
     supprimé seulement après validation). Au pire un objet orphelin, à balayer par un job de purge.
   - **Créations non sensibles** (années, classes, salles, niveaux…) : pas d'`Idempotency-Key` ; le
     verrou optimiste `If-Match` / `ETag` protège les modifications. Paiements, billets et appels
     hors-ligne restent soumis à la règle.
   Les événements de domaine en process (`ApplicationEventPublisher`) ne sont pas des effets
   externes ; un consommateur qui en déclenche un passe par l'outbox.
8. **Migrations Flyway** : une migration appliquée n'est jamais modifiée ; on en crée une nouvelle.
   Nommage `V<AAAAMMJJHHMM>__<module>_<description>.sql`.
9. **Erreurs** : RFC 9457 `ProblemDetail` avec un code métier (`BILLET_EXPIRE`).

## Conventions

- Vocabulaire métier en français (`BilletEntree`, `Inscription`, `Bulletin`), termes techniques
  en anglais (`Repository`, `Controller`, `Service`).
- Clés primaires UUID v7. Colonnes d'audit + `version` (verrou optimiste) sur chaque table.
- Suppression logique pour les données scolaires ; jamais de suppression physique de notes ou paiements.
- Couverture ≥ 80 % sur `domain` et `application`.
- Commits conventionnels (`feat(billet): ...`), une branche par fonctionnalité.
- Jamais de données réelles d'élèves dans les tests ou les fixtures : données anonymisées.

## Définition de « terminé »

Une tâche n'est terminée que si : `./mvnw verify` (et/ou `nx test` + `nx lint`) passe,
les tests d'isolement tenant et de droits existent pour le nouveau code, la migration Flyway
est fournie, le contrat OpenAPI est à jour, et l'agent `revue-securite` n'a rien de bloquant.

## Où trouver quoi

- Règles des bulletins et cas de test réels : `docs/bulletins/`
- Créer un module : skill `/nouveau-module` · une migration : skill `/nouvelle-migration`
- Revues : agents `revue-architecture`, `revue-securite`, `expert-bulletins`
