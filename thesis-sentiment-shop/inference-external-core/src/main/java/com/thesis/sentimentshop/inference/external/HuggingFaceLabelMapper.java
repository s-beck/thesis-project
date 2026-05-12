package com.thesis.sentimentshop.inference.external;

import com.thesis.sentimentshop.inference.Sentiment;
import com.thesis.sentimentshop.inference.SentimentClassificationException;
import com.thesis.sentimentshop.inference.SentimentClassificationException.FailureMode;

import java.util.Locale;
import java.util.Map;

public final class HuggingFaceLabelMapper {

    private static final Map<String, Sentiment> LABEL_TO_SENTIMENT = Map.of(
            "negative", Sentiment.NEGATIVE,
            "neutral", Sentiment.NEUTRAL,
            "positive", Sentiment.POSITIVE
    );

    private HuggingFaceLabelMapper() {
    }

    public static Sentiment map(String label) {
        if (label == null) {
            throw new SentimentClassificationException(
                    FailureMode.UNKNOWN,
                    "HuggingFace response contained null label");
        }
        Sentiment sentiment = LABEL_TO_SENTIMENT.get(label.toLowerCase(Locale.ROOT));
        if (sentiment == null) {
            throw new SentimentClassificationException(
                    FailureMode.UNKNOWN,
                    "HuggingFace returned unrecognised label: " + label);
        }
        return sentiment;
    }
}