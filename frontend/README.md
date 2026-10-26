# Frontend

Nx 24 + Angular 22 (composants autonomes, zoneless, Signals), Angular Material, Vitest, Playwright.
Compatible Windows (Git Bash et PowerShell) : toutes les commandes passent par `npm`/`npx nx`, les
scripts d'outillage sont en Node (`tools/`).

| Projet                | Rôle                                                            | Port | Client Keycloak |
| --------------------- | --------------------------------------------------------------- | ---- | --------------- |
| `apps/backoffice`     | direction, secrétariat, enseignants, comptabilité               | 4200 | `backoffice`    |
| `apps/parent`         | espace parent, PWA, **budget 500 Ko** au premier chargement     | 4201 | `parent`        |
| `apps/vie-scolaire`   | vie scolaire (surveillants, enseignants, direction)             | 4202 | `vie-scolaire`  |
| `libs/auth`           | OIDC (code + PKCE), session, intercepteur, gardes de route      |      |                 |
| `libs/api-client`     | client d'API généré depuis `docs/api/openapi.json`              |      |                 |
| `libs/ui`             | thème, coquilles, sélecteur d'école, pages d'état (en français) |      |                 |
| `apps/backoffice-e2e` | tests Playwright (Keycloak et API réels)                        |      |                 |

Règles de dépendances (vérifiées par ESLint, `@nx/enforce-module-boundaries`) : les apps utilisent
`auth`, `api-client` et `ui` ; `auth` utilise `api-client` et `ui` ; `ui` et `api-client` ne
dépendent de rien.

## Commandes

```bash
npm install
npx nx serve backoffice            # idem parent, vie-scolaire ; /api est relayé vers localhost:8080
npx nx run-many -t test lint build # tout vérifier
npx nx test auth                   # un projet
npx nx e2e backoffice-e2e          # Playwright (Chromium)
npm run api:generate               # régénère le client depuis le contrat OpenAPI versionné
npm run api:update                 # relance aussi le backend pour régénérer le contrat (Docker requis)
```

L'e2e suppose `docker compose -f infra/docker-compose.yml up -d` et le backend en profil dev
(`./mvnw spring-boot:run` dans `backend/`) sur le port 8080. Le mot de passe des comptes fictifs
vient de `E2E_MOT_DE_PASSE` (par défaut, celui du realm de dev).

## Authentification et école

- Connexion OIDC avec PKCE vers le realm `ecole` (`apps/*/src/app/environnement.ts` : à remplacer
  au déploiement). Aucun secret côté navigateur.
- **Les rôles ne sont pas dans le jeton.** Après la connexion, `GET /api/v1/moi/ecoles` donne les
  écoles de l'utilisateur ; s'il en a plusieurs, il en choisit une, puis `GET /api/v1/moi` donne
  ses rôles dans cette école. Le garde `gardeRoles('DIRECTEUR', …)` s'appuie sur ce profil : c'est
  du confort d'interface, le serveur contrôle chaque appel.
- L'intercepteur ajoute `Authorization` et `X-Tenant-Id` aux seuls appels vers `/api` de la même
  origine. En production, l'API est servie sous le même domaine (Nginx) : pas de CORS ; en dev,
  `proxy.conf.json` relaie `/api`.
- L'école choisie est gardée dans `sessionStorage` (jamais le jeton).

## Budget de l'espace parent

`apps/parent` : erreur de build au-delà de 500 Ko bruts au premier chargement (alerte à 450 Ko),
voir `project.json`. Il utilise `CoquilleApplicationLegere` (aucun composant Material dans le premier
chargement) ; les composants Material des pages de session sont chargés à la demande. La coquille
Material complète (sidenav, menu, overlay) coûte environ 160 Ko et ne tient pas dans ce budget.
Pour mesurer : `npx nx build parent` puis le tableau « Initial total ».

## PWA

`apps/parent` : `ngsw-config.json`, `public/manifest.webmanifest`, icônes de substitution dans
`public/icons/` (à remplacer par la charte). Le service worker n'est actif qu'en production
(`npx nx build parent`) et ne met en cache ni `/api` ni les réponses d'authentification.
