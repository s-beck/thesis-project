package com.thesis.sentimentshop.reviews;

import com.thesis.sentimentshop.inference.Sentiment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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
}
