# AI-assisted code: Generated with Claude (Anthropic) and reviewed/modified by the author.
import logging
import re
import time
from dataclasses import dataclass

import torch
from transformers import AutoModelForSequenceClassification, AutoTokenizer

logger = logging.getLogger(__name__)

_URL_PATTERN = re.compile(r"https?://\S+")
_MENTION_PATTERN = re.compile(r"@\S+")

# Maximum sequence length = RoBERTa-base positional encoding limit
_MAX_TOKENS = 512

_LABEL_MAPPING = {
    "negative": "NEGATIVE",
    "neutral": "NEUTRAL",
    "positive": "POSITIVE",
}

@dataclass(frozen=True)
class ClassificationResult:
    sentiment: str
    confidence: float
    latency_ms: int


class SentimentClassifier:
    def __init__(self, model_name: str):
        logger.info("Loading model %s", model_name)
        load_start = time.monotonic()

        self._tokenizer = AutoTokenizer.from_pretrained(model_name)
        self._model = AutoModelForSequenceClassification.from_pretrained(model_name)
        self._model.eval()

        self._id_to_label: dict[int, str] = {}
        for idx, raw_label in self._model.config.id2label.items():
            normalised = raw_label.strip().lower()
            if normalised not in _LABEL_MAPPING:
                raise RuntimeError(
                    f"Unexpected model label '{raw_label}' at id {idx}; "
                    f"expected one of {sorted(_LABEL_MAPPING)}"
                )
            self._id_to_label[int(idx)] = _LABEL_MAPPING[normalised]

        load_ms = int((time.monotonic() - load_start) * 1000)
        logger.info("Model loaded in %d ms; labels = %s", load_ms, self._id_to_label)

        self._model_name = model_name

    @property
    def model_name(self) -> str:
        return self._model_name

    def classify(self, text: str) -> ClassificationResult:
        if not isinstance(text, str):
            raise TypeError("text must be a string")
        if not text.strip():
            raise ValueError("text must not be empty or whitespace-only")

        preprocessed = self._preprocess(text)
        infer_start = time.monotonic()

        with torch.inference_mode():
            encoded = self._tokenizer(
                preprocessed,
                return_tensors="pt",
                truncation=True,
                max_length=_MAX_TOKENS,
            )
            logits = self._model(**encoded).logits[0]
            probabilities = torch.softmax(logits, dim=-1)
            predicted_id = int(torch.argmax(probabilities).item())
            confidence = float(probabilities[predicted_id].item())

        latency_ms = int((time.monotonic() - infer_start) * 1000)
        sentiment = self._id_to_label[predicted_id]

        return ClassificationResult(
            sentiment=sentiment,
            confidence=confidence,
            latency_ms=latency_ms,
        )

    @staticmethod
    def _preprocess(text: str) -> str:
        urls_replaced = _URL_PATTERN.sub("http", text)
        return _MENTION_PATTERN.sub("@user", urls_replaced)