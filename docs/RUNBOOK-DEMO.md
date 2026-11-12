# Demo runbook: from requirement to production

This runbook demonstrates the whole delivery flow of this repository, end to end, with exact commands:

```
requirement issue → Claude drafts spec PR (red) → PO reviews & merges
   → Claude implements (impl PR, green) → developer reviews & merges
   → staging (spec + load test) → release approval → production (@smoke) → monitoring
```

**Duration:** about 45–60 minutes, mostly waiting on CI. **Example requirement:** REQ-ORD-016, a tiered loyalty
discount (section 2). **Roles you play:** product owner (PO), developer and release approver.

All commands run from the repository root in a terminal where `gh` and `az` are logged in.

> **zsh users (the macOS default): enable comments first.** Many commands below end with `# explanation`.
> Interactive zsh does not treat `#` as a comment by default, so the comment words are passed on as arguments. For example
> `scripts/acceptance.sh   # fails, on purpose` fails with `Unknown acceptance.target '#'`.

```bash
setopt interactivecomments 2>/dev/null || true     # zsh: treat "# ..." as a comment (no-op in bash)
cd ~/IdeaProjects/atdd-orders
REPO=attila-balogh-biro86/atdd-orders
REQ=REQ-ORD-016
```

---

## 0. Pre-flight checklist (do this before the audience arrives)

| Check | Command | Expected |
|---|---|---|
| `gh` logged in, git uses it | `gh auth status && gh auth setup-git` | `Logged in to github.com` |
| Azure logged in | `az account show --query name -o tsv` | your subscription |
| Claude API key present | `gh secret list` | `ANTHROPIC_API_KEY` |
| Azure variables present | `gh variable list` | `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID` (+ `PROD_URL` once prod exists) |
| Environments | `gh api repos/$REPO/environments --jq '.environments[].name'` | `preview`, `staging`, `production` |
| Production needs approval | `gh api repos/$REPO/environments/production --jq '[.protection_rules[].type]'` | contains `required_reviewers` |
| Labels exist | `gh label list --json name --jq '.[].name' \| grep -E 'agent:|requirement\|spec-change\|platform-change\|needs-clarification'` | all six labels |
| `main` is green | `gh run list --workflow main.yml --limit 1` | `completed success` |
| Nothing left over | `gh pr list` and `cat delivery/pending-requirements.txt` | no open PRs; only `REQ-ORD-014` pending |
| Local tools | `java -version` (21), `mvn -v`, `bash --version` (4+ for `ci/guard.sh`) | |
| zsh comments enabled | `setopt \| grep -q interactivecomments && echo ok` | `ok` |

**Known issue: the k6 load test can fail right after a deploy.** `ci/deploy-containerapp.sh` declares a deployment healthy
while the *old* revision still answers. k6 then registers its customer on the old revision, and after Azure switches
traffic every order gets `404` (one run had 62% failures). The permanent fix (`platform/deploy-wait-for-new-revision`:
wait until `/actuator/info` reports the deployed SHA) must be merged first. If it isn't, and the "SLO check (k6)" job fails
with many failed requests but normal latency, use **Re-run failed jobs** (section 7.2).

Bring the local checkout up to date:

```bash
git switch main && git pull --ff-only
```

---

## 1. Show the starting point (5 min)

```bash
mvn -B clean test                         # unit, property (jqwik) and architecture (ArchUnit) tests
scripts/acceptance.sh                     # executable spec against the app booted in-process
python3 ci/traceability.py target/cucumber/report.json    # requirement → acceptance criterion → result
open target/cucumber/report.html          # living documentation
```

Talking points:
- **The spec is the source of truth.** `specs/features/orders/*.feature` holds plain-English rules and examples, with no HTTP or JSON.
- **The same spec runs everywhere:** on this laptop, on a per-PR preview, on staging, and (`@smoke`) in production.
- **`delivery/pending-requirements.txt` keeps `main` green.** It lists requirements that are specified but not built yet. Show one:

```bash
ONLY='@REQ-ORD-014' scripts/acceptance.sh                  # fails, on purpose
python3 ci/red_check.py target/cucumber/report.json        # "Red for the right reason."
```

---

## 2. The example requirement: REQ-ORD-016

