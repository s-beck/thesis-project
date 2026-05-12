package com.thesis.sentimentshop.inference.selfhosted;

import com.thesis.sentimentshop.inference.Sentiment;

public record ClassifyResultMessage(
        long reviewId,
        Sentiment sentiment,
        double confidence,
        long latencyMs,
        long computedAtEpochMs) {
}