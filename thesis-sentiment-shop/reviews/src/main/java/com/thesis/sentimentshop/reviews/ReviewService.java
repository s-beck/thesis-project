package com.thesis.sentimentshop.reviews;

import com.thesis.sentimentshop.catalog.Product;
import com.thesis.sentimentshop.catalog.ProductRepository;
import com.thesis.sentimentshop.inference.AsyncSentimentClassifier;
import com.thesis.sentimentshop.inference.Sentiment;
import com.thesis.sentimentshop.inference.SentimentClassificationException;
import com.thesis.sentimentshop.inference.SentimentClassificationException.FailureMode;
import com.thesis.sentimentshop.inference.SentimentClassifier;
import com.thesis.sentimentshop.inference.SentimentResult;
import com.thesis.sentimentshop.inference.SentimentResultSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class ReviewService implements SentimentResultSink {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final ReviewRepository reviews;
    private final ProductRepository products;
    private final Optional<SentimentClassifier> syncClassifier;
    private final Optional<AsyncSentimentClassifier> asyncClassifier;

    public ReviewService(ReviewRepository reviews,
                         ProductRepository products,
                         Optional<SentimentClassifier> syncClassifier,
                         Optional<AsyncSentimentClassifier> asyncClassifier) {
        if (syncClassifier.isPresent() == asyncClassifier.isPresent()) {
            throw new IllegalStateException(
                    "Exactly one of SentimentClassifier or AsyncSentimentClassifier "
                            + "must be on the classpath. Found: sync="
                            + syncClassifier.isPresent()
                            + ", async=" + asyncClassifier.isPresent()
                            + ". Check the active Maven profile in web/pom.xml.");
        }
        this.reviews = reviews;
        this.products = products;
        this.syncClassifier = syncClassifier;
        this.asyncClassifier = asyncClassifier;
    }

    @Transactional
    public Review submit(Long productId, String author, String text, int rating) {
        Product product = products.findById(productId)
                .orElseThrow(() -> new NoSuchElementException(
                        "product not found: " + productId));

        Review review = new Review(product, author, text, rating);

        if (syncClassifier.isPresent()) {
            classifySynchronously(review, text, productId);
            return reviews.save(review);
        }

        Review persisted = reviews.save(review);
        try {
            asyncClassifier.orElseThrow().submit(persisted.getId(), text);
        } catch (SentimentClassificationException e) {
            log.warn("Async submission failed for review {} on product {}: {} ({})",
                    persisted.getId(), productId, e.getMessage(), e.failureMode());
            persisted.recordFailure(e.failureMode());
        }
        return persisted;
    }

    private void classifySynchronously(Review review, String text, Long productId) {
        try {
            SentimentResult result = syncClassifier.orElseThrow().classify(text);
            review.recordSentiment(result.sentiment(), result.confidence());
        } catch (SentimentClassificationException e) {
            log.warn("Classification failed for review on product {}: {} ({})",
                    productId, e.getMessage(), e.failureMode());
            review.recordFailure(e.failureMode());
        }
    }

    @Override
    @Transactional
    public void onResult(long reviewId, SentimentResult result) {
        Review review = reviews.findById(reviewId)
                .orElseThrow(() -> new NoSuchElementException(
                        "review not found: " + reviewId));
        if (isTerminal(review)) {
            log.info("Ignoring late result for review {} — already in terminal state ({})",
                    reviewId, terminalLabel(review));
            return;
        }
        review.recordSentiment(result.sentiment(), result.confidence());
    }

    @Override
    @Transactional
    public void onFailure(long reviewId, FailureMode failureMode) {
        Review review = reviews.findById(reviewId)
                .orElseThrow(() -> new NoSuchElementException(
                        "review not found: " + reviewId));
        if (isTerminal(review)) {
            log.info("Ignoring late failure ({}) for review {} — already in terminal state ({})",
                    failureMode, reviewId, terminalLabel(review));
            return;
        }
        review.recordFailure(failureMode);
    }

    private static boolean isTerminal(Review review) {
        return review.getSentiment() != null || review.getClassificationFailureMode() != null;
    }

    private static String terminalLabel(Review review) {
        if (review.getSentiment() != null) {
            return "sentiment=" + review.getSentiment();
        }
        return "failureMode=" + review.getClassificationFailureMode();
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
