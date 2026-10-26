#!/usr/bin/env bash
# Vérification de bout en bout (dev) : Keycloak -> jeton -> API -> appartenance en base.
# Prérequis : infra démarrée, backend lancé (cd backend && ./mvnw spring-boot:run), curl et jq.
set -euo pipefail

KC=${KC:-http://localhost:8180/realms/ecole}
API=${API:-http://localhost:8080}
MDP=${MDP:-Passer-2026-dev}
ECOLE_A=01930000-0000-7000-8000-0000000000a1
ECOLE_B=01930000-0000-7000-8000-0000000000b2
echecs=0
REPONSE=$(mktemp)
trap 'rm -f "$REPONSE"' EXIT

jeton() {
  curl -fsS "$KC/protocol/openid-connect/token" \
    -d grant_type=password -d client_id=dev-cli -d scope=openid \
    --data-urlencode "username=$1" --data-urlencode "password=$MDP" | jq -r .access_token
}

# verifier <libellé> <statut attendu> <filtre jq attendu ou -> <jeton|-> <école|->
verifier() {
  local libelle=$1 attendu=$2 filtre=$3 tok=$4 ecole=$5 args=(-s -o "$REPONSE" -w '%{http_code}')
  [ "$tok" != "-" ] && args+=(-H "Authorization: Bearer $tok")
  [ "$ecole" != "-" ] && args+=(-H "X-Tenant-Id: $ecole")
  local statut; statut=$(curl "${args[@]}" "$API/api/v1/moi")
  local ok=1
  [ "$statut" = "$attendu" ] || ok=0
  if [ "$filtre" != "-" ] && ! jq -e "$filtre" "$REPONSE" >/dev/null 2>&1; then ok=0; fi
  if [ $ok = 1 ]; then echo "OK    $libelle ($statut)"; else echo "ÉCHEC $libelle : $statut $(cat "$REPONSE")"; echecs=$((echecs+1)); fi
}

echo "Jetons Keycloak…"
T_MULTI=$(jeton enseignant.parent)
T_DIR=$(jeton directeur.a)
echo "Revendications du jeton (aucun rôle métier attendu) :"
echo "$T_MULTI" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | jq -c '{sub, aud, iss, azp, realm_access}' || true

verifier "sans jeton -> 401"                          401 -                                    -          $ECOLE_A
verifier "enseignant.parent dans A -> ENSEIGNANT"     200 '.roles == ["ENSEIGNANT"]'           "$T_MULTI" $ECOLE_A
verifier "enseignant.parent dans B -> PARENT seul"    200 '.roles == ["PARENT"]'               "$T_MULTI" $ECOLE_B
verifier "directeur.a dans A -> DIRECTEUR"            200 '.roles == ["DIRECTEUR"]'            "$T_DIR"   $ECOLE_A
verifier "directeur.a dans B -> 403"                  403 '.code == "TENANT_NON_AUTORISE"'     "$T_DIR"   $ECOLE_B
verifier "sans école -> 400"                          400 '.code == "TENANT_ABSENT"'           "$T_DIR"   -
verifier "jeton altéré -> 401"                        401 -                                    "${T_DIR}x" $ECOLE_A

[ $echecs = 0 ] && echo "Connexion de bout en bout : OK" || { echo "$echecs vérification(s) en échec"; exit 1; }
