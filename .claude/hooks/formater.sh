#!/usr/bin/env bash
# PostToolUse (Edit|Write) : formate le fichier modifié.
INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')
[ -z "$FILE_PATH" ] || [ ! -f "$FILE_PATH" ] && exit 0

case "$FILE_PATH" in
  */frontend/*.ts|*/frontend/*.html|*/frontend/*.scss|*/frontend/*.json)
    (cd "$CLAUDE_PROJECT_DIR/frontend" && npx --no-install prettier --write "$FILE_PATH" >/dev/null 2>&1) ;;
  */backend/*.java)
    # google-java-format si installé ; sinon Spotless s'appliquera au build.
    command -v google-java-format >/dev/null && google-java-format --replace "$FILE_PATH" ;;
esac
exit 0
