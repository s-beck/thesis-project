package com.thesis.sentimentshop.web.api.dto;

import com.thesis.sentimentshop.inference.Sentiment;
import com.thesis.sentimentshop.inference.SentimentClassificationException.FailureMode;
import com.thesis.sentimentshop.reviews.Review;

import java.time.Instant;

public record ReviewDTO(Long id,
                        Long productId,
                        String author,
                        String text,
                        int rating,
                        Sentiment sentiment,
                        Double sentimentConfidence,
                        FailureMode classificationFailureMode,
                        Instant createdAt,
                        Instant classifiedAt) {

    public static ReviewDTO from(Review review) {
        return new ReviewDTO(
                review.getId(),
                review.getProduct().getId(),
                review.getAuthor(),
                review.getText(),
                review.getRating(),
                review.getSentiment(),
                review.getSentimentConfidence(),
                review.getClassificationFailureMode(),
                review.getCreatedAt(),
                review.getClassifiedAt()
        );
    }
}
