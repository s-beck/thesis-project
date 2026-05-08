package com.thesis.sentimentshop.inference;

public interface SentimentClassifier {
    SentimentResult classify(String text);
}
