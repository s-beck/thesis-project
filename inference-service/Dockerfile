# syntax=docker/dockerfile:1.7
# AI-assisted code: Generated with Claude (Anthropic) and reviewed/modified by the author.
FROM python:3.11-slim

ARG SENTIMENT_MODEL_NAME=cardiffnlp/twitter-roberta-base-sentiment-latest

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    TOKENIZERS_PARALLELISM=false \
    OMP_NUM_THREADS=1 \
    MKL_NUM_THREADS=1 \
    HF_HUB_DISABLE_TELEMETRY=1 \
    SENTIMENT_MODEL_NAME=${SENTIMENT_MODEL_NAME}

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

RUN python -c "from transformers import AutoModelForSequenceClassification, AutoTokenizer; \
               name='${SENTIMENT_MODEL_NAME}'; \
               AutoTokenizer.from_pretrained(name); \
               AutoModelForSequenceClassification.from_pretrained(name)"

COPY classifier.py app.py ./

EXPOSE 8000

HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=3 \
    CMD python -c "import urllib.request, sys; \
                   r = urllib.request.urlopen('http://localhost:8000/health', timeout=3); \
                   sys.exit(0 if r.status == 200 else 1)" || exit 1

CMD ["waitress-serve", \
     "--threads=4", \
     "--host=0.0.0.0", \
     "--port=8000", \
     "app:app"]