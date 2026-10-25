#!/usr/bin/env python3
"""Requirement → scenario → result traceability from the Cucumber JSON report.

Usage: python3 ci/traceability.py target/cucumber/report.json [environment-label]

Prints a Markdown table and appends it to $GITHUB_STEP_SUMMARY when running in Actions.
"""
import collections
import json
import os
import sys


def main(path, env_label):
    with open(path, encoding="utf-8") as fh:
        features = json.load(fh)

    by_req = collections.OrderedDict()
    for feature in features:
        ftags = [t["name"] for t in feature.get("tags", [])]
        for el in feature.get("elements", []):
            if el.get("type") != "scenario":
                continue
            tags = ftags + [t["name"] for t in el.get("tags", [])]
            req = next((t[1:] for t in tags if t.startswith("@REQ-")), "untraced")
            ac = next((t[1:] for t in tags if t.startswith("@AC-")), "-")
            statuses = [s.get("result", {}).get("status") for s in el.get("before", []) + el.get("steps", []) + el.get("after", [])]
            status = "failed" if "failed" in statuses else ("passed" if statuses and all(s == "passed" for s in statuses) else "not run")
            by_req.setdefault(req, []).append((ac, el.get("name"), status))

    icon = {"passed": "✅", "failed": "❌", "not run": "⚪"}
    lines = [f"### Executable specification results{f' ({env_label})' if env_label else ''}", "",
             "| Requirement | Acceptance criterion | Scenario | Result |", "|---|---|---|---|"]
    totals = collections.Counter()
    for req, rows in by_req.items():
        for ac, name, status in rows:
            totals[status] += 1
            lines.append(f"| {req} | {ac} | {name} | {icon[status]} {status} |")
    lines += ["", f"**{totals['passed']} passed, {totals['failed']} failed, {totals['not run']} not run** "
                  f"across {len(by_req)} requirement(s)."]
    md = "\n".join(lines)
    print(md)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as fh:
            fh.write(md + "\n")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "target/cucumber/report.json",
         sys.argv[2] if len(sys.argv) > 2 else "")
