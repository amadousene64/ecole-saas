---
name: nouveau-module
description: Crée un nouveau module métier complet (backend Spring Modulith + migration Flyway + tests + lib Angular feature). À utiliser quand on démarre un module ou une grande fonctionnalité, par ex. « /nouveau-module viescolaire billets d'entrée ».
---

# Créer un module métier

Arguments : `$ARGUMENTS` (nom du module, puis description courte).

Suis ces étapes dans l'ordre, en t'arrêtant pour montrer le plan après l'étape 1.

1. **Plan** : liste les entités, cas d'usage, endpoints (méthode, chemin, rôle), événements
   émis/consommés et écrans Angular. Vérifie dans `docs/conception/` et `CLAUDE.md` que
   rien ne contredit la conception. Attends validation si le périmètre est ambigu.
2. **Migration** : utilise la skill `/nouvelle-migration` pour les tables.
3. **Backend** sous `backend/src/main/java/sn/ecole/<module>/` :
   - `package-info.java` avec `@ApplicationModule(displayName = "...")`
   - `domain/` : entités (étendent `TenantAwareEntity`), objets valeur, événements, règles
   - `application/` : un service par cas d'usage, `@Transactional`, `@PreAuthorize`
   - `api/` : contrôleur REST `/api/v1/...`, DTO `record`, mapper MapStruct, `ProblemDetail`
   - `infrastructure/` : repositories Spring Data, clients externes derrière une interface
4. **Tests** (écris-les avant ou avec le code) :
   - unitaires du domaine (règles métier, cas limites)
   - intégration Testcontainers de chaque endpoint (succès, 400, 403, 404, 409)
   - **isolement tenant** : un utilisateur de l'école A ne lit ni ne modifie l'école B
   - **périmètre** : un enseignant hors de la classe reçoit 403
5. **Frontend** : `npx nx g @nx/angular:library feature-<module> --directory=libs/feature-<module>`
   puis composants standalone, client API régénéré depuis OpenAPI, formulaires typés.
6. **Vérification** : `./mvnw verify`, `npx nx affected -t lint test`.
7. **Revue** : lance les agents `revue-architecture` puis `revue-securite`, corrige le bloquant.
8. Résume : fichiers créés, endpoints, ce qui reste à faire.
