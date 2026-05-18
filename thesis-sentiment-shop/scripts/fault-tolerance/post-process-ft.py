from __future__ import annotations

import argparse
import json
import re
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

import pandas as pd

PHASE_REVIEWS = 10  # must match run-fault-tolerance.sh
PHASE_BEARING_OUTCOMES = {"INJECTED", "STRUCTURAL"}

# A jsonl file's name for injectable run (ft-<mode>) or a structural run (ft-<mode>-structural)
# structural indicates the broker-stop fallback was executed so the file's events represent the structural run, not the injector attempt
FILENAME_RE = re.compile(
    r"^(?P<variant>[a-z-]+)_ft-(?P<mode>[a-z_]+?)(?P<struct>-structural)?\.jsonl$"
)

@dataclass
class PhaseStats:
    review_ids: set[int] = field(default_factory=set)
    classified: set[int] = field(default_factory=set)
    failed: dict[int, str] = field(default_factory=dict)  # reviewId -> failureMode
    swept: set[int] = field(default_factory=set)
    dlq: set[int] = field(default_factory=set)

@dataclass
class CellRecord:
    variant: str
    failure_mode: str
    outcome: str = "NOT_ATTEMPTED"
    mechanism: str = ""
    detail: str = ""
    phase_stats: dict[str, PhaseStats] = field(default_factory=dict)

def _classify_phase_by_marker(events: list[dict]) -> dict[int, str]:
    phase_of_review: dict[int, str] = {}
    current_phase = "baseline"
    for ev in events:
        et = ev.get("event")
        if et == "fault-injection.armed":
            current_phase = "fault"
        elif et == "fault-injection.cleared":
            current_phase = "recovery"
        elif et == "review.submitted":
            rid = ev.get("reviewId")
            if rid is not None and rid not in phase_of_review:
                phase_of_review[rid] = current_phase
    return phase_of_review

def _classify_phase_by_count(events: list[dict]) -> dict[int, str]:
    phase_of_review: dict[int, str] = {}
    submit_order: list[int] = []
    seen: set[int] = set()
    for ev in events:
        if ev.get("event") == "review.submitted":
            rid = ev.get("reviewId")
            if rid is not None and rid not in seen:
                seen.add(rid)
                submit_order.append(rid)
    for idx, rid in enumerate(submit_order):
        if idx < PHASE_REVIEWS:
            phase_of_review[rid] = "baseline"
        elif idx < 2 * PHASE_REVIEWS:
            phase_of_review[rid] = "fault"
        elif idx < 3 * PHASE_REVIEWS:
            phase_of_review[rid] = "recovery"
        else:
            phase_of_review[rid] = "overflow"
    return phase_of_review

def _aggregate_phase_stats(
    events: list[dict],
    phase_of_review: dict[int, str],
) -> dict[str, PhaseStats]:
    stats: dict[str, PhaseStats] = defaultdict(PhaseStats)
    for ev in events:
        et = ev.get("event")
        rid = ev.get("reviewId")
        if rid is None:
            continue
        phase = phase_of_review.get(rid)
        if phase is None:
            continue
        if et == "review.submitted":
            stats[phase].review_ids.add(rid)
        elif et == "review.classified":
            stats[phase].classified.add(rid)
        elif et == "review.failed":
            stats[phase].failed[rid] = ev.get("failureMode", "")
            if ev.get("origin") == "sweeper":
                stats[phase].swept.add(rid)
            elif ev.get("origin") == "dlq":
                stats[phase].dlq.add(rid)
        elif et == "sweeper.swept":
            stats[phase].swept.add(rid)
    return stats

def _load_jsonl(path: Path) -> list[dict]:
    events: list[dict] = []
    with path.open() as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                events.append(json.loads(line))
            except json.JSONDecodeError as exc:
                print(f"  warning: skipping malformed JSON in {path.name}: {exc}",
                      file=sys.stderr)
    return events

def _parse_sidecar(path: Path) -> dict[str, str]:
    """Parse a key=value .outcome sidecar."""
    out: dict[str, str] = {}
    if not path.is_file():
        return out
    for line in path.read_text().splitlines():
        if "=" not in line:
            continue
        k, v = line.split("=", 1)
        out[k.strip()] = v.strip()
    return out

