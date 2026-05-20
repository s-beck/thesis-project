# inference-self-hosted-core – Self-Hosted Variants (S-Sync, S-Async)

Shared library for the self-hosted integration variants. Holds the
`RestClient`-based HTTP client to the Python inference microservice, the
wire-format DTOs, and the transport-to-`FailureMode` mapping policy.
The two variant modules (`inference-self-hosted-sync` for S-Sync,
`inference-self-hosted-async` for S-Async) depend on this core and
contribute only their dispatch layer and Spring wiring.

This module is a compile-time library, not a deployment unit. The self-hosted
variants are classified as microservices with the Python `inference-service`
container as an independently-deployable process.

For variant selection and general backend setup, see `thesis-sentiment-shop/README.md`.
For the Python service, see `inference-service/README.md`.

---

## Role in the architecture

The self-hosted variants delegate inference to a Python Flask/Waitress service
running in a separate container. The Java application communicates with this
service over HTTP. The defining property of these variants, compared to the
embedded variants, is the presence of a real network boundary between the
application and the ML model, which expands the failure surface to its full
theoretical set.

---

## Shared classes

| Class | Purpose |
|---|---|
| `RemoteSentimentClient` | `SentimentClassifier` implementation. Owns a Spring `RestClient`. Maps HTTP and transport outcomes to `SentimentClassificationException` with the correct `FailureMode`. Stateless after construction, thread-safe. |
| `ClassifyRequest` | Wire-format DTO for `POST /classify` bodies. |
| `ClassifyResponse` | Wire-format DTO for `POST /classify` 200 responses. Carries `sentiment`, `confidence`, and Python-side `latencyMs`. |
| `ClassifyMessage` | AMQP request DTO (S-Async). |
| `ClassifyResultMessage` | AMQP result DTO (S-Async). |

---

## Transport-to-FailureMode mapping

| Wire outcome | Java exception                                              | `FailureMode` |
|---|-------------------------------------------------------------|---|
| 5xx response | `HttpServerErrorException`                                  | `MODEL_ERROR` |
| 4xx response | `HttpClientErrorException`                                  | `MODEL_ERROR` |
| Read timeout exceeded | `ResourceAccessException` (cause: `SocketTimeoutException`) | `TIMEOUT` |
| Connect refused / DNS failure | `ResourceAccessException` (cause: `ConnectException` etc.)  | `UNREACHABLE` |
| Deserialisation failure, unexpected status | `RestClientException` / `RestClientResponseException`       | `UNKNOWN` |
| 200 with null body or unrecognised label | –                                                           | `UNKNOWN` |

---

## S-Sync

Classification runs synchronously on the HTTP request thread via a blocking
`RestClient` call to the Python service. Identical request-thread semantics to
E-Sync, but with a real network boundary.

**Configuration:**

| Property | Default | Purpose |
|---|---|---|
| `sentiment.self-hosted.base-url` | `http://localhost:18000` | Python service URL |
| `sentiment.self-hosted.connect-timeout-ms` | `2000` | TCP connect timeout |
| `sentiment.self-hosted.read-timeout-ms` | `5000` | Read timeout |

**Failure surface:** all four `FailureMode` values apply. This is the
thesis-relevant finding: a network boundary gives the harness a non-`SKIPPED`
result for every failure mode.

---

## S-Async

Classification is dispatched via RabbitMQ. The HTTP request thread publishes a
`ClassifyMessage` to the broker and returns immediately after persisting the
review as pending. The Python consumer dequeues the message, classifies the
text, and publishes a `ClassifyResultMessage` back. The Spring `SentimentResultListener`
delivers the result to `ReviewService` via `SentimentResultSink`.

The broker provides structural retry behaviour via RabbitMQ redelivery and a dead-letter
queue (DLQ). Reviews that exhaust the redelivery limit land in the DLQ and the
`DeadLetterListener` records the failure mode on the review.

A `PendingReviewSweeper` handles the case where a review remains pending beyond
a configurable timeout (e.g. because the result was lost between consumer and
publisher).
---

## Latency semantics

`SentimentResult.latency` is the **caller-side wall-clock latency**: network
round-trip plus any queueing plus Python-side inference time. This matches the
embedded variants' semantics to allow cross-variant comparison.

`ClassifyResponse.latencyMs` carries the **Python-side intra-process inference
latency** separately, preserved for decomposition analysis if needed.

---

## No retries in S-Sync

`RemoteSentimentClient` does not retry failed calls. A retry policy would mask
the `TIMEOUT` and `UNREACHABLE` events that are the subject of the fault-tolerance
measurement. Retry behaviour belongs to S-Async, where it is a structural
property of the broker.

---

## Fault injection

Enable fault injection with `--sentiment.fault-injection.enabled=true` at startup.

---

## Dependencies

- `inference-api` – `SentimentClassifier` contract, `FaultInjectingClassifier`
- `org.springframework:spring-web` – `RestClient` and exception hierarchy
- `com.fasterxml.jackson.core:jackson-databind` – DTO deserialisation
- `spring-amqp` / `spring-rabbit` – AMQP messaging (S-Async)