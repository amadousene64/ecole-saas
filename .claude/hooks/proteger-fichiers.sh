#!/usr/bin/env bash
# PreToolUse (Edit|Write) : bloque l'écriture dans les fichiers sensibles
# et la modification d'une migration Flyway déjà existante.
INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')
[ -z "$FILE_PATH" ] && exit 0

for pattern in ".env" "secrets/" ".git/" "id_rsa" ".pem" ".p12" ".jks"; do
  if [[ "$FILE_PATH" == *"$pattern"* ]]; then
    echo "Bloqué : $FILE_PATH est un fichier sensible ($pattern)." >&2
    exit 2
  fi
done

# Une migration Flyway déjà présente ne doit jamais être modifiée.
if [[ "$FILE_PATH" == *"/db/migration/V"*".sql" && -f "$FILE_PATH" ]]; then
  echo "Bloqué : $FILE_PATH est une migration existante. Crée une nouvelle migration (skill /nouvelle-migration)." >&2
  exit 2
fi
exit 0
