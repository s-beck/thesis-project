#!/opt/homebrew/bin/bash

set -euo pipefail

# --- Paths ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
RESULTS_DIR="$SCRIPT_DIR/results"
CORPUS_FILE="$SCRIPT_DIR/corpus.csv"
JMETER_LOG_DIR="$SCRIPT_DIR/jmeter-logs"
COMPOSE_FILE="$PROJECT_DIR/docker-compose.yml"

mkdir -p "$RESULTS_DIR" "$JMETER_LOG_DIR"

# --- Tunables ---
WARMUP_RUN_ID="warmup"
MEASUREMENT_RUNS=5 # measurement runs per cell
STARTUP_TIMEOUT_S=90 # max wait for /actuator/health=UP
SHUTDOWN_GRACE_S=10 # SIGTERM then wait, then SIGKILL
COOLDOWN_S=60 # between consecutive runs
TAIL_WAIT_SYNC_S=35 # sync variants: no broker backlog to drain
TAIL_WAIT_ASYNC_S=150 # async variants: drain broker + sweeper
ASYNC_HIGH_THROUGHPUT_PER_MIN=3000
COMPOSE_HEALTH_TIMEOUT_S=120 # max wait for required containers to be healthy
CONSUMER_CONNECT_WAIT_S=5 # let the s-async consumer connect after the app

HEALTH_URL="http://localhost:8080/actuator/health"
SUT_PORT=8080

# --- Variant & profile matrix ---
ALL_VARIANTS=(e-sync e-async s-sync s-async x-sync x-async)
ASYNC_VARIANTS=(e-async s-async x-async)
ALL_PROFILES=(baseline moderate high)

# High-profile thread count per variant
declare -A HIGH_THREADS=(
    [e-sync]=20  [e-async]=20
    [s-sync]=30  [s-async]=30
    [x-sync]=10  [x-async]=10
)

declare -A VARIANT_SERVICES=(
    [e-sync]="mariadb"
    [e-async]="mariadb"
    [s-sync]="mariadb inference-service"
    [s-async]="mariadb inference-service rabbitmq"
    [x-sync]="mariadb"
    [x-async]="mariadb rabbitmq"
)

# --- Logging ---
log() { printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }

is_async() {
    local v="$1"
    for a in "${ASYNC_VARIANTS[@]}"; do
        [[ "$v" == "$a" ]] && return 0
    done
    return 1
}

# --- Per-run infrastructure reset ---
reset_state() {
    local variant="$1"
    local services="${VARIANT_SERVICES[$variant]}"

    log "Resetting infrastructure for $variant (services: $services)"

    # Tear down containers and volumes
    docker compose -f "$COMPOSE_FILE" down -v --remove-orphans >/dev/null 2>&1 || true

    # Bring up only the services this variant depends on
    # shellcheck disable=SC2086
    docker compose -f "$COMPOSE_FILE" up -d $services >/dev/null 2>&1

    # Wait until each started service reports healthy
    local elapsed=0
    while (( elapsed < COMPOSE_HEALTH_TIMEOUT_S )); do
        local not_healthy=0
        for svc in $services; do
            local state
            state=$(docker compose -f "$COMPOSE_FILE" ps --format '{{.Service}} {{.Health}}' \
                    2>/dev/null | awk -v s="$svc" '$1==s {print $2}')
            if [[ "$state" != "healthy" ]]; then
                if [[ -z "$state" ]]; then
                    # No healthcheck defined: accept if the container is running.
                    local running
                    running=$(docker compose -f "$COMPOSE_FILE" ps --status running \
                              --format '{{.Service}}' 2>/dev/null | grep -cx "$svc" || true)
                    [[ "$running" -eq 1 ]] || not_healthy=1
                else
                    not_healthy=1
                fi
            fi
        done
        if [[ "$not_healthy" -eq 0 ]]; then
            log "Infrastructure healthy after ${elapsed}s"
            return 0
        fi
        sleep 3
        elapsed=$((elapsed + 3))
    done

    log "Infrastructure for $variant not healthy within ${COMPOSE_HEALTH_TIMEOUT_S}s"
    docker compose -f "$COMPOSE_FILE" ps || true
    return 1
}

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
        if is_async "$variant"; then
            jmeter_args+=(-JasyncThroughput="$ASYNC_HIGH_THROUGHPUT_PER_MIN")
            log "Async high pacing: ${ASYNC_HIGH_THROUGHPUT_PER_MIN} submissions/min"
        else
            jmeter_args+=(-JasyncThroughput=0)
        fi
    fi

    log "JMeter: profile=$profile variant=$variant run.id=$run_id"
    jmeter "${jmeter_args[@]}"
}

run_cell() {
    local variant="$1"
    local profile="$2"
    local run_id="$3"

    # Fresh DB + broker for every run, with only this variant's services up.
    reset_state "$variant" || return 1
    start_sut "$variant" "$run_id" || return 1

    if [[ "$variant" == "s-async" ]]; then
        log "Starting inference-service-consumer (broker topology now exists)"
        docker compose -f "$COMPOSE_FILE" up -d inference-service-consumer >/dev/null 2>&1
        sleep "$CONSUMER_CONNECT_WAIT_S"
    fi

    run_jmeter "$profile" "$variant" "$run_id" || true

    local tail_wait="$TAIL_WAIT_SYNC_S"
    if is_async "$variant"; then
        tail_wait="$TAIL_WAIT_ASYNC_S"
    fi
    log "Tail-wait ${tail_wait}s for async drain + sweeper"
    sleep "$tail_wait"

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
            run_cell "$variant" "$profile" "${profile}_${WARMUP_RUN_ID}"

            for ((i = 1; i <= MEASUREMENT_RUNS; i++)); do
                log "--- Measurement run $i/$MEASUREMENT_RUNS ---"
                run_cell "$variant" "$profile" "${profile}_m${i}"
            done
        done
    done

    log "Campaign complete."
}

main "$@"