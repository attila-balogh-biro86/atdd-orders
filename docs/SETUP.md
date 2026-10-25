# Setting up the repository on GitHub and Azure

## 1. GitHub repository settings

### Branch ruleset for `main`
- Require a pull request; 1 approval; **require review from Code Owners**; dismiss stale approvals.
- Require status check **`PR gate`** (the only required check; it aggregates all conditional jobs).
- Require branches to be up to date; block force pushes; squash merge only.
- Bypass list: none (agents and admins go through the same gate).

### Labels
`requirement`, `agent:spec`, `agent:implement`, `spec-change`, `platform-change`, `needs-clarification`.

### Environments
| Environment | Protection | Variables |
|---|---|---|
| `preview` | none | `ACR_NAME`, `AZURE_RESOURCE_GROUP`, `CONTAINERAPPS_ENV` |
| `staging` | deployment branches: `main` | same |
| `production` | deployment branches: `main`; **required reviewers** (release approvers) | same |

### Repository variables
| Name | Purpose |
|---|---|
| `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID` | OIDC login (no secrets stored). Leave unset to run without deploy jobs. |
| `HOLDOUT_REPO` | e.g. `your-org/orders-holdout-specs` (private). Leave unset to skip holdout. |
| `PROD_URL` | Base URL for synthetic monitoring |

### Secrets
| Name | Purpose |
|---|---|
| `ANTHROPIC_API_KEY` | Claude Code in Actions (`agents.yml`) |
| `HOLDOUT_TOKEN` | Fine-grained PAT or GitHub App token with read access to `HOLDOUT_REPO` only |

### Claude GitHub App
Install it on the repository (in Claude Code: `/install-github-app`). The action uses the app's token,
so PRs and commits it creates **do** trigger the PR pipeline (a plain `GITHUB_TOKEN` would not).

## 2. Azure

```bash
RG=rg-orders-atdd; LOC=westeurope; PREFIX=ordersatdd
az group create -n $RG -l $LOC
az deployment group create -g $RG -f infra/main.bicep -p prefix=$PREFIX

# Identity for GitHub Actions (OIDC, no client secret)
APP_ID=$(az ad app create --display-name gh-orders-atdd --query appId -o tsv)
az ad sp create --id $APP_ID
SCOPE=$(az group show -n $RG --query id -o tsv)
az role assignment create --assignee $APP_ID --role Contributor --scope $SCOPE
az role assignment create --assignee $APP_ID --role "User Access Administrator" --scope $SCOPE  # lets apps get AcrPull

for subject in "pull_request" "environment:preview" "environment:staging" "environment:production"; do
  az ad app federated-credential create --id $APP_ID --parameters "{
    \"name\": \"gh-${subject//[:]/-}\",
    \"issuer\": \"https://token.actions.githubusercontent.com\",
    \"subject\": \"repo:<owner>/<repo>:${subject}\",
    \"audiences\": [\"api://AzureADTokenExchange\"] }"
done
```

Set `AZURE_CLIENT_ID=$APP_ID`, the tenant and subscription IDs as repository variables, and
`ACR_NAME=${PREFIX}acr`, `AZURE_RESOURCE_GROUP=$RG`, `CONTAINERAPPS_ENV=${PREFIX}-env` on each environment.

Use a narrower custom role instead of Contributor + UAA for anything beyond a demo.

## 3. Holdout repository
Create a private repo with a `features/` folder. Put extra examples there, written in the same step
language (see `docs/holdout-example/`). Only QA has write access; the implementation agent's token
has no read access. **Delete `docs/holdout-example/` from your real repository.**

## 4. Developer machine
- JDK 21, Maven 3.9+, Node 18+ (for Claude Code and its hook), Python 3, `gh`.
- `claude` in the repo root picks up `CLAUDE.md`, `.claude/settings.json` (permissions + hook),
  `.claude/agents/*` and `.claude/commands/*` automatically.
- Personal overrides go in `.claude/settings.local.json` (git-ignored).
