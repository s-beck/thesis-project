package com.thesis.sentimentshop.inference.externalasync;

import com.thesis.sentimentshop.inference.SentimentResult;
import com.thesis.sentimentshop.inference.SentimentResultSink;
import com.thesis.sentimentshop.inference.external.ClassifyResultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

public final class ExternalSentimentResultListener {

    private static final Logger log = LoggerFactory.getLogger(ExternalSentimentResultListener.class);

    private final Supplier<SentimentResultSink> sinkSupplier;
    private volatile SentimentResultSink resolvedSink;

    public ExternalSentimentResultListener(Supplier<SentimentResultSink> sinkSupplier) {
        this.sinkSupplier = sinkSupplier;
    }

    @RabbitListener(
            queues = "${sentiment.external-async.results-queue:sentiment.external.results}",
            containerFactory = "sentimentExternalRabbitListenerContainerFactory")
    public void onResult(ClassifyResultMessage message) {
        log.debug("X-Async received result for review {}: {} (conf={}, latency={}ms)",
                message.reviewId(), message.sentiment(),
                message.confidence(), message.latencyMs());

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
                        "Sink supplier returned null. The X-Async result listener cannot "
                                + "deliver results without a sink. This usually indicates "
                                + "a Spring wiring problem in the x-async profile.");
            }
            resolvedSink = local;
        }
        return local;
    }
}