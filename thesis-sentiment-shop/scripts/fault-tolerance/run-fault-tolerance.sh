#!/opt/homebrew/bin/bash

set -euo pipefail

# --- Paths ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
MEASUREMENT_DIR="$PROJECT_DIR/logs/measurement" # same location web-pom.xml writes JSONL to
LOG_DIR="$SCRIPT_DIR/logs"
CORPUS_FILE="$SCRIPT_DIR/../jmeter/corpus.csv" # reuse the perf-campaign corpus

mkdir -p "$LOG_DIR" "$MEASUREMENT_DIR"

# --- Tunables ---
PHASE_REVIEWS=10
STARTUP_TIMEOUT_S=90
SHUTDOWN_GRACE_S=10
TAIL_WAIT_S=20
COOLDOWN_S=15
INTER_REVIEW_SLEEP_S=0.2
HEALTH_URL="http://localhost:8080/actuator/health"
SUT_PORT=8080
API_BASE="http://localhost:8080/api"
FAULT_API="$API_BASE/test/fault-injection"

TIMEOUT_PENDING_MS=10000
TIMEOUT_SWEEPER_WAIT_S=20

ALL_VARIANTS=(e-sync e-async s-sync s-async x-sync x-async)
ALL_MODES=(MODEL_ERROR TIMEOUT UNREACHABLE UNKNOWN)

declare -A STRUCTURAL_ELIGIBLE=(
    [s-async:TIMEOUT]=1
    [x-async:TIMEOUT]=1
)

is_structural_eligible() {
    local variant="$1" mode="$2"
    [[ -n "${STRUCTURAL_ELIGIBLE[${variant}:${mode}]:-}" ]]
}

# --- Logging ---
log() { printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }

# --- Product-ID resolution ---
PRODUCT_ID=""
resolve_product_id() {
    log "Resolving a product ID from /api/products"
    local raw
    raw=$(curl -fsS "$API_BASE/products?size=1") || {
        log "Failed to fetch product list"; return 1;
    }
    PRODUCT_ID=$(printf '%s' "$raw" | grep -oE '"id":[[:space:]]*[0-9]+' | head -n1 | grep -oE '[0-9]+')
    if [[ -z "$PRODUCT_ID" ]]; then
        log "Could not parse a product ID from: $raw"; return 1;
    fi
    log "Using product id=$PRODUCT_ID for all submissions in this scenario"
}

# --- Spring Boot JVM lifecycle ---
SUT_PID=""
SUT_LOG=""

start_sut() {
    local variant="$1"
    local run_id="$2"
    local extra_jvm_arg="${3:-}"
    SUT_LOG="$LOG_DIR/sut_${variant}_${run_id}.log"

    log "Starting SUT: variant=$variant run.id=$run_id ${extra_jvm_arg:+extraJvmArg=$extra_jvm_arg}"
    (
        cd "$PROJECT_DIR"
        mvn -q -pl web -P "$variant,evaluation" spring-boot:run \
            -Drun.id="$run_id" \
            -Dsentiment.fault-injection.enabled=true \
            $extra_jvm_arg \
            > "$SUT_LOG" 2>&1
    ) &
    SUT_PID=$!

    local elapsed=0
    while (( elapsed < STARTUP_TIMEOUT_S )); do
        if curl -fsS "$HEALTH_URL" 2>/dev/null | grep -q '"status":"UP"'; then
            log "SUT up after ${elapsed}s (pid=$SUT_PID)"
            return 0
        fi
        sleep 2
        elapsed=$((elapsed + 2))
    done
    log "SUT did not become healthy within ${STARTUP_TIMEOUT_S}s"
    log "Tail of $SUT_LOG:"
    tail -50 "$SUT_LOG" || true
    stop_sut
    return 1
}

stop_sut() {
    if [[ -z "$SUT_PID" ]]; then return 0; fi
    log "Stopping SUT (pid=$SUT_PID)"
    pkill -TERM -P "$SUT_PID" 2>/dev/null || true
    kill -TERM "$SUT_PID" 2>/dev/null || true

    local elapsed=0
    while (( elapsed < SHUTDOWN_GRACE_S )); do
        if ! kill -0 "$SUT_PID" 2>/dev/null; then break; fi
        sleep 1
        elapsed=$((elapsed + 1))
    done
    if kill -0 "$SUT_PID" 2>/dev/null; then
        log "SUT did not stop in ${SHUTDOWN_GRACE_S}s, sending SIGKILL"
        pkill -KILL -P "$SUT_PID" 2>/dev/null || true
        kill -KILL "$SUT_PID" 2>/dev/null || true
    fi
    wait "$SUT_PID" 2>/dev/null || true
    SUT_PID=""

    if command -v lsof >/dev/null; then
        local stray
        stray=$(lsof -ti tcp:"$SUT_PORT" 2>/dev/null || true)
        if [[ -n "$stray" ]]; then
            log "Killing stray listener on port $SUT_PORT: $stray"
            kill -KILL $stray 2>/dev/null || true
        fi
    fi
}

