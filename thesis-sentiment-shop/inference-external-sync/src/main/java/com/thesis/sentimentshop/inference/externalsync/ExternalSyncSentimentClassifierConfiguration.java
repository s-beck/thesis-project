package com.thesis.sentimentshop.inference.externalsync;

import com.thesis.sentimentshop.inference.FaultInjectingClassifier;
import com.thesis.sentimentshop.inference.SentimentClassificationException.FailureMode;
import com.thesis.sentimentshop.inference.SentimentClassifier;
import com.thesis.sentimentshop.inference.external.HuggingFaceSentimentClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;

@Configuration
public class ExternalSyncSentimentClassifierConfiguration {

    private static final Set<FailureMode> SUPPORTED_FAILURE_MODES = EnumSet.allOf(FailureMode.class);

    private static final String VARIANT_NAME = "X-Sync";

    @Bean
    @ConditionalOnProperty(
            name = "sentiment.fault-injection.enabled",
            havingValue = "false",
            matchIfMissing = true
    )
    public SentimentClassifier huggingFaceSentimentClassifier(
            @Value("${sentiment.external.url}") String endpointUrl,
            @Value("${sentiment.external.token:}") String token,
            @Value("${sentiment.external.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${sentiment.external.read-timeout-ms:10000}") long readTimeoutMs) {
        return realClient(endpointUrl, token, connectTimeoutMs, readTimeoutMs);
    }

    @Bean
    @ConditionalOnProperty(
            name = "sentiment.fault-injection.enabled",
            havingValue = "true"
    )
    public FaultInjectingClassifier faultyHuggingFaceSentimentClassifier(
            @Value("${sentiment.external.url}") String endpointUrl,
            @Value("${sentiment.external.token:}") String token,
            @Value("${sentiment.external.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${sentiment.external.read-timeout-ms:10000}") long readTimeoutMs) {
        return new FaultInjectingClassifier(
                realClient(endpointUrl, token, connectTimeoutMs, readTimeoutMs),
                SUPPORTED_FAILURE_MODES,
                VARIANT_NAME);
    }

    private static HuggingFaceSentimentClient realClient(String endpointUrl,
                                                         String token,
                                                         long connectTimeoutMs,
                                                         long readTimeoutMs) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "X-Sync requires a HuggingFace API token. Set the "
                            + "HUGGINGFACE_API_TOKEN environment variable (or "
                            + "sentiment.external.token property) before "
                            + "starting under the x-sync Maven profile.");
        }
        return new HuggingFaceSentimentClient(
                endpointUrl,
                token,
                Duration.ofMillis(connectTimeoutMs),
                Duration.ofMillis(readTimeoutMs));
    }
}