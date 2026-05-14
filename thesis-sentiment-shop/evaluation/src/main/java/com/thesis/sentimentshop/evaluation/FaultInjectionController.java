package com.thesis.sentimentshop.evaluation;

import com.thesis.sentimentshop.inference.FaultInjectionControl;
import com.thesis.sentimentshop.inference.SentimentClassificationException.FailureMode;
import com.thesis.sentimentshop.inference.measurement.MeasurementLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/test/fault-injection")
public class FaultInjectionController {

    private static final Logger log = LoggerFactory.getLogger(FaultInjectionController.class);

    private final ObjectProvider<FaultInjectionControl> controlProvider;

    public FaultInjectionController(ObjectProvider<FaultInjectionControl> controlProvider) {
        this.controlProvider = controlProvider;
    }

    public record ArmRequest(String mode, int count) {}

    @PostMapping("/arm")
    public ResponseEntity<?> arm(@RequestBody ArmRequest request) {
        FaultInjectionControl control;
        try {
            control = resolve();
        } catch (NoSuchElementException e) {
            return unavailable(e.getMessage());
        }

        FailureMode mode;
        try {
            mode = FailureMode.valueOf(request.mode().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "invalid mode",
                    "got", String.valueOf(request.mode()),
                    "allowed", List.of("MODEL_ERROR", "TIMEOUT", "UNREACHABLE", "UNKNOWN")));
        }

        try {
            control.scheduleFailures(mode, request.count());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        MeasurementLog.faultInjectionArmed(mode.name(), request.count());
        log.info("Fault injection armed: variant={}, mode={}, count={}",
                control.variantName(), mode, request.count());

        return ResponseEntity.ok(control.currentState());
    }

    @PostMapping("/clear")
    public ResponseEntity<?> clear() {
        FaultInjectionControl control;
        try {
            control = resolve();
        } catch (NoSuchElementException e) {
            return unavailable(e.getMessage());
        }
        control.clear();
        MeasurementLog.faultInjectionCleared();
        log.info("Fault injection cleared: variant={}", control.variantName());
        return ResponseEntity.ok(control.currentState());
    }

    @GetMapping("/state")
    public ResponseEntity<?> state() {
        FaultInjectionControl control;
        try {
            control = resolve();
        } catch (NoSuchElementException e) {
            return unavailable(e.getMessage());
        }
        return ResponseEntity.ok(Map.of(
                "variant", control.variantName(),
                "supportedModes", control.supportedModes(),
                "state", control.currentState()));
    }

    private FaultInjectionControl resolve() {
        FaultInjectionControl c = controlProvider.getIfAvailable();
        if (c == null) {
            throw new NoSuchElementException(
                    "No FaultInjectionControl bean is registered in this context. "
                            + "Set sentiment.fault-injection.enabled=true and ensure "
                            + "a variant Maven profile (e.g. e-sync, s-async) is active.");
        }
        return c;
    }

    private static ResponseEntity<Map<String, String>> unavailable(String reason) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", reason));
    }
}