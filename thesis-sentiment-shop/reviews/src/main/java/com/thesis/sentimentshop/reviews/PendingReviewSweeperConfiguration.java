package com.thesis.sentimentshop.reviews;

import com.thesis.sentimentshop.inference.AsyncSentimentClassifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// AI-assisted code: Generated with Claude (Anthropic) and reviewed/modified by the author.
@Configuration
public class PendingReviewSweeperConfiguration {

    @Bean
    @ConditionalOnBean(AsyncSentimentClassifier.class)
    public PendingReviewSweeper pendingReviewSweeper(
            ReviewRepository reviews,
            @Value("${sentiment.async.pending-timeout-ms:30000}") long pendingTimeoutMs) {
        return new PendingReviewSweeper(reviews, pendingTimeoutMs);
    }
}