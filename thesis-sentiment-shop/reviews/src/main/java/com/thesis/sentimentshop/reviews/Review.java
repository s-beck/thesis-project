package com.thesis.sentimentshop.reviews;

import com.thesis.sentimentshop.catalog.Product;
import com.thesis.sentimentshop.inference.Sentiment;
import com.thesis.sentimentshop.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "reviews")
public class Review extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "author", nullable = false, length = 100)
    private String author;

    @Column(nullable = false, length = 4000)
    private String text;

    @Column(nullable = false)
    private int rating;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Sentiment sentiment;

    @Column(name = "sentiment_confidence")
    private Double sentimentConfidence;

    @Column(name = "classified_at")
    private Instant classifiedAt;

    protected Review() {
        // JPA
    }

    public Review(Product product, String author, String text, int rating) {
        this.product = product;
        this.author = author;
        this.text = text;
        this.rating = rating;
    }

    public void recordSentiment(Sentiment sentiment, double confidence) {
        this.sentiment = sentiment;
        this.sentimentConfidence = confidence;
        this.classifiedAt = Instant.now();
    }

    public Product getProduct() { return product; }
    public String getAuthor() { return author; }
    public String getText() { return text; }
    public int getRating() { return rating; }
    public Sentiment getSentiment() { return sentiment; }
    public Double getSentimentConfidence() { return sentimentConfidence; }
    public Instant getClassifiedAt() { return classifiedAt; }
}
