package com.thesis.sentimentshop.inference.stub;

import com.thesis.sentimentshop.inference.Sentiment;
import com.thesis.sentimentshop.inference.SentimentClassifier;
import com.thesis.sentimentshop.inference.SentimentResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;

import java.time.Duration;
import java.time.Instant;

@Configuration
public class StubSentimentClassifierConfiguration {

    @Bean
    @ConditionalOnMissingBean(SentimentClassifier.class)
    public SentimentClassifier stubSentimentClassifier() {
        return text -> {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("text must not be blank");
            }
            return new SentimentResult(
                    Sentiment.NEUTRAL,
                    0.5,
                    Duration.ZERO,
                    Instant.now()
            );
        };
    }
}
