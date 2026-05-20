# scripts – Model export, performance campaign, and fault-tolerance harness

This directory contains the evaluation infrastructure for the thesis:
the ONNX model export script, JMeter test plans, post-processing scripts,
and the fault-tolerance bash harness.

---

## Model export (`export-model.py`)

A one-shot Python script that downloads the
`cardiffnlp/twitter-roberta-base-sentiment-latest` checkpoint from HuggingFace,
exports it to ONNX, and validates the artefact. This script is not part of
any build pipeline — it is run once per machine before using the embedded variants
(E-Sync, E-Async).

```bash
python -m venv .venv
source .venv/bin/activate          # Windows: .venv\Scripts\activate
pip install -r requirements.txt
python export-model.py
```

Output is written to `model-artefact/` at the project root (gitignored).
The X-Sync and X-Async variants do not need the artefact on disk – they reach
the same model checkpoint by reference via the HuggingFace API.

---

## Performance campaigns (`run-campaign.sh`)

Manages the full per-run JVM lifecycle for a performance measurement campaign:

1. Start the application under the selected Maven profile (`mvn spring-boot:run`)
2. Health-poll until the actuator reports UP
3. Run the JMeter test plan for the selected load profile
4. Tail-wait for async result drain (relevant for E-Async, S-Async, X-Async)
5. Stop the application
6. Cool-down before the next run

```bash
/opt/homebrew/bin/bash ./run-campaign.sh <variant>
```

> **bash 5+ required.** The script uses associative arrays (`declare -A`),
> which are not available in macOS system bash 3.2.
> Use Homebrew bash on macOS: `/opt/homebrew/bin/bash`.

### JMeter test plans

Three plans covering three load profiles:

| Plan | Profile | Concurrent threads | Description                                                             |
|---|---|---|-------------------------------------------------------------------------|
| `baseline.jmx` | baseline | low | Baseline – latency without contention                                   |
| `moderate.jmx` | moderate | medium | Moderate load                                                           |
| `high.jmx` | high | high | High load – backpressure and queue saturation visible in async variants |

All plans include a setUp thread group that fetches live product IDs from
`GET /api/products?size=100` and publishes them as JMeter properties, so
review submissions always target valid product IDs without hardcoding.

### Post-processing (`post-process.py`)

Reads the JTL output files produced by JMeter and emits:

- `per_run.csv` – one row per JTL file, including HTTP and application-level
  latency columns (the latter from JSONL measurement events).
- `summary.csv` – one row per (variant * load profile), selecting the median
  run by mean latency. Warmup runs are excluded.

For async variants, end-to-end latency is computed by pairing `review.submitted`
and `review.classified` events in the JSONL stream (by `reviewId`), rather than
relying on JMeter's HTTP measurement.

---

## Fault-tolerance harness (`run-fault-tolerance.sh`)

Manages the per-scenario lifecycle for fault-tolerance measurement:

1. Start the application with `--sentiment.fault-injection.enabled=true`
2. Health-poll until UP
3. Execute three phases via `curl`:
    - **Phase 1 (baseline):** 10 reviews, no fault active
    - **Phase 2 (fault):** 10 reviews, fault injected via the `/api/evaluation/fault-injection` endpoint
    - **Phase 3 (recovery):** 10 reviews, fault cleared
4. Tail-wait for async drain
5. Stop the application
6. Cool-down

```bash
/opt/homebrew/bin/bash ./run-fault-tolerance.sh <variant> <failure-mode>
```

The script walks the full Cartesian product of six variants * four failure modes
(24 cells). Each cell produces a `.outcome` sidecar file recording whether the
variant's fault wrapper accepted or refused the requested failure mode.

### Post-processing (`post-process-ft.py`)

Reads JSONL measurement events and `.outcome` sidecar files and emits:

- `ft_matrix.csv` – one row per (variant * failure mode) cell. Columns include outcome (`INJECTED`, `SKIPPED_BY_WRAPPER`,
  `STRUCTURAL`, `ERROR`), mechanism, and detail.
- `ft_summary.csv` – per-phase pass/fail evidence for `INJECTED` and `STRUCTURAL`
  cells.

### Failure modes and outcomes

| Outcome | Meaning                                                                          |
|---|----------------------------------------------------------------------------------|
| `INJECTED` | Fault wrapper accepted the mode                                                  |
| `SKIPPED_BY_WRAPPER` | Variant cannot structurally produce this failure mode and wrapper refused to arm |
| `STRUCTURAL` | Failure is produced by infrastructure (e.g. broker stop), not by injection       |
| `ERROR` | Harness or configuration error – cell requires investigation                     |