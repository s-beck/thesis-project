package com.thesis.sentimentshop.inference.selfhostedsync;

import com.thesis.sentimentshop.inference.FaultInjectingClassifier;
import com.thesis.sentimentshop.inference.SentimentClassificationException.FailureMode;
import com.thesis.sentimentshop.inference.SentimentClassifier;
import com.thesis.sentimentshop.inference.selfhosted.RemoteSentimentClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;

@Configuration
public class SelfHostedSyncSentimentClassifierConfiguration {

    private static final Set<FailureMode> SUPPORTED_FAILURE_MODES = EnumSet.allOf(FailureMode.class);

    private static final String VARIANT_NAME = "S-Sync";

    @Bean
    @ConditionalOnProperty(
            name = "sentiment.fault-injection.enabled",
            havingValue = "false",
            matchIfMissing = true
    )
    public SentimentClassifier remoteSentimentClassifier(
            @Value("${sentiment.self-hosted.base-url:http://localhost:18000}") String baseUrl,
            @Value("${sentiment.self-hosted.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${sentiment.self-hosted.read-timeout-ms:5000}") long readTimeoutMs) {
        return realClient(baseUrl, connectTimeoutMs, readTimeoutMs);
    }

    @Bean
    @ConditionalOnProperty(
            name = "sentiment.fault-injection.enabled",
            havingValue = "true"
    )
    public SentimentClassifier faultyRemoteSentimentClassifier(
            @Value("${sentiment.self-hosted.base-url:http://localhost:18000}") String baseUrl,
            @Value("${sentiment.self-hosted.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${sentiment.self-hosted.read-timeout-ms:5000}") long readTimeoutMs) {
        return new FaultInjectingClassifier(
                realClient(baseUrl, connectTimeoutMs, readTimeoutMs),
                SUPPORTED_FAILURE_MODES,
                VARIANT_NAME);
    }

    private static RemoteSentimentClient realClient(String baseUrl,
                                                    long connectTimeoutMs,
                                                    long readTimeoutMs) {
        return new RemoteSentimentClient(
                baseUrl,
                Duration.ofMillis(connectTimeoutMs),
                Duration.ofMillis(readTimeoutMs));
    }
}