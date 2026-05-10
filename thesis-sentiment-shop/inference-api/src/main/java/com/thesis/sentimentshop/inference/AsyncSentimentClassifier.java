package com.thesis.sentimentshop.inference;

public interface AsyncSentimentClassifier {
    void submit(long reviewId, String text);
}
