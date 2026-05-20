# AI-assisted code: Generated with Claude (Anthropic) and reviewed/modified by the author.
import logging
import os
import sys

from flask import Flask, jsonify, request
from classifier import SentimentClassifier

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
    stream=sys.stdout,
)
logger = logging.getLogger("inference-service")

_MODEL_NAME = os.environ.get(
    "SENTIMENT_MODEL_NAME",
    "cardiffnlp/twitter-roberta-base-sentiment-latest",
)
_classifier = SentimentClassifier(_MODEL_NAME)

app = Flask(__name__)

@app.post("/classify")
def classify():
    payload = request.get_json(silent=True)
    if payload is None or "text" not in payload:
        return jsonify(error="missing 'text' field"), 400

    text = payload["text"]
    if not isinstance(text, str):
        return jsonify(error="'text' must be a string"), 400

    try:
        result = _classifier.classify(text)
    except ValueError as exc:
        return jsonify(error=str(exc)), 400
    except Exception:
        logger.exception("Classification failed")
        return jsonify(error="classification failed"), 500

    return jsonify(
        sentiment=result.sentiment,
        confidence=result.confidence,
        latencyMs=result.latency_ms,
    ), 200

@app.get("/health")
def health():
    return jsonify(status="ok"), 200

@app.get("/info")
def info():
    return jsonify(model=_classifier.model_name), 200


@app.errorhandler(404)
def not_found(_):
    return jsonify(error="route not found"), 404


@app.errorhandler(405)
def method_not_allowed(_):
    return jsonify(error="method not allowed"), 405