# Démarrer le projet avec Claude Code

Ce kit configure Claude Code pour développer la plateforme de gestion scolaire :
mémoire du projet, agents de revue, skills de génération, hooks de protection,
plugins et serveurs MCP. Copiez son contenu à la racine de votre dépôt.

## 1. Prérequis sur votre poste

| Outil | Pourquoi |
| --- | --- |
| Claude Code | `npm install -g @anthropic-ai/claude-code` (ou l'installeur natif) |
| JDK 25 + Maven Wrapper | backend Spring Boot |
| Node.js LTS + npm | frontend Angular / Nx, serveurs MCP |
| Docker | PostgreSQL, Keycloak, RabbitMQ, Redis, MinIO en local, Testcontainers |
| `jdtls` | serveur de langage Java pour le plugin `jdtls-lsp` |
| `typescript-language-server` | serveur de langage pour le plugin `typescript-lsp` |
| `jq` | utilisé par les hooks |

## 2. Installation

```bash
git init ecole-saas && cd ecole-saas
# copiez ici le contenu du kit (CLAUDE.md, .claude/, .mcp.json, docs/, installer-plugins.sh)
chmod +x installer-plugins.sh && ./installer-plugins.sh
claude          # au premier lancement, acceptez les serveurs MCP du projet
```

Dans Claude Code, vérifiez avec `/plugin` (onglet Installed), `/mcp` et `/agents`.

## 3. Ce que contient le kit

| Élément | Rôle |
| --- | --- |
| `CLAUDE.md` | Mémoire du projet, lue à chaque session : stack, règles d'architecture, commandes, définition de « terminé » |
| `.claude/agents/revue-securite.md` | Audit isolement des écoles, droits, OWASP, paiements |
| `.claude/agents/revue-architecture.md` | Contrôle des frontières de modules et des couches |
| `.claude/agents/expert-bulletins.md` | Moteur de calcul des bulletins, appuyé sur vos bulletins réels |
| `.claude/skills/nouveau-module` | `/nouveau-module <nom> <description>` : module complet backend + tests + front |
| `.claude/skills/nouvelle-migration` | `/nouvelle-migration <module> <description>` : migration Flyway avec RLS |
| `.claude/hooks/proteger-fichiers.sh` | Bloque l'écriture dans `.env`, secrets et migrations déjà existantes |
| `.claude/hooks/formater.sh` | Formate automatiquement chaque fichier modifié |
| `.claude/settings.json` | Permissions, hooks, plugins activés pour toute l'équipe |
| `.mcp.json` | Serveurs MCP : Angular CLI, Context7, Playwright |
| `docs/bulletins/` | Règles des bulletins + cas de test chiffrés tirés de vos exemples |

## 4. Ordre de travail recommandé

Une session = une tâche. Faites `/clear` entre deux tâches. Pour les grosses tâches, passez
d'abord en mode plan (Maj+Tab) pour valider l'approche avant que Claude n'écrive du code.

1. **Socle**
   > Crée le squelette du projet décrit dans CLAUDE.md : backend Spring Boot 4 multi-module
   > Modulith avec le module `shared` (TenantAwareEntity, résolution du tenant depuis le JWT,
   > RLS via `app.tenant_id`, audit, outbox, ProblemDetail), le test ModularityTests,
   > un test MigrationsTest Testcontainers, et `infra/docker-compose.yml`
   > (postgres 17, keycloak, rabbitmq, redis, minio). Frontend : workspace Nx Angular 22
   > avec les apps backoffice, parent, vie-scolaire et la lib auth OIDC. Mode plan d'abord.
2. **Établissement et structure** : `/nouveau-module etablissement années scolaires, cycles, niveaux, séries, classes, périodes`
3. **Inscriptions** : `/nouveau-module inscription élèves, parents, liens parentaux, inscriptions annuelles, redoublants, options`
4. **Vie scolaire et billets** : `/nouveau-module viescolaire appel par séance hors-ligne, absences, retards, billets d'entrée en classe`
5. **Espace parent** : fil par enfant, notifications push puis SMS
6. **Notes et bulletins** :
   > Utilise l'agent expert-bulletins. Implémente le moteur de calcul dans le module bulletin
   > et un test paramétré qui reproduit exactement docs/bulletins/cas-de-test.json.
   > Puis le PDF du bulletin secondaire et du bulletin primaire.
7. **Finances et mobile money**, puis la version 2 (emplois du temps, communication, statistiques).

À la fin de chaque tâche : « lance revue-architecture puis revue-securite et corrige le bloquant »,
puis `/commit-commands:commit` (ou demandez une PR).

## 5. Bonnes pratiques

- **Tests d'abord pour les règles métier** : demandez à Claude d'écrire le test, de le voir
  échouer, puis d'implémenter. Les cas de `docs/bulletins/cas-de-test.json` sont la référence.
- **Travail en parallèle** : une branche par module, avec `git worktree`, et une session
  Claude Code par worktree.
- **Revue de PR automatique** : le plugin `pr-review-toolkit` en local, ou l'action GitHub
  `anthropics/claude-code-action` sur chaque pull request.
- **Ne donnez jamais de vraies données d'élèves** à Claude ni aux tests : utilisez des données anonymisées.
- **Code intelligence** : les plugins LSP fonctionnent sur votre poste ; dans les sessions
  Claude Code dans le cloud, les serveurs de langage ne sont pas démarrés.
- Quand Claude se trompe de façon récurrente, ajoutez la règle dans `CLAUDE.md`
  (ou demandez-lui « ajoute cette règle à CLAUDE.md »).
