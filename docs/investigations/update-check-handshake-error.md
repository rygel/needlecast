# Update Check Network Error - Investigation

**Date:** 2026-06-10  
**Status:** Re-investigated; app-side classification fix added  
**Reported version:** 0.7.3 local/dev build  
**Current checkout version:** 0.8.3-beta.1

## Symptom

A manual update check showed a "Could not check for updates" dialog with a message that sounded handshake-related.

## What the Evidence Supports

The captured stack trace shows `java.net.ConnectException` from `java.net.http.HttpClient` while sparkle4j was fetching the appcast:

```text
04:42:00.711 [update-check] WARN  needlecast.update - Update check failed
java.net.ConnectException
    at java.net.http/jdk.internal.net.http.HttpClientImpl.send(HttpClientImpl.java:955)
    at io.github.rygel.sparkle4j.AppcastFetcher.fetch(AppcastFetcher.java:52)
    ...
Caused by: java.net.ConnectException
    at java.net.http/jdk.internal.net.http.PlainHttpConnection.connectAsync(PlainHttpConnection.java:227)
    at java.net.http/jdk.internal.net.http.AsyncSSLConnection.connectAsync(AsyncSSLConnection.java:56)
```

This is not an `SSLHandshakeException`. The `AsyncSSLConnection` frame is part of Java's HTTPS connection path, but the root exception is still `ConnectException`. That means the recorded failure happened while establishing the network connection, before there is evidence of a TLS certificate/protocol failure.

## Cross-Checks

### Appcast endpoint

The appcast endpoint is reachable from this machine:

```text
GET https://github.com/rygel/needlecast/releases/latest/download/appcast.xml
HTTP 200
```

GitHub's latest release API currently reports:

```text
tag_name: v0.8.2
published_at: 2026-06-10 03:40:35 UTC
asset count: 9
```

The appcast content advertises `sparkle:version` `0.8.2` and contains six platform download enclosures:

- Windows installer
- Windows portable ZIP
- macOS DMG
- macOS portable ZIP
- Linux DEB
- Linux portable TAR.GZ

The remaining release assets are `appcast.xml`, `needlecast-0.8.2.jar`, and `needlecast-0.8.2.jar.sha256`, which are release assets but not appcast enclosures.

### Current app version

The source resource keeps Maven filtering syntax:

```properties
app.version=${project.version}
```

That is expected in source. The built resource in `needlecast-desktop/target/classes/version.properties` is filtered correctly in this checkout:

```properties
app.version=0.8.3-beta.1
```

So the previous concern that `currentVersion()` may return null is not reproduced in this checkout.

### sparkle4j HTTP behavior

The project uses `io.github.rygel:sparkle4j:0.5.6`.

Decompiling the local dependency confirms `AppcastFetcher` uses:

- `java.net.http.HttpClient`
- 10 second connect timeout
- 30 second request timeout
- redirect mode `NORMAL`

These match the original note.

### Needlecast behavior

The update checker implementation in `MainWindow.kt` matches the documented behavior:

- Periodic checks run every 15 minutes, with a 30 second initial delay.
- Failures are counted consecutively.
- The status bar warning is shown after 3 consecutive periodic failures.
- A successful periodic check resets the failure count and hides the warning.
- Manual checks run on a background thread and now show classified user-facing error text instead of raw exception messages.

## Fresh Test Results

### Live appcast HTTP check

Ran five consecutive `Invoke-WebRequest` requests against:

```text
https://github.com/rygel/needlecast/releases/latest/download/appcast.xml
```

Result:

```text
5/5 succeeded
HTTP status: 200
appcast version: 0.8.2
enclosure count: 6
response size: 1751 bytes
latency: 955 ms first request, then 265-291 ms
```

### Live sparkle4j check

Ran sparkle4j `0.5.6` directly through JShell with `currentVersion("0.7.3")`.

Result:

```text
present=true
version=0.8.2
```

This verifies the appcast is consumable by the same library used by Needlecast.

### Local log review

