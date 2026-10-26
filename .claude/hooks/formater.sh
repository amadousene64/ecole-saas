#!/usr/bin/env bash
# PostToolUse (Edit|Write) : formate le fichier modifié.

if ! command -v jq >/dev/null 2>&1; then
  echo "jq est absent : le hook de formatage ne peut pas s'exécuter. Installez jq (winget install jqlang.jq)." >&2
  exit 2
fi

INPUT=$(cat)
FILE_PATH=$(printf '%s' "$INPUT" | jq -r '.tool_input.file_path // empty')
{ [ -z "$FILE_PATH" ] || [ ! -f "$FILE_PATH" ]; } && exit 0
NORM=${FILE_PATH//\\//}

case "$NORM" in
  */frontend/*.ts|*/frontend/*.html|*/frontend/*.scss|*/frontend/*.json)
    (cd "$CLAUDE_PROJECT_DIR/frontend" && npx --no-install prettier --write "$FILE_PATH" >/dev/null 2>&1) ;;
  */backend/*.java)
    # google-java-format si installé ; sinon Spotless s'appliquera au build.
    if command -v google-java-format >/dev/null 2>&1; then
      google-java-format --replace "$FILE_PATH"
    fi ;;
esac
exit 0
