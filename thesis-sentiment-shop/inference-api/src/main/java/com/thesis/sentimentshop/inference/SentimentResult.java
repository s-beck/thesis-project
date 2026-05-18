package com.thesis.sentimentshop.inference;

import java.time.Duration;
import java.time.Instant;

// AI-assisted code: Created during baseline app init. Generated with Claude (Anthropic) and reviewed by the author.

public record SentimentResult(
        Sentiment sentiment,
        double confidence,
        Duration latency,
        Instant completedAt
) {
    public SentimentResult {
        if (sentiment == null) {
            throw new IllegalArgumentException("sentiment must not be null");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException(
                    "confidence must be in [0.0, 1.0], was " + confidence);
        }
        if (latency == null || latency.isNegative()) {
            throw new IllegalArgumentException("latency must be non-negative");
        }
        if (completedAt == null) {
            throw new IllegalArgumentException("completedAt must not be null");
        }
    }
}
