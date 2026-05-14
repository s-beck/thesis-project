package com.thesis.sentimentshop.reviews;

import com.thesis.sentimentshop.inference.SentimentClassificationException.FailureMode;
import com.thesis.sentimentshop.inference.measurement.MeasurementEvent;
import com.thesis.sentimentshop.inference.measurement.MeasurementLog;
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
        log.debug("Sweeper tick: pendingTimeoutMs={}", pendingTimeoutMs);
        try {
            Instant now = Instant.now();
            Instant cutoff = now.minus(Duration.ofMillis(pendingTimeoutMs));
            List<Review> stale = reviews.findStalePending(cutoff);
            log.debug("Sweeper found {} stale review(s)", stale.size());
            if (stale.isEmpty()) {
                return;
            }
            for (Review review : stale) {
                long pendingForMs = Duration.between(review.getCreatedAt(), now).toMillis();
                review.recordFailure(FailureMode.TIMEOUT);
                MeasurementLog.sweeperSwept(review.getId(), pendingForMs, now);
                MeasurementLog.reviewFailed(
                        review.getId(),
                        FailureMode.TIMEOUT,
                        pendingForMs,
                        now,
                        MeasurementEvent.Origin.SWEEPER);
            }
            log.warn("Pending-review sweeper flipped {} review(s) to TIMEOUT "
                    + "(pending longer than {} ms)", stale.size(), pendingTimeoutMs);
        } catch (Throwable t) {
            log.error("Pending-review sweeper threw {} — swallowing to keep scheduler alive",
                    t.getClass().getName(), t);
        }
    }
}