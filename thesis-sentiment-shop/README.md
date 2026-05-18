# thesis-sentiment-shop – Backend

Spring Boot multi-module backend for the thesis reference application.
The application is an e-commerce-style product catalog with customer reviews
and an aggregate sentiment overview. Six architectural variants for ML
integration are selectable at build time via Maven profiles.

For project-level context, prerequisites, and installation instructions,
see the parent `README.md` one level up.

---
## Module structure

```
sentiment-shop/                     
|–– inference-api/                  <– Contract module: SentimentClassifier,
|                                      AsyncSentimentClassifier, SentimentResultSink,
|                                      FaultInjectingClassifier, SentimentResult,
|                                      SentimentClassificationException(no Spring)
|–– inference-stub/                 <– Default no-op implementation
|–– inference-embedded-core/        <– Shared embedded library: ONNX classifier, CardiffNLP preprocessor
|–– inference-embedded-sync/        <– E-Sync variant wiring (depends on core)
|–– inference-embedded-async/       <– E-Async variant wiring (depends on core)
|–– inference-self-hosted-core/     <– Shared self-hosted library: RestClient, DTOs, failure-mapping policy
|–– inference-self-hosted-sync/     <– S-Sync variant wiring (depends on core)
|–– inference-self-hosted-async/    <– S-Async variant wiring + AMQP publisher/listener
|–– inference-external-core/        <– Shared external library: HuggingFace HTTP client, wire DTOs, 
|                                      failure mapping
|–– inference-external-sync/        <– X-Sync variant wiring (depends on core)
|–– inference-external-async/       <– X-Async variant wiring + AMQP publisher/listener
|–– persistence/                    <– BaseEntity, JPA configuration
|–– catalog/                        <– Product domain
|–– reviews/                        <– Review domain, SentimentSummary, PendingReviewSweeper
|–– sample-data/                    <– Seed CSV and CommandLineRunner loader
└–– web/                            <– Spring Boot main, controllers, security stub
```

The dependency direction is strictly one-way. `reviews` depends only on
`inference-api`, never on a concrete variant module. The `web` module is the
sole composition root and the only place where a concrete classifier
implementation is wired in, via Maven profiles.
---
## Active variants

| Profile | Variant | Classifier bean | Infrastructure required |
|---|---------|---|---|
| `stub` *(default)* | –       | `StubSentimentClassifier` (NEUTRAL, 0.5 confidence) | MariaDB |
| `e-sync` | E-Sync  | `OnnxSentimentClassifier` | MariaDB + ONNX model artefact |
| `e-async` | E-Async | `EmbeddedAsyncSentimentClassifier` | MariaDB + ONNX model artefact |
| `s-sync` | S-Sync  | `RemoteSentimentClient` | MariaDB + inference-service container |
| `s-async` | S-Async | `RabbitPublishingAsyncSentimentClassifier` | MariaDB + RabbitMQ + inference-service-consumer |
| `x-sync` | X-Sync  | `HuggingFaceSentimentClient` | MariaDB + HuggingFace API token |
| `x-async` | X-Async | `RabbitPublishingExternalAsyncSentimentClassifier` | MariaDB + RabbitMQ + HuggingFace API token |

Build and run:

```bash
mvn -P<profile> clean install
mvn -P<profile> -pl web spring-boot:run
```

---
## Configuration

### Shared

| Property | Default | Purpose |
|---|---|---|
| `spring.datasource.url` | `application.yml` | MariaDB JDBC URL |
| `sentiment.fault-injection.enabled` | `false` | Enable fault-injection wrapper |

### Embedded variants (E-Sync, E-Async)

| Property / variable | Default | Purpose |
|---|---|---|
| `SENTIMENT_MODEL_PATH` | `./model-artefact` | Directory with `model.onnx`, `tokenizer.json`, `config.json` |
| `sentiment.async.workers` | `4` | Worker thread count (E-Async only) |
| `sentiment.async.queue-capacity` | `256` | Bounded queue capacity (E-Async only) |

### Self-hosted variants (S-Sync, S-Async)

| Property | Default | Purpose |
|---|---|---|
| `sentiment.self-hosted.base-url` | `http://localhost:18000` | Python inference service URL |
| `sentiment.self-hosted.connect-timeout-ms` | `2000` | TCP connect timeout |
| `sentiment.self-hosted.read-timeout-ms` | `5000` | Read timeout |

### External variants (X-Sync, X-Async)

| Property / variable | Default | Purpose                                           |
|---|---|---------------------------------------------------|
| `HUGGINGFACE_API_TOKEN` | *(must be set)* | Bearer token, application fails to start if empty |
| `sentiment.external.url` | HuggingFace router URL | Inference endpoint                                |
| `sentiment.self-hosted.connect-timeout-ms` | `2000` | TCP connect timeout                               |
| `sentiment.self-hosted.read-timeout-ms` | `10000` | Read timeout                                      |

---
## REST API Endpoints

| Method | Path                                | Description                                                        |
|--------|-------------------------------------|--------------------------------------------------------------------|
| GET    | `/api/products`                     | List products                                                      |
| GET    | `/api/products/{id}`                | Product detail                                                     |
| GET    | `/api/products/{productId}/reviews` | List reviews for a product                                         |
| POST   | `/api/products/{productId}/reviews` | Submit review, returns the persisted review with sentiment fields  |
| GET    | `/api/sentiment/summary`            | Aggregate sentiment counts (includes `pending` for async variants) |
| GET    | `/actuator/health`                  | Health check                                                       |
| GET    | `/actuator/metrics`                 | Metrics                                                            |
| GET    | `/actuator/beans`                   | Spring bean list                                                   |

---
## Stubbed behaviour
**Authentication**: every request is authenticated as `testuser` regardless of credentials. Authentication is explicitly
out of scope for this study. See `SecurityConfiguration`.

**Sentiment classifier**: `stub` returns `NEUTRAL` with confidence 0.5 in zero latency. Replaced by the active variant
module under any non-default Maven profile.

---

## Further reading

- `inference-embedded-core/README.md` — embedded variant detail (E-Sync, E-Async)
- `inference-self-hosted-core/README.md` — self-hosted variant detail (S-Sync, S-Async)
- `inference-external-core/README.md` — external variant detail (X-Sync, X-Async)
- `scripts/README.md` — model export, JMeter campaigns, fault-tolerance harness