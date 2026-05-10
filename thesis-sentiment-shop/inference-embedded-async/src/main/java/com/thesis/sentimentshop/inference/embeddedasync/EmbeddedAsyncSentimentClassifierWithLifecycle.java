package com.thesis.sentimentshop.inference.embeddedasync;

import com.thesis.sentimentshop.inference.AsyncSentimentClassifier;
import com.thesis.sentimentshop.inference.SentimentClassifier;
import com.thesis.sentimentshop.inference.SentimentResultSink;
import com.thesis.sentimentshop.inference.embedded.OnnxSentimentClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public final class EmbeddedAsyncSentimentClassifierWithLifecycle implements AsyncSentimentClassifier {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedAsyncSentimentClassifierWithLifecycle.class);

    private final EmbeddedAsyncSentimentClassifier dispatcher;
    private final OnnxSentimentClassifier core;

    EmbeddedAsyncSentimentClassifierWithLifecycle(SentimentClassifier delegate,
                                                  Supplier<SentimentResultSink> sinkSupplier,
                                                  int workerCount,
                                                  int queueCapacity,
                                                  long shutdownGraceMillis,
                                                  OnnxSentimentClassifier core) {
        this.dispatcher = new EmbeddedAsyncSentimentClassifier(
                delegate, sinkSupplier, workerCount, queueCapacity, shutdownGraceMillis);
        this.core = core;
    }

    @Override
    public void submit(long reviewId, String text) {
        dispatcher.submit(reviewId, text);
    }

    public void shutdown() {
        dispatcher.shutdown();
        try {
            core.close();
        } catch (Exception e) {
            log.warn("ONNX core close() raised an exception during shutdown", e);
        }
    }
}