> **Story:** As a loyalty member I want a bigger discount on large orders so that I'm rewarded for buying more.
>
> **Rules**
> 1. Loyalty members get **15% off orders strictly above 1000.00 EUR** (instead of 10%).
> 2. Loyalty orders above 100.00 up to and including 1000.00 EUR keep the existing **10%**.
> 3. Standard customers are unaffected (no discount).
> 4. Amounts are rounded **half-up** to cents.

Expected examples (pre-computed, HALF_UP):

| Membership | Total | Payable | Why |
|---|---|---|---|
| loyalty | 999.99 | 899.99 | 10% tier |
| loyalty | 1000.00 | 900.00 | boundary: still 10% |
| loyalty | 1000.01 | 850.01 | just above: 15% (850.0085 → 850.01) |
| loyalty | 1234.56 | 1049.38 | rounding (1049.376 → 1049.38) |
| loyalty | 5000.00 | 4250.00 | 15% |
| standard | 5000.00 | 5000.00 | counter-example: no discount |

Why this example works well in a demo:
- **No new test glue.** It reuses the existing step phrases ("Given a loyalty customer / When they place an order totalling … EUR /
  Then the payable amount is … EUR"), so the spec PR is a pure feature-file change and is red for the right reason straight away.
- **It shows boundaries and rounding,** which is what example mapping is for.
- **It shows the value of unit tests.** The existing property test `discountNeverExceedsTenPercent` encodes the *old* rule, so the
  implementer has to update it. A rule change touches the tests that pinned the old rule.

---

## 3. Create the requirement issue (as PO, 2 min)

The decisions are written into the issue so the agent can proceed without a clarification round. (To demo the clarification
path instead, see the variant at the end of this section.)

```bash
cat > /tmp/req-016.md <<'EOF'
### Requirement ID

REQ-ORD-016

### Story

As a loyalty member I want a bigger discount on large orders so that I'm rewarded for buying more.

### Rules

- Loyalty members get 15% off orders strictly above 1000.00 EUR (instead of 10%).
- Loyalty orders above 100.00 up to and including 1000.00 EUR keep the existing 10% discount.
- Standard customers are unaffected (no discount).
- Amounts are rounded half-up to cents.

### Known examples

- loyalty 999.99 → payable 899.99
- loyalty 1000.00 → payable 900.00 (boundary: still 10%)
- loyalty 1000.01 → payable 850.01
- loyalty 1234.56 → payable 1049.38
- loyalty 5000.00 → payable 4250.00
- standard 5000.00 → payable 5000.00

### Open questions

Decisions (PO):
1. The tiers do not stack: an order gets either 10% or 15%, never 25%.
2. The 15% tier applies to the whole order amount, not only to the part above 1000.00.
3. Order validation and the order value limit (REQ-ORD-014) are unchanged by this requirement.
EOF

ISSUE_URL=$(gh issue create --title "[$REQ] Tiered loyalty discount" --body-file /tmp/req-016.md --label requirement)
ISSUE=${ISSUE_URL##*/}; echo "Issue #$ISSUE: $ISSUE_URL"
```

Talking point: the issue is a **conversation starter**, not the spec. The spec is the `.feature` file the next step produces.

**Variant (show the clarification path):** delete the "Decisions (PO)" lines. The agent then posts its questions on the issue
and adds `needs-clarification`. Answer by editing the issue body (the agent reads the body, not the comments), then restart it
as described in 4.2.

---

## 4. Claude drafts the spec PR (as PO, about 5–10 min)

### 4.1 Start the spec agent

```bash
gh issue edit "$ISSUE" --add-label agent:spec
sleep 15
SPEC_RUN=$(gh run list --workflow agents.yml --event issues --limit 1 --json databaseId --jq '.[0].databaseId')
gh run watch "$SPEC_RUN"
```

What happens (`.github/workflows/agents.yml` → `spec-draft`, `ATDD_ROLE=spec`): Claude runs `/spec $ISSUE`.
1. The `spec-author` subagent does example mapping and writes `specs/features/orders/…feature`, then adds `REQ-ORD-016` to the pending list.
2. The `acceptance-engineer` subagent makes it executable and proves it's red (`ci/red_check.py`).
3. Claude opens a PR from a `spec/REQ-ORD-016-…` branch.

### 4.2 If the run ends without a PR

```bash
gh issue view "$ISSUE" --json labels,comments --jq '{labels:[.labels[].name], last:.comments[-1].body}'
```
- **The issue has `needs-clarification` and a comment with questions:** answer them in the issue **body**, then restart the agent:
  ```bash
  gh issue edit "$ISSUE" --remove-label needs-clarification --remove-label agent:spec
  gh issue edit "$ISSUE" --add-label agent:spec
  ```
- **No comment at all:** open the run log and look at `permission_denials_count` and Claude's output (see Troubleshooting).

### 4.3 Review the spec PR

```bash
SPEC_PR=$(gh pr list --json number,headRefName --jq ".[]|select(.headRefName|startswith(\"spec/$REQ\"))|.number")
echo "Spec PR #$SPEC_PR"
gh pr view "$SPEC_PR" --web          # description with the red-check evidence
gh pr diff "$SPEC_PR"                # .feature file + pending-list entry
gh pr checks "$SPEC_PR" --watch      # wait for "PR gate"
```

What CI proves on a **spec PR** (`.github/workflows/pr.yml`):

| Check | Proves |
|---|---|
| Path guard (`ci/guard.sh`) | only `specs/`, `acceptance/`, `delivery/` changed; `kind=spec`, `added=REQ-ORD-016` |
| Build, unit, property & architecture | test code follows the layering rules |
| Contract lint | `openapi.yaml` is valid |
| **New scenarios are red** | the new scenarios fail **on a `Then` assertion**, not with undefined steps or crashes |
| Executable spec (embedded) | everything already implemented is still green (REQ-ORD-016 is excluded as pending) |
| **PR gate** | all of the above; the only required check |

Show "red for the right reason" yourself on the PR branch:

```bash
git fetch origin && git switch "$(gh pr view "$SPEC_PR" --json headRefName --jq .headRefName)"
ONLY="@$REQ" scripts/acceptance.sh
python3 ci/red_check.py target/cucumber/report.json
# Expected: "✓ red …: payable amount … expected 850.01 but was 900.01", ending with "✓ Red for the right reason."
# The 999.99 and 1000.00 rows are listed as "already passing": they pin the boundary that must NOT change.
git switch main
```

**PO review:** read the `.feature` file. Do the `Rule:` lines match the issue? Are the boundary rows there (1000.00 vs 1000.01)?

> Note: if the first PR run shows "PR gate: failed" with every job **cancelled**, ignore it. Opening the PR and adding
> the label start two runs, and the older one is cancelled. Only the latest run counts.

### 4.4 Merge the spec

```bash
gh pr merge "$SPEC_PR" --merge --delete-branch
gh issue edit "$ISSUE" --remove-label agent:spec
```

REQ-ORD-016 is now **specified but pending** on `main`. Merging also starts the Main pipeline. That's harmless (the code is
unchanged, and REQ-ORD-016 is excluded), but it waits for production approval. Either approve it (section 7.3) or reject it:

