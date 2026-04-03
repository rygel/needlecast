#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
APP_NAME="Needlecast"
# APP_VERSION can be set by CI (e.g. "0.7.0"). Falls back to pom.xml.
APP_VERSION="${APP_VERSION:-$(grep -m1 '<version>' "$ROOT_DIR/desktop/pom.xml" | sed 's/.*<version>\(.*\)<\/version>.*/\1/')}"
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

ICON_PATH="$ROOT_DIR/desktop/src/main/resources/icons/needlecast.png"

jpackage \
  --type app-image \
  --dest "$BUILD_DIR/jpackage" \
  --input "$(dirname "$JAR_PATH")" \
  --name "$APP_NAME" \
  --app-version "$APP_VERSION" \
  --icon "$ICON_PATH" \
  --main-jar "$(basename "$JAR_PATH")" \
  --main-class io.github.rygel.needlecast.MainKt \
  --runtime-image "$RUNTIME_DIR" \
  --java-options "-XX:SharedArchiveFile=\$APPDIR/runtime/lib/server/appcds.jsa"

echo "App image created under $BUILD_DIR/jpackage"

# ── Platform-specific installer ───────────────────────────────────────────────
OS="$(uname -s)"

if [[ "$OS" == "Darwin" ]]; then
    echo "Building macOS DMG (version $APP_VERSION)..."
    jpackage \
      --type dmg \
      --app-image "$BUILD_DIR/jpackage/$APP_NAME.app" \
      --dest "$BUILD_DIR" \
      --name "$APP_NAME" \
      --app-version "$APP_VERSION"
    echo "DMG: $BUILD_DIR/$APP_NAME-$APP_VERSION.dmg"

elif [[ "$OS" == "Linux" ]]; then
    echo "Building Linux .deb (version $APP_VERSION)..."
    jpackage \
      --type deb \
      --app-image "$BUILD_DIR/jpackage/$APP_NAME" \
      --dest "$BUILD_DIR" \
      --name "$APP_NAME" \
      --app-version "$APP_VERSION" \
      --linux-shortcut \
      --linux-menu-group "Development"
    echo "deb: $BUILD_DIR/needlecast_${APP_VERSION}_amd64.deb"
fi
