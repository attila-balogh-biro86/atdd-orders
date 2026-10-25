# Orders Service: agent working agreement

This repo uses **Acceptance Test-Driven Development with executable specifications**.
The specification is the source of truth for *what* to build and the gate that verifies *that it was built*.
Read this file fully before changing anything.

## Layout and ownership

| Path | Layer | Owner | Implementer may edit? |
|---|---|---|---|
| `specs/features/**/*.feature` | Intent: Gherkin rules + examples | Product + architects (CODEOWNERS) | **No** |
| `specs/contracts/openapi.yaml` | Contract | Architects | **No** |
| `specs/quality/slo.json` | Quality thresholds (k6) | Architects | **No** |
| `acceptance/java/**` | Steps → DSL → drivers, governance rules | QA / test engineers | **No** |
| `delivery/pending-requirements.txt` | Specified-but-not-implemented list | Spec PR adds, impl PR removes | Remove lines only |
| `src/main/**`, `src/test/**` | Implementation + unit/property tests | Developers / implementer agent | Yes |
| `.github/**`, `.claude/**`, `CLAUDE.md`, `ci/**`, `scripts/**` | Delivery governance | Platform team | **No** |

A PreToolUse hook (`.claude/hooks/guard-paths.mjs`) enforces this locally based on `ATDD_ROLE`
(`implement` by default, `spec` for spec work). CI (`ci/guard.sh`) enforces it again on every PR;
the hook is a convenience, CI is the authority.

## Two kinds of pull request, never mixed

1. **Spec PR** (`ATDD_ROLE=spec claude`): feature files, contract, DSL/driver/step code, and the
   new requirement ID added to `delivery/pending-requirements.txt`. CI proves the new scenarios are
   **red for the right reason** (a `Then` assertion fails; no undefined steps, no crashes).
2. **Implementation PR** (default role): production code + unit tests, and removal of the requirement ID
   from the pending list. CI proves the scenarios are now **green**, runs mutation testing and the
   holdout scenarios, then deploys an ephemeral environment and re-runs the spec against it.

## Commands

```bash
mvn -B test                                  # unit, property (jqwik) and architecture (ArchUnit) tests
scripts/acceptance.sh                        # executable spec, embedded app, pending reqs excluded
ONLY='@REQ-ORD-014' scripts/acceptance.sh    # one requirement (use for red/green loops)
python3 ci/red_check.py target/cucumber/report.json   # "failing for the right reason?"
scripts/acceptance.sh remote https://<env>   # same spec against a deployed environment
mvn -B test-compile org.pitest:pitest-maven:mutationCoverage   # mutation testing (threshold 80%)
ci/guard.sh origin/main                      # the CI path guard, locally
```

## Rules for the implementer

- Start by running the failing scenarios for the requirement. Their output is your task description.
- Implement the **rule** stated in the `Rule:` line, not the example values. Hardcoding example inputs or
  outputs, branching on specific amounts, or special-casing test data is a defect; holdout scenarios
  you cannot see will catch it.
- Never edit, skip, retag or delete scenarios. Never add entries to `delivery/pending-requirements.txt`.
  If a scenario looks wrong, stop and explain why in the PR description; that's a spec PR for a human.
- Keep the domain (`com.example.orders.domain`) free of Spring/Jakarta (ArchUnit enforces it).
- Add unit/property tests for the domain logic you write; PIT must stay ≥ 80%.
- You are done only when `mvn -B test`, `scripts/acceptance.sh` and the mutation run all pass locally.
  Report the actual command output; do not summarise from memory.

## Rules for spec work

- Write rules + concrete examples, especially boundaries (100.00 vs 100.01) and rounding.
- Reuse existing step phrases; add a new step only when the domain language genuinely needs it.
- Steps are one-line delegations to `OrdersDsl`; assertions live in the DSL; I/O lives in drivers.
- Scenarios must not mention HTTP, JSON, URLs, CSS selectors or class names.
- Tag every feature `@REQ-<ID>` and every scenario `@AC-<ID>-<n>`. Tag read-only-safe journeys `@smoke`.
- After writing, run `ONLY='@REQ-…' scripts/acceptance.sh; python3 ci/red_check.py …` and include the
  red evidence in the PR.

## Domain notes

- Amounts are EUR, 2 decimals, `BigDecimal`, rounding HALF_UP.
- Errors are RFC 7807 problem details (`detail` carries the human-readable reason).
