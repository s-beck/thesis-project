# inference-embedded-sync

E-Sync variant realizes the synchronous, in-process inference via Microsoft ONNX
Runtime for Java.

The model is loaded into the JVM at application start-up, kept resident for the lifetime of
the process, and called synchronously on the request thread. The actual classifier implementation
lives in `inference-embedded-core`. This module contributes only the Spring wiring that registers 
the core's `OnnxSentimentClassifier` as the `SentimentClassifier` bean.

## Activation

```
mvn -Pe-sync clean install
mvn -Pe-sync -pl web spring-boot:run
```

Activating the `e-sync` Maven profile pulls this module onto the
classpath in place of `inference-stub`. For the full profile/variant mapping
see the `thesis-sentiment-shop/README.md` one level up.

To run with fault injection enabled (for fault-tolerance tests):

```
mvn -Pe-sync -pl web spring-boot:run \
    -Dspring-boot.run.arguments=--sentiment.fault-injection.enabled=true
```

## Configuration

| Variable / property      | Default            | Purpose |
|--------------------------|--------------------|---------|
| `SENTIMENT_MODEL_PATH`   | `./model-artefact` | Directory containing `model.onnx`, `tokenizer.json`, `config.json` |
| `sentiment.fault-injection.enabled`  | `false`            | When `true`, registers the fault-injecting wrapper instead of the bare classifier |

The `SENTIMENT_MODEL_PATH` is resolved relative to the working directory at start-up. The
default points at the `scripts/export-model` output sitting at the
project root.

## Behaviour

1. **On boot:** `EmbeddedSyncSentimentClassifierConfiguration` creates a
   single `OnnxSentimentClassifier` bean from
   `inference-embedded-core` with `destroyMethod = "close"`. By
   default, with fault injection disabled, that bean is an
   `OnnxSentimentClassifier` constructed directly. With fault injection
   enabled, it is a `FaultInjectingClassifierAdapter` wrapping a
   private `OnnxSentimentClassifier`. The two cases are mutually exclusive.
2. **Classify:** the call goes straight through to the core
   classifier, which runs the CardiffNLP-style preprocessing (`@user` 
   and URL substitution), tokenises, builds two `OnnxTensor`s for 
   `input_ids` and `attention_mask`, runs the session,
   takes argmax of the resulting logits, applies a numerically stable
   softmax for the confidence score, and returns a `SentimentResult`.
3. **Shutdown:** Spring calls `close()` on the bean, which releases the
   native handles held by `OrtSession` and the tokenizer.

## Fault injection (evaluation preparation)

`FaultInjectingClassifier` from `inference-embedded-core` is a
decorator that produces injected failures on demand for the 
fault-tolerance measurements. It is loaded into the Spring context
only when `sentiment.fault-injection.enabled=true`. In normal
operation the wrapper class is not instantiated and there is no
overhead.

The set of failure modes the E-Sync variant can structurally produce
(`MODEL_ERROR`, `UNKNOWN`) is passed to the wrapper at construction
time. Modes outside this set are recorded as `SKIPPED` rather than
silently ignored or actually injected. The harness reads this state
to distinguish "variant cannot fail this way" from "variant did not
fail this time."

The wrapper API:

| Method                                | Purpose                                                                          |
|---------------------------------------|----------------------------------------------------------------------------------|
| `scheduleFailures(FailureMode, int)`  | Queue N consecutive failures of the given mode for the next N `classify(...)` calls |
| `clear()`                             | Discard any queued failures and reset to idle                                    |
| `currentState()`                      | Inspect the wrapper's state without modifying it                                 |

The state returned by `currentState()` is one of:

| Disposition | Meaning                                                                                                                     |
|-------------|-----------------------------------------------------------------------------------------------------------------------------|
| `IDLE`      | No failures queued. The next `classify(...)` delegates to the real classifier                                               |
| `ARMED`     | Failures queued and subsequent calls will throw `SentimentClassificationException`                                          |
| `SKIPPED`   | A `scheduleFailures(...)` call requested a `FailureMode` the variant cannot structurally produce. It is recorded, not armed |

`FaultInjectingClassifierAdapter` extends the core wrapper with 
`AutoCloseable` so Spring can release the native ONNX
handles on context shutdown.

## Dependencies

- `inference-api` (compile)
- `inference-embedded-core` (compile)
- `spring-context`, `spring-boot-autoconfigure` — for `@Configuration` only