trap 'log "Caught signal; cleaning up"; stop_sut; restore_rabbitmq_if_stopped; exit 130' INT TERM
trap 'stop_sut; restore_rabbitmq_if_stopped' EXIT

# --- Structural fault helpers (rabbitmq stop/start) ---
RABBITMQ_STOPPED_BY_US=0

stop_rabbitmq() {
    log "Stopping rabbitmq container (structural fault)"
    (cd "$PROJECT_DIR" && docker compose stop rabbitmq) || {
        log "docker compose stop rabbitmq failed"; return 1;
    }
    RABBITMQ_STOPPED_BY_US=1
}

start_rabbitmq() {
    log "Starting rabbitmq container (recovery)"
    (cd "$PROJECT_DIR" && docker compose start rabbitmq) || {
        log "docker compose start rabbitmq failed"; return 1;
    }
    RABBITMQ_STOPPED_BY_US=0
    sleep 5
}

restore_rabbitmq_if_stopped() {
    if (( RABBITMQ_STOPPED_BY_US == 1 )); then
        log "Cleanup: restoring rabbitmq that we stopped"
        (cd "$PROJECT_DIR" && docker compose start rabbitmq) || true
        RABBITMQ_STOPPED_BY_US=0
    fi
}

# --- HTTP calls against the SUT ---
submit_review() {
    local product_id="$1"
    local idx="$2"
    local phase="$3"
    local text="Fault-tolerance review phase=$phase idx=$idx (variant scenario)"
    local status
    status=$(curl -s -o /dev/null -w '%{http_code}' \
        -X POST "$API_BASE/products/$product_id/reviews" \
        -H 'Content-Type: application/json' \
        -d "{\"rating\":5,\"text\":\"$text\"}") || status="err"
    log "  submit phase=$phase idx=$idx http=$status"
}

arm_injector() {
    local mode="$1"
    local count="$2"
    ARM_DISPOSITION="ERROR"
    ARM_BODY=""
    log "Arming injector: mode=$mode count=$count"
    local response status body
    response=$(curl -sS -w '\n%{http_code}' -X POST "$FAULT_API/arm" \
        -H 'Content-Type: application/json' \
        -d "{\"mode\":\"$mode\",\"count\":$count}") || {
        log "ERROR: curl to arm endpoint failed"; return 1;
    }
    status=$(printf '%s' "$response" | tail -n1)
    body=$(printf '%s' "$response" | sed '$d')
    ARM_BODY="$body"
    if [[ "$status" != "2"* ]]; then
        log "ERROR: arm endpoint returned HTTP $status; body: $body"
        log "       Likely cause: sentiment.fault-injection.enabled is not set"
        log "       in the running JVM — the wrapper bean is not present."
        return 1
    fi
    if printf '%s' "$body" | grep -qE '"disposition"[[:space:]]*:[[:space:]]*"ARMED"'; then
        ARM_DISPOSITION="ARMED"
        log "Arm accepted (HTTP $status): $body"
    elif printf '%s' "$body" | grep -qE '"disposition"[[:space:]]*:[[:space:]]*"SKIPPED"'; then
        ARM_DISPOSITION="SKIPPED"
        log "Arm skipped by wrapper — mode $mode is not in this variant's supportedModes: $body"
    else
        ARM_DISPOSITION="ERROR"
        log "ERROR: arm returned 2xx but disposition was not ARMED or SKIPPED; body: $body"
        return 1
    fi
}

