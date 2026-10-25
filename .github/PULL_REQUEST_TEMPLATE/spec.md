## Spec PR: <REQ-ID> <title>

Closes part of #<issue>

### Rules
<!-- One sentence per rule, as in the Rule: lines -->
-

### Examples worth discussing
<!-- Boundaries, rounding, counter-examples -->
-

### Decisions taken during example mapping
-

### Red evidence
<!-- Paste `python3 ci/red_check.py` output. CI re-checks it. -->
```
```

### Checklist
- [ ] Feature tagged `@REQ-…`, scenarios tagged `@AC-…`
- [ ] Requirement ID added to `delivery/pending-requirements.txt`
- [ ] No production code changed (`src/**`)
- [ ] Contract (`specs/contracts/openapi.yaml`) updated if the API changes
- [ ] Holdout examples added to the private holdout repo (reviewer: QA)
