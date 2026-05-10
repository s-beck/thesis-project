"""
Export the sentiment classification model to ONNX.

This script is run ONCE, by hand, to produce the model artefact that is
then consumed by all six implementation variants. It is NOT part of the
Maven build and is NOT invoked at application runtime — keeping Python
out of the JVM's path

The script does three things:
  1. Downloads the HuggingFace checkpoint specified by MODEL_ID.
  2. Exports it to ONNX format via Optimum's official exporter.
  3. Validates the exported artefact with a single inference, asserting
     that the label mapping is the one the Java-side classifier expects
     (0=Negative, 1=Neutral, 2=Positive).

If step 3 fails, the artefact is unsafe to ship and the script exits
non-zero. This guards against a future checkpoint silently rotating the
label order, which would manifest in production as a sentiment classifier
that systematically swaps positive and negative reviews.

Usage:
    python -m venv .venv
    source .venv/bin/activate
    pip install -r requirements.txt
    python export.py

Output is written to ../../model-artefact/ relative to this script.
"""

from __future__ import annotations

import shutil
import subprocess
import sys
from pathlib import Path

import numpy as np
import onnxruntime as ort
from transformers import AutoConfig, AutoTokenizer

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

MODEL_ID = "cardiffnlp/twitter-roberta-base-sentiment-latest"

# Output directory, relative to this script. Lives outside any Maven module
# so that all six variants can read from a single canonical location.
OUTPUT_DIR = (Path(__file__).parent / ".." / ".." / "model-artefact").resolve()

# The label mapping the Java side relies on. Must match the Sentiment enum
# in inference-api/.../Sentiment.java.
EXPECTED_LABELS = {0: "negative", 1: "neutral", 2: "positive"}

# A short, deterministic sanity-check sentence with an obvious sentiment.
# Used only to verify the exported graph runs end-to-end and that the
# label order is what we expect; not a model accuracy test.
SANITY_TEXT = "I love this product, it works great!"
SANITY_EXPECTED_LABEL = "positive"


# ---------------------------------------------------------------------------
# Step 1 + 2: export
# ---------------------------------------------------------------------------

def export() -> None:
    if OUTPUT_DIR.exists():
        print(f"Removing existing artefact directory: {OUTPUT_DIR}")
        shutil.rmtree(OUTPUT_DIR)
    OUTPUT_DIR.mkdir(parents=True)

    print(f"Exporting {MODEL_ID} to ONNX at {OUTPUT_DIR} ...")
    result = subprocess.run(
        [
            sys.executable, "-m", "optimum.exporters.onnx",
            "--model", MODEL_ID,
            "--task", "text-classification",
            "--monolith",  # single .onnx file rather than split graphs
            str(OUTPUT_DIR),
        ],
        check=False,
    )
    if result.returncode != 0:
        sys.exit(f"optimum-cli export failed with exit code {result.returncode}")


# ---------------------------------------------------------------------------
# Step 3: validate
# ---------------------------------------------------------------------------

def validate() -> None:
    print("Validating exported artefact ...")

    # Confirm the files Java will need are actually there.
    required_files = ["model.onnx", "tokenizer.json", "config.json"]
    for fname in required_files:
        path = OUTPUT_DIR / fname
        if not path.is_file():
            sys.exit(f"Missing expected file in artefact: {fname}")

    # Confirm the label mapping is what Java expects.
    config = AutoConfig.from_pretrained(OUTPUT_DIR)
    actual_labels = {int(k): v.lower() for k, v in config.id2label.items()}
    if actual_labels != EXPECTED_LABELS:
        sys.exit(
            f"Label mapping mismatch. Expected {EXPECTED_LABELS}, "
            f"got {actual_labels}. The Java SentimentClassifier and "
            f"Sentiment enum will need to be updated, OR a different "
            f"checkpoint should be used."
        )

    tokenizer = AutoTokenizer.from_pretrained(OUTPUT_DIR)
    session = ort.InferenceSession(str(OUTPUT_DIR / "model.onnx"))

    inputs = tokenizer(SANITY_TEXT, return_tensors="np")
    feed = {name: inputs[name] for name in (i.name for i in session.get_inputs())}
    logits = session.run(None, feed)[0]
    predicted_id = int(np.argmax(logits, axis=-1)[0])
    predicted_label = actual_labels[predicted_id]

    if predicted_label != SANITY_EXPECTED_LABEL:
        sys.exit(
            f"Sanity check failed: classified \"{SANITY_TEXT}\" as "
            f"{predicted_label!r}, expected {SANITY_EXPECTED_LABEL!r}. "
            f"The export likely succeeded but produced a graph that "
            f"behaves incorrectly — do not ship this artefact."
        )

    print(f"  Files present:        {required_files}")
    print(f"  Label mapping:        {actual_labels}")
    print(f"  Sanity classification: {predicted_label!r} (expected {SANITY_EXPECTED_LABEL!r})")
    print("Validation passed.")


if __name__ == "__main__":
    export()
    validate()
    print(f"\nDone. Artefact is at: {OUTPUT_DIR}")