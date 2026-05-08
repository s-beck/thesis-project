package com.thesis.sentimentshop.inference;

public class SentimentClassificationException extends RuntimeException {

    public enum FailureMode {
        MODEL_ERROR,
        TIMEOUT,
        UNREACHABLE,
        UNKNOWN
    }

    private final FailureMode failureMode;

    public SentimentClassificationException(FailureMode failureMode, String message) {
        super(message);
        this.failureMode = failureMode;
    }

    public SentimentClassificationException(FailureMode failureMode,
                                            String message,
                                            Throwable cause) {
        super(message, cause);
        this.failureMode = failureMode;
    }

    public FailureMode failureMode() {
        return failureMode;
    }
}
