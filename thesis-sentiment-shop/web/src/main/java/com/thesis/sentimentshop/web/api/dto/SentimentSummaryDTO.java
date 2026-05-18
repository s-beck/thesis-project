package com.thesis.sentimentshop.web.api.dto;

import com.thesis.sentimentshop.inference.Sentiment;
import com.thesis.sentimentshop.reviews.SentimentSummary;

import java.util.Map;

// AI-assisted code: Created during baseline app init. Generated with Claude (Anthropic) and reviewed by the author.

public record SentimentSummaryDTO(
        Map<Sentiment, Long> counts,
        long totalClassified,
        long totalPending
) {
    public static SentimentSummaryDTO from(SentimentSummary s) {
        return new SentimentSummaryDTO(s.counts(), s.totalClassified(), s.totalPending());
    }
}
