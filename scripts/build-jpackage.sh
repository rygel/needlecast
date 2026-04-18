#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
APP_NAME="Needlecast"
# APP_VERSION can be set by CI (e.g. "0.7.0"). Falls back to root pom.xml.
APP_VERSION="${APP_VERSION:-$(grep -m1 '<version>' "$ROOT_DIR/pom.xml" | sed 's/.*<version>\(.*\)<\/version>.*/\1/')}"
if [[ -z "$APP_VERSION" ]]; then echo "Could not determine app version"; exit 1; fi
JAR_PATH="$ROOT_DIR/needlecast-desktop/target/needlecast.jar"
BUILD_DIR="$ROOT_DIR/build"
RUNTIME_DIR="$BUILD_DIR/runtime"
APP_CDS_DIR="$BUILD_DIR/appcds"
CLASSLIST="$APP_CDS_DIR/classlist.txt"
ARCHIVE="$APP_CDS_DIR/appcds.jsa"

if [[ ! -f "$JAR_PATH" ]]; then
  echo "Missing jar: $JAR_PATH"
  echo "Build it with: mvn -pl needlecast-desktop -am package -DskipTests"
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

ICON_PATH="$ROOT_DIR/needlecast-desktop/src/main/resources/icons/needlecast.png"

JPACKAGE_VERSION="${APP_VERSION%-*}"

# macOS jpackage rejects major version 0 for CFBundleShortVersionString.
# Map 0.x.y → 1.x.y on macOS only; Linux and Windows accept 0.x.y fine.
OS="$(uname -s)"
if [[ "$OS" == "Darwin" && "$JPACKAGE_VERSION" == 0.* ]]; then
    JPACKAGE_VERSION="1.${JPACKAGE_VERSION#0.}"
fi

# ── Platform-specific icon preparation ────────────────────────────────────────
OS="$(uname -s)"

# macOS wants a real .icns. jpackage's built-in PNG→ICNS conversion often yields
# an incomplete icon that macOS falls back to the generic Java badge for, so
# build a proper multi-resolution .iconset with sips + iconutil.
if [[ "$OS" == "Darwin" ]]; then
    echo "Generating needlecast.icns from $ICON_PATH..."
    ICONSET_DIR="$BUILD_DIR/needlecast.iconset"
    mkdir -p "$ICONSET_DIR"
    for size in 16 32 128 256 512; do
        doubled=$((size * 2))
        sips -z "$size"    "$size"    "$ICON_PATH" --out "$ICONSET_DIR/icon_${size}x${size}.png"     >/dev/null
        sips -z "$doubled" "$doubled" "$ICON_PATH" --out "$ICONSET_DIR/icon_${size}x${size}@2x.png"  >/dev/null
    done
    iconutil -c icns "$ICONSET_DIR" -o "$BUILD_DIR/needlecast.icns"
    ICON_PATH="$BUILD_DIR/needlecast.icns"
fi

JPACKAGE_ARGS=(
  --type app-image
  --dest "$BUILD_DIR/jpackage"
  --input "$(dirname "$JAR_PATH")"
  --name "$APP_NAME"
  --app-version "$JPACKAGE_VERSION"
  --icon "$ICON_PATH"
  --main-jar "$(basename "$JAR_PATH")"
  --main-class io.github.rygel.needlecast.MainKt
  --runtime-image "$RUNTIME_DIR"
  --java-options "-XX:SharedArchiveFile=\$APPDIR/runtime/lib/server/appcds.jsa"
)

# Belt-and-suspenders on macOS: the bundle's CFBundleName already sets the Dock
# label, but -Xdock:name ensures it is correct in edge cases (e.g. when the JVM
# is launched before the bundle is fully initialized).
if [[ "$OS" == "Darwin" ]]; then
    JPACKAGE_ARGS+=(--java-options "-Xdock:name=$APP_NAME")
    JPACKAGE_ARGS+=(--java-options "-Dapple.awt.application.name=$APP_NAME")
fi

jpackage "${JPACKAGE_ARGS[@]}"

echo "App image created under $BUILD_DIR/jpackage"

# ── Platform-specific installer ───────────────────────────────────────────────

if [[ "$OS" == "Darwin" ]]; then
    echo "Building macOS DMG (version $JPACKAGE_VERSION)..."
    jpackage \
      --type dmg \
      --app-image "$BUILD_DIR/jpackage/$APP_NAME.app" \
      --dest "$BUILD_DIR" \
      --name "$APP_NAME" \
      --app-version "$JPACKAGE_VERSION"
    echo "DMG created in $BUILD_DIR/"

    echo "Building macOS portable zip..."
    PORTABLE="$BUILD_DIR/needlecast-${APP_VERSION}-macos-portable.zip"
    (cd "$BUILD_DIR/jpackage" && zip -r "$PORTABLE" "$APP_NAME.app")
    echo "Portable archive: $PORTABLE"

elif [[ "$OS" == "Linux" ]]; then
    echo "Building Linux .deb (version $JPACKAGE_VERSION)..."
    jpackage \
      --type deb \
      --app-image "$BUILD_DIR/jpackage/$APP_NAME" \
      --dest "$BUILD_DIR" \
      --name "$APP_NAME" \
      --app-version "$JPACKAGE_VERSION" \
      --linux-shortcut \
      --linux-menu-group "Development"
    echo "deb created in $BUILD_DIR/"

    echo "Building Linux portable tar.gz..."
    PORTABLE="$BUILD_DIR/needlecast-${APP_VERSION}-linux-portable.tar.gz"
    tar czf "$PORTABLE" -C "$BUILD_DIR/jpackage" "$APP_NAME"
    echo "Portable archive: $PORTABLE"
fi
