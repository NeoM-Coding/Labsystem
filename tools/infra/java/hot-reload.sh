#!/bin/sh
set -eu

APP_JAR="${APP_JAR:-/app/artifacts/app.jar}"
RELOAD_INTERVAL="${HOT_RELOAD_INTERVAL_SECONDS:-2}"
SETTLE_SECONDS="${HOT_RELOAD_SETTLE_SECONDS:-2}"
RESTART_DELAY="${APP_RESTART_DELAY_SECONDS:-3}"
PID_FILE="/tmp/lab-app.pid"
APP_PID=""

log() {
    printf '[java-runner] %s\n' "$*"
}

jar_signature() {
    stat -c '%i:%s:%Y' "$APP_JAR" 2>/dev/null || true
}

wait_for_stable_jar() {
    local before after
    while :; do
        if [ ! -f "$APP_JAR" ]; then
            log "waiting for application jar: $APP_JAR" >&2
            sleep "$RELOAD_INTERVAL"
            continue
        fi

        before="$(jar_signature)"
        sleep "$SETTLE_SECONDS"
        after="$(jar_signature)"
        if [ -n "$before" ] && [ "$before" = "$after" ]; then
            printf '%s' "$after"
            return
        fi
        log "jar is still being replaced, waiting until it is stable" >&2
    done
}

stop_application() {
    if [ -n "$APP_PID" ] && kill -0 "$APP_PID" 2>/dev/null; then
        log "stopping Java process $APP_PID"
        kill -TERM "$APP_PID" 2>/dev/null || true
        wait "$APP_PID" 2>/dev/null || true
    fi
    APP_PID=""
    rm -f "$PID_FILE"
}

shutdown() {
    trap - INT TERM
    stop_application
    exit 0
}

trap shutdown INT TERM

while :; do
    CURRENT_SIGNATURE="$(wait_for_stable_jar)"
    log "starting $APP_JAR (signature: $CURRENT_SIGNATURE)"
    java -jar "$APP_JAR" &
    APP_PID=$!
    printf '%s\n' "$APP_PID" > "$PID_FILE"

    RELOAD_REQUESTED=false
    while kill -0 "$APP_PID" 2>/dev/null; do
        sleep "$RELOAD_INTERVAL" &
        wait $! || true
        LATEST_SIGNATURE="$(jar_signature)"
        if [ -n "$LATEST_SIGNATURE" ] && [ "$LATEST_SIGNATURE" != "$CURRENT_SIGNATURE" ]; then
            STABLE_SIGNATURE="$(wait_for_stable_jar)"
            if [ "$STABLE_SIGNATURE" != "$CURRENT_SIGNATURE" ]; then
                log "application jar changed; reloading without recreating the container"
                RELOAD_REQUESTED=true
                stop_application
                break
            fi
        fi
    done

    if [ "$RELOAD_REQUESTED" = false ]; then
        EXIT_CODE=0
        wait "$APP_PID" 2>/dev/null || EXIT_CODE=$?
        APP_PID=""
        rm -f "$PID_FILE"
        log "Java process exited with code $EXIT_CODE; restarting in ${RESTART_DELAY}s"
        sleep "$RESTART_DELAY"
    fi
done
