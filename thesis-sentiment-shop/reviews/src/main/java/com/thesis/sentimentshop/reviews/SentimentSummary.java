package com.thesis.sentimentshop.reviews;

import com.thesis.sentimentshop.inference.Sentiment;

import java.util.EnumMap;
import java.util.Map;

// AI-assisted code: Created during baseline app init. Generated with Claude (Anthropic) and reviewed by the author.

public record SentimentSummary(
        Map<Sentiment, Long> counts,
        long totalClassified,
        long totalPending
) {
    public static SentimentSummary empty() {
        Map<Sentiment, Long> zeros = new EnumMap<>(Sentiment.class);
        for (Sentiment s : Sentiment.values()) {
            zeros.put(s, 0L);
        }
        return new SentimentSummary(zeros, 0, 0);
    }
}
