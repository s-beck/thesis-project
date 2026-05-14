package com.thesis.sentimentshop.inference;

import com.thesis.sentimentshop.inference.SentimentClassificationException.FailureMode;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class FaultInjectingClassifier implements SentimentClassifier, FaultInjectionControl {

    public enum Disposition {
        IDLE,
        ARMED,
        SKIPPED
    }

    public record InjectionState(Disposition disposition,
                                 FailureMode mode,
                                 int remaining,
                                 FailureMode skippedMode,
                                 String skippedReason) {

        static InjectionState idle() {
            return new InjectionState(Disposition.IDLE, null, 0, null, null);
        }

        static InjectionState armed(FailureMode mode, int remaining) {
            return new InjectionState(Disposition.ARMED, mode, remaining, null, null);
        }

        static InjectionState skipped(FailureMode mode, String reason) {
            return new InjectionState(Disposition.SKIPPED, null, 0, mode, reason);
        }
    }

    private final SentimentClassifier delegate;
    private final Set<FailureMode> supportedModes;
    private final String variantName;
    private final AtomicReference<InjectionState> state =
            new AtomicReference<>(InjectionState.idle());

    public FaultInjectingClassifier(SentimentClassifier delegate,
                                    Set<FailureMode> supportedModes,
                                    String variantName) {
        this.delegate = delegate;
        this.supportedModes = EnumSet.copyOf(supportedModes);
        this.variantName = variantName;
    }

    @Override
    public void scheduleFailures(FailureMode mode, int count) {
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive, got " + count);
        }
        if (!supportedModes.contains(mode)) {
            state.set(InjectionState.skipped(mode,
                    mode + " is structurally not producible by the "
                            + variantName + " variant"));
            return;
        }
        state.set(InjectionState.armed(mode, count));
    }

    @Override
    public void clear() {
        state.set(InjectionState.idle());
    }

    public InjectionState currentInjectionState() {
        return state.get();
    }

    @Override
    public Snapshot currentState() {
        return toSnapshot(state.get());
    }

    @Override
    public Set<FailureMode> supportedModes() {
        return EnumSet.copyOf(supportedModes);
    }

    @Override
    public String variantName() {
        return variantName;
    }

    private static Snapshot toSnapshot(InjectionState s) {
        return new Snapshot(
                switch (s.disposition()) {
                    case IDLE -> FaultInjectionControl.Disposition.IDLE;
                    case ARMED -> FaultInjectionControl.Disposition.ARMED;
                    case SKIPPED -> FaultInjectionControl.Disposition.SKIPPED;
                },
                s.mode(),
                s.remaining(),
                s.skippedMode(),
                s.skippedReason());
    }

    @Override
    public SentimentResult classify(String text) {
        InjectionState observed = consumeOneIfArmed();
        if (observed.disposition() == Disposition.ARMED) {
            throw new SentimentClassificationException(
                    observed.mode(),
                    "Injected failure (" + observed.mode() + ") for fault-tolerance test");
        }
        return delegate.classify(text);
    }

    private InjectionState consumeOneIfArmed() {
        while (true) {
            InjectionState current = state.get();
            if (current.disposition() != Disposition.ARMED) {
                return current;
            }
            int remaining = current.remaining() - 1;
            InjectionState next = (remaining > 0)
                    ? InjectionState.armed(current.mode(), remaining)
                    : InjectionState.idle();
            if (state.compareAndSet(current, next)) {
                return current;
            }
            // CAS lost — another thread won the race; re-read and retry.
        }
    }
}
