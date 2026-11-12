# Setting up the repository on GitHub and Azure

## 1. GitHub repository settings

### Branch ruleset for `main`
- Require a pull request; 1 approval; **require review from Code Owners**; dismiss stale approvals.
- Require status check **`PR gate`** (the only required check; it aggregates all conditional jobs).
- Require branches to be up to date; block force pushes; squash merge only.
- Bypass list: none (agents and admins go through the same gate).

> Replace the placeholder teams in `.github/CODEOWNERS` (`@your-org/...`) with real users or teams
> **before** enabling "require review from Code Owners". Owners that don't exist block every PR.

### Labels
`requirement`, `agent:spec`, `agent:implement`, `spec-change`, `platform-change`, `needs-clarification`.

```bash
for l in requirement agent:spec agent:implement spec-change platform-change needs-clarification; do
  gh label create "$l" 2>/dev/null || echo "exists: $l"
done
```

### Environments
| Environment | Protection | Variables |
|---|---|---|
| `preview` | none | `ACR_NAME`, `AZURE_RESOURCE_GROUP`, `CONTAINERAPPS_ENV` |
| `staging` | deployment branches: `main` | same |
| `production` | deployment branches: `main`; **required reviewers** (release approvers) | same |

> **Required reviewers** are only available on **public** repositories, or on private repositories
> with a paid plan (GitHub Pro / Team / Enterprise). On a free private repo the API answers
> `HTTP 422 … billing plan supports the required reviewers protection rule`, and `main.yml` would then
> deploy to production **without** an approval step.
>
> Create `production` (with its protection) **before** the first `main` run. If the pipeline references
> an environment that doesn't exist, GitHub creates it automatically, with no protection.

### Repository variables
| Name | Purpose |
|---|---|
| `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID` | OIDC login (no secrets stored). Leave unset to run without deploy jobs in **PRs**; note that `main.yml` does not check for them, so its staging job fails until Azure is configured. |
| `HOLDOUT_REPO` | e.g. `your-org/orders-holdout-specs` (private). Leave unset to skip holdout. |
| `PROD_URL` | Base URL for synthetic monitoring. Set it after the first production deploy. |

> `PROD_URL` must be a **repository** variable, not a `production` environment variable.
> `synthetic.yml` doesn't declare an environment, so it can't see environment variables and would skip silently.

### Secrets
| Name | Purpose |
|---|---|
| `ANTHROPIC_API_KEY` | Claude Code in Actions (`agents.yml`) |
| `HOLDOUT_TOKEN` | Fine-grained PAT or GitHub App token with read access to `HOLDOUT_REPO` only |

There are no Azure secrets: GitHub logs in to Azure with OIDC (section 2).

### Claude GitHub App
Install it on the repository (in Claude Code: `/install-github-app`). The action uses the app's token,
so PRs and commits it creates **do** trigger the PR pipeline (a plain `GITHUB_TOKEN` would not).

## 2. Azure

