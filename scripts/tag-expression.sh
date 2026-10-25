#!/usr/bin/env bash
# Prints the Cucumber tag expression for the acceptance gate:
#   not @manual and not @<each pending requirement>
set -euo pipefail
cd "$(dirname "$0")/.."

expr="not @manual"
while IFS= read -r line; do
  req="$(echo "$line" | sed 's/#.*//' | tr -d '[:space:]')"
  [[ -z "$req" ]] && continue
  expr="$expr and not @$req"
done < delivery/pending-requirements.txt
echo "$expr"
