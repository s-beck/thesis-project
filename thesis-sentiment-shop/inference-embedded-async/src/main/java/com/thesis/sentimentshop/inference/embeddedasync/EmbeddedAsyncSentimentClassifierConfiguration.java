package com.thesis.sentimentshop.inference.embeddedasync;

import com.thesis.sentimentshop.inference.AsyncSentimentClassifier;
import com.thesis.sentimentshop.inference.FaultInjectingClassifier;
import com.thesis.sentimentshop.inference.SentimentClassificationException.FailureMode;
import com.thesis.sentimentshop.inference.SentimentClassifier;
import com.thesis.sentimentshop.inference.SentimentResultSink;
import com.thesis.sentimentshop.inference.embedded.OnnxSentimentClassifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;

@Configuration
public class EmbeddedAsyncSentimentClassifierConfiguration {

    private static final Set<FailureMode> SUPPORTED_FAILURE_MODES =
            EnumSet.of(FailureMode.MODEL_ERROR, FailureMode.UNREACHABLE, FailureMode.UNKNOWN);

    private static final String VARIANT_NAME = "E-Async";

    @Bean(destroyMethod = "shutdown")
    public AsyncSentimentClassifier embeddedAsyncSentimentClassifier(
            ObjectProvider<SentimentResultSink> sinkProvider,
            @Value("${SENTIMENT_MODEL_PATH:./model-artefact}") String modelDir,
            @Value("${sentiment.async.workers:4}") int workerCount,
            @Value("${sentiment.async.queue-capacity:256}") int queueCapacity,
            @Value("${sentiment.async.shutdown-grace-ms:5000}") long shutdownGraceMillis,
            @Value("${sentiment.fault-injection.enabled:false}") boolean faultInjectionEnabled) {

        OnnxSentimentClassifier core = realClassifier(modelDir);
        SentimentClassifier maybeFaulty = faultInjectionEnabled
                ? new FaultInjectingClassifier(core, SUPPORTED_FAILURE_MODES, VARIANT_NAME)
                : core;

        return new EmbeddedAsyncSentimentClassifierWithLifecycle(
                maybeFaulty,
                sinkProvider::getObject,
                workerCount,
                queueCapacity,
                shutdownGraceMillis,
                core);
    }

    private OnnxSentimentClassifier realClassifier(String modelDir) {
        Path baseDir = Path.of(modelDir);
        return new OnnxSentimentClassifier(
                baseDir.resolve("model.onnx"),
                baseDir.resolve("tokenizer.json"));
    }
}