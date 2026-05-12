package com.thesis.sentimentshop.inference.externalasync;

import com.thesis.sentimentshop.inference.SentimentClassificationException.FailureMode;
import com.thesis.sentimentshop.inference.SentimentResultSink;
import com.thesis.sentimentshop.inference.external.ClassifyMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.util.function.Supplier;

public final class ExternalDeadLetterListener {

    private static final Logger log = LoggerFactory.getLogger(ExternalDeadLetterListener.class);

    private final Supplier<SentimentResultSink> sinkSupplier;
    private volatile SentimentResultSink resolvedSink;

    public ExternalDeadLetterListener(Supplier<SentimentResultSink> sinkSupplier) {
        this.sinkSupplier = sinkSupplier;
    }

    @RabbitListener(
            queues = "${sentiment.external-async.requests-dlq:sentiment.external.requests.dlq}",
            containerFactory = "sentimentExternalRabbitListenerContainerFactory")
    public void onDeadLetter(ClassifyMessage payload, Message rawMessage) {
        FailureMode mode = readFailureModeHeader(rawMessage);
        log.warn("X-Async DLQ landing for review {} → {} (from header)",
                payload.reviewId(), mode);
        sink().onFailure(payload.reviewId(), mode);
    }

    private static FailureMode readFailureModeHeader(Message rawMessage) {
        if (rawMessage == null) {
            return FailureMode.MODEL_ERROR;
        }
        Object header = rawMessage.getMessageProperties()
                .getHeaders()
                .get(ExternalAsyncRequestConsumer.FAILURE_MODE_HEADER);
        if (header == null) {
            log.warn("DLQ message has no {} header; defaulting to MODEL_ERROR",
                    ExternalAsyncRequestConsumer.FAILURE_MODE_HEADER);
            return FailureMode.MODEL_ERROR;
        }
        try {
            return FailureMode.valueOf(header.toString());
        } catch (IllegalArgumentException ex) {
            log.warn("DLQ message has unparseable {} header '{}'; defaulting to MODEL_ERROR",
                    ExternalAsyncRequestConsumer.FAILURE_MODE_HEADER, header);
            return FailureMode.MODEL_ERROR;
        }
    }

    private SentimentResultSink sink() {
        SentimentResultSink local = resolvedSink;
        if (local == null) {
            local = sinkSupplier.get();
            if (local == null) {
                throw new IllegalStateException(
                        "Sink supplier returned null. The X-Async DLQ listener cannot "
                                + "report failures without a sink. This usually indicates "
                                + "a Spring wiring problem in the x-async profile.");
            }
            resolvedSink = local;
        }
        return local;
    }
}