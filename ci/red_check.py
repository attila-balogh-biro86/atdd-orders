#!/usr/bin/env python3
"""Prove that a new requirement's scenarios are red *for the right reason*.

Usage: python3 ci/red_check.py target/cucumber/report.json

Rules, over the scenarios in the report (CI runs it on just the newly specified requirements):
  - no step may be undefined / pending / ambiguous   → the spec must be executable,
  - every failing scenario must fail in an outcome step (Then/And after Then) on an assertion,
    not in Given/When and not with a crash (connection refused, NPE, ...),
  - at least one scenario must fail                  → otherwise the spec adds no new behaviour.
Scenarios that already pass are allowed but listed: they typically pin a boundary that existing code
already satisfies (e.g. "exactly 10000.00 is still accepted"). Reviewers should confirm that's intended.
Exit code 0 = good red.
"""
import json
import re
import sys

ASSERTION = re.compile(r"(AssertionError|AssertionFailedError|ComparisonFailure|MultipleFailuresError)")


def first_line(msg):
    return msg.strip().splitlines()[0] if msg and msg.strip() else "<no message>"


def check(report_path):
    with open(report_path, encoding="utf-8") as fh:
        features = json.load(fh)

    problems, passing, good_red, total = [], [], [], 0
    for feature in features:
        for el in feature.get("elements", []):
            if el.get("type") != "scenario":
                continue
            total += 1
            name = f'{feature.get("name")} / {el.get("name")} (line {el.get("line")})'
            hooks = el.get("before", []) + el.get("after", [])
            steps = el.get("steps", [])
            statuses = [s.get("result", {}).get("status") for s in hooks + steps]

            bad = {"undefined", "pending", "ambiguous"} & set(statuses)
            if bad:
                problems.append(f"{name}: has {', '.join(sorted(bad))} steps; the spec is not executable yet")
                continue
            if "failed" not in statuses:
                passing.append(name)
                continue
            if any(h.get("result", {}).get("status") == "failed" for h in hooks):
                problems.append(f"{name}: fails in a hook, not in a step")
                continue

            phase = None
            for step in steps:
                kw = step.get("keyword", "").strip()
                if kw in ("Given", "When", "Then"):
                    phase = kw
                result = step.get("result", {})
                if result.get("status") != "failed":
                    continue
                msg = result.get("error_message", "")
                if phase != "Then":
                    problems.append(f'{name}: fails in a {phase} step ("{step.get("name")}"): {first_line(msg)}. '
                                    "Setup or action broke, so the failure says nothing about the expected outcome")
                elif not ASSERTION.search(first_line(msg)):
                    problems.append(f"{name}: fails with a crash, not an assertion: {first_line(msg)}")
                else:
                    good_red.append(f"{name}: {first_line(msg)}")
                break

    print(f"Checked {total} scenario(s): {len(good_red)} good red, {len(passing)} already passing, "
          f"{len(problems)} problem(s).")
    for g in good_red:
        print(f"  ✓ red   {g}")
    for p in passing:
        print(f"  • green {p}  (already satisfied; confirm it pins an intended boundary)")
    if total == 0:
        print("No scenarios found; did the tag filter match anything?")
        return 2
    if not good_red and not problems:
        problems.append("No scenario fails: this spec describes no new behaviour")
    if problems:
        print("NOT a good red:")
        for p in problems:
            print(f"  ✗ {p}")
        return 1
    print("✓ Red for the right reason.")
    return 0


if __name__ == "__main__":
    sys.exit(check(sys.argv[1] if len(sys.argv) > 1 else "target/cucumber/report.json"))
