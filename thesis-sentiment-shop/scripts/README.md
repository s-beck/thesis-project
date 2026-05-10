# Model export

This directory contains a one-shot script that exports the sentiment
classification model to ONNX format. It is the **only** Python in the
repository, and it is intentionally **not** part of any build pipeline.

## Why this exists at all

All six implementation variants use the same
model: `cardiffnlp/twitter-roberta-base-sentiment-latest`.

> Cardiff NLP (2022). *Twitter RoBERTa Base Sentiment Latest.*
> Hugging Face.
> https://huggingface.co/cardiffnlp/twitter-roberta-base-sentiment-latest
> Licence: **CC-BY-4.0**.

The way each variant *loads and calls* that model differs but the artefact itself is constant:

| Variant | How the artefact is consumed |
| --- | --- |
| E-Sync, E-Async | Loaded into the JVM via ONNX Runtime for Java |
| S-Sync, S-Async |  |
| X-Sync, X-Async |  |

For E-* and S-* the artefact is needed on disk. The X-* variants don't,
since they hit the HF endpoint directly, but they use the *same model_id*,
so they are loading the same checkpoint by reference.

The export script materialises the on-disk form and validates it.

## How to run it

From this directory:

```bash
python -m venv .venv
source .venv/bin/activate          # Windows: .venv\Scripts\activate
pip install -r requirements.txt
python export-model.py
```

The script will:

1. Download the HuggingFace checkpoint.
2. Export to ONNX via `optimum-cli`.
3. Validate the artefact (files present, label mapping, sanity inference).
   The Output is written to `../../model-artefact/`,
   sitting at the project root outside any Maven module. That directory is
   gitignored. To regenerate the artefact this script needs to be rerun.

## Output layout

```
model-artefact/
|—— model.onnx          <- the exported graph
|—— tokenizer.json      <- consumed by ai.djl.huggingface:tokenizers
|—— config.json         <- contains id2label, used for label decoding
|—— special_tokens_map.json
└—— ...
```