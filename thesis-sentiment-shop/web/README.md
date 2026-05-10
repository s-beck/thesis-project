# web

The Spring Boot application containing REST controllers, security configuration,
exception handling, and the `main` method. It is the only module that knows
about every other module.

## Role in the architecture

`web` is the composition root. It is the only module that depends on a
concrete `SentimentClassifier` implementation. On which one it depends, 
is determined by the active Maven profile, not by Spring runtime
configuration.

## Variant selection

The classifier dependency is wrapped in `<profiles>` rather than
declared directly:

```xml
<profile>
    <id>stub</id>
    <activation>
        <activeByDefault>true</activeByDefault>
    </activation>
    <dependencies>
        <dependency>
            <groupId>com.thesis</groupId>
            <artifactId>inference-stub</artifactId>
        </dependency>
    </dependencies>
</profile>

<profile>
<id>e-sync</id>
<dependencies>
    <dependency>
        <groupId>com.thesis</groupId>
        <artifactId>inference-embedded-sync</artifactId>
    </dependency>
</dependencies>
</profile>
```

## Controllers

| Controller                     | Path                                | Method | Notes                                         |
|--------------------------------|-------------------------------------|--------|-----------------------------------------------|
| `ProductController`            | `/api/products`, `/api/products/{id}` | GET    |                                               |
| `ReviewController`             | `/api/products/{id}/reviews`        | GET, POST | POST returns the review with sentiment fields. |
| `SentimentSummaryController`   | `/api/sentiment/summary`            | GET    | Aggregate counts.                             |
| Spring Boot Actuator           | `/actuator/*`                       | GET    | `health`, `metrics`, `beans` exposed.         |

## What is stubbed

`SecurityConfiguration` is a deliberate placeholder. Every request is authenticated as the user `testuser`, 
regardless of headers or credentials. This decision was made intentionally because authentication falls outside 
the scope of this study. In a real-world implementation, this configuration would be replaced. The placeholder 
remains unchanged in all six variants.

## Exception handling

`GlobalExceptionHandler`that translates
domain exceptions to HTTP responses.

| FailureMode   | HTTP status               |
|---------------|---------------------------|
| `NOT_FOUND`   | 404 Not Found             |

## Dependencies

- `catalog`, `reviews`, `sample-data` (compile)
- One of: `inference-stub` *or* `inference-embedded-sync` (compile, profile-selected)
- `spring-boot-starter-web`
- `spring-boot-starter-security`
- `spring-boot-starter-validation`
- `spring-boot-starter-actuator`
- `mariadb-java-client` (runtime)
