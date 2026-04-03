#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
APP_NAME="Needlecast"
JAR_PATH="$ROOT_DIR/desktop/target/needlecast.jar"
BUILD_DIR="$ROOT_DIR/build"
RUNTIME_DIR="$BUILD_DIR/runtime"
APP_CDS_DIR="$BUILD_DIR/appcds"
CLASSLIST="$APP_CDS_DIR/classlist.txt"
ARCHIVE="$APP_CDS_DIR/appcds.jsa"

if [[ ! -f "$JAR_PATH" ]]; then
  echo "Missing jar: $JAR_PATH"
  echo "Build it with: mvn -pl desktop -am package -DskipTests"
  exit 1
fi

rm -rf "$BUILD_DIR"
mkdir -p "$APP_CDS_DIR"

DEPS=$(jdeps --multi-release 21 --ignore-missing-deps --print-module-deps "$JAR_PATH")

jlink \
  --add-modules "$DEPS" \
  --strip-debug \
  --no-header-files \
  --no-man-pages \
  --compress=zip-6 \
  --output "$RUNTIME_DIR"

"$RUNTIME_DIR/bin/java" \
  -Djava.awt.headless=true \
  -Xshare:off \
  "-XX:DumpLoadedClassList=$CLASSLIST" \
  -cp "$JAR_PATH" \
  io.github.rygel.needlecast.tools.CdsTraining

"$RUNTIME_DIR/bin/java" \
  -Xshare:dump \
  "-XX:SharedClassListFile=$CLASSLIST" \
  "-XX:SharedArchiveFile=$ARCHIVE"

mkdir -p "$RUNTIME_DIR/lib/server"
cp "$ARCHIVE" "$RUNTIME_DIR/lib/server/appcds.jsa"

jpackage \
  --type app-image \
  --dest "$BUILD_DIR/jpackage" \
  --input "$(dirname "$JAR_PATH")" \
  --name "$APP_NAME" \
  --main-jar "$(basename "$JAR_PATH")" \
  --main-class io.github.rygel.needlecast.MainKt \
  --runtime-image "$RUNTIME_DIR" \
  --java-options "-XX:SharedArchiveFile=\$APPDIR/runtime/lib/server/appcds.jsa"

echo "App image created under $BUILD_DIR/jpackage"
