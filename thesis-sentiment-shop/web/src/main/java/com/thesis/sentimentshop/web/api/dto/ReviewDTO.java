package com.thesis.sentimentshop.web.api.dto;

import com.thesis.sentimentshop.inference.Sentiment;
import com.thesis.sentimentshop.inference.SentimentClassificationException.FailureMode;
import com.thesis.sentimentshop.reviews.Review;

import java.time.Instant;

// AI-assisted code: Created during baseline app init. Generated with Claude (Anthropic) and reviewed by the author.

public record ReviewDTO(Long id,
                        Long productId,
                        String author,
                        String text,
                        int rating,
                        Sentiment sentiment,
                        Double sentimentConfidence,
                        FailureMode classificationFailureMode, // Author Edit: added to be able to save the failure mode
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
                review.getClassificationFailureMode(), // Author Edit: added to be able to save the failure mode
                review.getCreatedAt(),
                review.getClassifiedAt()
        );
    }
}
