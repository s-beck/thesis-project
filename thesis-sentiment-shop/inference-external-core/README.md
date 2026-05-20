# inference-external-core – External Variants (X-Sync, X-Async)

Shared library for the external integration variants. Holds the
HuggingFace HTTP client, the wire-format DTOs, and the vendor-to-`FailureMode`
mapping policy. The two variant modules (`inference-external-sync` for X-Sync,
`inference-external-async` for X-Async) depend on this core and contribute
only their dispatch layer and Spring wiring.

This module is a compile-time library. It is a sibling of
`inference-self-hosted-core` and `inference-embedded-core`.

For variant selection and general backend setup, see `thesis-sentiment-shop/README.md`.

---

## Role in the architecture

The external variants delegate inference to the HuggingFace Inference API –
a vendor-managed, publicly hosted endpoint. No Python inference container is
needed.

The defining property of the external variants compared to the self-hosted
variants is the shift from operator-managed to vendor-managed infrastructure,
with the attendant implications for failure mode ownership and cold-start behaviour.

---

## Shared classes

| Class | Purpose |
|---|---|
| `HuggingFaceSentimentClient` | `SentimentClassifier` implementation. Calls the HuggingFace Inference API with a bearer-token authorization header. Maps HTTP and transport outcomes to `SentimentClassificationException`. Stateless after construction, thread-safe. |
| `HuggingFaceClassifyRequest` | Wire-format DTO for the HuggingFace request body. |
| `HuggingFaceClassifyResponse` | Wire-format DTO for the HuggingFace response body (nested label/score array). |
| `HuggingFaceLabelMapper` | Maps HuggingFace label strings to the application's `Sentiment` enum. |
| `ClassifyMessage` | AMQP request DTO (X-Async). Parallel record to the self-hosted variant; not shared. |
| `ClassifyResultMessage` | AMQP result DTO (X-Async). |

---

## Authentication

Both variants require a HuggingFace API token. The token is read from the
`HUGGINGFACE_API_TOKEN` environment variable and passed as a `Bearer` header.

---

## Transport-to-FailureMode mapping

The HuggingFace failure-mapping policy differs from the self-hosted policy:

| Wire outcome | `FailureMode` | Notes |
|---|---|---|
| 5xx (including 503 cold-start) | `UNREACHABLE` | Vendor unavailable; cold-start is logged distinctly |
| 429 (rate limit) | `UNREACHABLE` | Vendor capacity limit |
| 4xx (other) | `UNKNOWN` | Unexpected client error |
| Read/connect timeout | `TIMEOUT` | 2000 ms connect, 10 000 ms read |
| Connect refused / DNS failure | `UNREACHABLE` | Transport-level unreachable |
| Deserialisation failure, unrecognised label | `UNKNOWN` | Fallback |

The 4xx –> `UNKNOWN` / 5xx –> `UNREACHABLE` split differs deliberately from
the self-hosted 4xx/5xx –> `MODEL_ERROR` policy. A 5xx from HuggingFace
represents vendor unavailability, not a model forward-pass failure as it
would from the self-hosted Python service.

---

## X-Sync

Classification runs synchronously on the HTTP request thread. Identical
request-thread semantics to S-Sync, but calling the HuggingFace API instead
of the local Python service.

**Configuration:**

| Property / variable | Default | Purpose |
|---|---|---|
| `HUGGINGFACE_API_TOKEN` | *(must be set)* | Bearer token |
| `sentiment.external.url` | HuggingFace router URL | Inference endpoint |
| `sentiment.self-hosted.connect-timeout-ms` | `2000` | TCP connect timeout |
| `sentiment.self-hosted.read-timeout-ms` | `10000` | Read timeout (longer than S-Sync due to vendor cold-start) |

---

## X-Async

Classification is dispatched via RabbitMQ, but the **consumer runs inside the
Spring Boot JVM** – not in a separate Python process. The Java consumer dequeues
the request message and calls `HuggingFaceSentimentClient` directly. This is
the most operationally compact async variant: two infrastructure dependencies
(MariaDB, RabbitMQ), zero language-boundary processes.

Results are published back to a result queue via an exchange. A Spring
`ExternalSentimentResultListener` delivers results to `ReviewService` via
`SentimentResultSink`. The DLQ pattern and `PendingReviewSweeper` mirror
S-Async but the failure-mode header mechanism differs (X-Async stamps
`x-sentiment-failure-mode` on result messages so the DLQ listener can
recover the original `FailureMode` without relying on broker metadata).

---

## No retries

Neither X-Sync nor X-Async retries failed HTTP calls to HuggingFace. Vendor
failures surface to the application layer as `SentimentClassificationException`.
Broker-level redelivery in X-Async handles transient consumer failures, not
vendor API failures.

---

## Fault injection

Enable with `--sentiment.fault-injection.enabled=true`.

---

## Dependencies

- `inference-api` – `SentimentClassifier` contract, `FaultInjectingClassifier`
- `org.springframework:spring-web` – `RestClient` and exception hierarchy
- `com.fasterxml.jackson.core:jackson-databind` – DTO deserialisation
- `spring-amqp` / `spring-rabbit` – AMQP messaging (X-Async)