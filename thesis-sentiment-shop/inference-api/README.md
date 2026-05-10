# inference-api

The contract module. Defines the abstraction that the rest of the
application depends on, without committing to any particular ML
integration mechanism.

## Role in the architecture

This module exists so that `reviews` can
classify sentiment without knowing whether the classifier runs in-process,
in a separate container, or behind a vendor API. Every variant
ships a separate implementation module that depends on this one and is wired into `web` via
a Maven profile. Swapping the variant is a build-time decision, not a
runtime configuration toggle.

## Public surface

| Type | Purpose                                                                                                                                       |
|------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| `SentimentClassifier` | Single-method interface with synchronous return type by design – see "On the synchronous signature" below                                     |
| `SentimentResult` | Record carrying the predicted `Sentiment`, a confidence value, the inference duration, and a timestamp                                        |
| `Sentiment` | Three-class enum that mirrors the model's `id2label` mapping                                                                                  |
| `SentimentClassificationException` | Runtime exception with a `FailureMode` enum|

## On the synchronous signature

The signature returns `SentimentResult` directly rather than
`CompletableFuture<SentimentResult>` or similar. The synchronous /
asynchronous distinction is realised by the *caller* not by changing the
contract. The async variants themselfs define where the asynchrony
lives.

## Dependencies

None
