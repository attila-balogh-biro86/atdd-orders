# Delivery workflow: from requirement to production

This is the team playbook. It describes who does what, which PRs exist, what each automated
gate proves, and where Claude Code fits in. The short version:

> **The executable specification is written first, locked, proven red, and then used as the
> acceptance gate in every environment the artifact passes through.** Agents may write the spec or the
> implementation, never both in the same change, and never grade their own work.

## 1. The lifecycle at a glance

```mermaid
flowchart LR
  I[Requirement issue] -->|label agent:spec or /spec| S[Spec PR]
  S -->|CI: guard, contract lint,<br/>RED for the right reason| SR{PO + architect<br/>+ QA review}
  SR -->|merge| M1[(main: spec locked,<br/>REQ in pending list)]
  M1 -->|label agent:implement or /implement| IP[Implementation PR]
  IP -->|CI: guard, unit/property/arch,<br/>spec GREEN + holdout,<br/>mutation, preview deploy,<br/>spec GREEN on preview, fuzzing| AR[Agent conformance review]
  AR --> HR{Developer review}
  HR -->|merge| M2[(main)]
  M2 --> ST[Staging deploy] -->|spec GREEN + k6 SLO| AP{Release approval}
  AP --> PR[Production deploy] -->|@smoke spec| MON[Synthetic monitoring:<br/>@smoke every 30 min]
```

## 2. Roles and responsibilities

| Activity | Product owner | Architect | QA / test engineer | Developer | Claude Code |
|---|---|---|---|---|---|
| Write requirement issue | **Owns** | Consulted | Consulted | | |
| Example mapping (rules, examples, questions) | **Decides** | Consulted | Facilitates | Consulted | `spec-author` drafts, asks questions |
| Feature files / contract / SLOs | Approves (CODEOWNER) | Approves (CODEOWNER) | Reviews | | Drafts |
| Steps / DSL / drivers | | Reviews | **Approves** (CODEOWNER) | | `acceptance-engineer` writes |
| Holdout examples (private repo) | | | **Writes** | | Never sees them |
| Production code + unit tests | | | | **Approves** | `implementer` writes |
| Conformance review | | | | Reads it | `spec-conformance-reviewer` (independent session) |
| Pipelines, hooks, CLAUDE.md | | **Platform team owns** | | | Locked |
| Release to production | Approves (env reviewer) | | | | |

Humans make the decisions that need judgement or accountability: what the rule is, whether the
examples are right, whether to ship. Agents do the volume work in between, inside guard rails.

## 3. Step by step

### Step 1: Requirement issue
A PO opens an issue from the **Requirement** template: ID, story, rules, any known examples.
This is a conversation starter, not a spec.

### Step 2: Spec PR (the most important review in the process)
Either label the issue `agent:spec` (Claude runs in Actions), or locally:

```bash
git switch main && git pull
ATDD_ROLE=spec claude
> /spec 42
```

The `spec-author` subagent runs example mapping and **stops to ask** when an answer changes behaviour.
In CI, where nobody can answer, it posts the questions on the issue instead and labels it
`needs-clarification`. Then the `acceptance-engineer` subagent adds any missing step/DSL/driver
code and proves the new scenarios are red.

The PR contains: `specs/**`, possibly `acceptance/**`, and the new ID in `delivery/pending-requirements.txt`.
It must not contain `src/**`; `ci/guard.sh` fails the build if it does.

