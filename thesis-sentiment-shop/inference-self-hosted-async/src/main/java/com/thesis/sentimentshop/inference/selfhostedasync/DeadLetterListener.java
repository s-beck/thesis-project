package com.thesis.sentimentshop.inference.selfhostedasync;

import com.thesis.sentimentshop.inference.SentimentClassificationException.FailureMode;
import com.thesis.sentimentshop.inference.SentimentResultSink;
import com.thesis.sentimentshop.inference.selfhosted.ClassifyMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

// AI-assisted code: Generated with Claude (Anthropic) and reviewed/modified by the author.
public final class DeadLetterListener {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterListener.class);

    private static final String REASON_DELIVERY_LIMIT = "delivery_limit";
    private static final String REASON_REJECTED = "rejected";

    private final Supplier<SentimentResultSink> sinkSupplier;
    private volatile SentimentResultSink resolvedSink;

    public DeadLetterListener(Supplier<SentimentResultSink> sinkSupplier) {
        this.sinkSupplier = sinkSupplier;
    }

    @RabbitListener(queues = "${sentiment.async.requests-dlq:sentiment.requests.dlq}",
            containerFactory = "sentimentRabbitListenerContainerFactory")
    public void onDeadLetter(ClassifyMessage payload, Message rawMessage) {
        FailureMode mode = readFailureModeFromXDeath(rawMessage);
        log.warn("Request DLQ landing for review {} → {} (from x-death)",
                payload.reviewId(), mode);
        sink().onFailure(payload.reviewId(), mode);
    }

    private static FailureMode readFailureModeFromXDeath(Message rawMessage) {
        if (rawMessage == null) {
            log.warn("DLQ message has null raw envelope; defaulting to MODEL_ERROR");
            return FailureMode.MODEL_ERROR;
        }
        List<Map<String, ?>> xDeath = rawMessage.getMessageProperties().getXDeathHeader();
        if (xDeath == null || xDeath.isEmpty()) {
            log.warn("DLQ message has no x-death header; defaulting to MODEL_ERROR");
            return FailureMode.MODEL_ERROR;
        }
        Object reasonRaw = xDeath.get(0).get("reason");
        String reason = reasonRaw == null ? null : reasonRaw.toString();
        if (REASON_DELIVERY_LIMIT.equals(reason)) {
            return FailureMode.UNREACHABLE;
        }
        if (REASON_REJECTED.equals(reason)) {
            return FailureMode.MODEL_ERROR;
        }
        log.warn("DLQ message has unexpected x-death reason '{}'; defaulting to MODEL_ERROR",
                reason);
        return FailureMode.MODEL_ERROR;
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