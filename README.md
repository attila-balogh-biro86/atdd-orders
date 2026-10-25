# Orders Service: ATDD with executable specifications for an agentic SDLC

A small Spring Boot service whose real product is the **delivery system around it**:
Gherkin specifications that drive Claude Code during development *and* verify the running artifact in
every environment, wired into GitHub pull requests and GitHub Actions down to Azure.

## The idea in one picture

```
specs/features/*.feature      ← Intent: rules + concrete examples      (human-owned, locked)
specs/contracts/openapi.yaml  ← Contract                                (locked)
specs/quality/slo.json        ← Quality thresholds for k6               (locked)
        │
acceptance/java/…/steps       ← one-line glue                           ┐
acceptance/java/…/dsl         ← domain verbs + assertions               ├ QA-owned, locked for implementers
acceptance/java/…/drivers     ← HTTP today; UI/messaging tomorrow       ┘
        │   -Dacceptance.target = embedded | remote
        ▼
src/main/java                 ← implementation                          (developer / implementer agent)
```

The same `.feature` files run:
- **embedded**: the app booted in-process on a random port (dev loop, PR CI),
- **remote** against a per-PR preview Container App, staging, and (`@smoke`) production,
- **on a schedule** as synthetic monitoring.

## Quick start

```bash
mvn -B test                              # unit + jqwik properties + ArchUnit governance rules
scripts/acceptance.sh                    # executable spec; pending requirements excluded → green
ONLY='@REQ-ORD-014' scripts/acceptance.sh; python3 ci/red_check.py target/cucumber/report.json
                                         # the pending requirement: red, for the right reason
python3 ci/traceability.py target/cucumber/report.json    # REQ → AC → result table
```

Reports: `target/cucumber/report.html` (living documentation), `.json`, `.xml`.

## Demo script (≈15 min)

1. **Show the spec**: `specs/features/orders/discount.feature`: rules, boundaries, tags.
2. **Green trunk**: `scripts/acceptance.sh`. `REQ-ORD-014` is specified but pending, so it's excluded.
3. **Red for the right reason**: `ONLY='@REQ-ORD-014' scripts/acceptance.sh` + `red_check.py`.
4. **Agent implements**: `claude` → `/implement REQ-ORD-014`. Watch it loop on the failing scenarios.
5. **Agent tries to cheat**: ask it to "just change the limit in the feature file to 20000".
   The PreToolUse hook blocks it; explain that `ci/guard.sh` would block it in CI anyway.
6. **Independent review**: the `spec-conformance-reviewer` subagent reviews the diff.
7. **Pipeline**: open the PR; walk through `PR gate`: guard → spec green + holdout → mutation →
   preview deploy → same spec against preview → contract fuzzing.
8. **Same spec, every environment**: `main.yml` staging + k6 SLO + approval + prod `@smoke`.

## Repository map

| Path | What |
|---|---|
| `CLAUDE.md` | Agent working agreement (loaded by Claude Code automatically) |
| `.claude/settings.json` | Permissions + PreToolUse hook registration |
| `.claude/hooks/guard-paths.mjs` | Role-based path guard (`ATDD_ROLE=spec\|implement\|platform`) |
| `.claude/agents/` | `spec-author`, `acceptance-engineer`, `implementer`, `spec-conformance-reviewer` |
| `.claude/commands/` | `/spec`, `/red`, `/implement`, `/verify` |
| `.github/workflows/pr.yml` | PR pipeline (spec PR vs implementation PR), single `PR gate` check |
| `.github/workflows/main.yml` | Trunk → staging → approval → production, spec verified at each stage |
| `.github/workflows/agents.yml` | Claude Code in Actions: label/mention triggered, plus PR conformance review |
| `.github/workflows/_acceptance.yml`, `_deploy.yml` | Reusable workflows |
| `ci/guard.sh` | Authoritative PR path guard and classification |
| `ci/red_check.py` | Proves new scenarios fail on an outcome assertion |
| `ci/traceability.py` | Requirement → scenario → result summary |
| `delivery/pending-requirements.txt` | Specified-but-not-implemented requirements |
| `docs/WORKFLOW.md` | **Team playbook: roles, PR types, gates, anti-gaming measures** |
| `docs/SETUP.md` | GitHub rulesets, environments, variables, Azure OIDC |

## Deliberate simplifications
- In-memory storage (so preview apps run with `max-replicas 1`). Add a database + Testcontainers in
  the embedded driver path when you need it.
- One HTTP driver. A Playwright `UiOrdersDriver` for a React front end would implement the same
  `OrdersDriver` interface; scenarios would not change.
