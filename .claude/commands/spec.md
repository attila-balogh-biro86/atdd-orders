---
description: Draft an executable spec PR from a requirement (issue number or text). Run with ATDD_ROLE=spec.
argument-hint: <issue-number | requirement text>
---

Requirement: $ARGUMENTS

If this is a number, read it with `gh issue view $ARGUMENTS`.

1. Use the `spec-author` subagent to run example mapping and write the feature file(s), contract
   changes and the pending-list entry. Show me the rules, examples and open questions BEFORE writing
   files, and wait for my answers on any open question.
2. Use the `acceptance-engineer` subagent to make the scenarios executable and prove they are red.
3. Create branch `spec/<REQ-ID>-<slug>`, commit, and open a PR with
   `gh pr create --template spec.md --label spec-change`, pasting the red_check output.
