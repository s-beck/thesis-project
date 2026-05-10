# inference-embedded-core

Shared core for the embedded variants (E-Sync and E-Async). Holds the
ONNX-Runtime-backed classifier and the CardiffNLP preprocessor. The variant modules
(`inference-embedded-sync`, `inference-embedded-async`) depend on this
core and contribute only their dispatch layer and Spring wiring.

This module exists because the two embedded variants share the entire
inference stack – model loading, tokenisation, preprocessing, the
synchronous `classify(...)` body – and only diverge in how that
synchronous body is invoked. Duplicating it across the two variant
modules would put the fragile native-handle code in two places
where it could drift.

## Status as a deployment unit

This module is a compile-time library, not a deployment unit. At
runtime the embedded variants ship as a single Spring Boot fat jar.
The core's classes are loaded into the same JVM as `web`, `reviews`,
and the variant-specific dispatch classes, and called in-process. The
modular monolith property of the embedded variants is unchanged by
the extraction.

## Public surface

| Class                                      | Purpose                                                                                                                                                                                                               |
|--------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `OnnxSentimentClassifier`                  | The actual ONNX-Runtime-backed `SentimentClassifier`. Loads `model.onnx` and `tokenizer.json` at construction. `classify(text)` runs synchronously on the calling thread. Stateless after construction and thread-safe |
| `ReviewTextPreprocessor`                   | Replicates the CardiffNLP `@user` / `http` substitution. Stateless and thread-safe.                                                                                                       |
| `FaultInjectingClassifier`                 | Decorator over any `SentimentClassifier`. Produces injected failures on demand for the fault-tolerance harness. Variant-specific failure-mode applicability is supplied at construction.                      |

The variant modules wire these classes into Spring. This module has
no `@Configuration` and depends on no Spring artefact.

## Dependencies

- `inference-api` – the `SentimentClassifier` contract
- `com.microsoft.onnxruntime:onnxruntime` – model execution
- `ai.djl.huggingface:tokenizers` – tokenisation
