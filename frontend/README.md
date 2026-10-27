# Frontend

Nx 24 + Angular 22 (composants autonomes, zoneless, Signals), Angular Material, Vitest, Playwright.
Compatible Windows (Git Bash et PowerShell) : toutes les commandes passent par `npm`/`npx nx`, les
scripts d'outillage sont en Node (`tools/`).

| Projet              | Rôle                                                             | Port | Client Keycloak |
| ------------------- | ---------------------------------------------------------------- | ---- | --------------- |
| `apps/backoffice`   | direction, censeur, secrétariat, enseignants, comptabilité       | 4200 | `backoffice`    |
| `apps/parent`       | espace parent et élève, PWA, **budget 500 Ko** au 1er chargement | 4201 | `parent`        |
| `apps/vie-scolaire` | vie scolaire (direction, censeur, enseignants, surveillants)     | 4202 | `vie-scolaire`  |
| `libs/auth`         | OIDC (code + PKCE), session, intercepteur, gardes de route       |      |                 |
| `libs/api-client`   | client d'API généré depuis `docs/api/openapi.json`               |      |                 |
| `libs/ui`           | thème, coquilles, sélecteur d'école, pages d'état (en français)  |      |                 |
| `libs/feature-etablissement` | écrans du module établissement : profil et images, structure pédagogique, années et périodes, classes et salles |      |                 |
| `apps/e2e`          | 17 scénarios Playwright (Keycloak et API réels) : backoffice 10, parent 3, vie-scolaire 4 |      |                 |

Règles de dépendances (vérifiées par ESLint, `@nx/enforce-module-boundaries`) : les apps utilisent
`auth`, `api-client`, `ui` et les libs `feature-*` ; une `feature-*` (écrans d'un module métier) utilise
`auth`, `api-client` et `ui`, jamais une autre feature ; `auth` utilise `api-client` et `ui` ; `ui` et `api-client` ne
dépendent de rien.

## Commandes

```bash
npm install
npx nx serve backoffice            # idem parent, vie-scolaire ; /api est relayé vers localhost:8080
npx nx run-many -t test lint build # tout vérifier
npx nx test auth                   # un projet
npx nx e2e e2e                    # Playwright (Chromium), les trois apps
npm run api:generate               # régénère le client depuis le contrat OpenAPI versionné
npm run api:update                 # relance aussi le backend pour régénérer le contrat (Docker requis)
```

L'e2e suppose `docker compose -f infra/docker-compose.yml up -d` et le backend en profil dev
(`./mvnw spring-boot:run` dans `backend/`) sur le port 8080. Le mot de passe des comptes fictifs
vient de `E2E_MOT_DE_PASSE` (par défaut, celui du realm de dev).

Un projet Playwright par app (`testMatch: <app>.spec.ts` et `<app>-<sujet>.spec.ts`) : 17 scénarios
s'exécutent (backoffice 10 dont 5 du module établissement, parent 3, vie-scolaire 4). `connexion.spec.ts`
(4 `test`) n'est rattaché à aucun projet et n'est donc jamais lancé : ses parcours sont repris par les
specs des apps. En CI, l'e2e tourne chaque lundi et à la demande (`.github/workflows/e2e.yml`).

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

## Rôles autorisés par application

| Application  | Rôles (dans l'école choisie)                           |
| ------------ | ------------------------------------------------------ |
| backoffice   | DIRECTEUR, CENSEUR, SECRETARIAT, ENSEIGNANT, COMPTABLE |
| vie-scolaire | DIRECTEUR, CENSEUR, ENSEIGNANT, SURVEILLANT            |
| parent       | PARENT, ELEVE                                          |

Le super-administrateur de la plateforme n'appartient à aucune de ces applications.

## Formats

`FcfaPipe` (`| fcfa`, libs/ui) affiche les montants en FCFA sans décimale ; les dates et nombres
utilisent la locale `fr` (`registerLocaleData` dans `app.config.ts`). Le build de production a été
vérifié dans un navigateur pour les trois apps (« lundi 26 octobre 2026 », « 1 500 000 FCFA »).

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
