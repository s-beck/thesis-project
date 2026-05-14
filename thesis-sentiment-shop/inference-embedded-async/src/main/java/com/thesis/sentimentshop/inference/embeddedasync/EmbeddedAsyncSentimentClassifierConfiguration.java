package com.thesis.sentimentshop.inference.embeddedasync;

import com.thesis.sentimentshop.inference.AsyncSentimentClassifier;
import com.thesis.sentimentshop.inference.FaultInjectingClassifier;
import com.thesis.sentimentshop.inference.FaultInjectionControl;
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
    private volatile FaultInjectingClassifier sharedFaultInjector;

    @Bean(destroyMethod = "shutdown")
    public AsyncSentimentClassifier embeddedAsyncSentimentClassifier(
            ObjectProvider<SentimentResultSink> sinkProvider,
            @Value("${SENTIMENT_MODEL_PATH:./model-artefact}") String modelDir,
            @Value("${sentiment.fault-injection.enabled:false}") boolean faultInjectionEnabled,
            @Value("${sentiment.async.workers:4}") int workerCount,
            @Value("${sentiment.async.queue-capacity:256}") int queueCapacity,
            @Value("${sentiment.async.shutdown-grace-ms:5000}") long shutdownGraceMillis) {

        Path baseDir = Path.of(modelDir);
        OnnxSentimentClassifier core = new OnnxSentimentClassifier(
                baseDir.resolve("model.onnx"),
                baseDir.resolve("tokenizer.json"));

        SentimentClassifier dispatcherDelegate = core;
        if (faultInjectionEnabled) {
            FaultInjectingClassifier injector = new FaultInjectingClassifier(
                    core, SUPPORTED_FAILURE_MODES, VARIANT_NAME);
            this.sharedFaultInjector = injector;
            dispatcherDelegate = injector;
        }

        return new EmbeddedAsyncSentimentClassifierWithLifecycle(
                dispatcherDelegate,
                sinkProvider::getObject,
                workerCount,
                queueCapacity,
                shutdownGraceMillis,
                core);
    }

    @Bean
    public FaultInjectionControl embeddedAsyncFaultInjectionControl(
            AsyncSentimentClassifier embeddedAsyncSentimentClassifier) {
        if (this.sharedFaultInjector == null) {
            return NoOpFaultInjectionControl.INSTANCE;
        }
        return this.sharedFaultInjector;
    }

    private enum NoOpFaultInjectionControl implements FaultInjectionControl {
        INSTANCE;

        @Override
        public void scheduleFailures(FailureMode mode, int count) {
            // ignore
        }

        @Override
        public void clear() {
            // ignore
        }

        @Override
        public Snapshot currentState() {
            return new Snapshot(Disposition.IDLE, null, 0, null, null);
        }

        @Override
        public Set<FailureMode> supportedModes() {
            return EnumSet.noneOf(FailureMode.class);
        }

        @Override
        public String variantName() {
            return VARIANT_NAME + " (fault injection disabled)";
        }
    }
}