**CI proves:**
- the PR is spec-only (guard),
- the contract still lints,
- architecture rules hold (steps are thin, the spec doesn't import production code),
- the new scenarios **fail on a `Then` assertion**: not undefined, not crashing in setup, not a
  connection error (`ci/red_check.py`). At least one must fail, or the spec adds no behaviour. Scenarios
  that already pass are listed so reviewers can confirm they intentionally pin a boundary (for example
  "exactly 10000.00 is still accepted").
- the already-implemented spec is still green.

**Humans review:** PO (are these the right rules and examples?), architect (contract, consistency),
QA (step language, and writes extra **holdout** examples into the private holdout repo).

Merging puts the requirement on `main` as *specified but pending*. The main pipeline excludes pending
IDs, so trunk stays green.

### Step 3: Implementation PR
Label the issue `agent:implement`, or locally:

```bash
ATDD_ROLE=implement claude      # implement is the default role
> /implement REQ-ORD-014
```

The `implementer` subagent loops on `ONLY='@REQ-ORD-014' scripts/acceptance.sh` until green, adds unit
and property tests, runs `/verify`, and removes the ID from the pending list. The PreToolUse hook blocks
any attempt to edit `specs/`, `acceptance/`, `.github/`, `.claude/`, `ci/` or `scripts/`.

Before opening the PR, the `spec-conformance-reviewer` subagent reviews the diff in a fresh context:
it looks for rule-vs-example overfitting, boundary errors and out-of-scope changes. The same review
runs again in Actions on every push to an `impl/*` branch and posts a PR comment.

**CI proves:**
- the PR is implementation-only and only *removes* pending IDs,
- unit, property and architecture tests pass,
- the full executable spec is green, **including the newly un-pended requirement and the holdout scenarios**,
- mutation score ≥ 80% on the domain (PIT): the tests actually constrain the logic,
- the image deploys to a **preview Container App**, and the **same scenarios** pass against it over the network,
- the running service conforms to the OpenAPI contract under fuzzing (Schemathesis).

**Humans review:** a developer reviews the code with the agent's conformance review alongside.
The developer isn't re-checking behaviour (the spec did that); they're checking design and maintainability.

### Step 4: Main → staging → production
Merging triggers `main.yml`:
1. unit tests and the embedded spec (with holdout) on the merge commit,
2. build the image **once**, deploy to staging, run the full spec remotely, run k6 with thresholds read
   from `specs/quality/slo.json`,
3. production deploy waits for **environment approval** (required reviewers on the `production` environment),
   promotes the exact staging image, then runs the `@smoke` scenarios against production,
4. `synthetic.yml` keeps running `@smoke` against production on a schedule; the spec doubles as monitoring.

## 4. What stops an agent (or a hurried human) from gaming the spec

| Attack | Defence |
|---|---|
| Edit or delete a failing scenario | Hook blocks it locally; `ci/guard.sh` rejects mixed PRs; CODEOWNERS on `specs/` |
| Add the requirement to the pending list to skip it | Guard: only spec PRs may *add* IDs |
| Hardcode the example values | Holdout scenarios from a private repo; property tests; conformance reviewer |
| Put logic or fake assertions in step glue | ArchUnit: steps may depend only on the DSL; glue is under `acceptance/` (locked) |
| Make the spec call production code directly | ArchUnit: `acceptance` must not depend on `domain`/`application`/`web` |
| Tests that execute code without constraining it | Mutation testing threshold |
| "Works in the test, not when deployed" | Same scenarios against preview, staging and production |
| Weaken the pipeline itself | `.github/`, `ci/`, `scripts/`, `.claude/` need the `platform-change` label + platform CODEOWNER |
| Agent claims "all tests pass" | Only the `PR gate` status counts; agents must paste real output |

## 5. Branching and naming

| Branch | Content | Opened by |
|---|---|---|
| `spec/REQ-ORD-014-order-limit` | Spec PR | `ATDD_ROLE=spec` session or `agent:spec` |
| `impl/REQ-ORD-014-order-limit` | Implementation PR | default session or `agent:implement` |
| `platform/<topic>` | Pipeline/tooling change (`platform-change` label) | Platform team |

Trunk-based: short-lived branches, squash merges into `main`, which is always deployable because
pending requirements are excluded from the gate rather than left red.

## 6. Changing a requirement that is already implemented

1. Spec PR edits the scenarios **and** re-adds the ID to the pending list (CI proves the changed
   scenarios are red against current code).
2. Implementation PR changes the code and removes the ID again.

If a change is purely a clarification (scenarios already pass), the spec PR doesn't add the ID;
`red_check` isn't triggered, and the normal green gate applies.

## 7. When the spec is wrong

Implementers (human or agent) don't fix specs. They stop and describe the problem in the PR or issue.
That produces a spec PR reviewed by the PO. This is deliberate friction: it keeps business decisions
with the people accountable for them.
