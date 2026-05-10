#!/usr/bin/env python3
"""
Parse RETO pipeline logs and produce a structured report.
Extracts Phase 0-4 outputs, task decomposition, capability bindings,
slot grounding, resolution, and final JSON from adb logcat.

Usage: python3 parse_ret_results.py <logfile> [output.md]
"""
import re
import sys
from collections import defaultdict
from datetime import datetime

LOG_PATTERNS = {
    "phase0_decompose": re.compile(r"TaskDecomposer.*Decomposed into (\d+) tasks: (.+)"),
    "phase0_task": re.compile(r"Phase 0 — Tasks.*: (.+)"),
    "phase1_bind": re.compile(r"CapabilityBinder.*Bound (\d+) actions: (.+)"),
    "phase1_bound": re.compile(r"Phase 1 — Bound.*: (.+)"),
    "phase1_unsupported": re.compile(r"Phase 1 — Unsupported.*: (.+)"),
    "phase2_slots": re.compile(r"SlotGroundingPlanner.*built (\d+) tool requirements and (\d+) literal slots"),
    "phase2_extracted": re.compile(r"Phase 2 — Extracted.*: (.+)"),
    "phase2_fallback": re.compile(r"Phase 2 — Slot Grounding Fallback.*"),
    "phase2_coverage": re.compile(r"Phase 2 — Coverage.*"),
    "phase4_analysis": re.compile(r"Phase 2 — Raw Output.*"),
    "phase4_action": re.compile(r"Phase 3 — Raw Output.*"),
    "phase4_json": re.compile(r"Phase 4 — Raw Output.*"),
    "resolution": re.compile(r"ResolverRegistry.*"),
    "workflow_parsed": re.compile(r"Parsed workflow.*with (\d+) actions"),
    "workflow_valid": re.compile(r"Validation.*(Valid|errors)"),
    "missing_info": re.compile(r"Missing info.*: (.+)"),
    "trigger": re.compile(r"Trigger hint.*: (.+)"),
    "goal": re.compile(r"Parsed analysis goal.*: (.+)"),
    "error": re.compile(r"Generation error.*"),
}


def parse_log(filepath):
    """Parse logcat file and extract RETO pipeline events."""
    events = []
    with open(filepath, "r", errors="replace") as f:
        for line in f:
            ts = line[:18].strip() if len(line) > 18 else ""
            for name, pattern in LOG_PATTERNS.items():
                m = pattern.search(line)
                if m:
                    events.append({
                        "timestamp": ts,
                        "type": name,
                        "groups": m.groups(),
                        "raw": line.strip()
                    })
                    break
    return events


def build_report(events):
    """Build a structured markdown report from parsed events."""
    lines = []
    lines.append("# RETO Pipeline Test Report")
    lines.append(f"**Parsed:** {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    lines.append(f"**Events:** {len(events)}")
    lines.append("")

    # Group by patterns
    sections = defaultdict(list)
    for ev in events:
        sections[ev["type"]].append(ev)

    # Phase 0
    decompositions = sections.get("phase0_decompose", [])
    if decompositions:
        lines.append("## Phase 0 — Task Decomposition")
        lines.append("")
        for ev in decompositions:
            lines.append(f"- **{ev['groups'][0]} tasks:** `{ev['groups'][1][:120]}`")
        lines.append("")

    # Phase 1
    bindings = sections.get("phase1_bind", [])
    if bindings:
        lines.append("## Phase 1 — Capability Binding")
        lines.append("")
        for ev in bindings:
            lines.append(f"- **{ev['groups'][0]} actions:** `{ev['groups'][1][:120]}`")
        lines.append("")

    unsupported = sections.get("phase1_unsupported", [])
    if unsupported:
        lines.append("### Unsupported")
        for ev in unsupported:
            lines.append(f"- {ev['groups'][0]}")
        lines.append("")

    # Phase 2
    slots = sections.get("phase2_slots", [])
    if slots:
        lines.append("## Phase 2 — Slot Grounding")
        lines.append("")
        for ev in slots:
            lines.append(f"- **{ev['groups'][0]} tool requirements, {ev['groups'][1]} literal slots**")
        lines.append("")

    fallbacks = sections.get("phase2_fallback", [])
    if fallbacks:
        lines.append(f"⚠️ **Fallback used:** {len(fallbacks)} time(s)")
        lines.append("")

    coverage = sections.get("phase2_coverage", [])
    if coverage:
        lines.append("### Coverage")
        for ev in coverage:
            lines.append(f"- {ev['raw'][:200]}")
        lines.append("")

    # Parsed results
    parsed = sections.get("workflow_parsed", [])
    if parsed:
        lines.append("## Parsed Workflow")
        lines.append("")
        for ev in parsed:
            lines.append(f"- **{ev['groups'][0]} actions**")
        lines.append("")

    goals = sections.get("goal", [])
    if goals:
        lines.append("### Goals")
        for ev in goals:
            lines.append(f"- {ev['groups'][0]}")
        lines.append("")

    triggers = sections.get("trigger", [])
    if triggers:
        lines.append("### Triggers")
        for ev in triggers:
            lines.append(f"- {ev['groups'][0]}")
        lines.append("")

    missing = sections.get("missing_info", [])
    if missing:
        lines.append("### Missing Info")
        for ev in missing:
            lines.append(f"- {ev['groups'][0]}")
        lines.append("")

    validation = sections.get("workflow_valid", [])
    if validation:
        lines.append("### Validation")
        for ev in validation:
            lines.append(f"- {ev['raw'][:200]}")
        lines.append("")

    errors = sections.get("error", [])
    if errors:
        lines.append("## ⚠️ Errors")
        lines.append("")
        for ev in errors:
            lines.append(f"- {ev['raw'][:300]}")
        lines.append("")

    # Summary stats
    lines.append("---")
    lines.append("## Quick Stats")
    lines.append("")
    lines.append(f"| Metric | Count |")
    lines.append(f"|--------|-------|")
    lines.append(f"| Phase 0 task decompositions | {len(decompositions)} |")
    lines.append(f"| Phase 1 capability bindings | {len(bindings)} |")
    lines.append(f"| Phase 2 slot groundings | {len(slots)} |")
    lines.append(f"| Phase 2 fallbacks used | {len(fallbacks)} |")
    lines.append(f"| Parsed workflows | {len(parsed)} |")
    lines.append(f"| Errors | {len(errors)} |")

    return "\n".join(lines)


def main():
    if len(sys.argv) < 2:
        print("Usage: python3 parse_ret_results.py <logfile> [output.md]")
        sys.exit(1)

    logfile = sys.argv[1]
    output = sys.argv[2] if len(sys.argv) > 2 else "report.md"

    print(f"Parsing: {logfile}")
    events = parse_log(logfile)
    print(f"Found {len(events)} RETO events")

    report = build_report(events)

    with open(output, "w") as f:
        f.write(report)

    print(f"Report written to: {output}")


if __name__ == "__main__":
    main()
