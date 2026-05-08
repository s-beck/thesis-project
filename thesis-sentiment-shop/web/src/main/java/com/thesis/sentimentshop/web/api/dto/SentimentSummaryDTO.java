package com.thesis.sentimentshop.web.api.dto;

import com.thesis.sentimentshop.inference.Sentiment;
import com.thesis.sentimentshop.reviews.SentimentSummary;

import java.util.Map;

public record SentimentSummaryDTO(
        Map<Sentiment, Long> counts,
        long totalClassified,
        long totalPending
) {
    public static SentimentSummaryDTO from(SentimentSummary s) {
        return new SentimentSummaryDTO(s.counts(), s.totalClassified(), s.totalPending());
    }
}