GitHub Actions deploys as a **user-assigned managed identity** that trusts GitHub's OIDC tokens
(workload identity federation). There is no client secret to store or rotate. A managed identity
lives inside the resource group and needs no tenant-wide rights to create (unlike an Entra app
registration, which many company tenants don't allow).

```bash
RG=rg-orders-atdd; LOC=westeurope; PREFIX=ordersatdd; ID_NAME=gh-orders-atdd
OWNER=<github-owner>; REPO_NAME=<repo-name>

# 1. Platform: container registry, Log Analytics, Container Apps environment
az group create -n "$RG" -l "$LOC"
az deployment group create -g "$RG" -f infra/main.bicep -p prefix="$PREFIX"

# 2. The identity GitHub Actions logs in as, with rights on this resource group only
az identity create -g "$RG" -n "$ID_NAME" -l "$LOC"
CLIENT_ID=$(az identity show -g "$RG" -n "$ID_NAME" --query clientId -o tsv)
PRINCIPAL_ID=$(az identity show -g "$RG" -n "$ID_NAME" --query principalId -o tsv)
SCOPE=$(az group show -n "$RG" --query id -o tsv)
for role in "Contributor" "User Access Administrator"; do   # UAA lets new apps get AcrPull on the registry
  az role assignment create --assignee-object-id "$PRINCIPAL_ID" --assignee-principal-type ServicePrincipal \
    --role "$role" --scope "$SCOPE"
done

# 3. Trust GitHub: one federated credential per GitHub environment.
#    GitHub presents the ID-based subject  repo:<owner>@<owner-id>/<repo>@<repo-id>:environment:<env>
OWNER_ID=$(gh api "repos/${OWNER}/${REPO_NAME}" --jq .owner.id)
REPO_ID=$(gh api "repos/${OWNER}/${REPO_NAME}" --jq .id)
for env in preview staging production; do
  az identity federated-credential create -g "$RG" --identity-name "$ID_NAME" --name "gh-${env}" \
    --issuer https://token.actions.githubusercontent.com \
    --subject "repo:${OWNER}@${OWNER_ID}/${REPO_NAME}@${REPO_ID}:environment:${env}" \
    --audiences api://AzureADTokenExchange
done
az identity federated-credential list -g "$RG" --identity-name "$ID_NAME" --query '[].subject' -o tsv   # check!

# 4. GitHub variables
gh variable set AZURE_CLIENT_ID       --body "$CLIENT_ID"
gh variable set AZURE_TENANT_ID       --body "$(az account show --query tenantId -o tsv)"
gh variable set AZURE_SUBSCRIPTION_ID --body "$(az account show --query id -o tsv)"
for env in preview staging production; do
  gh api -X PUT "repos/${OWNER}/${REPO_NAME}/environments/${env}" --silent
  gh variable set ACR_NAME             --env "$env" --body "${PREFIX}acr"
  gh variable set AZURE_RESOURCE_GROUP --env "$env" --body "$RG"
  gh variable set CONTAINERAPPS_ENV    --env "$env" --body "${PREFIX}-env"
done
```

Then add the protection rules from section 1 (branch policy `main` on `staging` and `production`, plus
required reviewers on `production`), either in **Settings → Environments** or with
`gh api -X PUT repos/<owner>/<repo>/environments/<env>`.

Notes:
- **Federated credentials are per environment only.** No `pull_request` subject is needed: every deploy
  job (including PR previews and `pr-cleanup.yml`) runs inside a named `environment:`, so GitHub always
  presents `…:environment:<name>`. Leaving `pull_request` out means a PR can only reach Azure through `preview`.
- **The subject must match exactly.** GitHub currently presents the **ID-based** format
  (`repo:owner@123/repo@456:…`), not the older `repo:owner/repo:…`. The `azure/login` step prints the
  subject it presented ("subject claim - …"); copy it from there if in doubt.
- **zsh users: write `${VAR}` with braces inside subjects.** In zsh, `"repo:$REPO:environment"` applies
  the `:e` modifier and produces `repo:nvironment`. Always verify with the `list` command above.
- Use a narrower custom role instead of Contributor + User Access Administrator for anything beyond a demo.

### Troubleshooting Azure login
| Error in the `azure/login` step | Cause | Fix |
|---|---|---|
| `AADSTS700213: No matching federated identity record found for presented assertion subject '…'` | The credential's subject doesn't match the token, e.g. old `repo:owner/repo` format, a typo, a zsh-mangled subject, or the wrong environment name | Recreate the federated credential with the exact subject from the error message |
| `AADSTS700016` / application not found | `AZURE_CLIENT_ID` is wrong, or is the principal ID instead of the client ID | Use `az identity show … --query clientId` |
| Login works, then `AuthorizationFailed` | Role assignment missing or still propagating | Check `az role assignment list --assignee <principal-id> --all`; wait a minute |
| A new container app can't pull its image | No `User Access Administrator`, so the deploy couldn't grant AcrPull | Add the role, or grant `AcrPull` on the registry to the app's identity manually |

After changing anything on the **Azure** side, "Re-run failed jobs" is enough. After changing a **workflow
file**, start a fresh run instead: a re-run replays the workflow as it was at the original commit.

## 3. Holdout repository
Create a private repo with a `features/` folder. Put extra examples there, written in the same step
language (see `docs/holdout-example/`). Only QA has write access; the implementation agent's token
has no read access. **Delete `docs/holdout-example/` from your real repository.**

## 4. Developer machine
- JDK **21** (the project and CI target 21; newer JDKs work but differ from CI), Maven 3.9+,
  Node 18+ (for Claude Code and its hook), Python 3, `gh`.
- macOS: `ci/guard.sh` needs bash 4+ (`brew install bash`); macOS ships bash 3.2.
- If `git push`/`fetch` over HTTPS asks for credentials while `gh` works, run `gh auth setup-git` once.
- If Maven reports a failure that doesn't match the run you just did, run `mvn -B clean`: a stale
  `target/failsafe-reports/failsafe-summary.xml` can make `failsafe:verify` fail on an old result.
- `claude` in the repo root picks up `CLAUDE.md`, `.claude/settings.json` (permissions + hook),
  `.claude/agents/*` and `.claude/commands/*` automatically.
- Personal overrides go in `.claude/settings.local.json` (git-ignored).
