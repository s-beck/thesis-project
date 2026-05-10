package com.thesis.sentimentshop.inference.embeddedsync;

import com.thesis.sentimentshop.inference.SentimentClassificationException.FailureMode;
import com.thesis.sentimentshop.inference.SentimentClassifier;
import com.thesis.sentimentshop.inference.embedded.OnnxSentimentClassifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;

@Configuration
public class EmbeddedSyncSentimentClassifierConfiguration {

    private static final Set<FailureMode> SUPPORTED_FAILURE_MODES =
            EnumSet.of(FailureMode.MODEL_ERROR, FailureMode.UNKNOWN);

    private static final String VARIANT_NAME = "E-Sync";

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(
            name = "sentiment.fault-injection.enabled",
            havingValue = "false",
            matchIfMissing = true
    )
    public SentimentClassifier onnxSentimentClassifier(
            @Value("${SENTIMENT_MODEL_PATH:./model-artefact}") String modelDir) {
        return realClassifier(modelDir);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(
            name = "sentiment.fault-injection.enabled",
            havingValue = "true"
    )
    public SentimentClassifier faultyOnnxSentimentClassifier(
            @Value("${SENTIMENT_MODEL_PATH:./model-artefact}") String modelDir) {
        return new FaultInjectingClassifierAdapter(
                realClassifier(modelDir),
                SUPPORTED_FAILURE_MODES,
                VARIANT_NAME);
    }

    private OnnxSentimentClassifier realClassifier(String modelDir) {
        Path baseDir = Path.of(modelDir);
        return new OnnxSentimentClassifier(
                baseDir.resolve("model.onnx"),
                baseDir.resolve("tokenizer.json"));
    }
}
