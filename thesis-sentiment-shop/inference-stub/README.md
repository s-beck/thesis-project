inference-stub

Default no-op implementation of `SentimentClassifier`. Active under the
Maven `stub` profile (the default).

## Role in the architecture

This module exists so that `mvn install` produces a runnable application
without any model artefact, container, or external service being
present. The variant modules
replace it under their own profiles.

## Behaviour

`StubSentimentClassifier.classify(text)` returns:

| Field        | Value                |
|--------------|----------------------|
| `sentiment`  | `Sentiment.NEUTRAL`  |
| `confidence` | `0.5`                |
| `duration`   | `Duration.ZERO`      |
| `timestamp`  | `Instant.now()`      |

## Wiring

`StubSentimentClassifierConfiguration` declares the bean with
`@ConditionalOnMissingBean(SentimentClassifier.class)`. With the Maven
profile structure used in this project the conditional is technically
redundant but it stays as a defence-in-depth, in case two classifier modules ever
end up on the classpath simultaneously through misconfiguration, the
stub yields rather than competing.

## Dependencies

- `inference-api` (compile)
- `spring-context`, `spring-boot-autoconfigure` (compile, for `@Configuration`)
