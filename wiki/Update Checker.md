# Update Checker

## Overview

Needlecast checks for new releases automatically in the background and shows a badge in the status bar when an update is available.

---

## How It Works

Needlecast uses **Sparkle4j** (a Java port of Apple's Sparkle update framework) to check for updates via an **appcast XML** file published to GitHub releases.

**Appcast URL:**
```
https://github.com/rygel/needlecast/releases/latest/download/appcast.xml
```

The current application version is read from `/version.properties` (bundled in the JAR).

---

## Automatic Checks

- **First check:** 30 seconds after the main window opens
- **Interval:** Every 15 minutes while the application is running

---

## Manual Check

**Help → Check for Updates…** — immediately polls the appcast and shows a dialog with the result.

---

## Update Badge

When a newer version is found, the status bar (bottom-right) shows:

```
⬆ {version} available
```

The badge is displayed in **cyan** (accent color). Clicking it opens the GitHub releases page in your default browser:

```
https://github.com/rygel/needlecast/releases/latest
```

The badge remains visible until the application is restarted or another version check finds no update.

---

## Related

- [[UI Overview#Status Bar|Status bar]] — where the badge appears
- [[Settings]] — there is no setting to disable automatic update checks
