# inference-embedded-async

E-Async variant. Asynchronous, in-process inference via a bounded
thread-pool dispatcher over the shared embedded core. Same JVM, same
ONNX model, same classifier as E-Sync — the only thing that differs
between the two variants is the dispatch layer.

The classifier core lives in `inference-embedded-core`. This module
contributes the dispatcher `EmbeddedAsyncSentimentClassifier`, a
lifecycle wrapper, and the Spring wiring that exposes them as the
`AsyncSentimentClassifier` bean.

## Activation

```
mvn -Pe-async clean install
mvn -Pe-async -pl web spring-boot:run
```

Activating the `e-async` Maven profile pulls this module onto the
classpath in place of `inference-stub`. See the top-level README for
the full profile/variant mapping.

To run with fault injection enabled (for chapter 5 fault-tolerance
tests):

```
mvn -Pe-async -pl web spring-boot:run \
    -Dspring-boot.run.arguments=--sentiment.fault-injection.enabled=true
```

## Configuration

| Variable / property                       | Default            | Purpose                                                                                                                                       |
|-------------------------------------------|--------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| `SENTIMENT_MODEL_PATH`                    | `./model-artefact` | Directory containing `model.onnx`, `tokenizer.json`, `config.json`. Identical to E-Sync                                                       |
| `sentiment.async.workers`                 | `4`                | Worker thread count with fixed-size pool                                                                                                      |
| `sentiment.async.queue-capacity`          | `256`              | Bounded queue capacity. When full, `submit(...)` throws a `SentimentClassificationException(UNREACHABLE)` synchronously – see Failure semantics |
| `sentiment.async.shutdown-grace-ms`       | `5000`             | Time to wait on shutdown for in-flight tasks to complete before forced cancellation                                                           |
| `sentiment.fault-injection.enabled`       | `false`            | When `true`, the dispatcher wraps the core classifier in `FaultInjectingClassifier`            |

Worker and queue sizing are deliberately bounded. The thesis records
this as a property of the variant. An unbounded queue would hide
backpressure from the latency measurements.

## Behaviour

1. **On boot:** `EmbeddedAsyncSentimentClassifierConfiguration`
   constructs an `OnnxSentimentClassifier` from the embedded core, then
   optionally wraps it in `FaultInjectingClassifier`. The result is
   handed to an `EmbeddedAsyncSentimentClassifier` dispatcher, which
   creates a fixed-size `ThreadPoolExecutor` with a bounded
   `LinkedBlockingQueue` and the `AbortPolicy` rejection handler. The
   dispatcher and the underlying ONNX core are bundled into one
   `AsyncSentimentClassifier` bean with a single `destroyMethod`.
2. **Submit:** `ReviewService.submit(...)` persists the review in the
   "pending" state (both `sentiment` and `classificationFailureMode`
   null), then calls `asyncClassifier.submit(reviewId, text)`. The call
   returns immediately. If the queue is saturated, the call throws
   synchronously and the service records the failure on the review before
   returning.
3. **Classify (worker thread):** the worker delegates to the core
   classifier, then calls back into `ReviewService` via
   `SentimentResultSink.onResult(...)` or `onFailure(...)`. Worker
   exceptions never escape the task body.
4. **Shutdown:** Spring calls `shutdown()` on the bean. The executor is
   shut down first, so no worker is mid-call when the native handles
   are released, with a grace period for in-flight tasks. The ONNX
   session is closed after the executor terminates. Tasks
   force-cancelled at the grace boundary leave their reviews in the
   pending state – documented as a known property of the variant.

## Fault injection (evaluation preparation)

`FaultInjectingClassifier` from `inference-embedded-core` is reused
unchanged. The wrapper sits *inside* the dispatcher, wrapping the core classifier
directly. Failures it injects therefore appear on worker threads and
are delivered via `onFailure(...)` callback, exactly as a real
`MODEL_ERROR` would be. Injection of `UNREACHABLE`, however, requires
a different mechanism because the dispatcher itself, not the classifier, 
is the source of that failure mode. The evaluation harness drives this 
by submitting enough work to fill the
queue while holding the workers occupied.

## Dependencies

- `inference-api` (compile)
- `inference-embedded-core` (compile)
- `spring-context`, `spring-boot-autoconfigure` — for `@Configuration` only
