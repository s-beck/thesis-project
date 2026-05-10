package com.thesis.sentimentshop.inference.embeddedasync;

import com.thesis.sentimentshop.inference.AsyncSentimentClassifier;
import com.thesis.sentimentshop.inference.SentimentClassificationException;
import com.thesis.sentimentshop.inference.SentimentClassificationException.FailureMode;
import com.thesis.sentimentshop.inference.SentimentClassifier;
import com.thesis.sentimentshop.inference.SentimentResult;
import com.thesis.sentimentshop.inference.SentimentResultSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public final class EmbeddedAsyncSentimentClassifier implements AsyncSentimentClassifier {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedAsyncSentimentClassifier.class);

    private final SentimentClassifier delegate;
    private final Supplier<SentimentResultSink> sinkSupplier;
    private final ThreadPoolExecutor executor;
    private final long shutdownGraceMillis;

    private volatile SentimentResultSink resolvedSink;

    public EmbeddedAsyncSentimentClassifier(SentimentClassifier delegate,
                                            Supplier<SentimentResultSink> sinkSupplier,
                                            int workerCount,
                                            int queueCapacity,
                                            long shutdownGraceMillis) {
        if (workerCount <= 0) {
            throw new IllegalArgumentException("workerCount must be positive, got " + workerCount);
        }
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be positive, got " + queueCapacity);
        }
        this.delegate = delegate;
        this.sinkSupplier = sinkSupplier;
        this.shutdownGraceMillis = shutdownGraceMillis;
        this.executor = new ThreadPoolExecutor(
                workerCount,
                workerCount,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                namedDaemonThreadFactory("e-async-worker"),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public void submit(long reviewId, String text) {
        try {
            executor.execute(() -> runClassification(reviewId, text));
        } catch (RejectedExecutionException e) {
            throw new SentimentClassificationException(
                    FailureMode.UNREACHABLE,
                    "Async dispatcher queue saturated; cannot accept review " + reviewId,
                    e);
        }
    }

    private void runClassification(long reviewId, String text) {
        SentimentResultSink sink = sink();
        try {
            SentimentResult result = delegate.classify(text);
            sink.onResult(reviewId, result);
        } catch (SentimentClassificationException e) {
            log.warn("Async classification failed for review {}: {} ({})",
                    reviewId, e.getMessage(), e.failureMode());
            safelyReportFailure(sink, reviewId, e.failureMode());
        } catch (RuntimeException e) {
            log.error("Async classification raised unexpected exception for review {}",
                    reviewId, e);
            safelyReportFailure(sink, reviewId, FailureMode.UNKNOWN);
        }
    }

    private SentimentResultSink sink() {
        SentimentResultSink local = resolvedSink;
        if (local == null) {
            local = sinkSupplier.get();
            if (local == null) {
                throw new IllegalStateException(
                        "Sink supplier returned null. The async classifier cannot deliver "
                                + "results without a sink. This usually indicates a Spring "
                                + "wiring problem in the e-async profile.");
            }
            resolvedSink = local;
        }
        return local;
    }

    private void safelyReportFailure(SentimentResultSink sink, long reviewId, FailureMode mode) {
        try {
            sink.onFailure(reviewId, mode);
        } catch (RuntimeException e) {
            log.error("Sink onFailure threw for review {}; review will remain pending",
                    reviewId, e);
        }
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(shutdownGraceMillis, TimeUnit.MILLISECONDS)) {
                var stillPending = executor.shutdownNow();
                log.warn("E-Async executor shutdown grace exceeded; cancelling {} pending tasks",
                        stillPending.size());
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    int queueDepth() {
        return executor.getQueue().size();
    }

    int activeWorkerCount() {
        return executor.getActiveCount();
    }

    private static ThreadFactory namedDaemonThreadFactory(String prefix) {
        AtomicLong counter = new AtomicLong();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}