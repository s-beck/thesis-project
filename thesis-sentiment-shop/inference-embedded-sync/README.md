# inference-embedded-sync

E-Sync variant realizes the synchronous, in-process inference via Microsoft ONNX
Runtime for Java.

The model is loaded into the JVM at application start-up, kept resident for the lifetime of
the process, and called synchronously on the request thread. The
inference code lives behind the `SentimentClassifier` interface and is
isolated to this module.

## Activation

```
mvn -Pe-sync clean install
mvn -Pe-sync -pl web spring-boot:run
```

Activating the `e-sync` Maven profile pulls this module onto the
classpath in place of `inference-stub`. See the top-level README for the
full profile/variant mapping.

## Configuration

| Variable / property      | Default            | Purpose |
|--------------------------|--------------------|---------|
| `SENTIMENT_MODEL_PATH`   | `./model-artefact` | Directory containing `model.onnx`, `tokenizer.json`, `config.json` |

The path is resolved relative to the working directory at start-up. The
default points at the `scripts/export-model` output sitting at the
project root.

## Behaviour

1. **On boot:** `EmbeddedSyncSentimentClassifierConfiguration` creates a
   single `OnnxSentimentClassifier` bean with `destroyMethod = "close"`.
   Construction reads `model.onnx` into an `OrtSession` and
   `tokenizer.json` into a DJL `HuggingFaceTokenizer`. Both are
   thread-safe for inference once initialised.
2. **Classify:** `classify(text)` inside `OnnxSentimentClassifier` runs the CardiffNLP-style
   preprocessing (`@user` and URL substitution), tokenises, builds two
   `OnnxTensor`s for `input_ids` and `attention_mask`, runs the session,
   takes argmax of the resulting logits, applies a numerically stable
   softmax for the confidence score, and returns a `SentimentResult`.
3. **Shutdown:** Spring calls `close()` on the bean, which releases the
   native handles held by `OrtSession` and the tokenizer.

## Wiring

The `OnnxSentimentClassifier` itself has no Spring import.
Wiring lives in a separate `@Configuration` class.

## Dependencies

- `inference-api` (compile)
- `com.microsoft.onnxruntime:onnxruntime` — ML inference runtime; bundles native binaries for Linux x64, macOS x64+arm64, Windows x64.
- `ai.djl.huggingface:tokenizers` — JNI binding to HuggingFace's Rust tokenizer, reads `tokenizer.json` directly.
- `spring-context`, `spring-boot-autoconfigure` — for `@Configuration` only.

The two ML dependencies' versions are pinned in the parent POM
(`onnxruntime.version`, `djl.tokenizers.version`) for reproducibility.
