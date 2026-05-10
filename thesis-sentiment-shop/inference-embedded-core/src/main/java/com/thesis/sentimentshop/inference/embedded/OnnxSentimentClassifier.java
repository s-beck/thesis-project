package com.thesis.sentimentshop.inference.embedded;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.thesis.sentimentshop.inference.Sentiment;
import com.thesis.sentimentshop.inference.SentimentClassificationException;
import com.thesis.sentimentshop.inference.SentimentClassificationException.FailureMode;
import com.thesis.sentimentshop.inference.SentimentClassifier;
import com.thesis.sentimentshop.inference.SentimentResult;

import java.nio.LongBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public final class OnnxSentimentClassifier implements SentimentClassifier, AutoCloseable {

    private static final Sentiment[] LABEL_ORDER = {
            Sentiment.NEGATIVE,  // index 0
            Sentiment.NEUTRAL,   // index 1
            Sentiment.POSITIVE,  // index 2
    };

    private final OrtEnvironment environment;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;
    private final ReviewTextPreprocessor preprocessor;

    public OnnxSentimentClassifier(Path modelPath, Path tokenizerPath) {
        this.preprocessor = new ReviewTextPreprocessor();
        try {
            this.environment = OrtEnvironment.getEnvironment();
            this.session = environment.createSession(
                    modelPath.toString(),
                    new OrtSession.SessionOptions()
            );
            this.tokenizer = HuggingFaceTokenizer.newInstance(tokenizerPath);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to initialise ONNX session or tokenizer at "
                            + modelPath + " / " + tokenizerPath, e);
        }
    }

    @Override
    public SentimentResult classify(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }

        Instant start = Instant.now();
        try {
            String preprocessed = preprocessor.preprocess(text);
            Encoding encoding = tokenizer.encode(preprocessed);

            long[] inputIds = encoding.getIds();
            long[] attentionMask = encoding.getAttentionMask();
            long[] shape = {1, inputIds.length};

            try (OnnxTensor idsTensor = OnnxTensor.createTensor(
                    environment, LongBuffer.wrap(inputIds), shape);
                 OnnxTensor maskTensor = OnnxTensor.createTensor(
                         environment, LongBuffer.wrap(attentionMask), shape);
                 OrtSession.Result result = session.run(Map.of(
                         "input_ids", idsTensor,
                         "attention_mask", maskTensor))) {

                float[] logits = ((float[][]) result.get(0).getValue())[0];
                int predictedIndex = argMax(logits);
                double confidence = softmax(logits)[predictedIndex];

                return new SentimentResult(
                        LABEL_ORDER[predictedIndex],
                        confidence,
                        Duration.between(start, Instant.now()),
                        Instant.now());
            }
        } catch (OrtException e) {
            throw new SentimentClassificationException(
                    FailureMode.MODEL_ERROR,
                    "ONNX Runtime failed during inference",
                    e);
        } catch (RuntimeException e) {
            throw new SentimentClassificationException(
                    FailureMode.UNKNOWN,
                    "Unexpected error during sentiment classification",
                    e);
        }
    }

    @Override
    public void close() throws OrtException {
        // Order matters: close session before the environment.
        session.close();
        tokenizer.close();
    }

    private static int argMax(float[] logits) {
        int best = 0;
        for (int i = 1; i < logits.length; i++) {
            if (logits[i] > logits[best]) {
                best = i;
            }
        }
        return best;
    }

    private static double[] softmax(float[] logits) {
        double max = logits[0];
        for (int i = 1; i < logits.length; i++) {
            if (logits[i] > max) {
                max = logits[i];
            }
        }
        double sum = 0.0;
        double[] probs = new double[logits.length];
        for (int i = 0; i < logits.length; i++) {
            probs[i] = Math.exp(logits[i] - max);
            sum += probs[i];
        }
        for (int i = 0; i < probs.length; i++) {
            probs[i] /= sum;
        }
        return probs;
    }
}