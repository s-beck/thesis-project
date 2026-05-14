package com.thesis.sentimentshop.inference;

import com.thesis.sentimentshop.inference.SentimentClassificationException.FailureMode;

import java.util.Set;

public interface FaultInjectionControl {

    void scheduleFailures(FailureMode mode, int count);
    void clear();

    Snapshot currentState();
    Set<FailureMode> supportedModes();
    String variantName();

    enum Disposition {
        IDLE,
        ARMED,
        SKIPPED
    }

    record Snapshot(Disposition disposition,
                    FailureMode mode,
                    int remaining,
                    FailureMode skippedMode,
                    String skippedReason) {}
}