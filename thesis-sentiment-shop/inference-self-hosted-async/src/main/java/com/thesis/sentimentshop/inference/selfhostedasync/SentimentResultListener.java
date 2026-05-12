package com.thesis.sentimentshop.inference.selfhostedasync;

import com.thesis.sentimentshop.inference.SentimentResult;
import com.thesis.sentimentshop.inference.SentimentResultSink;
import com.thesis.sentimentshop.inference.selfhosted.ClassifyResultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

public final class SentimentResultListener {

    private static final Logger log = LoggerFactory.getLogger(SentimentResultListener.class);

    private final Supplier<SentimentResultSink> sinkSupplier;
    private volatile SentimentResultSink resolvedSink;

    public SentimentResultListener(Supplier<SentimentResultSink> sinkSupplier) {
        this.sinkSupplier = sinkSupplier;
    }

    @RabbitListener(queues = "${sentiment.async.results-queue:sentiment.results}",
            containerFactory = "sentimentRabbitListenerContainerFactory")
    public void onResult(ClassifyResultMessage message) {
        log.debug("Received result for review {}: {} (conf={}, latency={}ms)",
                message.reviewId(), message.sentiment(), message.confidence(), message.latencyMs());

        SentimentResult result = new SentimentResult(
                message.sentiment(),
                message.confidence(),
                Duration.ofMillis(message.latencyMs()),
                Instant.ofEpochMilli(message.computedAtEpochMs()));
        sink().onResult(message.reviewId(), result);
    }

    private SentimentResultSink sink() {
        SentimentResultSink local = resolvedSink;
        if (local == null) {
            local = sinkSupplier.get();
            if (local == null) {
                throw new IllegalStateException(
                        "Sink supplier returned null. The S-Async listener cannot deliver "
                                + "results without a sink. This usually indicates a Spring "
                                + "wiring problem in the s-async profile.");
            }
            resolvedSink = local;
        }
        return local;
    }
}