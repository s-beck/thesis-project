# AI-assisted code: Generated with Claude (Anthropic) and reviewed/modified by the author.
import json
import logging
import os
import signal
import sys
import threading
from concurrent.futures import ThreadPoolExecutor
from typing import Optional

import pika
from pika.adapters.blocking_connection import BlockingChannel
from pika.exceptions import AMQPConnectionError
from pika.spec import Basic, BasicProperties

from classifier import SentimentClassifier

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
    stream=sys.stdout,
)
logger = logging.getLogger("inference-service-consumer")

_MODEL_NAME = os.environ.get(
    "SENTIMENT_MODEL_NAME",
    "cardiffnlp/twitter-roberta-base-sentiment-latest",
)
_RABBITMQ_HOST = os.environ.get("RABBITMQ_HOST", "rabbitmq")
_RABBITMQ_PORT = int(os.environ.get("RABBITMQ_PORT", "5672"))
_RABBITMQ_USER = os.environ.get("RABBITMQ_USER", "guest")
_RABBITMQ_PASS = os.environ.get("RABBITMQ_PASS", "guest")

_REQUESTS_QUEUE = os.environ.get("SENTIMENT_REQUESTS_QUEUE", "sentiment.requests")
_RESULTS_EXCHANGE = os.environ.get("SENTIMENT_RESULTS_EXCHANGE", "sentiment.exchange")
_RESULTS_ROUTING_KEY = os.environ.get("SENTIMENT_RESULTS_ROUTING_KEY", "results")

# Mirrors Waitress threads=4
_WORKER_COUNT = int(os.environ.get("SENTIMENT_CONSUMER_WORKERS", "4"))
_PREFETCH_COUNT = _WORKER_COUNT  # one in-flight message per worker

# Time to wait for in-flight workers during graceful shutdown
_SHUTDOWN_GRACE_SECONDS = int(os.environ.get("SENTIMENT_CONSUMER_SHUTDOWN_GRACE_S", "30"))

_shutdown_event = threading.Event()

def _install_signal_handlers() -> None:
    def handle(signum, _frame):
        logger.info("Received signal %d; initiating graceful shutdown", signum)
        _shutdown_event.set()

    signal.signal(signal.SIGTERM, handle)
    signal.signal(signal.SIGINT, handle)

class MalformedMessageError(Exception):
    '''Raised when a request message cannot be parsed or is missing fields'''

def _parse_request(body: bytes) -> tuple[int, str]:
    try:
        payload = json.loads(body)
    except json.JSONDecodeError as exc:
        raise MalformedMessageError(f"body is not valid JSON: {exc}") from exc

    if not isinstance(payload, dict):
        raise MalformedMessageError("body must be a JSON object")

    review_id = payload.get("reviewId")
    text = payload.get("text")

    if not isinstance(review_id, int):
        raise MalformedMessageError("'reviewId' must be an integer")
    if not isinstance(text, str):
        raise MalformedMessageError("'text' must be a string")

    return review_id, text

def _build_result_message(review_id: int, sentiment: str, confidence: float, latency_ms: int) -> bytes:
    payload = {
        "reviewId": review_id,
        "sentiment": sentiment,
        "confidence": confidence,
        "latencyMs": latency_ms,
    }
    return json.dumps(payload).encode("utf-8")

