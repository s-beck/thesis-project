# inference-service – Python Sentiment Microservice

Python sentiment classification service used by the self-hosted variants
(S-Sync and S-Async). The service loads the same
`cardiffnlp/twitter-roberta-base-sentiment-latest` model that the embedded
variants load via ONNX, keeping the model constant across all six variants.

For project-level context, prerequisites, and installation instructions,
see the parent `README.md` one level up.

---

## Structure

```
inference-service/
|–– app.py              <– Flask and Waitress HTTP entry point (S-Sync)
|–– consumer.py         <– pika AMQP consumer entry point (S-Async)
|–– classifier.py       <– Shared classifier, imported by both entry points
|–– Dockerfile          <– Single image, compose selects entry point via command override
└–– requirements.txt
```

The HTTP and AMQP entry points share `classifier.py` directly. There is no
internal HTTP hop between the consumer and the model.

---

## Entry points

### S-Sync – HTTP (`app.py`)

Flask application served by Waitress. Exposes a single endpoint:

```
POST /classify
Body:  { "text": "<review text>" }
Response: { "sentiment": "POSITIVE|NEUTRAL|NEGATIVE",
            "confidence": <float>,
            "latencyMs": <float> }
```

`latencyMs` is the Python-side inference latency only (excludes network).
The Java client records caller-side wall-clock latency separately.

### S-Async — AMQP consumer (`consumer.py`)

pika-based consumer. Reads from the `sentiment.requests` (quorum) queue,
classifies using the shared `classifier.py`, and publishes results to the
`sentiment.exchange` exchange with routing key `results`.

---

## Docker

The image is built from `inference-service/` and the entry point is selected
by the `command:` override in `docker-compose.yml`:

| Compose service | Command | Used by |
|---|---|---|
| `inference-service` | *(default: `app.py`)* | S-Sync |
| `inference-service-consumer` | `python consumer.py` | S-Async |

S-Async does **not** need the Flask `inference-service` container. The consumer
imports `classifier.py` directly.

---

## Configuration (consumer)

The consumer reads the following environment variables (supplied via `docker-compose.yml`):

| Variable | Purpose |
|---|---|
| `RABBITMQ_HOST` | RabbitMQ hostname |
| `RABBITMQ_PORT` | AMQP port |
| `RABBITMQ_USER` | Credentials |
| `RABBITMQ_PASS` | Credentials |
| `SENTIMENT_REQUESTS_QUEUE` | Requests queue name |
| `SENTIMENT_RESULTS_EXCHANGE` | Results exchange name |
| `SENTIMENT_RESULTS_ROUTING_KEY` | Routing key for result publication |