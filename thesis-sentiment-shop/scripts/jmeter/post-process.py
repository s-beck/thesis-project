import argparse
import json
import re
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Optional

import numpy as np
import pandas as pd

SYNC_VARIANTS = {"e-sync", "s-sync", "x-sync"}
ASYNC_VARIANTS = {"e-async", "s-async", "x-async"}

# Filename pattern: <variant>_<profile>_<runId>.jtl
JTL_PATTERN = re.compile(
    r"^(?P<variant>e-sync|e-async|s-sync|s-async|x-sync|x-async)_"
    r"(?P<profile>baseline|moderate|high)_"
    r"(?P<run_id>[A-Za-z0-9]+)\.jtl$"
)

@dataclass
class RunSummary:
    variant: str
    profile: str
    run_id: str
    is_warmup: bool
    samples_total: int
    samples_success: int
    samples_failed: int
    duration_s: float
    throughput_rps: float
    # JMeter HTTP latency
    http_mean_ms: float
    http_median_ms: float
    http_p95_ms: float
    http_p99_ms: float
    # Application-recorded latency (from JSONL)
    app_n: int
    app_mean_ms: Optional[float]
    app_median_ms: Optional[float]
    app_p95_ms: Optional[float]
    app_p99_ms: Optional[float]
    # Async-only counters
    submitted_n: int
    classified_n: int
    failed_n: int
    swept_n: int
    pairing_loss_pct: Optional[float]  # submitted - classified - failed, vs submitted


def percentiles(values: np.ndarray) -> tuple[float, float, float, float]:
    if values.size == 0:
        return (float("nan"),) * 4
    return (
        float(values.mean()),
        float(np.percentile(values, 50)),
        float(np.percentile(values, 95)),
        float(np.percentile(values, 99)),
    )


def parse_jtl(path: Path) -> pd.DataFrame:
    if not path.exists():
        return pd.DataFrame()
    df = pd.read_csv(
        path,
        engine="python",
        on_bad_lines="warn",
        quoting=0,
    )
    # JMeter columns of interest: timeStamp (epoch ms), elapsed (ms), success
    for col in ("timeStamp", "elapsed"):
        if col in df.columns:
            df[col] = pd.to_numeric(df[col], errors="coerce")
    if "success" in df.columns:
        df["success"] = df["success"].astype(str).str.lower() == "true"
    df = df.dropna(subset=["timeStamp", "elapsed"])
    return df


