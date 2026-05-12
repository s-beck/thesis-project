package com.thesis.sentimentshop.inference.selfhostedasync;

import com.thesis.sentimentshop.inference.AsyncSentimentClassifier;
import com.thesis.sentimentshop.inference.SentimentClassificationException;
import com.thesis.sentimentshop.inference.SentimentClassificationException.FailureMode;
import com.thesis.sentimentshop.inference.selfhosted.ClassifyMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.concurrent.TimeoutException;

// AI-assisted code: Generated with Claude (Anthropic) and reviewed/modified by the author.
public final class RabbitPublishingAsyncSentimentClassifier implements AsyncSentimentClassifier {

    private static final Logger log = LoggerFactory.getLogger(RabbitPublishingAsyncSentimentClassifier.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String requestsRoutingKey;
    private final long confirmTimeoutMs;

    public RabbitPublishingAsyncSentimentClassifier(RabbitTemplate rabbitTemplate,
                                                    String exchange,
                                                    String requestsRoutingKey,
                                                    long confirmTimeoutMs) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.requestsRoutingKey = requestsRoutingKey;
        this.confirmTimeoutMs = confirmTimeoutMs;
    }

    @Override
    public void submit(long reviewId, String text) {
        ClassifyMessage message = new ClassifyMessage(reviewId, text);
        CorrelationData correlation = new CorrelationData(Long.toString(reviewId));

        Boolean confirmed;
        try {
            confirmed = rabbitTemplate.invoke(operations -> {
                operations.convertAndSend(exchange, requestsRoutingKey, message, correlation);
                return operations.waitForConfirms(confirmTimeoutMs);
            });
        } catch (AmqpException e) {
            log.warn("Publish to broker failed for review {}: {}", reviewId, e.getMessage());
            throw new SentimentClassificationException(
                    FailureMode.UNREACHABLE,
                    "Broker publish failed for review " + reviewId,
                    e);
        } catch (RuntimeException e) {
            log.error("Unexpected error publishing review {}", reviewId, e);
            throw new SentimentClassificationException(
                    FailureMode.UNKNOWN,
                    "Unexpected publish failure for review " + reviewId,
                    e);
        }

        if (confirmed == null || !confirmed) {
            log.warn("Publisher confirm for review {} returned negative or timed out after {} ms",
                    reviewId, confirmTimeoutMs);
            throw new SentimentClassificationException(
                    FailureMode.UNREACHABLE,
                    "Broker did not confirm publication of review " + reviewId
                            + " within " + confirmTimeoutMs + " ms",
                    new TimeoutException("publisher confirm timeout"));
        }
    }
}