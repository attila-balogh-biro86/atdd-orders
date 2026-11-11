# Developer onboarding: shipping a feature in this repo

Welcome. This guide takes you from a fresh laptop to a feature running in production. It follows one
real example, **REQ-ORD-015: order cancellation**, through every step.

Read it once end to end. After that, the [cheat sheet](#cheat-sheet) at the bottom is all you need.
For the reasoning behind the process (roles, gates, why things are locked), see [WORKFLOW.md](WORKFLOW.md).

---

## 1. The mental model

Hold on to these five ideas. Everything else follows from them.

1. **The specification is the source of truth.** `specs/features/*.feature` says what the system
   does, as rules plus concrete examples. If it's not in a scenario, it's not a requirement yet.
2. **The spec is executable.** The same scenarios run on your laptop, on a preview deployment of your
   pull request, on staging and in production. "Done" means they pass, not that someone says so.
3. **Every feature is two pull requests.** First a *spec PR* that adds failing scenarios, then an
   *implementation PR* that makes them pass. CI rejects a PR that changes both.
4. **You have a role per session.** In a spec session you may edit `specs/` and `acceptance/` but not
   `src/main/`. In an implementation session it's the reverse. Claude Code enforces this locally; CI
   enforces it again.
5. **Agents do the volume work, people make the decisions.** Claude Code drafts specs, writes test glue
   and implements code. A product owner decides the rules, a developer reviews the code, and a release
   approver decides when to ship.

```
issue ──► spec PR (red) ──► merge ──► impl PR (green) ──► merge ──► staging ──► approval ──► production
          PO + QA review               dev review                    spec + SLO              @smoke checks
```

---

## 2. Day one: set up your machine

### Install
| Tool | Version | Why |
|---|---|---|
| JDK | 21 | Build and run the service |
| Maven | 3.9+ | Build, unit tests, acceptance suite |
| Node.js | 18+ | Claude Code and its guard hook |
| Python | 3.10+ | CI helper scripts (`ci/*.py`) |
| GitHub CLI `gh` | latest | Issues and PRs from the terminal |
| Claude Code | latest | Your pair: `npm install -g @anthropic-ai/claude-code` |

### Clone and prove everything works
```bash
gh repo clone your-org/orders-service && cd orders-service
mvn -B test              # unit, property and architecture tests
scripts/acceptance.sh    # the executable spec against an in-process app
```

Both should finish green. Open `target/cucumber/report.html` in a browser. That report is the living
documentation of what the system does today.

Now see the one requirement that is specified but not built yet:
```bash
ONLY='@REQ-ORD-014' scripts/acceptance.sh        # fails, as expected
python3 ci/red_check.py target/cucumber/report.json
```
You'll see `Red for the right reason.` That's what a correctly specified, not-yet-implemented
requirement looks like.

### Get to know Claude Code in this repo
Start `claude` in the repo root. It loads these automatically:

| File | What it gives you |
|---|---|
| `CLAUDE.md` | The rules every agent session follows. Read it yourself too, it's short. |
| `.claude/settings.json` | Allowed commands and the path-guard hook |
| `.claude/agents/` | Four subagents: `spec-author`, `acceptance-engineer`, `implementer`, `spec-conformance-reviewer` |
| `.claude/commands/` | `/spec`, `/red`, `/implement`, `/verify` |

Try this to see the guard rail in action:
```
claude
> change the limit in specs/features/orders/order-limit.feature to 20000
```
The edit is refused with `[atdd-guard] Role 'implement' may not modify specs/...`. That's intended:
the default role is `implement`.

### Access you need
Ask your lead for: write access to the repo, membership of `@your-org/developers`, and an
`ANTHROPIC_API_KEY` or Claude subscription for Claude Code. You don't need Azure access. The pipeline
deploys for you.

---

## 3. Shipping a feature, step by step

Our example: the product owner wants customers to be able to cancel orders.

### Step 1: Pick up the requirement issue

The PO opens an issue from the **Requirement** template:

> **REQ-ORD-015: Order cancellation**
> As a customer I want to cancel an order I placed so that I'm not charged for something I no longer want.
> Rules: a placed order can be cancelled; a cancelled order can't be cancelled again.
> Open questions: can a customer cancel someone else's order?

Assign it to yourself. Don't write code yet.

### Step 2: Agree the examples (example mapping)

This is the most valuable 20 minutes of the feature. Get the PO and a QA engineer together, or do it
with Claude:

```bash
git switch main && git pull
ATDD_ROLE=spec claude
> /spec 57          # 57 = the issue number
```

The `spec-author` agent reads the issue and shows you **rules, examples and open questions** before it
writes any files. It stops and waits for answers when a question would change behaviour. For our
example it might ask:

- What happens when cancelling an order ID that doesn't exist? → *Agreed: "unknown order".*
- Can a customer cancel someone else's order? → *PO: out of scope for now, raise a separate requirement.*
- Does a cancelled order still show its amounts? → *Yes, unchanged.*

Record each answer as an example, not as prose. If the PO isn't available, post the questions on the
issue and add the `needs-clarification` label. Don't guess business rules.

### Step 3: Write the spec

The agent writes the feature file. Review it as if you were the PO:

```gherkin
@REQ-ORD-015 @api
Feature: Order cancellation
  Customers can cancel orders they no longer want.

  Rule: A placed order can be cancelled, and keeps its amounts

    @AC-ORD-015-1 @smoke
    Scenario: Cancel a placed order
      Given a loyalty customer
      And they have placed an order totalling 200.00 EUR
      When they cancel the order
      Then the order is cancelled
      And the payable amount is 180.00 EUR

  Rule: A cancelled order cannot be cancelled again

    @AC-ORD-015-2
    Scenario: Cancel twice
      Given a standard customer
      And they have placed an order totalling 50.00 EUR
      And they have cancelled the order
      When they cancel the order
      Then the cancellation is rejected because "already cancelled"

  Rule: Only existing orders can be cancelled

    @AC-ORD-015-3
    Scenario: Unknown order
      Given a standard customer
      When they cancel an order that does not exist
      Then the cancellation is rejected because the order is unknown
```

What good looks like:
- Every rule is one sentence in a `Rule:` line, with at least one example under it.
- Values are concrete (`200.00`, not "a large order").
- No HTTP, JSON, URLs or class names. A PO can read it.
- The feature has a `@REQ-` tag and every scenario has an `@AC-` tag.
- `@smoke` only goes on a journey that is safe to run against production. Here it creates and cancels
  its own order, so it's safe.

The agent also:
- adds `POST /api/orders/{id}/cancel` and a `status` field to `specs/contracts/openapi.yaml`,
- adds `REQ-ORD-015` to `delivery/pending-requirements.txt`, so `main` stays green until the feature exists.

### Step 4: Make the spec executable

The `acceptance-engineer` agent now adds the missing test code, in four thin layers:

| Layer | File | What gets added |
|---|---|---|
| Steps | `acceptance/.../steps/OrderSteps.java` | `@When("they cancel the order")` → one call: `orders.cancelLastOrder()` |
| DSL | `acceptance/.../dsl/OrdersDsl.java` | `cancelLastOrder()`, `assertCancelled()`, `assertCancellationRejectedBecause(..)` |
| Driver interface | `acceptance/.../drivers/OrdersDriver.java` | `CancelOutcome cancelOrder(UUID orderId)` |
| HTTP driver | `acceptance/.../drivers/HttpOrdersDriver.java` | `POST /api/orders/{id}/cancel`, mapping 200/404/409 to domain outcomes |

Rules the architecture tests enforce: steps contain no logic, assertions live in the DSL, and nothing in
`acceptance/` imports production classes.

One design choice matters for the next step: `And they have cancelled the order` is setup, so its DSL
method calls the driver **without asserting**. If it asserted, the scenario would fail in a `Given` step
before the feature exists, and nobody could tell whether the expectation is right.

### Step 5: Prove it's red for the right reason

```bash
mvn -B test                                   # architecture rules still pass
ONLY='@REQ-ORD-015' scripts/acceptance.sh      # fails, as it should
python3 ci/red_check.py target/cucumber/report.json
```

Or just `/red REQ-ORD-015` in Claude Code. You want every scenario to fail **in a `Then` step on an
assertion**. Typical results:

| red_check says | Meaning | Fix |
|---|---|---|
| `has undefined steps` | A step phrase has no glue | Add the step (Step 4) |
| `fails in a Given/When step` | Setup or the action threw before any expectation was checked | Drivers must turn every response into a domain outcome instead of throwing, and setup steps that use the new behaviour must not assert. The `Then` step carries the check. |
| `fails with a crash, not an assertion` | NPE, connection refused, … | Fix the test code |
| `PASSES already` | Existing code already does this | Confirm it pins an intended boundary, or the scenario tests nothing new |
| `Red for the right reason.` | Ready | Open the PR |

> **Watch for false greens.** In our example, `AC-ORD-015-3` (unknown order) may pass before any code
> exists: the endpoint is missing, Spring answers 404, and a naive driver reads every 404 as
> "unknown order". Make the driver check the problem `title` ("Order not found"), not just the status
> code, and the scenario turns red as it should.

### Step 6: Open the spec PR

```bash
git switch -c spec/REQ-ORD-015-order-cancellation
git add specs acceptance delivery
git commit -m "spec: REQ-ORD-015 order cancellation"
gh pr create --template spec.md --label spec-change
```
(The `/spec` command does this for you.) Paste the `red_check` output into the PR description.

**What CI checks** (single required check: `PR gate`):
- the PR only touches spec paths and adds `REQ-ORD-015` to the pending list,
- the OpenAPI contract lints,
- unit and architecture tests pass,
- the new scenarios are red for the right reason,
- every already-implemented scenario is still green.

**Who reviews:** the PO (right rules, right examples?), an architect (contract), and QA
(step language). QA also adds **holdout examples** to the private holdout repo, for instance cancelling
a 10000.00 EUR order. You won't see those, and neither will the implementing agent. That's the point.

Merge once approved. `REQ-ORD-015` is now *specified but pending* on `main`.

### Step 7: Implement it

Start a fresh session in the default `implement` role:

```bash
git switch main && git pull
claude
> /implement REQ-ORD-015
```

The `implementer` agent works in a loop:
1. runs `ONLY='@REQ-ORD-015' scripts/acceptance.sh` and reads the failures,
2. implements the **rules**: an `OrderStatus` enum, `Order.cancel()` that refuses a second cancel,
   `OrderService.cancelOrder(id)`, the controller endpoint, and 404/409 problem details,
3. adds unit and property tests, for example "cancelling never changes `total` or `payable`",
4. repeats until green, then removes `REQ-ORD-015` from the pending list,
5. runs `/verify` (guard, unit, full spec, traceability, mutation testing),
6. asks the `spec-conformance-reviewer` agent to review the diff in a fresh context.

**Your job while it works:** watch the loop, and stop it if it goes off track. Things to look for:
- logic that branches on example values (`if (total.equals("50.00"))`),
- endpoints or behaviour that no scenario asked for,
- a request to edit a scenario. It can't: the hook blocks it. If it says a scenario looks wrong, read
  section 4.

You can also implement by hand. The process is the same: same role, same commands, same gates.

### Step 8: Open the implementation PR

```bash
git switch -c impl/REQ-ORD-015-order-cancellation
git add src delivery
git commit -m "feat: REQ-ORD-015 order cancellation"
gh pr create --template implementation.md
```

**What CI checks:**
- the PR only touches `src/` and only *removes* `REQ-ORD-015` from the pending list,
- unit, property and architecture tests pass,
- the full spec is green on an in-process app, **including the holdout examples**,
- mutation score on the domain is at least 80%,
- the image deploys to a preview environment, `orders-pr-<n>`,
- the same scenarios pass against that preview over the network,
- the running service matches the OpenAPI contract under fuzzing.

An agent also posts a **conformance review** comment on the PR.

**Who reviews:** another developer. Behaviour is already proven by the spec, so the review focuses on
design, readability and maintainability. Read the conformance review comment first.

If CI is red, read the step summary on the failed run. The traceability table shows exactly which
requirement and scenario failed, and on which environment.

### Step 9: After merge

Merging to `main` starts the release pipeline. You don't need to do anything, but you own the feature
until it's live:

1. Unit tests and the full spec (with holdout) run on the merge commit.
2. The image is built **once** and deployed to staging. The full spec runs against staging, plus a k6
   load test with thresholds from `specs/quality/slo.json`.
3. A release approver approves the `production` deployment in GitHub.
4. The **same image** goes to production, and the `@smoke` scenarios run against it.
   `AC-ORD-015-1` is tagged `@smoke`, so cancellation is checked in production straight away.
5. `synthetic.yml` keeps running `@smoke` against production every 30 minutes.

Close the issue when the production smoke run is green.

---

## 4. Other situations

| Situation | What you do |
|---|---|
| **A scenario looks wrong** while implementing | Stop. Don't work around it. Comment on the issue or PR explaining why. If the PO agrees, it becomes a spec PR. Implementers never edit specs, by design. |
| **Bug report** | First a spec PR with a scenario that reproduces the bug (red, ID re-added to pending, or a new `@AC` under the existing requirement), then an implementation PR. The bug can never come back silently. |
| **Change an existing rule** (e.g. discount from 10% to 15%) | Spec PR edits the examples and re-adds the requirement ID to the pending list; CI proves the changed scenarios are red. Then an implementation PR. |
| **Refactoring, no behaviour change** | Implementation PR only. The full spec staying green is your safety net. Don't touch the pending list. |
| **Clarifying a scenario without changing behaviour** | Spec PR without a pending-list entry. The normal green gate applies. |
| **Pipeline, hook or `CLAUDE.md` change** | `platform/<topic>` branch, `platform-change` label, platform team review. Run Claude with `ATDD_ROLE=platform`. |
| **Urgent production fix** | Still two PRs, back to back. A one-scenario spec PR can be reviewed in minutes. There's no bypass. |
| **The hook blocks something legitimate** | Check you're in the right role (`echo $ATDD_ROLE`). If the rule itself is wrong, raise it with the platform team. Don't edit the hook. |
| **CI says "mixed PR"** | Split it: move the spec changes to a `spec/` branch, merge that first, then rebase the implementation. |

---

## 5. Definition of done

**Spec PR**
- [ ] Rules agreed with the PO; open questions answered or split into separate requirements
- [ ] Feature tagged `@REQ-…`, scenarios tagged `@AC-…`, `@smoke` only where safe in production
- [ ] Contract updated if the API changes
- [ ] Requirement ID added to `delivery/pending-requirements.txt`
- [ ] `red_check` passes and its output is in the PR
- [ ] QA has added holdout examples

**Implementation PR**
- [ ] `/verify` passes locally, with real output pasted in the PR
- [ ] Requirement ID removed from the pending list
- [ ] Unit or property tests cover the rule, not just the examples
- [ ] Conformance review read and addressed
- [ ] No changes outside `src/` (and the pending-list removal)

**Feature**
- [ ] Production `@smoke` run is green; issue closed

---

## Cheat sheet

```bash
# Sessions
ATDD_ROLE=spec claude          # writing specs and test glue
claude                         # implementing (default role)

# Inside Claude Code
/spec <issue>                  # example mapping → feature files → red → spec PR
/red REQ-ORD-015               # prove scenarios fail for the right reason
/implement REQ-ORD-015         # implement until green → review → impl PR
/verify                        # every gate CI runs, locally

# By hand
mvn -B test                                         # unit + property + architecture
scripts/acceptance.sh                               # full spec, pending requirements excluded
ONLY='@REQ-ORD-015' scripts/acceptance.sh           # one requirement only
TAGS='@smoke' scripts/acceptance.sh remote <url>    # smoke scenarios against a deployed env
python3 ci/red_check.py target/cucumber/report.json
python3 ci/traceability.py target/cucumber/report.json
ci/guard.sh origin/main                             # what CI's path guard will say
mvn -B test-compile org.pitest:pitest-maven:mutationCoverage

# Branches
spec/REQ-ORD-015-<slug>     impl/REQ-ORD-015-<slug>     platform/<topic>
```

| Where | What |
|---|---|
| `target/cucumber/report.html` | Living documentation and last spec run |
| `target/pit-reports/` | Mutation testing report |
| PR → Checks → `PR gate` | The only verdict that counts |
| Run summary → "Executable specification results" | Requirement → scenario → result, per environment |