def parse_jsonl(path: Path) -> pd.DataFrame:
    """Read application-recorded measurement events from JSONL."""
    if not path.exists():
        return pd.DataFrame()
    records = []
    with path.open("r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                records.append(json.loads(line))
            except json.JSONDecodeError:
                continue  # tolerate a partial trailing line
    return pd.DataFrame(records) if records else pd.DataFrame()


def summarise_run(jtl_path: Path, jsonl_dir: Path) -> Optional[RunSummary]:
    m = JTL_PATTERN.match(jtl_path.name)
    if not m:
        return None
    variant = m.group("variant")
    profile = m.group("profile")
    run_id = m.group("run_id")
    is_warmup = run_id == "warmup"

    jtl = parse_jtl(jtl_path)
    if jtl.empty:
        print(f"  WARN: empty or unreadable JTL: {jtl_path.name}")
        return None

    samples_total = len(jtl)
    success_mask = jtl["success"] if "success" in jtl.columns else pd.Series(True, index=jtl.index)
    samples_success = int(success_mask.sum())
    samples_failed = samples_total - samples_success

    # Wall-clock duration: span of timestamps in seconds
    duration_s = float((jtl["timeStamp"].max() - jtl["timeStamp"].min()) / 1000.0)
    if duration_s <= 0:
        duration_s = float("nan")
    throughput_rps = samples_success / duration_s if duration_s > 0 else float("nan")

    # HTTP latency from successful samples only
    http_vals = jtl.loc[success_mask, "elapsed"].to_numpy()
    http_mean, http_median, http_p95, http_p99 = percentiles(http_vals)

    # Application-recorded latency from JSONL
    jsonl_path = jsonl_dir / f"{variant}_{run_id}.jsonl"
    jsonl = parse_jsonl(jsonl_path)

    submitted_n = classified_n = failed_n = swept_n = 0
    app_vals = np.array([], dtype=float)

    if not jsonl.empty and "event" in jsonl.columns:
        submitted_n = int((jsonl["event"] == "review.submitted").sum())
        classified = jsonl[jsonl["event"] == "review.classified"]
        classified_n = len(classified)
        failed_n = int((jsonl["event"] == "review.failed").sum())
        swept_n = int((jsonl["event"] == "sweeper.swept").sum())

        if not classified.empty and "latencyMs" in classified.columns:
            app_vals = pd.to_numeric(
                classified["latencyMs"], errors="coerce"
            ).dropna().to_numpy()

    app_mean, app_median, app_p95, app_p99 = percentiles(app_vals)

    pairing_loss_pct: Optional[float] = None
    if submitted_n > 0:
        accounted_for = classified_n + failed_n
        pairing_loss_pct = (
            100.0 * (submitted_n - accounted_for) / submitted_n
        )

    return RunSummary(
        variant=variant,
        profile=profile,
        run_id=run_id,
        is_warmup=is_warmup,
        samples_total=samples_total,
        samples_success=samples_success,
        samples_failed=samples_failed,
        duration_s=duration_s,
        throughput_rps=throughput_rps,
        http_mean_ms=http_mean,
        http_median_ms=http_median,
        http_p95_ms=http_p95,
        http_p99_ms=http_p99,
        app_n=int(app_vals.size),
        app_mean_ms=app_mean if app_vals.size else None,
        app_median_ms=app_median if app_vals.size else None,
        app_p95_ms=app_p95 if app_vals.size else None,
        app_p99_ms=app_p99 if app_vals.size else None,
        submitted_n=submitted_n,
        classified_n=classified_n,
        failed_n=failed_n,
        swept_n=swept_n,
        pairing_loss_pct=pairing_loss_pct,
    )


def select_median_run(group: pd.DataFrame, headline_col: str) -> pd.Series:
    sorted_group = group.sort_values(headline_col, kind="mergesort").reset_index(drop=True)
    return sorted_group.iloc[len(sorted_group) // 2]


def build_summary(per_run: pd.DataFrame) -> pd.DataFrame:
    measurement = per_run[~per_run["is_warmup"]].copy()
    if measurement.empty:
        return pd.DataFrame()

    # Variant-dependent headline metric: sync -> http_mean_ms, async -> app_mean_ms
    measurement["headline_mean_ms"] = measurement.apply(
        lambda r: r["http_mean_ms"] if r["variant"] in SYNC_VARIANTS else r["app_mean_ms"],
        axis=1,
    )
    measurement["headline_source"] = measurement["variant"].apply(
        lambda v: "http" if v in SYNC_VARIANTS else "app"
    )

    rows = []
    for (variant, profile), group in measurement.groupby(["variant", "profile"]):
        median_row = select_median_run(group, "headline_mean_ms")
        runs_n = len(group)
        headline_source = median_row["headline_source"]
        # Select percentile columns matching the headline source
        if headline_source == "http":
            mean_ms = median_row["http_mean_ms"]
            median_ms = median_row["http_median_ms"]
            p95_ms = median_row["http_p95_ms"]
            p99_ms = median_row["http_p99_ms"]
        else:
            mean_ms = median_row["app_mean_ms"]
            median_ms = median_row["app_median_ms"]
            p95_ms = median_row["app_p95_ms"]
            p99_ms = median_row["app_p99_ms"]

        rows.append({
            "variant": variant,
            "profile": profile,
            "runs_aggregated": runs_n,
            "selected_run_id": median_row["run_id"],
            "headline_source": headline_source,
            "mean_ms": mean_ms,
            "median_ms": median_ms,
            "p95_ms": p95_ms,
            "p99_ms": p99_ms,
            "throughput_rps": median_row["throughput_rps"],
            "samples_success": median_row["samples_success"],
            "samples_failed": median_row["samples_failed"],
            "pairing_loss_pct": median_row["pairing_loss_pct"],
        })
    return pd.DataFrame(rows).sort_values(["variant", "profile"]).reset_index(drop=True)


def main() -> None:
    here = Path(__file__).resolve().parent
    project = here.parent.parent  # scripts/jmeter/ -> project root

    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--results-dir", type=Path,
                        default=here / "results",
                        help="Directory containing .jtl files")
    parser.add_argument("--jsonl-dir", type=Path,
                        default=project / "logs" / "measurement",
                        help="Directory containing application .jsonl files")
    parser.add_argument("--out-dir", type=Path, default=here / "summaries",
                        help="Where to write per_run.csv and summary.csv")
    args = parser.parse_args()

    args.out_dir.mkdir(parents=True, exist_ok=True)

    jtl_files = sorted(args.results_dir.glob("*.jtl"))
    print(f"Found {len(jtl_files)} JTL files in {args.results_dir}")

    summaries = []
    for jtl in jtl_files:
        s = summarise_run(jtl, args.jsonl_dir)
        if s is None:
            print(f"  skipped: {jtl.name}")
            continue
        summaries.append(asdict(s))
        print(f"  {jtl.name}: "
              f"n={s.samples_success}/{s.samples_total} "
              f"http_mean={s.http_mean_ms:.1f}ms "
              f"app_n={s.app_n}")

    if not summaries:
        print("No runs to aggregate.")
        return

    per_run = pd.DataFrame(summaries)
    per_run_path = args.out_dir / "per_run.csv"
    per_run.to_csv(per_run_path, index=False)
    print(f"\nWrote {per_run_path}")

    summary = build_summary(per_run)
    summary_path = args.out_dir / "summary.csv"
    summary.to_csv(summary_path, index=False)
    print(f"Wrote {summary_path}")

    print("\nSummary (chapter 5 table):")
    print(summary.to_string(index=False))


if __name__ == "__main__":
    main()