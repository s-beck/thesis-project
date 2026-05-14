package com.thesis.sentimentshop.inference.measurement;

public final class MeasurementEvent {

    public static final String STARTUP = "startup";
    public static final String REVIEW_SUBMITTED = "review.submitted";
    public static final String REVIEW_CLASSIFIED = "review.classified";
    public static final String REVIEW_FAILED = "review.failed";
    public static final String SWEEPER_SWEPT = "sweeper.swept";
    public static final String FAULT_INJECTION_ARMED = "fault-injection.armed";
    public static final String FAULT_INJECTION_CLEARED = "fault-injection.cleared";

    public static final class Origin {
        public static final String SUBMIT = "submit";
        public static final String CALLBACK = "callback";
        public static final String SWEEPER = "sweeper";
        public static final String DLQ = "dlq";
        private Origin() {}
    }

    public static final class Path {
        public static final String SYNC = "sync";
        public static final String ASYNC_CALLBACK = "async-callback";
        private Path() {}
    }

    private MeasurementEvent() {}
}