#!/usr/bin/env bash
# Installe les plugins Claude Code du projet (scope projet = partagés avec l'équipe).
# Prérequis : Claude Code installé, et les serveurs de langage ci-dessous dans le PATH.
set -e

command -v jdtls >/dev/null || echo "⚠ jdtls absent : installez Eclipse JDT Language Server (ex. brew install jdtls)."
command -v typescript-language-server >/dev/null || echo "⚠ typescript-language-server absent : npm install -g typescript-language-server typescript"
command -v jq >/dev/null || echo "⚠ jq absent : nécessaire aux hooks (apt install jq / brew install jq)."

claude plugin marketplace add anthropics/claude-plugins-official 2>/dev/null || true

for p in jdtls-lsp typescript-lsp security-guidance pr-review-toolkit commit-commands github; do
  claude plugin install "$p@claude-plugins-official" --scope project
done

chmod +x .claude/hooks/*.sh
echo "Terminé. Lancez 'claude' à la racine du projet."
