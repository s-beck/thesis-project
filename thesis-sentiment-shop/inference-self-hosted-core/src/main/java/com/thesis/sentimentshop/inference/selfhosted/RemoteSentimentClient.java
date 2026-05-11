package com.thesis.sentimentshop.inference.selfhosted;

import com.thesis.sentimentshop.inference.Sentiment;
import com.thesis.sentimentshop.inference.SentimentClassificationException;
import com.thesis.sentimentshop.inference.SentimentClassificationException.FailureMode;
import com.thesis.sentimentshop.inference.SentimentClassifier;
import com.thesis.sentimentshop.inference.SentimentResult;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;

public class RemoteSentimentClient implements SentimentClassifier {

    private static final String CLASSIFY_PATH = "/classify";

    private final RestClient restClient;

    public RemoteSentimentClient(String baseUrl,
                                 Duration connectTimeout,
                                 Duration readTimeout) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory(connectTimeout, readTimeout))
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public SentimentResult classify(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }

        Instant started = Instant.now();
        ClassifyResponse response;

        try {
            response = restClient.post()
                    .uri(CLASSIFY_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ClassifyRequest(text))
                    .retrieve()
                    .body(ClassifyResponse.class);
        } catch (HttpServerErrorException ex) {
            throw new SentimentClassificationException(
                    FailureMode.MODEL_ERROR,
                    "Inference service returned " + ex.getStatusCode().value()
                            + ": " + ex.getResponseBodyAsString(),
                    ex);
        } catch (HttpClientErrorException ex) {
            throw new SentimentClassificationException(
                    FailureMode.MODEL_ERROR,
                    "Inference service rejected request with "
                            + ex.getStatusCode().value()
                            + ": " + ex.getResponseBodyAsString(),
                    ex);
        } catch (RestClientResponseException ex) {
            throw new SentimentClassificationException(
                    FailureMode.UNKNOWN,
                    "Unexpected HTTP status " + ex.getStatusCode().value()
                            + " from inference service",
                    ex);
        } catch (ResourceAccessException ex) {
            throw mapTransportFailure(ex);
        } catch (RestClientException ex) {
            throw new SentimentClassificationException(
                    FailureMode.UNKNOWN,
                    "RestClient call failed: " + ex.getMessage(),
                    ex);
        }

        if (response == null) {
            throw new SentimentClassificationException(
                    FailureMode.UNKNOWN,
                    "Inference service returned 200 with no body");
        }

        Duration latency = Duration.between(started, Instant.now());
        return new SentimentResult(
                mapSentiment(response.sentiment()),
                response.confidence(),
                latency,
                Instant.now()
        );
    }

    private static SentimentClassificationException mapTransportFailure(ResourceAccessException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof SocketTimeoutException) {
            return new SentimentClassificationException(
                    FailureMode.TIMEOUT,
                    "Inference service did not respond within the configured timeout",
                    ex);
        }
        if (cause instanceof ConnectException
                || cause instanceof UnknownHostException) {
            return new SentimentClassificationException(
                    FailureMode.UNREACHABLE,
                    "Inference service unreachable: " + cause.getMessage(),
                    ex);
        }
        return new SentimentClassificationException(
                FailureMode.UNREACHABLE,
                "Transport-level failure calling inference service: "
                        + (cause != null ? cause.getMessage() : ex.getMessage()),
                ex);
    }

    private static Sentiment mapSentiment(String fromService) {
        if (fromService == null) {
            throw new SentimentClassificationException(
                    FailureMode.UNKNOWN,
                    "Inference service returned null sentiment");
        }
        try {
            return Sentiment.valueOf(fromService);
        } catch (IllegalArgumentException ex) {
            throw new SentimentClassificationException(
                    FailureMode.UNKNOWN,
                    "Inference service returned unknown sentiment label: " + fromService,
                    ex);
        }
    }

    private static ClientHttpRequestFactory requestFactory(Duration connectTimeout,
                                                           Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) connectTimeout.toMillis());
        factory.setReadTimeout((int) readTimeout.toMillis());
        return factory;
    }
}