#!/usr/bin/env bash
# Désactive « Full scope allowed » sur les clients du realm de dev (backoffice, parent,
# vie-scolaire, dev-cli) d'un Keycloak déjà démarré, par l'API d'administration : sans réimporter
# le realm ni effacer les volumes. Idempotent. Même réglage dans infra/keycloak/realm-ecole.json
# (`fullScopeAllowed: false`) pour une installation neuve.
#
# Pourquoi : avec « Full scope allowed », le jeton d'accès reçoit TOUS les rôles de l'utilisateur
# (realm et autres clients), même ceux dont l'application n'a pas besoin. Désactivé, un jeton ne
# porte que les rôles explicitement mappés au client (aucun pour l'instant : l'API lit les rôles
# dans sa table d'appartenances, jamais dans le jeton). L'audience `ecole-api` vient du mapper
# d'audience du client, pas des rôles : elle est conservée.
#
# Pour la production, appliquer le même réglage à chaque client de la plateforme.
# Prérequis : infra démarrée (Keycloak sur :8180), curl et jq.
set -euo pipefail

KC=${KC:-http://localhost:8180}
REALM=${REALM:-ecole}
ADMIN=${KEYCLOAK_ADMIN:-admin}
ADMIN_MDP=${KEYCLOAK_ADMIN_PASSWORD:-admin-dev}
CLIENTS=${CLIENTS:-"backoffice parent vie-scolaire dev-cli"}

# Hôte strictement local (« http://localhost:8180@autre-hote » enverrait les identifiants ailleurs).
if ! [[ "$KC" =~ ^http://(localhost|127[.]0[.]0[.]1)(:[0-9]+)?(/|$) ]]; then
  if [ "${FORCER_DEV:-}" != "1" ]; then
    echo "Refus : KC=$KC n'est pas un Keycloak local (FORCER_DEV=1 pour passer outre)." >&2
    exit 2
  fi
fi

# Le mot de passe administrateur passe par l'entrée standard de curl, jamais en argument.
JETON=$(jq -rn --arg u "$ADMIN" --arg p "$ADMIN_MDP" \
  '"grant_type=password&client_id=admin-cli&username=\($u|@uri)&password=\($p|@uri)"' |
  curl -fsS "$KC/realms/master/protocol/openid-connect/token" -d @- | jq -r .access_token)

api() { curl -fsS -H "Authorization: Bearer $JETON" "$@"; }

for client in $CLIENTS; do
  id=$(api "$KC/admin/realms/$REALM/clients?clientId=$client" | jq -r '.[0].id // empty')
  if [ -z "$id" ]; then
    echo "absent   $client (ignoré)"
    continue
  fi
  representation=$(api "$KC/admin/realms/$REALM/clients/$id")
  if [ "$(jq -r .fullScopeAllowed <<<"$representation")" = "false" ]; then
    echo "déjà     $client"
    continue
  fi
  jq '.fullScopeAllowed = false' <<<"$representation" |
    api -X PUT -H 'Content-Type: application/json' -d @- "$KC/admin/realms/$REALM/clients/$id"
  echo "durci    $client"
done
