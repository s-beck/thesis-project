package com.thesis.sentimentshop.reviews;

import com.thesis.sentimentshop.catalog.Product;
import com.thesis.sentimentshop.catalog.ProductRepository;
import com.thesis.sentimentshop.inference.Sentiment;
import com.thesis.sentimentshop.inference.SentimentClassificationException;
import com.thesis.sentimentshop.inference.SentimentClassifier;
import com.thesis.sentimentshop.inference.SentimentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final ReviewRepository reviews;
    private final ProductRepository products;
    private final SentimentClassifier classifier;

    public ReviewService(ReviewRepository reviews,
                         ProductRepository products,
                         SentimentClassifier classifier) {
        this.reviews = reviews;
        this.products = products;
        this.classifier = classifier;
    }

    @Transactional
    public Review submit(Long productId, String author, String text, int rating) {
        Product product = products.findById(productId)
                .orElseThrow(() -> new NoSuchElementException(
                        "product not found: " + productId));

        Review review = new Review(product, author, text, rating);

        try {
            SentimentResult result = classifier.classify(text);
            review.recordSentiment(result.sentiment(), result.confidence());
        } catch (SentimentClassificationException e) {
            log.warn("Classification failed for review on product {}: {} ({})",
                    productId, e.getMessage(), e.failureMode());
            // Review is persisted without sentiment — graceful degradation.
        }

        return reviews.save(review);
    }

    @Transactional
    public void recordClassification(Long reviewId, SentimentResult result) {
        Review review = reviews.findById(reviewId)
                .orElseThrow(() -> new NoSuchElementException(
                        "review not found: " + reviewId));
        review.recordSentiment(result.sentiment(), result.confidence());
    }

    @Transactional(readOnly = true)
    public Page<Review> listForProduct(Long productId, Pageable pageable) {
        return reviews.findByProductId(productId, pageable);
    }

    @Transactional(readOnly = true)
    public SentimentSummary summary() {
        Map<Sentiment, Long> counts = new EnumMap<>(Sentiment.class);
        for (Sentiment s : Sentiment.values()) {
            counts.put(s, 0L);
        }

        long totalClassified = 0;
        for (Object[] row : reviews.countBySentiment()) {
            Sentiment s = (Sentiment) row[0];
            long count = (Long) row[1];
            counts.put(s, count);
            totalClassified += count;
        }

        long totalPending = reviews.countBySentimentIsNull();
        return new SentimentSummary(counts, totalClassified, totalPending);
    }
}