```bash
MAIN_RUN=$(gh run list --workflow main.yml --limit 1 --json databaseId --jq '.[0].databaseId')
gh run cancel "$MAIN_RUN"     # optional: skip releasing a spec-only change
```

---

## 5. Claude implements it (as developer, about 10–15 min)

### 5.1 Start the implementation agent

```bash
gh issue edit "$ISSUE" --add-label agent:implement
sleep 15
IMPL_RUN=$(gh run list --workflow agents.yml --event issues --limit 1 --json databaseId --jq '.[0].databaseId')
gh run watch "$IMPL_RUN"
```

What happens (`implement` job, `ATDD_ROLE=implement`): Claude runs `/implement REQ-ORD-016`.
1. It runs `ONLY='@REQ-ORD-016' scripts/acceptance.sh` and reads the failures.
2. It implements the **rule** in `src/main/**`, most likely in `domain/DiscountPolicy.java`.
3. It updates and adds unit and property tests. Expect `discountNeverExceedsTenPercent` to change, because it encoded the old rule.
4. It loops until green, removes `REQ-ORD-016` from the pending list, runs the gates, and opens a PR from `impl/REQ-ORD-016-…`.

It **cannot** edit the spec. The `.claude/hooks/guard-paths.mjs` hook blocks `specs/` and `acceptance/` in the implement role.

### 5.2 Review the implementation PR

