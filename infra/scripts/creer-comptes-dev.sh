#!/usr/bin/env bash
# Crée dans le realm de dev les comptes fictifs ajoutés après l'import initial du realm
# (comptable.a, surveillant.a, parent.demo), par l'API d'administration de Keycloak : sans
# réimporter le realm ni effacer les volumes. Idempotent. Mêmes comptes dans realm-ecole.json pour
# une installation neuve ; mêmes identifiants que les appartenances de
# backend/src/main/resources/db/dev/R__dev_donnees_fictives.sql.
#
# La création simple d'un utilisateur (POST /users) ignore l'identifiant fourni : on passe par
# l'import partiel (POST /partialImport), qui le respecte. Un compte de même nom créé avec un autre
# identifiant (essai précédent) est supprimé puis recréé : ces noms sont réservés au dev.
#
# DÉVELOPPEMENT LOCAL SEULEMENT : le script supprime des comptes par leur nom et en crée avec un mot
# de passe connu. Il refuse un Keycloak qui n'est pas local.
# Prérequis : infra démarrée (Keycloak sur :8180), curl et jq.
set -euo pipefail

KC=${KC:-http://localhost:8180}
REALM=${REALM:-ecole}
ADMIN=${KEYCLOAK_ADMIN:-admin}
ADMIN_MDP=${KEYCLOAK_ADMIN_PASSWORD:-admin-dev}
MDP=${MDP:-Passer-2026-dev}

# Hôte strictement local (« http://localhost:8180@autre-hote » enverrait les identifiants ailleurs).
if ! [[ "$KC" =~ ^http://(localhost|127[.]0[.]0[.]1)(:[0-9]+)?(/|$) ]]; then
  if [ "${FORCER_DEV:-}" != "1" ]; then
    echo "Refus : KC=$KC n'est pas un Keycloak local (FORCER_DEV=1 pour passer outre)." >&2
    exit 2
  fi
fi

# Le mot de passe administrateur passe par l'entrée standard de curl, jamais en argument (une
# ligne de commande est visible dans la liste des processus).
JETON=$(jq -rn --arg u "$ADMIN" --arg p "$ADMIN_MDP" \
  '"grant_type=password&client_id=admin-cli&username=\($u|@uri)&password=\($p|@uri)"' |
  curl -fsS "$KC/realms/master/protocol/openid-connect/token" -d @- | jq -r .access_token)

api() { curl -fsS -H "Authorization: Bearer $JETON" "$@"; }

# creer <id> <nom d'utilisateur> <prénom> <nom>
creer() {
  local id=$1 utilisateur=$2 prenom=$3 nom=$4 existant
  existant=$(api "$KC/admin/realms/$REALM/users?username=$utilisateur&exact=true" | jq -r '.[0].id // empty')
  if [ "$existant" = "$id" ]; then
    echo "déjà là  $utilisateur ($id)"
    return 0
  fi
  if [ -n "$existant" ]; then
    api -o /dev/null -X DELETE "$KC/admin/realms/$REALM/users/$existant"
    echo "supprimé $utilisateur (mauvais identifiant $existant)"
  fi
  jq -n --arg id "$id" --arg u "$utilisateur" --arg p "$prenom" --arg n "$nom" --arg m "$MDP" \
    '{ifResourceExists:"SKIP",
      users:[{id:$id, username:$u, email:($u+"@exemple.test"), firstName:$p, lastName:$n,
              enabled:true, emailVerified:true,
              credentials:[{type:"password", value:$m, temporary:false}]}]}' |
    api -o /dev/null -X POST -H 'Content-Type: application/json' -d @- \
      "$KC/admin/realms/$REALM/partialImport"
  echo "créé     $utilisateur ($id)"
}

creer 01930000-0000-7000-8000-000000000111 comptable.a Comptable "Fictif A"
creer 01930000-0000-7000-8000-000000000112 surveillant.a Surveillant "Fictif A"
creer 01930000-0000-7000-8000-000000000113 parent.demo Parent "Démo Fictif"
