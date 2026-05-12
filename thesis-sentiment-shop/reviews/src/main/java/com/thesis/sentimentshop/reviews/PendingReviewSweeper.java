package com.thesis.sentimentshop.reviews;

import com.thesis.sentimentshop.inference.SentimentClassificationException.FailureMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

// AI-assisted code: Generated with Claude (Anthropic) and reviewed/modified by the author.
public class PendingReviewSweeper {

    private static final Logger log = LoggerFactory.getLogger(PendingReviewSweeper.class);

    private final ReviewRepository reviews;
    private final long pendingTimeoutMs;

    public PendingReviewSweeper(ReviewRepository reviews, long pendingTimeoutMs) {
        this.reviews = reviews;
        this.pendingTimeoutMs = pendingTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${sentiment.async.sweeper-interval-ms:5000}")
    @Transactional
    public void sweep() {
        Instant cutoff = Instant.now().minus(Duration.ofMillis(pendingTimeoutMs));
        List<Review> stale = reviews.findStalePending(cutoff);
        if (stale.isEmpty()) {
            return;
        }
        for (Review review : stale) {
            review.recordFailure(FailureMode.TIMEOUT);
        }
        log.warn("Pending-review sweeper flipped {} review(s) to TIMEOUT "
                + "(pending longer than {} ms)", stale.size(), pendingTimeoutMs);
    }
}