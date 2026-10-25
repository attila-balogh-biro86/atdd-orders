## Implementation PR: <REQ-ID> <title>

Implements #<issue> · Spec PR: #<spec-pr>

### Approach
<!-- How the RULE is implemented (not the examples) -->

### Evidence (paste real output)
- `mvn -B test`:
- `scripts/acceptance.sh`:
- Mutation score:

### Spec-conformance review
<!-- Verdict from the spec-conformance-reviewer subagent, or link to its PR comment -->

### Checklist
- [ ] Requirement ID removed from `delivery/pending-requirements.txt`
- [ ] No changes under `specs/`, `acceptance/`, `.github/`, `.claude/`, `ci/`, `scripts/`
- [ ] Unit / property tests cover the rule, not just the examples
- [ ] If anything in the spec looks wrong, it is described here (not worked around)
