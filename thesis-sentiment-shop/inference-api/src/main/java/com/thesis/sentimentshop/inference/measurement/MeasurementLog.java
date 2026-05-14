package com.thesis.sentimentshop.inference.measurement;

import com.thesis.sentimentshop.inference.Sentiment;
import com.thesis.sentimentshop.inference.SentimentClassificationException.FailureMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.spi.LoggingEventBuilder;

import java.time.Instant;

public final class MeasurementLog {

    private static final Logger LOG = LoggerFactory.getLogger("measurement");
    private static final Marker MEASUREMENT = MarkerFactory.getMarker("MEASUREMENT");

    private static final String VARIANT = System.getProperty("measurement.variant", "unknown");
    private static final String RUN_ID = System.getProperty("run.id", "unknown");

    private MeasurementLog() {}

    public static String variant() {
        return VARIANT;
    }

    public static String runId() {
        return RUN_ID;
    }

    public static void startup(long startupDurationMs) {
        builder(MeasurementEvent.STARTUP)
                .addKeyValue("startupDurationMs", startupDurationMs)
                .log();
    }

    public static void reviewSubmitted(long reviewId, long productId, Instant submittedAt) {
        builder(MeasurementEvent.REVIEW_SUBMITTED)
                .addKeyValue("reviewId", reviewId)
                .addKeyValue("productId", productId)
                .addKeyValue("submittedAt", submittedAt.toString())
                .log();
    }

    public static void reviewClassified(long reviewId,
                                        Sentiment sentiment,
                                        double confidence,
                                        long latencyMs,
                                        Instant classifiedAt,
                                        String path) {
        builder(MeasurementEvent.REVIEW_CLASSIFIED)
                .addKeyValue("reviewId", reviewId)
                .addKeyValue("sentiment", sentiment != null ? sentiment.name() : null)
                .addKeyValue("confidence", confidence)
                .addKeyValue("latencyMs", latencyMs)
                .addKeyValue("classifiedAt", classifiedAt.toString())
                .addKeyValue("path", path)
                .log();
    }

    public static void reviewFailed(long reviewId,
                                    FailureMode failureMode,
                                    long latencyMs,
                                    Instant failedAt,
                                    String origin) {
        builder(MeasurementEvent.REVIEW_FAILED)
                .addKeyValue("reviewId", reviewId)
                .addKeyValue("failureMode", failureMode != null ? failureMode.name() : null)
                .addKeyValue("latencyMs", latencyMs)
                .addKeyValue("failedAt", failedAt.toString())
                .addKeyValue("origin", origin)
                .log();
    }

    public static void sweeperSwept(long reviewId, long pendingForMs, Instant sweptAt) {
        builder(MeasurementEvent.SWEEPER_SWEPT)
                .addKeyValue("reviewId", reviewId)
                .addKeyValue("pendingForMs", pendingForMs)
                .addKeyValue("sweptAt", sweptAt.toString())
                .log();
    }

    public static void faultInjectionArmed(String mode, int count) {
        builder(MeasurementEvent.FAULT_INJECTION_ARMED)
                .addKeyValue("mode", mode)
                .addKeyValue("count", count)
                .log();
    }

    public static void faultInjectionCleared() {
        builder(MeasurementEvent.FAULT_INJECTION_CLEARED).log();
    }

    private static LoggingEventBuilder builder(String event) {
        return LOG.atInfo()
                .addMarker(MEASUREMENT)
                .setMessage(event)
                .addKeyValue("event", event)
                .addKeyValue("variant", VARIANT)
                .addKeyValue("runId", RUN_ID);
    }
}