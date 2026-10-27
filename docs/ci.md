# Intégration continue

Workflows dans `.github/workflows/`. Toutes les actions sont épinglées par SHA de commit (version en
commentaire) ; Dependabot (`github-actions`) propose leur mise à jour chaque semaine.

| Workflow | Déclencheur | Contenu |
|---|---|---|
| `ci.yml` | PR et push sur `main` | jobs `backend`, `frontend`, `securite` (les trois checks requis) |
| `dependency-check.yml` | lundi 03:00 UTC, manuel, PR touchant `backend/pom.xml` si `NVD_API_KEY` existe | OWASP dependency-check, avertissement seulement |
| `e2e.yml` | lundi 02:00 UTC, manuel | infra Docker + backend dev + scénarios Playwright |

## Les trois checks de `ci.yml`

- **backend** : Temurin 25, `./mvnw -B verify` (Testcontainers). `fetch-depth: 0` pour que
  `MigrationsImmuablesTest` compare avec `origin/main`. Sur un push vers `main` la référence est le
  commit lui-même : la vraie protection joue sur les PR.
- **frontend** : `npm ci`, puis `nx affected -t lint test build` sur les PR (base = `main`) et
  `nx run-many` sur les push vers `main`. Ensuite `npm run api:generate` puis `git diff` : le client
  versionné doit être la génération du contrat. Le contrat lui-même est contrôlé côté backend
  (`ContratOpenApiTest`).
- **securite** : **gitleaks bloquant** sur tout l'historique (le secret scanning de GitHub n'est pas
  disponible sur un dépôt privé en compte gratuit). `npm audit` n'émet qu'un avertissement.

gitleaks est installé en version fixée avec vérification de l'empreinte SHA-256. Pour monter de
version : changer `GITLEAKS_VERSION` et `GITLEAKS_SHA256` dans `ci.yml` (empreinte dans le fichier
`gitleaks_<version>_checksums.txt` de la release, ligne `linux_x64.tar.gz`).

## Secrets de dépôt (facultatifs)

- `NVD_API_KEY` : accélère beaucoup dependency-check (clé gratuite sur nvd.nist.gov). Sans elle,
  l'analyse hebdomadaire fonctionne mais la première mise à jour de la base est très lente, et les
  PR sont ignorées.
- `E2E_MOT_DE_PASSE` : mot de passe des comptes fictifs ; sans lui, la valeur de dev du realm
  est utilisée.

## Protection de la branche `main`

> Dépôt privé en compte gratuit : GitHub **n'applique pas** les rulesets ni la protection de
> branche. Ces réglages deviennent effectifs avec GitHub Pro (ou dépôt public). D'ici là, la
> discipline est manuelle : jamais de push direct sur `main`.

*Settings → Rules → Rulesets → New branch ruleset* ; nom « main », application **Active**, cible
`main`.

1. **Require a pull request before merging** : 1 approbation dès qu'il y a un second relecteur
   (0 en solo) ; rejeter les approbations périmées ; résoudre les conversations.
2. **Require status checks to pass**, avec `backend`, `frontend` et `securite`.
   - Cocher « Require branches to be up to date before merging » (indispensable à
     `MigrationsImmuablesTest`).
   - Ne pas rendre obligatoires `e2e` ni `dependency-check`.
3. **Block force pushes** et **Restrict deletions**.
4. **Require linear history**.
5. Aucun bypass, ou un rôle d'urgence seulement.
6. Facultatif : commits signés ; `CODEOWNERS` sur `backend/src/main/resources/db/migration/` et
   `docs/api/`.

Dans *Settings → General → Pull Requests* : fusion « Squash » uniquement, suppression automatique des
branches fusionnées.

Dans *Settings → Actions → General* : permissions du `GITHUB_TOKEN` en lecture seule par défaut ;
approbation obligatoire pour les workflows venant de forks.

Sécurité du dépôt : activer Dependabot alerts et Dependabot security updates. Secret scanning et
push protection sont réservés aux dépôts publics ou à GitHub Secret Protection, d'où gitleaks.
