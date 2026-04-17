# Media Viewers

## Overview

When you open a non-text file in the [[File Explorer]], the editor area switches to an appropriate viewer. Three viewer types are built in: Image Viewer, SVG Viewer, and Media Player.

---

## Image Viewer

Activated for: **JPEG, PNG, WebP, GIF, BMP, TIFF, ICO** (any format supported by `javax.imageio.ImageIO`)

### Controls

| Action | Result |
|---|---|
| `Ctrl+Scroll` | Zoom in / out |
| **Double-click** | Reset to fit-to-panel mode |
| **Click and drag** | Pan when zoomed in |
| Scroll bars | Pan horizontally and vertically |

### Zoom

- **Range:** 0.05× to 32× (5 % to 3200 %)
- Resets to "fit" mode on double-click
- Status bar shows `{width} × {height} px | {scale}%` or `{width} × {height} px | fit`
- **Rendering:** Bilinear interpolation for smooth scaling

### Auto-Reload

The viewer monitors the file's modification time and size. When the file changes on disk (e.g. a script regenerates an image), it reloads automatically — no manual refresh needed.

---

## SVG Viewer

Activated for: **SVG** (Scalable Vector Graphics), rendered via the `jsvg` library.

### Controls

Same as Image Viewer: `Ctrl+Scroll` to zoom, double-click to fit, drag to pan.

### Zoom

- **Range:** 0.05× to 32×
- Vectors remain crisp at any zoom level
- Status bar shows the same format as Image Viewer

### Rendering Hints

- Anti-aliasing: on
- Stroke control: PURE
- Rendering quality: QUALITY

### Auto-Reload

Same file-change monitoring as the Image Viewer.

---

## Media Player

Activated for audio and video files.

### Supported Formats

**Audio:** `mp3`, `wav`, `wave`, `aiff`, `aif`, `flac`, `ogg`, `oga`, `opus`, `m4a`, `aac`, `wma`

**Video:** `mp4`, `m4v`, `mov`, `mkv`, `avi`, `webm`, `mpg`, `mpeg`, `flv`, `3gp`, `ogv`

### Requirement: VLC

The media player requires **VLC 3.x** to be installed on the system. Needlecast uses the `vlcj` library which wraps VLC's native libraries.

- Needlecast checks for VLC at startup via `NativeDiscovery`
- If VLC is not found, opening a media file shows "Playback error" instead of a player
- Install VLC from [videolan.org](https://www.videolan.org/)

### Controls

| Control | Action |
|---|---|
| **Play / Pause** button | Start or pause playback |
| **Stop** button | Stop playback and reset position to the beginning |
| **Loop** checkbox | Repeat the file when it reaches the end |
| **Volume slider** | 0–100 (default 80) |
| **Seek slider** | 0–1000 proportional to duration — click or drag to jump |

### Time Display

Format: `MM:SS / MM:SS`  
For files longer than one hour: `HH:MM:SS / HH:MM:SS`

The position updates every **250 ms**.

### Error Handling

If a VLC error occurs during playback, the player shows "Playback error" in the status area.

---

## Related

- [[File Explorer]] — navigating to and opening files
- [[Code Editor]] — text file editing; non-text files route to these viewers