```bash
IMPL_PR=$(gh pr list --json number,headRefName --jq ".[]|select(.headRefName|startswith(\"impl/$REQ\"))|.number")
echo "Impl PR #$IMPL_PR"
gh pr diff "$IMPL_PR" --name-only     # only src/** and the pending-list removal
gh pr checks "$IMPL_PR" --watch
```

What CI proves on an **implementation PR**:

| Check | Proves |
|---|---|
| Path guard | only `src/` changed; the pending ID was **removed**, not added (`kind=impl`) |
| Build, unit, property & architecture | unit and jqwik property tests pass; the domain stays framework-free |
| Executable spec (embedded) | the **whole** spec is green, including REQ-ORD-016 now |
| **Mutation testing** (PIT ≥ 80%) | the unit tests really constrain the domain logic: planted bugs get caught |
| Deploy preview | the image deploys to `orders-pr-<n>` on Azure |
| Executable spec (preview) | the **same scenarios** pass against the deployed preview, over the network |
| Contract fuzzing | Schemathesis finds no responses that violate `openapi.yaml` |
| **PR gate** | all of the above |

Plus a **conformance review** comment from an independent Claude (the `spec-conformance-reviewer` agent): does the code implement
the rule, or just the example numbers?

```bash
gh pr view "$IMPL_PR" --comments | tail -40                                    # the conformance verdict
RID=$(gh run list --workflow pr.yml --branch "$(gh pr view "$IMPL_PR" --json headRefName --jq .headRefName)" --limit 1 --json databaseId --jq '.[0].databaseId')
gh run view "$RID" --log | grep -E '>> Generated [0-9]+ mutations|Test strength'  # mutation score
PREVIEW_URL="https://$(az containerapp show -n orders-pr-$IMPL_PR -g rg-orders-atdd --query properties.configuration.ingress.fqdn -o tsv)"
echo "$PREVIEW_URL"
```

Optional live proof on the preview:

```bash
C=$(curl -s -X POST "$PREVIEW_URL/api/customers" -H 'Content-Type: application/json' -d '{"membership":"LOYALTY"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')
curl -s -X POST "$PREVIEW_URL/api/orders" -H 'Content-Type: application/json' -d "{\"customerId\":\"$C\",\"total\":1000.01}"; echo
# → "payable":850.01
```

**Developer review:** behaviour is already proven by the spec, so review design and readability, and read the conformance comment.

### 5.3 Merge the implementation

```bash
gh pr merge "$IMPL_PR" --merge --delete-branch
gh issue edit "$ISSUE" --remove-label agent:implement
```

`pr-cleanup.yml` deletes the preview app `orders-pr-<n>` when the PR closes.

---

## 6. (Optional) Show the guard rails (5 min)

Run these locally, in a Claude Code session, to show that agents can't game the spec:

```bash
claude
> Change the expected payable of 1000.01 in the REQ-ORD-016 feature file to 800.01
# → [atdd-guard] Role 'implement' may not modify specs/...   (the PreToolUse hook blocks it)
```

And on the CI side: a PR that touches both `specs/` and `src/` fails the path guard with "Split it into a spec PR and an
implementation PR."

---

## 7. Release to production (as release approver, about 10 min)

### 7.1 Watch the trunk pipeline

```bash
sleep 10
MAIN_RUN=$(gh run list --workflow main.yml --limit 1 --json databaseId --jq '.[0].databaseId')
gh run watch "$MAIN_RUN"
```

Stages (`.github/workflows/main.yml`):

```
build (unit tests) → spec embedded (+holdout) → deploy staging (build image ONCE)
   → spec on staging  +  k6 SLO check (thresholds from specs/quality/slo.json)
   → ⏸ production approval → deploy production (same image) → @smoke on production
```

### 7.2 If the k6 SLO check fails

**Many failed requests with normal latency** means the known deploy-switch issue (section 0). Re-run only what failed:

```bash
gh run rerun "$MAIN_RUN" --failed && gh run watch "$MAIN_RUN"
```

**High latency (p95 > 300 ms)** is a real SLO breach, and that's a finding to discuss, not something to re-run away.

### 7.3 Approve the production deployment

In the browser: **Actions → Main → the run → Review deployments → production → Approve**. Or from the terminal:

