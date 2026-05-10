package com.thesis.sentimentshop.inference.embeddedsync;

import com.thesis.sentimentshop.inference.SentimentClassifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class EmbeddedSyncSentimentClassifierConfiguration {
    @Bean(destroyMethod = "close")
    public SentimentClassifier onnxSentimentClassifier(
            @Value("${SENTIMENT_MODEL_PATH:./model-artefact}") String modelDir) {
        Path baseDir = Path.of(modelDir);
        return new OnnxSentimentClassifier(
                baseDir.resolve("model.onnx"),
                baseDir.resolve("tokenizer.json"));
    }
}
