#!/usr/bin/env bash
# Dev-mode launcher for macOS.
#
# `mvn exec:java` runs the app in the already-started Maven JVM, which means
# -Xdock:name / -Xdock:icon have already been consumed and the Dock shows
# "java" with a generic icon. This script launches a fresh JVM with those
# flags so the Dock label and icon are correct during development.
#
# Usage:
#   mvn -pl needlecast-desktop -am package -DskipTests   # build the fat jar
#   scripts/run-mac.sh                                    # launch it

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
APP_NAME="Needlecast"
JAR_PATH="$ROOT_DIR/needlecast-desktop/target/needlecast.jar"
ICON_PATH="$ROOT_DIR/needlecast-desktop/src/main/resources/icons/needlecast.png"

if [[ ! -f "$JAR_PATH" ]]; then
    echo "Missing jar: $JAR_PATH" >&2
    echo "Build it first: mvn -pl needlecast-desktop -am package -DskipTests" >&2
    exit 1
fi

exec java \
    "-Xdock:name=$APP_NAME" \
    "-Xdock:icon=$ICON_PATH" \
    "-Dapple.awt.application.name=$APP_NAME" \
    -jar "$JAR_PATH" \
    "$@"
