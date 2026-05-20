package com.thesis.sentimentshop.inference.external;

import com.thesis.sentimentshop.inference.Sentiment;
import com.thesis.sentimentshop.inference.SentimentClassificationException;
import com.thesis.sentimentshop.inference.SentimentClassificationException.FailureMode;
import com.thesis.sentimentshop.inference.SentimentClassifier;
import com.thesis.sentimentshop.inference.SentimentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
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

public class HuggingFaceSentimentClient implements SentimentClassifier {

    private static final Logger log = LoggerFactory.getLogger(HuggingFaceSentimentClient.class);

    private final RestClient restClient;

    public HuggingFaceSentimentClient(String endpointUrl,
                                      String bearerToken,
                                      Duration connectTimeout,
                                      Duration readTimeout) {
        this.restClient = RestClient.builder()
                .baseUrl(endpointUrl)
                .requestFactory(requestFactory(connectTimeout, readTimeout))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // // AI-assisted code: Method generated with Claude (Anthropic) and reviewed/modified by the author.
    @Override
    public SentimentResult classify(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }

        Instant started = Instant.now();
        HuggingFaceClassifyResponse[][] body;

        // Author Edit: refactored by catching the different http error states
        try {
            body = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(HuggingFaceClassifyRequest.of(text))
                    .retrieve()
                    .body(HuggingFaceClassifyResponse[][].class);
        } catch (HttpServerErrorException ex) {
            throw mapServerError(ex); // Author Edit: added method for http error handling
        } catch (HttpClientErrorException ex) {
            throw mapClientError(ex); // Author Edit: added method for http error handling
        } catch (RestClientResponseException ex) {
            throw new SentimentClassificationException(
                    FailureMode.UNKNOWN,
                    "Unexpected HTTP status " + ex.getStatusCode().value()
                            + " from HuggingFace Inference API",
                    ex);
        } catch (ResourceAccessException ex) {
            throw mapTransportFailure(ex); // Author Edit: added method for error handling
        } catch (RestClientException ex) {
            throw new SentimentClassificationException(
                    FailureMode.UNKNOWN,
                    "RestClient call failed: " + ex.getMessage(),
                    ex);
        }

        if (body == null || body.length == 0 || body[0] == null || body[0].length == 0) {
            // 200 with structurally-malformed body: MODEL_ERROR.
            // The model "succeeded" at the HTTP level but produced nothing we can interpret as a classification.
            throw new SentimentClassificationException(
                    FailureMode.MODEL_ERROR,
                    "HuggingFace returned 200 with empty or malformed body");
        }

        HuggingFaceClassifyResponse top = topScoring(body[0]);
        Sentiment sentiment = HuggingFaceLabelMapper.map(top.label());
        Duration latency = Duration.between(started, Instant.now());

        return new SentimentResult(
                sentiment,
                top.score(),
                latency,
                Instant.now()
        );
    }

    // AI-assisted code: Method generated with Claude (Anthropic) and reviewed/modified by the author.
    private static HuggingFaceClassifyResponse topScoring(HuggingFaceClassifyResponse[] entries) {
        HuggingFaceClassifyResponse best = entries[0];
        for (int i = 1; i < entries.length; i++) {
            HuggingFaceClassifyResponse candidate = entries[i];
            if (candidate != null && candidate.score() > best.score()) {
                best = candidate;
            }
        }
        if (best == null || best.label() == null) {
            throw new SentimentClassificationException(
                    FailureMode.MODEL_ERROR,
                    "HuggingFace returned a result list with no usable entry");
        }
        return best;
    }
    // Author Edit: added method for https error handling
    private static SentimentClassificationException mapServerError(HttpServerErrorException ex) {
        int status = ex.getStatusCode().value();
        String body = safeBody(ex);
        if (status == 503 && body != null && body.contains("estimated_time")) {
            log.info("HuggingFace returned 503 model-loading (cold start). Body: {}", body);
            return new SentimentClassificationException(
                    FailureMode.UNREACHABLE,
                    "HuggingFace model is loading (503 cold start): " + body,
                    ex);
        }
        log.warn("HuggingFace returned {} server error. Body: {}", status, body);
        return new SentimentClassificationException(
                FailureMode.UNREACHABLE,
                "HuggingFace returned " + status + ": " + body,
                ex);
    }

    // Author Edit: added method for http error handling
    private static SentimentClassificationException mapClientError(HttpClientErrorException ex) {
        int status = ex.getStatusCode().value();
        String body = safeBody(ex);
        if (status == 429) {
            log.warn("HuggingFace returned 429 rate-limited. Body: {}", body);
            return new SentimentClassificationException(
                    FailureMode.UNREACHABLE,
                    "HuggingFace rate-limited the request (429): " + body,
                    ex);
        }
        log.error("HuggingFace returned {} client error — likely misconfiguration. Body: {}",
                status, body);
        return new SentimentClassificationException(
                FailureMode.UNKNOWN,
                "HuggingFace rejected request with " + status + ": " + body,
                ex);
    }

    // Author Edit: added method for error handling
    private static SentimentClassificationException mapTransportFailure(ResourceAccessException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof SocketTimeoutException) {
            return new SentimentClassificationException(
                    FailureMode.TIMEOUT,
                    "HuggingFace did not respond within the configured read timeout",
                    ex);
        }
        if (cause instanceof ConnectException
                || cause instanceof UnknownHostException) {
            return new SentimentClassificationException(
                    FailureMode.UNREACHABLE,
                    "HuggingFace unreachable: " + cause.getMessage(),
                    ex);
        }
        return new SentimentClassificationException(
                FailureMode.UNREACHABLE,
                "Transport-level failure calling HuggingFace: "
                        + (cause != null ? cause.getMessage() : ex.getMessage()),
                ex);
    }

    // Author Edit: excluded this part in own method to reuse in both server and client error handling
    private static String safeBody(RestClientResponseException ex) {
        try {
            return ex.getResponseBodyAsString();
        } catch (RuntimeException ignored) {
            return null;
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