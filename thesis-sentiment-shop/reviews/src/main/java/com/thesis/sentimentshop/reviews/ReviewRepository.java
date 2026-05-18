package com.thesis.sentimentshop.reviews;

import com.thesis.sentimentshop.inference.Sentiment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

// AI-assisted code: Created during baseline app init. Generated with Claude (Anthropic) and reviewed by the author.

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByProductId(Long productId, Pageable pageable);

    @Query("""
            SELECT r.sentiment, COUNT(r)
            FROM Review r
            WHERE r.sentiment IS NOT NULL
            GROUP BY r.sentiment
            """)
    List<Object[]> countBySentiment();

    @Query("""
            SELECT r.sentiment, COUNT(r)
            FROM Review r
            WHERE r.product.id = :productId AND r.sentiment IS NOT NULL
            GROUP BY r.sentiment
            """)
    List<Object[]> countBySentimentForProduct(@Param("productId") Long productId);

    long countBySentimentIsNull();

    long countBySentiment(Sentiment sentiment);

    // Reviews still in pending state
    @Query("""
            SELECT r FROM Review r
            WHERE r.sentiment IS NULL
              AND r.classificationFailureMode IS NULL
              AND r.createdAt < :cutoff
            """)
    List<Review> findStalePending(@Param("cutoff") Instant cutoff);
}
