#!/usr/bin/env bash
# Applique à un Keycloak de dev DÉJÀ DÉMARRÉ (l'import du realm ne se rejoue pas) ce que
# realm-ecole.json prévoit pour une installation neuve :
#   - le thème de connexion « ecole » pour le realm (pages d'erreur sans client connu comprises)
#     et pour les clients backoffice, parent et vie-scolaire ;
#   - le serveur d'e-mails de développement (Mailpit : boîte de réception sur http://localhost:8025).
# Idempotent. DÉVELOPPEMENT LOCAL SEULEMENT : refuse un Keycloak qui n'est pas local.
# Prérequis : infra démarrée (Keycloak sur :8180, Mailpit), curl et jq.
set -euo pipefail

KC=${KC:-http://localhost:8180}
REALM=${REALM:-ecole}
ADMIN=${KEYCLOAK_ADMIN:-admin}
ADMIN_MDP=${KEYCLOAK_ADMIN_PASSWORD:-admin-dev}
THEME=${THEME:-ecole}
SMTP_HOTE=${SMTP_HOTE:-mailpit}
SMTP_PORT=${SMTP_PORT:-1025}

# Hôte strictement local (« http://localhost:8180@autre-hote » enverrait les identifiants ailleurs).
if ! [[ "$KC" =~ ^http://(localhost|127[.]0[.]0[.]1)(:[0-9]+)?(/|$) ]]; then
  if [ "${FORCER_DEV:-}" != "1" ]; then
    echo "Refus : KC=$KC n'est pas un Keycloak local (FORCER_DEV=1 pour passer outre)." >&2
    exit 2
  fi
fi

JETON=$(jq -rn --arg u "$ADMIN" --arg p "$ADMIN_MDP" \
  '"grant_type=password&client_id=admin-cli&username=\($u|@uri)&password=\($p|@uri)"' |
  curl -fsS "$KC/realms/master/protocol/openid-connect/token" -d @- | jq -r .access_token)

api() { curl -fsS -H "Authorization: Bearer $JETON" "$@"; }

for client in backoffice parent vie-scolaire; do
  id=$(api "$KC/admin/realms/$REALM/clients?clientId=$client" | jq -r '.[0].id // empty')
  if [ -z "$id" ]; then
    echo "absent   $client (ignoré)"
    continue
  fi
  representation=$(api "$KC/admin/realms/$REALM/clients/$id")
  if [ "$(jq -r '.attributes.login_theme // ""' <<<"$representation")" = "$THEME" ]; then
    echo "déjà     thème $THEME sur $client"
    continue
  fi
  jq --arg t "$THEME" '.attributes.login_theme = $t' <<<"$representation" |
    api -X PUT -H 'Content-Type: application/json' -d @- "$KC/admin/realms/$REALM/clients/$id"
  echo "appliqué thème $THEME sur $client"
done

realm=$(api "$KC/admin/realms/$REALM")
if [ "$(jq -r '.smtpServer.host // ""' <<<"$realm")" = "$SMTP_HOTE" ] &&
  [ "$(jq -r '.loginTheme // ""' <<<"$realm")" = "$THEME" ]; then
  echo "déjà     thème et serveur d'e-mails du realm ($THEME, $SMTP_HOTE)"
else
  jq --arg h "$SMTP_HOTE" --arg p "$SMTP_PORT" --arg t "$THEME" \
    '.loginTheme = $t | .smtpServer = {host:$h, port:$p, from:"ne-pas-repondre@ecole.local", fromDisplayName:"Plateforme de gestion scolaire", auth:"false", ssl:"false", starttls:"false"}' <<<"$realm" |
    api -X PUT -H 'Content-Type: application/json' -d @- "$KC/admin/realms/$REALM"
  echo "appliqué thème $THEME et serveur d'e-mails ($SMTP_HOTE:$SMTP_PORT) sur le realm"
fi
