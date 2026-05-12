package com.thesis.sentimentshop.inference.selfhostedasync;

import com.thesis.sentimentshop.inference.SentimentClassificationException.FailureMode;
import com.thesis.sentimentshop.inference.SentimentResultSink;
import com.thesis.sentimentshop.inference.selfhosted.ClassifyMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.util.function.Supplier;

// AI-assisted code: Generated with Claude (Anthropic) and reviewed/modified by the author.
public final class DeadLetterListener {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterListener.class);

    private final Supplier<SentimentResultSink> sinkSupplier;
    private volatile SentimentResultSink resolvedSink;

    public DeadLetterListener(Supplier<SentimentResultSink> sinkSupplier) {
        this.sinkSupplier = sinkSupplier;
    }

    @RabbitListener(queues = "${sentiment.async.requests-dlq:sentiment.requests.dlq}",
            containerFactory = "sentimentRabbitListenerContainerFactory")
    public void onDeadLetter(ClassifyMessage message) {
        log.warn("Request DLQ landing for review {} after redelivery exhaustion; "
                + "marking as MODEL_ERROR", message.reviewId());
        sink().onFailure(message.reviewId(), FailureMode.MODEL_ERROR);
    }

    private SentimentResultSink sink() {
        SentimentResultSink local = resolvedSink;
        if (local == null) {
            local = sinkSupplier.get();
            if (local == null) {
                throw new IllegalStateException(
                        "Sink supplier returned null. The S-Async DLQ listener cannot "
                                + "report failures without a sink. This usually indicates "
                                + "a Spring wiring problem in the s-async profile.");
            }
            resolvedSink = local;
        }
        return local;
    }
}