def _classify_and_publish(
    classifier: SentimentClassifier,
    channel: BlockingChannel,
    connection: pika.BlockingConnection,
    delivery_tag: int,
    body: bytes,
) -> None:
    # Run on a worker thread - classifies and schedules ack+publish on the main thread

    try:
        review_id, text = _parse_request(body)
    except MalformedMessageError as exc:
        logger.warning("Malformed message (tag=%d): %s; routing to DLQ", delivery_tag, exc)
        connection.add_callback_threadsafe(
            lambda: _safe_nack(channel, delivery_tag, requeue=False)
        )
        return

    try:
        result = classifier.classify(text)
    except (ValueError, TypeError) as exc:
        # Deterministic input failure -> DLQ immediately
        logger.warning(
            "Input-side classification failure for review %d (tag=%d): %s; routing to DLQ",
            review_id, delivery_tag, exc,
        )
        connection.add_callback_threadsafe(
            lambda: _safe_nack(channel, delivery_tag, requeue=False)
        )
        return
    except Exception:
        # Potentially transient -> requeue, broker will redeliver up to N times
        logger.exception(
            "Classification raised unexpectedly for review %d (tag=%d); requeueing",
            review_id, delivery_tag,
        )
        connection.add_callback_threadsafe(
            # Author Edit: replaced with reject method
            lambda: _safe_reject(channel, delivery_tag, requeue=True)
        )
        return

    result_body = _build_result_message(
        review_id, result.sentiment, result.confidence, result.latency_ms
    )

    def publish_and_ack() -> None:
        try:
            channel.basic_publish(
                exchange=_RESULTS_EXCHANGE,
                routing_key=_RESULTS_ROUTING_KEY,
                body=result_body,
                properties=BasicProperties(
                    content_type="application/json",
                    delivery_mode=2,  # persistent
                ),
                mandatory=True,
            )
            channel.basic_ack(delivery_tag=delivery_tag)
        except Exception:
            logger.exception(
                "Failed to publish/ack result for review %d (tag=%d); requeueing",
                review_id, delivery_tag,
            )
            _safe_nack(channel, delivery_tag, requeue=True)

    connection.add_callback_threadsafe(publish_and_ack)

def _safe_nack(channel: BlockingChannel, delivery_tag: int, requeue: bool) -> None:
    try:
        channel.basic_nack(delivery_tag=delivery_tag, requeue=requeue)
    except Exception:
        logger.exception("Failed to nack message (tag=%d, requeue=%s)", delivery_tag, requeue)

# Author Edit: implemented reject method to fix the PendingReviewSweeper
def _safe_reject(channel: BlockingChannel, delivery_tag: int, requeue: bool) -> None:
    try:
        channel.basic_reject(delivery_tag=delivery_tag, requeue=requeue)
    except Exception:
        logger.exception("Failed to reject message (tag=%d, requeue=%s)", delivery_tag, requeue)

def _connect() -> pika.BlockingConnection:
    credentials = pika.PlainCredentials(_RABBITMQ_USER, _RABBITMQ_PASS)
    parameters = pika.ConnectionParameters(
        host=_RABBITMQ_HOST,
        port=_RABBITMQ_PORT,
        credentials=credentials,
        heartbeat=30,
        blocked_connection_timeout=60,
        connection_attempts=5,
        retry_delay=2.0,
    )
    return pika.BlockingConnection(parameters)

def _assert_topology(channel: BlockingChannel) -> None:
    channel.queue_declare(queue=_REQUESTS_QUEUE, passive=True)
    channel.exchange_declare(exchange=_RESULTS_EXCHANGE, passive=True)

def _run(classifier: SentimentClassifier) -> None:
    connection: Optional[pika.BlockingConnection] = None
    executor = ThreadPoolExecutor(
        max_workers=_WORKER_COUNT,
        thread_name_prefix="s-async-worker",
    )

    try:
        connection = _connect()
        channel = connection.channel()
        _assert_topology(channel)
        channel.basic_qos(prefetch_count=_PREFETCH_COUNT)

        def on_message(
            ch: BlockingChannel,
            method: Basic.Deliver,
            _properties: BasicProperties,
            body: bytes,
        ) -> None:
            executor.submit(
                _classify_and_publish,
                classifier, ch, connection, method.delivery_tag, body,
            )

        channel.basic_consume(queue=_REQUESTS_QUEUE, on_message_callback=on_message)
        logger.info(
            "Consumer ready: queue=%s workers=%d prefetch=%d",
            _REQUESTS_QUEUE, _WORKER_COUNT, _PREFETCH_COUNT,
        )

        while not _shutdown_event.is_set():
            connection.process_data_events(time_limit=1.0)

        logger.info("Shutdown requested; stopping consumer")
        channel.stop_consuming()

    except AMQPConnectionError:
        logger.exception("Broker connection failed")
        raise
    finally:
        logger.info("Draining %d in-flight workers (grace=%ds)", _WORKER_COUNT, _SHUTDOWN_GRACE_SECONDS)
        executor.shutdown(wait=True, cancel_futures=False)
        if connection is not None and connection.is_open:
            try:
                connection.close()
            except Exception:
                logger.exception("Error closing connection")
        logger.info("Consumer stopped cleanly")

def main() -> None:
    _install_signal_handlers()
    logger.info("Starting S-Async consumer")
    classifier = SentimentClassifier(_MODEL_NAME)
    _run(classifier)

if __name__ == "__main__":
    main()