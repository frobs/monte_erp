#!/usr/bin/env bash
#
# Runs the TestFX UI tests against a throwaway MySQL container.
#
#   scripts/run-ui-tests.sh                 # unit + UI tests on a private Xvfb
#   scripts/run-ui-tests.sh -Dtest=LoginUiTest
#   UI_USE_EXISTING_DISPLAY=1 scripts/run-ui-tests.sh   # use the current DISPLAY
#
# Requirements: Docker (daemon reachable) and Xvfb (or xvfb-run). On a desktop
# session the window manager may steal the focus from the tested window, which
# makes the robot lose clicks/key strokes: always prefer the private Xvfb.
set -euo pipefail

cd "$(dirname "$0")/.."

MAVEN_ARGS=(test -Pui-tests "$@")

fail() {
    echo "ERROR: $*" >&2
    exit 1
}

command -v docker >/dev/null 2>&1 || fail "docker is required to run the UI tests"
docker info >/dev/null 2>&1 || fail "the Docker daemon is not reachable"

cleanup_containers() {
    ids="$(docker ps -aq --filter label=monte-erp-ui-test 2>/dev/null || true)"
    if [ -n "$ids" ]; then
        # shellcheck disable=SC2086
        docker rm -f $ids >/dev/null 2>&1 || true
    fi
}

run_maven() {
    ./mvnw "${MAVEN_ARGS[@]}"
}

cleanup_containers
trap 'cleanup_containers' EXIT

if [ "${UI_USE_EXISTING_DISPLAY:-0}" = "1" ]; then
    [ -n "${DISPLAY:-}" ] || fail "UI_USE_EXISTING_DISPLAY=1 needs DISPLAY to be set"
    echo "Running the UI tests on the existing display ${DISPLAY}"
    run_maven
    exit 0
fi

if command -v xvfb-run >/dev/null 2>&1; then
    echo "Running the UI tests on a private Xvfb (xvfb-run)"
    xvfb-run -a -s "-screen 0 1920x1200x24" ./mvnw "${MAVEN_ARGS[@]}"
    exit 0
fi

command -v Xvfb >/dev/null 2>&1 \
    || fail "Xvfb (or xvfb-run) is required: install the 'xvfb' package"

DISPLAY_NUMBER=99
while [ -e "/tmp/.X11-unix/X${DISPLAY_NUMBER}" ]; do
    DISPLAY_NUMBER=$((DISPLAY_NUMBER + 1))
done

echo "Running the UI tests on a private Xvfb :${DISPLAY_NUMBER}"
Xvfb ":${DISPLAY_NUMBER}" -screen 0 1920x1200x24 -nolisten tcp \
    >/tmp/monte-erp-xvfb.log 2>&1 &
XVFB_PID=$!
trap 'kill "${XVFB_PID}" 2>/dev/null || true; cleanup_containers' EXIT

export DISPLAY=":${DISPLAY_NUMBER}"
for _ in $(seq 1 50); do
    if xdpyinfo >/dev/null 2>&1; then
        run_maven
        exit 0
    fi
    sleep 0.2
done

fail "Xvfb did not start; see /tmp/monte-erp-xvfb.log"
