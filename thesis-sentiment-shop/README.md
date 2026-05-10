# sentiment-shop

Reference application backend for the thesis. Multi-module modular monolith on
Java 21 + Spring Boot 3.3 + MariaDB.

## Module structure

```
sentiment-shop/                     parent pom
|—— inference-api/                  SentimentClassifier interface (no Spring)
|—— inference-stub/                 default no-op implementation
|—— inference-embedded-sync/        E-Sync variant: in-process ONNX Runtime
|—— persistence/                    BaseEntity, JPA auditing
|—— catalog/                        Product domain
|—— reviews/                        Review domain (uses inference-api only)
|—— sample-data/                    Bundled seed CSV and CommandLineRunner loader
└—— web/                            Spring Boot main, controllers, security stub
```

The dependency direction is strict and one-way:



`reviews` does not depend on any concrete classifier. The web module is
the only place where a concrete implementation is wired in. 
`inference-stub` can be replaced with a variant-specific module (e.g. `inference-embedded-sync`) 
using maven profiles configured in `web/pom.xml`. 

## Active variants

| Profile  | Active classifier                | Build command                       |
|----------|----------------------------------|-------------------------------------|
| `stub`   | `StubSentimentClassifier`        | `mvn -Pstub install` (default)      |
| `e-sync` | `OnnxSentimentClassifier`        | `mvn -Pe-sync install`              |

The `stub` profile is `activeByDefault`.

## Endpoints

| Method | Path                                | Description                       |
|--------|-------------------------------------|-----------------------------------|
| GET    | `/api/products`                     | List products                     |
| GET    | `/api/products/{id}`                | Product detail                    |
| GET    | `/api/products/{productId}/reviews` | List reviews for a product        |
| POST   | `/api/products/{productId}/reviews` | Submit review (returns sentiment) |
| GET    | `/api/sentiment/summary`            | Aggregate sentiment counts        |
| GET    | `/actuator/health`                  | Health check                      |
| GET    | `/actuator/metrics`                 |                                   |
| GET    | `/actuator/beans`                   |                                   |

## What is stubbed

- **Authentication**: every request is authenticated as `testuser` because a proper configuration is 
out-of-scope of this thesis. See
  `SecurityConfiguration`. 
- **Sentiment classifier**: `StubSentimentClassifierConfiguration` returns
  `NEUTRAL` with confidence 0.5 in zero latency. Replaced by the active variant module under any non-default
Maven profile.

## Configuration

| Variable / property      | Default                       | Used by                |
|--------------------------|-------------------------------|------------------------|
| `SENTIMENT_MODEL_PATH`   | `./model-artefact`            | `inference-embedded-sync` only |
| `spring.datasource.url`  | ---------------- | persistence            |

`SENTIMENT_MODEL_PATH` is read at application start-up and resolved relative
to the current working directory if not absolute. The default expects the
`scripts/export-model` output to sit at the project root. See
`scripts/export-model/README.md` for how to produce the artefact.

## Use of AI assistance

Parts of this source code were designed with the aid of AI and subsequently reviewed and revised by 
the author. The code fragments created in this way are clearly marked inline at the point where they 
appear. Where no such marking is present, the code is the author’s own original work. All decisions 
regarding the structural design including the module boundaries and integration choices relevant to 
the thesis were made by the author. AI support was used solely at the level of code design and routine 
implementation, but not at the level of justifying the design.