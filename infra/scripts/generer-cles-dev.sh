#!/usr/bin/env sh
# Génère les deux clés applicatives (chiffrement des données sensibles, HMAC des codes
# d'activation) pour le poste de développement. À charger dans le shell qui lance le backend :
#   eval "$(infra/scripts/generer-cles-dev.sh)"
# Ne jamais versionner ces valeurs ; en production elles viennent du gestionnaire de secrets.
# Attention : changer la clé de chiffrement rend illisibles les données sensibles déjà saisies.
set -eu
printf 'export ECOLE_CLE_CHIFFREMENT=%s\n' "$(openssl rand -base64 32)"
printf 'export ECOLE_CLE_HMAC_CODES=%s\n' "$(openssl rand -base64 32)"