```bash
ENV_ID=$(gh api repos/$REPO/actions/runs/$MAIN_RUN/pending_deployments --jq '.[0].environment.id')
gh api -X POST repos/$REPO/actions/runs/$MAIN_RUN/pending_deployments \
  -F "environment_ids[]=$ENV_ID" -f state=approved -f comment="Release $REQ"
gh run watch "$MAIN_RUN"
```

### 7.4 Verify production

```bash
PROD_URL="https://$(az containerapp show -n orders-prod -g rg-orders-atdd --query properties.configuration.ingress.fqdn -o tsv)"
echo "$PROD_URL"
gh variable set PROD_URL --body "$PROD_URL"      # first time only: turns on synthetic monitoring

curl -s "$PROD_URL/actuator/health/readiness"; echo
TAGS='@smoke' scripts/acceptance.sh remote "$PROD_URL"            # same spec, against production, from the laptop
python3 ci/traceability.py target/cucumber/report.json production

gh workflow run synthetic.yml && sleep 10 && gh run watch "$(gh run list --workflow synthetic.yml --limit 1 --json databaseId --jq '.[0].databaseId')"
```

`PROD_URL` must be a **repository** variable; `synthetic.yml` can't see environment variables.

### 7.5 Show the traceability

```bash
gh run view "$MAIN_RUN" --web     # each "spec (…)" job's summary has a REQ → AC → ✅/❌ table per environment
```

---

## 8. Close the loop

```bash
gh issue close "$ISSUE" --comment "Released: spec #$SPEC_PR, implementation #$IMPL_PR, production @smoke green."
git switch main && git pull --ff-only
cat delivery/pending-requirements.txt      # REQ-ORD-016 is gone again: specified, built, released
```

### Summary slide

| Step | Who decides | Who does the work | What proves it |
|---|---|---|---|
| Requirement | PO | PO | issue |
| Spec (rules + examples) | PO approves | Claude (`spec-author`, `acceptance-engineer`) | red for the right reason |
| Implementation | developer approves | Claude (`implementer`) | spec green + holdout + mutation ≥ 80% + preview |
| Independent review | developer reads | Claude (`spec-conformance-reviewer`) | PR comment |
| Release | release approver | pipeline | spec on staging + k6 SLO + @smoke in prod |
| Run | n/a | `synthetic.yml` | @smoke every 30 min |

---

## Troubleshooting (things that actually happened)

| Symptom | Cause | Fix |
|---|---|---|
| Agent run "succeeds" in about 20 s, but no PR or comment | Tool calls denied in CI (look for `permission_denials_count > 0`) | Add the missing tools to `--allowedTools` in `agents.yml`; Claude's issue comment usually names them |
| Changed `agents.yml`, but the run still uses the old settings | "Re-run" replays the original commit's workflow | Start a fresh run: remove and re-add the label |
| `conformance-review` fails: "non-human actor: claude (type: Bot)" | The PR was opened by the bot | `allowed_bots: 'claude'` on that job |
| PR gate failed, every job `cancelled` | A newer run for the same PR replaced it | Look at the latest run only |
| Contract lint fails | `operation-summary` / `security-defined` / an unquoted description | Add `summary:` + `security: []` per operation; quote descriptions containing commas |
| Azure login `AADSTS700213 … subject 'repo:owner@id/repo@id:environment:…'` | The federated credential uses the old subject format | Recreate it with the ID-based subject (see `docs/SETUP.md` §2) |
| k6: high `http_req_failed`, normal latency, mostly `404` | Deploy reported healthy while the old revision served; the in-memory customer was lost on the switch | Re-run failed jobs; merge the deploy-wait fix |
| `Unknown acceptance.target '#' (expected embedded\|remote)` | zsh passed the inline `# comment` as arguments | `setopt interactivecomments` (add it to `~/.zshrc`) |
| Local `scripts/acceptance.sh` reports an old "forked VM terminated" | Stale `target/failsafe-reports/failsafe-summary.xml` | `mvn -B clean` |
| `ci/guard.sh: mapfile: command not found` | macOS bash 3.2 | `brew install bash` |

## Reset after the demo (optional)

The released feature stays; it's real functionality. To demo again, use the next free ID (`REQ-ORD-017`) with a new rule
rather than reverting history. Preview apps are removed automatically when PRs close. To check for leftovers:

```bash
az containerapp list -g rg-orders-atdd --query '[].name' -o tsv     # expect: orders-staging, orders-prod
```
