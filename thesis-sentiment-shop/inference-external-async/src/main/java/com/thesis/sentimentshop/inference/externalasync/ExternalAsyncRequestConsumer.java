package com.thesis.sentimentshop.inference.externalasync;

import com.thesis.sentimentshop.inference.SentimentClassificationException;
import com.thesis.sentimentshop.inference.SentimentClassificationException.FailureMode;
import com.thesis.sentimentshop.inference.SentimentClassifier;
import com.thesis.sentimentshop.inference.SentimentResult;
import com.thesis.sentimentshop.inference.external.ClassifyMessage;
import com.thesis.sentimentshop.inference.external.ClassifyResultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

public final class ExternalAsyncRequestConsumer {

    private static final Logger log = LoggerFactory.getLogger(ExternalAsyncRequestConsumer.class);
    static final String FAILURE_MODE_HEADER = "x-sentiment-failure-mode";

    private final SentimentClassifier classifier;
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String resultsRoutingKey;

    public ExternalAsyncRequestConsumer(SentimentClassifier classifier,
                                        RabbitTemplate rabbitTemplate,
                                        String exchange,
                                        String resultsRoutingKey) {
        this.classifier = classifier;
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.resultsRoutingKey = resultsRoutingKey;
    }

    @RabbitListener(
            queues = "${sentiment.external-async.requests-queue:sentiment.external.requests}",
            containerFactory = "sentimentExternalRabbitListenerContainerFactory")
    public void onRequest(ClassifyMessage payload, Message rawMessage) {
        long startMs = System.currentTimeMillis();

        SentimentResult result;
        // AI-assisted code: Refactored method for error handling with the help of Claude (Anthropic), result has been reviewed by the author.
        try {
            result = classifier.classify(payload.text());
        } catch (SentimentClassificationException ex) {
            handleClassificationFailure(payload.reviewId(), rawMessage, ex);
            return; // unreachable — handleClassificationFailure always throws
        } catch (RuntimeException ex) {
            log.error("X-Async consumer: unexpected error for review {}",
                    payload.reviewId(), ex);
            stampFailureMode(rawMessage, FailureMode.UNKNOWN);
            throw new AmqpRejectAndDontRequeueException(
                    "Unexpected error classifying review " + payload.reviewId(), ex);
        }

        long endMs = System.currentTimeMillis();
        ClassifyResultMessage resultMessage = new ClassifyResultMessage(
                payload.reviewId(),
                result.sentiment(),
                result.confidence(),
                result.latency().toMillis(),
                endMs);

        log.debug("X-Async classified review {}: {} (conf={}, vendor-latency≈{}ms)",
                payload.reviewId(), result.sentiment(), result.confidence(),
                (endMs - startMs));

        rabbitTemplate.convertAndSend(exchange, resultsRoutingKey, resultMessage);
    }
    // AI-assisted code: method generated with Claude (Anthropic) and reviewed by the author.
    private void handleClassificationFailure(long reviewId,
                                             Message rawMessage,
                                             SentimentClassificationException ex) {
        FailureMode mode = ex.failureMode();
        stampFailureMode(rawMessage, mode);

        if (mode == FailureMode.UNKNOWN) {
            log.error("X-Async consumer: UNKNOWN (vendor misconfiguration) for review {} — "
                    + "rejecting without requeue", reviewId, ex);
            throw new AmqpRejectAndDontRequeueException(
                    "Misconfiguration for review " + reviewId
                            + "; not retrying. Will land on DLQ.", ex);
        }

        // UNREACHABLE or MODEL_ERROR — let the broker redeliver and
        // eventually DLQ after x-delivery-limit is exhausted.
        log.warn("X-Async consumer: {} for review {} — will redeliver",
                mode, reviewId, ex);
        throw ex;
    }

    // AI-assisted code: method generated with Claude (Anthropic) and reviewed by the author.
    private static void stampFailureMode(Message rawMessage, FailureMode mode) {
        if (rawMessage == null) {
            return;
        }
        rawMessage.getMessageProperties().setHeader(FAILURE_MODE_HEADER, mode.name());
    }
}