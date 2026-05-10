# reviews

The review domain contains review submission, persistence, sentiment annotation,
and aggregate sentiment summaries.

## Role in the architecture

The thesis-relevant property is that `ReviewService` calls the
classifier through the abstract `SentimentClassifier` interface and
holds no reference to any concrete implementation. Swapping variants
changes which JAR provides that interface, whereas `reviews` remain unchanged.

The `POST /api/products/{id}/reviews` flow goes:

```
web/ReviewController
   └–> reviews/ReviewService
          |–> reviews/ReviewRepository    (persist)
          └–> inference-api/SentimentClassifier  (classify)
                  └–> <variant module>     (selected by Maven profile)
```

## Public surface

| Type                   | Purpose                                                                  |
|------------------------|--------------------------------------------------------------------------|
| `Review`               | JPA entity (extends `BaseEntity`), references `Product` by ID            |
| `ReviewRepository`     | Spring Data JPA repository                                               |
| `ReviewService`        | Submit a review (classifies + persists), list reviews for a product      |
| `SentimentSummary`     | Aggregate counts per sentiment for the `/api/sentiment/summary` endpoint |

## On the Product reference

`Review` references `Product` by primary key (`productId`), not through
a `@ManyToOne Product`. This keeps `reviews` from depending on the
`catalog` Java module, and only on a database constraint that they share a
key space. Lookups in `ReviewService` use the ID directly.

## Dependencies

- `persistence` (compile)
- `inference-api` (compile) — abstract classifier contract only, never a concrete variant
- `spring-boot-starter-data-jpa` (compile)
