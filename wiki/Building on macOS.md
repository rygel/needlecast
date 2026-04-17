# Building on macOS

This guide walks through cloning, building, and running Needlecast on macOS.

---

## Prerequisites

### 1. Java 21 (required)

Install via [Homebrew](https://brew.sh):

```bash
brew install openjdk@21
```

Then link it so Maven can find it:

```bash
sudo ln -sfn $(brew --prefix openjdk@21)/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk
```

Verify:

```bash
java -version
# openjdk version "21.x.x"
```

### 2. Maven (required)

```bash
brew install maven
```

Verify:

```bash
mvn -version
# Apache Maven 3.9.x
```

### 3. Git (required)

macOS includes Git. If you need a newer version:

```bash
brew install git
```

### 4. VLC (recommended)

Required for media playback (video and audio). Without VLC the rest of the app works fine, but the media player panel will be unavailable.

```bash
brew install --cask vlc
```

### 5. ripgrep (recommended)

Faster file search across projects.

```bash
brew install ripgrep
```

---

## Clone and Build

```bash
git clone https://github.com/rygel/needlecast.git
cd needlecast
```

Build the desktop module and its dependencies:

```bash
mvn -pl needlecast-desktop -am package -DskipTests
```

The shaded JAR is output at `needlecast-desktop/target/needlecast-desktop-<version>.jar`.

---

## Run

```bash
mvn -pl needlecast-desktop compile exec:java
```

Or run the JAR directly:

```bash
java -jar needlecast-desktop/target/needlecast-desktop-0.6.19.jar
```

---

## Run Tests

Non-UI tests only (fast, no display needed):

```bash
mvn -pl needlecast-desktop test -T 4
```

Full verification (compile + test + package):

```bash
mvn -pl needlecast-desktop -am verify -T 4
```

The full test suite runs in under two minutes. If it takes longer, something is wrong — stop and investigate.

> **Note:** Swing/desktop UI tests require Xvfb and must only run inside a Docker container. Do not run them locally on macOS — they will capture your mouse and keyboard. See [CONTRIBUTING.md](https://github.com/rygel/needlecast/blob/develop/.github/CONTRIBUTING.md) for the container-based setup.

---

## Troubleshooting

### "Unable to find Java 21"

Make sure `java -version` reports 21 or later. If you have multiple JDKs installed, set `JAVA_HOME`:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

Add this line to `~/.zshrc` to make it permanent.

### VLC not found at runtime

VLC must be installed as a standard macOS app (in `/Applications/VLC.app`). If you installed it via Homebrew cask it should already be there. The media player panel will show an error if VLC is not detected.

### pty4j / terminal issues

The embedded terminal uses pty4j with JNA. On Apple Silicon Macs this works natively. If you see terminal-related errors, make sure you are running on an ARM or x86_64 JDK that matches your hardware — Rosetta Java can cause issues with native libraries.

### macOS Gatekeeper blocks the JAR

If macOS prevents the JAR from opening:

1. Open **System Settings → Privacy & Security**
2. Click **Open Anyway** next to the security warning

Or from the command line:

```bash
xattr -d com.apple.quarantine needlecast-desktop/target/needlecast-desktop-*.jar
```