write_outcome_sidecar() {
    local variant="$1" mode="$2" run_id="$3" outcome="$4" detail="${5:-}"
    local path="$MEASUREMENT_DIR/${variant}_${run_id}.outcome"
    {
        printf 'variant=%s\n' "$variant"
        printf 'failure_mode=%s\n' "$mode"
        printf 'run_id=%s\n' "$run_id"
        printf 'outcome=%s\n' "$outcome"
        printf 'detail=%s\n' "$detail"
        printf 'timestamp=%s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    } > "$path"
    log "Wrote outcome sidecar: $path (outcome=$outcome)"
}

verify_injector_present() {
    local response status
    response=$(curl -sS -w '\n%{http_code}' "$FAULT_API/state") || {
        log "ERROR: curl to state endpoint failed"; return 1;
    }
    status=$(printf '%s' "$response" | tail -n1)
    body=$(printf '%s' "$response" | sed '$d')
    if [[ "$status" != "2"* ]]; then
        log "ERROR: /fault-injection/state returned HTTP $status; body: $body"
        log "       The fault-injection wrapper bean is not in the Spring"
        log "       context. The 'sentiment.fault-injection.enabled=true'"
        log "       JVM property is not reaching the forked Spring Boot"
        log "       process. Check web-pom.xml's spring-boot-maven-plugin"
        log "       <jvmArguments> block — properties passed to 'mvn' on"
        log "       the command line are received by Maven, not by the"
        log "       forked JVM, unless the plugin forwards them explicitly."
        return 1
    fi
    log "Injector verified present: $body"
}

clear_injector() {
    log "Clearing injector"
    curl -fsS -X POST "$FAULT_API/clear" >/dev/null || true
}

# --- Scenario runners ---
run_attempted_scenario() {
    local variant="$1"
    local mode="$2"
    local run_id="ft-$(printf '%s' "$mode" | tr '[:upper:]' '[:lower:]')"
    local jsonl_path="$MEASUREMENT_DIR/${variant}_${run_id}.jsonl"
    local outcome_path="$MEASUREMENT_DIR/${variant}_${run_id}.outcome"

    log "=== Attempt: $variant x $mode ==="

    for stale in "$jsonl_path" "$outcome_path"; do
        if [[ -f "$stale" ]]; then
            local stamp; stamp=$(date '+%Y%m%d-%H%M%S')
            log "Rotating $stale -> ${stale}.${stamp}.bak"
            mv "$stale" "${stale}.${stamp}.bak"
        fi
    done

    start_sut "$variant" "$run_id" "" || {
        write_outcome_sidecar "$variant" "$mode" "$run_id" "ERROR" "SUT failed to start"
        return 1
    }
    verify_injector_present || {
        stop_sut
        write_outcome_sidecar "$variant" "$mode" "$run_id" "ERROR" "fault-injection wrapper not present"
        return 1
    }
    resolve_product_id || {
        stop_sut
        write_outcome_sidecar "$variant" "$mode" "$run_id" "ERROR" "could not resolve product id"
        return 1
    }

    log "--- Phase 1: baseline ($PHASE_REVIEWS reviews) ---"
    for ((i = 1; i <= PHASE_REVIEWS; i++)); do
        submit_review "$PRODUCT_ID" "$i" "baseline"
        sleep "$INTER_REVIEW_SLEEP_S"
    done
    log "Tail-wait ${TAIL_WAIT_S}s for async drain"
    sleep "$TAIL_WAIT_S"

    log "--- Phase 2: attempt arm (mode=$mode) ---"
    arm_injector "$mode" "$PHASE_REVIEWS" || {
        stop_sut
        write_outcome_sidecar "$variant" "$mode" "$run_id" "ERROR" "arm endpoint error: $ARM_BODY"
        return 1
    }

    case "$ARM_DISPOSITION" in
        ARMED)
            log "Wrapper accepted mode=$mode; submitting fault-phase reviews"
            for ((i = 1; i <= PHASE_REVIEWS; i++)); do
                submit_review "$PRODUCT_ID" "$i" "fault"
                sleep "$INTER_REVIEW_SLEEP_S"
            done
            log "Tail-wait ${TAIL_WAIT_S}s for async drain"
            sleep "$TAIL_WAIT_S"
            clear_injector

            # Phase 3 - Recovery
            log "--- Phase 3: recovery ($PHASE_REVIEWS reviews) ---"
            for ((i = 1; i <= PHASE_REVIEWS; i++)); do
                submit_review "$PRODUCT_ID" "$i" "recovery"
                sleep "$INTER_REVIEW_SLEEP_S"
            done
            log "Tail-wait ${TAIL_WAIT_S}s for async drain"
            sleep "$TAIL_WAIT_S"

            stop_sut
            write_outcome_sidecar "$variant" "$mode" "$run_id" "INJECTED" \
                "wrapper accepted; full three-phase scenario completed"
            ;;
        SKIPPED)
            clear_injector
            stop_sut
            log "Wrapper skipped mode=$mode; recording outcome and ending scenario"

            if is_structural_eligible "$variant" "$mode"; then
                write_outcome_sidecar "$variant" "$mode" "$run_id" "SKIPPED_BY_WRAPPER" \
                    "wrapper does not support this mode; structural fallback eligible"
                log "Cool-down ${COOLDOWN_S}s before structural run"
                sleep "$COOLDOWN_S"
                run_structural_timeout_scenario "$variant" "$mode" || \
                    log "Structural scenario $variant/$mode failed; continuing"
                return 0
            else
                write_outcome_sidecar "$variant" "$mode" "$run_id" "SKIPPED_BY_WRAPPER" \
                    "wrapper does not support this mode; no structural fallback"
            fi
            ;;
        *)
            stop_sut
            write_outcome_sidecar "$variant" "$mode" "$run_id" "ERROR" \
                "unexpected arm disposition: $ARM_DISPOSITION"
            ;;
    esac

    log "Cool-down ${COOLDOWN_S}s"
    sleep "$COOLDOWN_S"
}

