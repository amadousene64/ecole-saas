#!/usr/bin/env sh
# Démarre le backend en profil dev avec les clés de dev DU POSTE (chiffrement des données
# sensibles et HMAC des codes), lues dans un fichier hors du dépôt :
#
#   %USERPROFILE%\.ecole-saas\cles-dev.env   (Windows)
#   ~/.ecole-saas/cles-dev.env               (Linux, macOS)
#
# Le fichier est créé au premier lancement (infra/scripts/generer-cles-dev.sh), puis réutilisé :
# les données sensibles fictives restent lisibles d'un démarrage à l'autre. Il n'est jamais
# affiché ni versionné. Le perdre rend illisibles les données déjà chiffrées : régénérer alors
# les données de dev (README, « Réinitialiser les données de dev »).
#
# Usage (Git Bash) : sh infra/scripts/backend-dev.sh [arguments Maven supplémentaires]
set -eu

racine=$(cd "$(dirname "$0")/../.." && pwd)
dossier="${ECOLE_DOSSIER_CLES:-${USERPROFILE:-$HOME}/.ecole-saas}"
fichier="$dossier/cles-dev.env"

if [ ! -s "$fichier" ]; then
  mkdir -p "$dossier"
  (umask 077 && sh "$racine/infra/scripts/generer-cles-dev.sh" > "$fichier")
  echo "Clés de dev créées dans $fichier (à conserver, jamais à versionner)." >&2
fi

# shellcheck disable=SC1090
. "$fichier"
if [ -z "${ECOLE_CLE_CHIFFREMENT:-}" ] || [ -z "${ECOLE_CLE_HMAC_CODES:-}" ]; then
  echo "Clés absentes ou incomplètes dans $fichier : supprimez-le pour le régénérer." >&2
  exit 1
fi
export ECOLE_CLE_CHIFFREMENT ECOLE_CLE_HMAC_CODES

cd "$racine/backend"
exec ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev "$@"