def _build_phase_rows(cell: CellRecord) -> list[dict]:
    rows: list[dict] = []
    mode = cell.failure_mode
    for phase in ("baseline", "fault", "recovery"):
        if phase not in cell.phase_stats:
            rows.append({
                "variant": cell.variant,
                "failure_mode": mode,
                "outcome": cell.outcome,
                "mechanism": cell.mechanism,
                "phase": phase,
                "review_count": 0,
                "classified_count": 0,
                "failed_count": 0,
                "expected_failure_mode_match": "",
                "swept_count": 0,
                "dlq_count": 0,
                "notes": "no events for this phase",
            })
            continue

        ps = cell.phase_stats[phase]
        review_count = len(ps.review_ids)
        classified_count = len(ps.classified)
        failed_count = len(ps.failed)

        notes_parts: list[str] = []
        if phase == "baseline":
            expected = "n/a"
            if failed_count > 0:
                notes_parts.append(f"unexpected {failed_count} failed in baseline")
        elif phase == "fault":
            matching = sum(1 for fm in ps.failed.values() if fm == mode)
            non_matching = failed_count - matching
            expected = f"{matching}/{failed_count}"
            if non_matching > 0:
                bad_modes = sorted({fm for fm in ps.failed.values() if fm != mode})
                notes_parts.append(f"other failure modes seen: {','.join(bad_modes)}")
            if classified_count > 0:
                notes_parts.append(f"{classified_count} classified in fault phase (race)")
        else:  # recovery
            expected = "n/a"
            if failed_count > 0:
                notes_parts.append(f"unexpected {failed_count} failed in recovery")

        rows.append({
            "variant": cell.variant,
            "failure_mode": mode,
            "outcome": cell.outcome,
            "mechanism": cell.mechanism,
            "phase": phase,
            "review_count": review_count,
            "classified_count": classified_count,
            "failed_count": failed_count,
            "expected_failure_mode_match": expected,
            "swept_count": len(ps.swept),
            "dlq_count": len(ps.dlq),
            "notes": "; ".join(notes_parts),
        })
    return rows

def _build_matrix_row(cell: CellRecord) -> dict:
    headline = ""
    if cell.outcome in PHASE_BEARING_OUTCOMES:
        fault = cell.phase_stats.get("fault")
        if fault is not None:
            matching = sum(1 for fm in fault.failed.values() if fm == cell.failure_mode)
            total = len(fault.failed)
            headline = f"fault-phase: {matching}/{total} {cell.failure_mode}"
            if cell.outcome == "STRUCTURAL" and len(fault.swept) > 0:
                headline += f" (swept={len(fault.swept)})"
    return {
        "variant": cell.variant,
        "failure_mode": cell.failure_mode,
        "outcome": cell.outcome,
        "mechanism": cell.mechanism,
        "fault_phase_summary": headline,
        "detail": cell.detail,
    }

