#!/usr/bin/env bash
# Run the executable specification.
#
#   scripts/acceptance.sh                         # embedded app, all implemented requirements
#   scripts/acceptance.sh remote https://my-env   # against a deployed environment
#   TAGS='@smoke' scripts/acceptance.sh remote https://prod   # smoke subset
#   ONLY='@REQ-ORD-014' scripts/acceptance.sh     # just one requirement (ignores the pending list)
#
# Requirements listed in delivery/pending-requirements.txt are excluded unless ONLY is set.
set -euo pipefail
cd "$(dirname "$0")/.."

TARGET="${1:-embedded}"
BASE_URL="${2:-http://localhost:8080}"
REPORT="${REPORT:-target/cucumber/report}"

if [[ -n "${ONLY:-}" ]]; then
  EXPR="$ONLY"
else
  EXPR="$(scripts/tag-expression.sh)"
  [[ -n "${TAGS:-}" ]] && EXPR="($TAGS) and ($EXPR)"
fi

echo "▶ target=$TARGET baseUrl=$BASE_URL tags: $EXPR"
exec mvn -B ${MVN_ARGS:-} verify -DskipUTs=true \
  -Dacceptance.target="$TARGET" \
  -Dacceptance.baseUrl="$BASE_URL" \
  -Dacceptance.tags="$EXPR" \
  -Dacceptance.report="$REPORT"
