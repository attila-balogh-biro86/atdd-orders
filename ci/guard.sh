#!/usr/bin/env bash
# Authoritative PR path guard. Classifies the PR and enforces the spec/implementation split.
#
#   ci/guard.sh <base-ref>            e.g. origin/main
#   env PR_LABELS="label1,label2"     (set by the workflow; 'platform-change' unlocks governance paths)
#
# Writes kind=spec|impl|platform|docs and added=/removed= requirement IDs to $GITHUB_OUTPUT if set.
set -euo pipefail
BASE="${1:-origin/main}"
LABELS=",${PR_LABELS:-},"
fail() { echo "::error::$*"; exit 1; }
out()  { [[ -n "${GITHUB_OUTPUT:-}" ]] && echo "$1" >> "$GITHUB_OUTPUT"; echo "  $1"; }

mapfile -t CHANGED < <(git diff --name-only "$BASE"...HEAD)
echo "Changed files vs $BASE:"; printf '  %s\n' "${CHANGED[@]:-<none>}"

has() { local re="$1"; for f in "${CHANGED[@]}"; do [[ "$f" =~ $re ]] && return 0; done; return 1; }

SPEC=false; IMPL=false; GOV=false
has '^(specs|acceptance)/'                               && SPEC=true
has '^src/'                                              && IMPL=true
has '^(\.github|\.claude|ci|scripts)/|^CLAUDE\.md$|^pom\.xml$' && GOV=true

# Requirement IDs added to / removed from the pending list
PENDING=delivery/pending-requirements.txt
ADDED=$(git diff "$BASE"...HEAD -- "$PENDING" | grep -E '^\+[A-Z]' | sed 's/^+//' | tr -d '[:space:]' | paste -sd, - || true)
REMOVED=$(git diff "$BASE"...HEAD -- "$PENDING" | grep -E '^-[A-Z]' | sed 's/^-//' | tr -d '[:space:]' | paste -sd, - || true)

# ---- Rules ---------------------------------------------------------------------------------
$SPEC && $IMPL && fail "This PR changes both the specification (specs/, acceptance/) and the implementation (src/). Split it into a spec PR and an implementation PR."

if $GOV && [[ "$LABELS" != *",platform-change,"* ]]; then
  fail "Governance files changed (.github/, .claude/, ci/, scripts/, CLAUDE.md, pom.xml). These need the 'platform-change' label and platform-team CODEOWNER approval."
fi

if [[ -n "$ADDED" ]] && ! $SPEC; then
  fail "Requirement IDs were added to $PENDING ($ADDED) outside a spec PR. Adding to the pending list is how a failing spec gets silently skipped, so only spec PRs may do it."
fi

if [[ -n "$REMOVED" ]] && $SPEC; then
  fail "A spec PR removed IDs from $PENDING ($REMOVED). Requirements leave the pending list only via an implementation PR that makes them green."
fi

if [[ -n "$ADDED" ]]; then
  IFS=, read -ra IDS <<< "$ADDED"
  for id in "${IDS[@]}"; do
    grep -rqE "@$id(\s|$)" specs/features || fail "Pending requirement $id has no feature file tagged @$id."
  done
fi

if $SPEC; then KIND=spec; elif $IMPL; then KIND=impl; elif $GOV; then KIND=platform; else KIND=docs; fi
echo "PR classification:"
out "kind=$KIND"
out "added=$ADDED"
out "removed=$REMOVED"
