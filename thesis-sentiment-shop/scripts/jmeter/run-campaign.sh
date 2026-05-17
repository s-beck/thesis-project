#!/opt/homebrew/bin/bash

set -euo pipefail

# --- Paths ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
RESULTS_DIR="$SCRIPT_DIR/results"
CORPUS_FILE="$SCRIPT_DIR/corpus.csv"
JMETER_LOG_DIR="$SCRIPT_DIR/jmeter-logs"

mkdir -p "$RESULTS_DIR" "$JMETER_LOG_DIR"

# --- Tunables ---
WARMUP_RUN_ID="warmup"
MEASUREMENT_RUNS=5 # measurement runs per cell
STARTUP_TIMEOUT_S=90 # max wait for /actuator/health=UP
SHUTDOWN_GRACE_S=10 # SIGTERM then wait, then SIGKILL
TAIL_WAIT_S=35 # async results + sweeper drain
COOLDOWN_S=60 # between consecutive runs

HEALTH_URL="http://localhost:8080/actuator/health"
SUT_PORT=8080

# --- Variant & profile matrix ---
ALL_VARIANTS=(e-sync e-async s-sync s-async x-sync x-async)
ALL_PROFILES=(baseline moderate high)

# High-profile thread count per variant
declare -A HIGH_THREADS=(
    [e-sync]=20  [e-async]=20
    [s-sync]=30  [s-async]=30
    [x-sync]=10  [x-async]=10
)

# --- Logging ---
log() { printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }

# --- Spring Boot JVM lifecycle ---
SUT_PID=""
SUT_LOG=""

start_sut() {
    local variant="$1"
    local run_id="$2"
    SUT_LOG="$JMETER_LOG_DIR/sut_${variant}_${run_id}.log"

    log "Starting SUT: variant=$variant run.id=$run_id"
    (
        cd "$PROJECT_DIR"
        mvn -q -pl web -P "$variant,evaluation" spring-boot:run \
            -Drun.id="$run_id" \
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

    # Kill Maven process tree, spring-boot:run launches a forked JVM, both need to be terminated
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

    # kill anything still listening on the SUT port
    if command -v lsof >/dev/null; then
        local stray
        stray=$(lsof -ti tcp:"$SUT_PORT" 2>/dev/null || true)
        if [[ -n "$stray" ]]; then
            log "Killing stray listener on port $SUT_PORT: $stray"
            kill -KILL $stray 2>/dev/null || true
        fi
    fi
}

trap 'log "Caught signal; cleaning up"; stop_sut; exit 130' INT TERM
trap 'stop_sut' EXIT

# --- JMeter invocation ---
run_jmeter() {
    local profile="$1"
    local variant="$2"
    local run_id="$3"

    local plan="$SCRIPT_DIR/${profile}.jmx"
    local results_file="$RESULTS_DIR/${variant}_${profile}_${run_id}.jtl"
    local jmeter_log="$JMETER_LOG_DIR/jmeter_${variant}_${profile}_${run_id}.log"

    # Clear JTL from a previous campaign so JMeter starts fresh
    rm -f "$results_file"

    local -a jmeter_args=(
        -n -t "$plan"
        -l "$results_file"
        -j "$jmeter_log"
        -JcorpusFile="$CORPUS_FILE"
        -JresultsFile="$results_file"
    )

    # High-profile per-variant thread count
    if [[ "$profile" == "high" ]]; then
        jmeter_args+=(-Jthreads="${HIGH_THREADS[$variant]}")
    fi

    log "JMeter: profile=$profile variant=$variant run.id=$run_id"
    jmeter "${jmeter_args[@]}"
}

run_cell() {
    local variant="$1"
    local profile="$2"
    local run_id="$3"

    start_sut "$variant" "$run_id" || return 1
    run_jmeter "$profile" "$variant" "$run_id" || true

    log "Tail-wait ${TAIL_WAIT_S}s for async drain + sweeper"
    sleep "$TAIL_WAIT_S"

    stop_sut

    log "Cool-down ${COOLDOWN_S}s before next run"
    sleep "$COOLDOWN_S"
}

# --- Main loop ---
main() {
    local variants=("${ALL_VARIANTS[@]}")
    local profiles=("${ALL_PROFILES[@]}")

    if [[ $# -ge 1 ]]; then variants=("$1"); fi
    if [[ $# -ge 2 ]]; then profiles=("$2"); fi

    log "Campaign start: variants=(${variants[*]}) profiles=(${profiles[*]})"

    for variant in "${variants[@]}"; do
        for profile in "${profiles[@]}"; do
            log "=== Cell: $variant x $profile ==="

            log "--- Warmup run (discarded) ---"
            run_cell "$variant" "$profile" "$WARMUP_RUN_ID"

            for ((i = 1; i <= MEASUREMENT_RUNS; i++)); do
                log "--- Measurement run $i/$MEASUREMENT_RUNS ---"
                run_cell "$variant" "$profile" "m${i}"
            done
        done
    done

    log "Campaign complete."
}

main "$@"