run_structural_timeout_scenario() {
    local variant="$1"
    local mode="${2:-TIMEOUT}"
    local run_id="ft-${mode,,}-structural"
    local jsonl_path="$MEASUREMENT_DIR/${variant}_${run_id}.jsonl"
    local outcome_path="$MEASUREMENT_DIR/${variant}_${run_id}.outcome"

    log "=== Structural: $variant x $mode (broker stop) ==="

    for stale in "$jsonl_path" "$outcome_path"; do
        if [[ -f "$stale" ]]; then
            local stamp; stamp=$(date '+%Y%m%d-%H%M%S')
            log "Rotating $stale -> ${stale}.${stamp}.bak"
            mv "$stale" "${stale}.${stamp}.bak"
        fi
    done

    start_sut "$variant" "$run_id" "-Dsentiment.async.pending-timeout-ms=$TIMEOUT_PENDING_MS" || {
        write_outcome_sidecar "$variant" "$mode" "$run_id" "ERROR" "SUT failed to start"
        return 1
    }
    resolve_product_id || {
        stop_sut
        write_outcome_sidecar "$variant" "$mode" "$run_id" "ERROR" "could not resolve product id"
        return 1
    }

    log "--- Phase 1: baseline ($PHASE_REVIEWS reviews) ---"
    for ((i = 1; i <= PHASE_REVIEWS; i++)); do
        submit_review "$PRODUCT_ID" "$i" "baseline"
        sleep "$INTER_REVIEW_SLEEP_S"
    done
    log "Tail-wait ${TAIL_WAIT_S}s for async drain"
    sleep "$TAIL_WAIT_S"

    log "--- Phase 2: fault ($PHASE_REVIEWS reviews, structural=$mode) ---"
    stop_rabbitmq || {
        stop_sut
        write_outcome_sidecar "$variant" "$mode" "$run_id" "ERROR" "failed to stop rabbitmq"
        return 1
    }
    for ((i = 1; i <= PHASE_REVIEWS; i++)); do
        submit_review "$PRODUCT_ID" "$i" "fault"
        sleep "$INTER_REVIEW_SLEEP_S"
    done
    log "Waiting ${TIMEOUT_SWEEPER_WAIT_S}s for sweeper to flip pending reviews"
    sleep "$TIMEOUT_SWEEPER_WAIT_S"
    start_rabbitmq

    log "--- Phase 3: recovery ($PHASE_REVIEWS reviews) ---"
    for ((i = 1; i <= PHASE_REVIEWS; i++)); do
        submit_review "$PRODUCT_ID" "$i" "recovery"
        sleep "$INTER_REVIEW_SLEEP_S"
    done
    log "Tail-wait ${TAIL_WAIT_S}s for async drain"
    sleep "$TAIL_WAIT_S"

    stop_sut
    write_outcome_sidecar "$variant" "$mode" "$run_id" "STRUCTURAL" \
        "broker-stop mechanism (pending-timeout=${TIMEOUT_PENDING_MS}ms); sweeper-driven"
    log "Cool-down ${COOLDOWN_S}s"
    sleep "$COOLDOWN_S"
}

# --- Main loop ---
main() {
    local target_variant="${1:-}"
    local target_mode="${2:-}"

    local variants=("${ALL_VARIANTS[@]}")
    if [[ -n "$target_variant" ]]; then variants=("$target_variant"); fi
    local modes=("${ALL_MODES[@]}")
    if [[ -n "$target_mode" ]]; then modes=("$target_mode"); fi

    log "Fault-tolerance campaign start: variants=(${variants[*]}) modes=(${modes[*]})"
    log "Walking the full Cartesian product; the application's response to"
    log "each arm call determines whether the cell is INJECTED, SKIPPED, or"
    log "(for structural-eligible cells) re-run via the broker-stop fallback."

    for variant in "${variants[@]}"; do
        for mode in "${modes[@]}"; do
            run_attempted_scenario "$variant" "$mode" || \
                log "Scenario $variant/$mode failed; continuing"
        done
    done

    log "Fault-tolerance campaign complete."
}

main "$@"