The local `needlecast.log` contains historical failures of two forms:

```text
java.net.http.HttpConnectTimeoutException: HTTP connect timed out
Caused by: java.net.ConnectException: HTTP connect timed out
```

and:

```text
java.net.ConnectException
Caused by: java.net.ConnectException
    ... PlainHttpConnection.connectAsync
    ... AsyncSSLConnection.connectAsync
```

No `SSLHandshakeException` was found in the investigated update-check failures.

The log also shows the update timer was firing and app versions were known:

```text
Periodic update check
Building sparkle4j instance: version=0.7.3, interval=0h
```

So the older "timer may never fire because version is unknown" hypothesis is not supported by this machine's logs.

### Automated tests

Added `UpdateCheckErrorsTest` with coverage for:

- `HttpConnectTimeoutException` wrapping `ConnectException`
- plain `ConnectException`
- `UnknownHostException`
- `SSLHandshakeException`
- log-field sanitization

Commands run:

```text
mvn -pl needlecast-desktop -Dtest=UpdateCheckErrorsTest test
```

Result:

```text
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
```

```text
mvn -pl needlecast-desktop ktlint:check
```

Result:

```text
BUILD SUCCESS
```

The broader non-UI suite was also run:

```text
mvn -pl needlecast-desktop test
```

Result:

```text
Tests run: 413, Failures: 3, Errors: 0, Skipped: 3
```

The three failures are unrelated existing config-version assertions expecting config version `6` while current code returns `7`:

- `ConfigMigratorTest.version 5 config migrates to version 6 with new defaults`
- `JsonConfigStoreTest.round-trips AppConfig to file and back`
- `JsonConfigStoreTest.import and export round-trip`

## Corrected Root Cause Assessment

The original note's statement that the root cause is "transient network connectivity to GitHub's CDN" is too certain.

The defensible conclusion is:

> The captured error is a connection-level failure while reaching the GitHub appcast endpoint. It is not evidence of an SSL handshake, certificate, or TLS protocol problem.

Plausible causes include:

- transient network interruption
- VPN or corporate proxy behavior
- local firewall or endpoint protection
- DNS or routing instability
- GitHub or CDN edge connection failure
- short-lived connectivity loss during sleep/wake or network switching

There is not enough evidence in the captured stack trace to choose one of those as the proven root cause.

## What Was Incorrect or Overstated

- "Transient network connectivity to GitHub's CDN" was written as a proven root cause. It should be framed as a likely category, not a confirmed cause.
- "GitHub's CDN may rate-limit or drop connections" was speculative and not supported by the captured evidence.
- "No code changes needed" was too strong. The network handling mostly works, but the old manual dialog could present low-level exception text that users interpret as a handshake problem.
- "All 9 assets present" is true for the GitHub release, but the appcast itself contains six platform enclosures. The doc should distinguish release assets from appcast enclosures.
- The older `docs/research/auto-update-findings.md` is stale for this checkout: it says `pom.xml` was stuck at `0.8.0-beta.2`, but the current parent POM is `0.8.3-beta.1`.
- The previous classifier could mislabel an `HttpConnectTimeoutException` whose root cause is `ConnectException` as a generic connect failure. Classification now checks the whole cause chain and treats HTTP connect timeouts as `network_timeout`.

## Recommended Follow-Up

No update transport rewrite is indicated by this evidence.

Small improvements that are now implemented:

1. Keep the existing periodic retry and 3-failure status warning.
2. Improve the manual error dialog to classify failures with user-facing text:
   - connection failure: "Could not reach the update server. Check your network, VPN, proxy, or firewall."
   - DNS failure: "Could not resolve the update server."
   - TLS failure: "The secure connection failed. This may be caused by a proxy or certificate trust issue."
3. Log the exception class, root cause class, category, and appcast host at WARN, while keeping the full stack trace at DEBUG.

## Bottom Line

The original document was right that the captured stack trace is not a TLS handshake failure. It was not right to claim a proven GitHub CDN/transient-network root cause or that no code improvement is needed.
