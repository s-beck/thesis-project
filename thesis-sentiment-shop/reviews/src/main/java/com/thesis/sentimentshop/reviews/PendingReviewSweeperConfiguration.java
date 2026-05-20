package com.thesis.sentimentshop.reviews;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// AI-assisted code: Generated with Claude (Anthropic) and reviewed/modified by the author.
@Configuration
public class PendingReviewSweeperConfiguration {

    // Author Edit: added the conditional property to enable the sweeper for S-Async and X-Async only
    @Bean
    @ConditionalOnProperty(
            name = "sentiment.async.sweeper.enabled",
            havingValue = "true",
            matchIfMissing = false
    )
    public PendingReviewSweeper pendingReviewSweeper(
            ReviewRepository reviews,
            @Value("${sentiment.async.pending-timeout-ms:30000}") long pendingTimeoutMs) {
        return new PendingReviewSweeper(reviews, pendingTimeoutMs);
    }
}