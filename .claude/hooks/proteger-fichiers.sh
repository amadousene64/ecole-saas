#!/usr/bin/env bash
# PreToolUse (Edit|Write) : bloque l'écriture dans les fichiers sensibles
# et la modification d'une migration Flyway déjà existante.

# Sans jq, on ne peut pas lire le chemin : on bloque plutôt que de tout laisser passer.
if ! command -v jq >/dev/null 2>&1; then
  echo "Bloqué : jq est absent, le hook de protection ne peut pas vérifier le fichier. Installez jq (winget install jqlang.jq)." >&2
  exit 2
fi

INPUT=$(cat)
FILE_PATH=$(printf '%s' "$INPUT" | jq -r '.tool_input.file_path // .tool_input.notebook_path // empty') || {
  echo "Bloqué : entrée du hook illisible." >&2
  exit 2
}
[ -z "$FILE_PATH" ] && exit 0

# Sous Windows le chemin arrive avec des antislashs : on normalise.
NORM=${FILE_PATH//\\//}
NORM=${NORM,,} # Windows : chemins insensibles à la casse
BASE=${NORM##*/}

# Fichiers d'environnement (.env, .env.local…) ; le modèle .env.example reste autorisé.
if [[ "$BASE" == ".env" || ( "$BASE" == .env.* && "$BASE" != ".env.example" ) ]]; then
  echo "Bloqué : $FILE_PATH est un fichier d'environnement (secrets)." >&2
  exit 2
fi

for pattern in "/secrets/" "/.git/" "id_rsa" ".pem" ".p12" ".jks"; do
  if [[ "$NORM" == *"$pattern"* ]]; then
    echo "Bloqué : $FILE_PATH est un fichier sensible ($pattern)." >&2
    exit 2
  fi
done

# Une migration Flyway déjà présente ne doit jamais être modifiée.
if [[ "$NORM" == *"/db/migration/"*".sql" && -f "$FILE_PATH" ]]; then
  echo "Bloqué : $FILE_PATH est une migration existante. Crée une nouvelle migration (skill /nouvelle-migration)." >&2
  exit 2
fi
exit 0