def _collect_cells(in_dir: Path) -> dict[tuple[str, str], CellRecord]:
    cells: dict[tuple[str, str], CellRecord] = {}

    for path in sorted(in_dir.glob("*_ft-*.outcome")):
        sc = _parse_sidecar(path)
        variant = sc.get("variant")
        mode = sc.get("failure_mode")
        outcome = sc.get("outcome", "ERROR")
        detail = sc.get("detail", "")
        if not variant or not mode:
            print(f"  warning: skipping malformed sidecar: {path.name}",
                  file=sys.stderr)
            continue

        mechanism = "" # is implied by outcome
        if outcome == "INJECTED":
            mechanism = "injector"
        elif outcome == "STRUCTURAL":
            mechanism = "structural"
        key = (variant, mode)
        existing = cells.get(key)
        if existing is None:
            cells[key] = CellRecord(variant=variant, failure_mode=mode,
                                    outcome=outcome, detail=detail,
                                    mechanism=mechanism)
        else:
            if outcome in PHASE_BEARING_OUTCOMES and existing.outcome not in PHASE_BEARING_OUTCOMES:
                existing.outcome = outcome
                existing.detail = detail
                existing.mechanism = mechanism

    for path in sorted(in_dir.glob("*_ft-*.jsonl")):
        m = FILENAME_RE.match(path.name)
        if not m:
            continue
        variant = m.group("variant")
        mode = m.group("mode").upper()
        is_structural = m.group("struct") is not None

        events = _load_jsonl(path)
        if not events:
            continue

        if is_structural:
            phase_of_review = _classify_phase_by_count(events)
        else:
            phase_of_review = _classify_phase_by_marker(events)
        stats = _aggregate_phase_stats(events, phase_of_review)

        key = (variant, mode)
        cell = cells.get(key)
        if cell is None:
            # JSONL present but no sidecar (e.g. runs from older versions of the harness, or interrupted runs) -> infer outcome
            cell = CellRecord(variant=variant, failure_mode=mode)
            cells[key] = cell
            if is_structural:
                cell.outcome = "STRUCTURAL"
                cell.detail = "outcome inferred (no sidecar)"
            else:
                if any(ev.get("event") == "fault-injection.armed" for ev in events):
                    cell.outcome = "INJECTED"
                    cell.detail = "outcome inferred (no sidecar)"
                else:
                    cell.outcome = "ERROR"
                    cell.detail = "JSONL has no arm event and no sidecar"

        if cell.outcome == "STRUCTURAL" and is_structural:
            cell.phase_stats = {k: v for k, v in stats.items() if k != "overflow"}
            cell.mechanism = "structural"
        elif cell.outcome == "INJECTED" and not is_structural:
            cell.phase_stats = {k: v for k, v in stats.items() if k != "overflow"}
            cell.mechanism = "injector"
        elif cell.outcome not in PHASE_BEARING_OUTCOMES:
            cell.mechanism = "structural" if is_structural else "injector"

    return cells

def _default_input_dir() -> Path:
    script_dir = Path(__file__).resolve().parent
    project_root = script_dir.parent.parent
    return project_root / "logs" / "measurement"

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--input-dir",
        default=str(_default_input_dir()),
        help="Directory containing <variant>_ft-<mode>.jsonl and .outcome files "
             "(defaults to <project root>/logs/measurement)",
    )
    parser.add_argument(
        "--summary-output",
        default="ft_summary.csv",
        help="Per-phase pass/fail CSV (phase-bearing outcomes only)",
    )
    parser.add_argument(
        "--matrix-output",
        default="ft_matrix.csv",
        help="One-row-per-cell CSV showing the discovered injectability matrix",
    )
    args = parser.parse_args()

    in_dir = Path(args.input_dir)
    if not in_dir.is_dir():
        print(f"Input directory not found: {in_dir}", file=sys.stderr)
        return 1

    cells = _collect_cells(in_dir)
    if not cells:
        print(f"No fault-tolerance artefacts found in {in_dir}", file=sys.stderr)
        return 1

    # Matrix (every cell)
    matrix_rows = [_build_matrix_row(c) for c in cells.values()]
    matrix_df = pd.DataFrame(matrix_rows).sort_values(
        ["variant", "failure_mode"]).reset_index(drop=True)
    matrix_df.to_csv(args.matrix_output, index=False)
    print(f"Wrote {len(matrix_df)} rows to {args.matrix_output}")

    # Per-phase summary (phase-bearing outcomes only)
    phase_rows: list[dict] = []
    for cell in cells.values():
        if cell.outcome in PHASE_BEARING_OUTCOMES:
            phase_rows.extend(_build_phase_rows(cell))

    if phase_rows:
        df = pd.DataFrame(phase_rows)
        phase_order = pd.CategoricalDtype(
            ["baseline", "fault", "recovery"], ordered=True)
        df["phase"] = df["phase"].astype(phase_order)
        df = df.sort_values(["variant", "failure_mode", "phase"]).reset_index(drop=True)
        df.to_csv(args.summary_output, index=False)
        print(f"Wrote {len(df)} rows to {args.summary_output}")
    else:
        pd.DataFrame(columns=[
            "variant", "failure_mode", "outcome", "mechanism", "phase",
            "review_count", "classified_count", "failed_count",
            "expected_failure_mode_match", "swept_count", "dlq_count", "notes",
        ]).to_csv(args.summary_output, index=False)
        print(f"Wrote 0 rows to {args.summary_output} "
              "(no phase-bearing outcomes yet)")

    return 0

if __name__ == "__main__":
    sys.exit(main())
