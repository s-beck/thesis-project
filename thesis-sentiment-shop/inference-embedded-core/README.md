# inference-embedded-core – Embedded Variants (E-Sync, E-Async)

Shared library for the embedded integration variants. Holds the
ONNX-Runtime-backed classifier and the CardiffNLP preprocessor. The two variant modules
(`inference-embedded-sync`, `inference-embedded-async`) depend on this
core and contribute only their dispatch layer and Spring wiring.

This module is a compile-time library, not a deployment unit. Both embedded
variants ship as a single Spring Boot fat jar, the modular monolith property
of the variants is preserved.

For variant selection and general backend setup, see `thesis-sentiment-shop/README.md`.

---

## Role in the architecture

The embedded variants run inference in the same JVM as the web application –
no network boundary between the application and the ML model. The model is
loaded at application startup and kept resident for the lifetime of the process.

Both variants use the same model (`cardiffnlp/twitter-roberta-base-sentiment-latest`
exported to ONNX), the same tokeniser, and the same preprocessing logic.
The only difference between E-Sync and E-Async is the dispatch layer.

---

## Shared classes

| Class | Purpose |
|---|---|
| `OnnxSentimentClassifier` | ONNX-Runtime-backed `SentimentClassifier`. Loads `model.onnx` and `tokenizer.json` at construction. `classify(text)` runs synchronously on the calling thread. Stateless after construction, thread-safe. |
| `ReviewTextPreprocessor` | Replicates the CardiffNLP `@user` / URL substitution. Stateless, thread-safe. |

---

## E-Sync

Classification runs synchronously on the HTTP request thread. The `classify(text)`
call blocks until the ONNX session returns. No queue, no dispatcher, no worker threads.

---

## E-Async

Classification is dispatched to a bounded `ThreadPoolExecutor`. The HTTP request
thread returns immediately after persisting the review in a `pending` state.
The result arrives at `ReviewService` via the `SentimentResultSink` callback.

**Dispatch configuration:**

| Property | Default | Purpose |
|---|---|---|
| `sentiment.async.workers` | `4` | Fixed worker thread count |
| `sentiment.async.queue-capacity` | `256` | Bounded queue capacity. When full, `submit(...)` throws `UNREACHABLE` synchronously. |
| `sentiment.async.shutdown-grace-ms` | `5000` | Grace period before forced cancellation on shutdown |

---

## Model artefact

The ONNX model artefact must be present on disk before starting either embedded
variant. Generate it with the export script (one-time step):

```bash
cd scripts/export-model
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python export-model.py
```

Output is written to `model-artefact/` at the project root (gitignored).
The artefact location is configured via `SENTIMENT_MODEL_PATH` (default: `./model-artefact`).

---

## Fault injection

Fault injection is enabled by passing `--sentiment.fault-injection.enabled=true` at startup.
When enabled, the classifier bean is replaced by a `FaultInjectingClassifier` wrapper
that can be armed via the `/api/evaluation/fault-injection` HTTP endpoint.

The set of injectable failure modes reflects the structural applicability table above:
`{MODEL_ERROR, UNKNOWN}` for E-Sync and `{MODEL_ERROR, UNREACHABLE, UNKNOWN}` for E-Async.
Requesting a mode outside this set records the outcome as `SKIPPED` rather than silently
ignoring the request, so the harness can distinguish "variant cannot fail this way" from
"no failure was injected."

---

## Dependencies

- `inference-api` — `SentimentClassifier` contract, `FaultInjectingClassifier`
- `com.microsoft.onnxruntime:onnxruntime` — model execution
- `ai.djl.huggingface:tokenizers` — tokenisation
