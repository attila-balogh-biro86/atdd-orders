---
description: Run the same gates CI runs, locally, and summarise the results honestly.
---

Run these in order and stop at the first failure, showing its output:

1. `ci/guard.sh origin/main`             (path rules for this branch's diff)
2. `mvn -B test`                          (unit, property, architecture)
3. `scripts/acceptance.sh`                (executable spec, pending requirements excluded)
4. `python3 ci/traceability.py target/cucumber/report.json`
5. `mvn -B test-compile org.pitest:pitest-maven:mutationCoverage`

Then print a table: gate | result | key numbers (tests, scenarios, mutation score).
Report only what the commands printed.
