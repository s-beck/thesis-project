package com.thesis.sentimentshop.inference;

import com.thesis.sentimentshop.inference.SentimentClassificationException.FailureMode;

public interface SentimentResultSink {

    void onResult(long reviewId, SentimentResult result);

    void onFailure(long reviewId, FailureMode failureMode);
}
