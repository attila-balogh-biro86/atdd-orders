---
description: Prove that a requirement's scenarios currently fail for the right reason.
argument-hint: <REQ-ID>
---

1. `ONLY='@$ARGUMENTS' scripts/acceptance.sh` (expect a non-zero exit; that's the point).
2. `python3 ci/red_check.py target/cucumber/report.json`
3. Summarise per scenario: failing step and assertion message. Flag any scenario that passed,
   was undefined/pending, or failed with a crash instead of an assertion.
