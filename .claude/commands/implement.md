---
description: Implement a specified requirement until its executable spec is green, then open an implementation PR.
argument-hint: <REQ-ID e.g. REQ-ORD-014>
---

Requirement: $ARGUMENTS

1. Confirm `$ARGUMENTS` is listed in `delivery/pending-requirements.txt` and that its feature file
   exists under `specs/features/`. If not, stop: it needs a spec PR first.
2. Create branch `impl/$ARGUMENTS-<slug>`.
3. Use the `implementer` subagent to make `ONLY='@$ARGUMENTS' scripts/acceptance.sh` pass, following
   its loop and constraints.
4. Run `/verify`.
5. Use the `spec-conformance-reviewer` subagent on the diff. Address any request-changes findings
   (in `src/**` only) and re-run `/verify`.
6. Commit and open the PR with `gh pr create --template implementation.md`, including the
   reviewer verdict and real command output.
