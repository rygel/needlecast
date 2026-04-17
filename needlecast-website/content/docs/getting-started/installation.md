---
title: "Installation"
date: 2026-04-17
description: "How to install Needlecast on Windows, macOS, and Linux"
weight: 10
---

Needlecast is available for Windows, macOS, and Linux. Choose your platform below for installation instructions.

## Windows

### Installer (Recommended)
1. Download the latest `needlecast-VERSION-win64.exe` from [GitHub Releases](https://github.com/rygel/needlecast/releases)
2. Run the installer and follow the prompts
3. Needlecast will appear in your Start Menu

### Portable Version
1. Download `needlecast-VERSION-windows.zip`
2. Extract to any folder
3. Run `needlecast.exe`

## macOS

### DMG Package
1. Download `needlecast-VERSION-macos.dmg`
2. Open the DMG file
3. Drag Needlecast to your Applications folder
4. Launch from Applications or Spotlight

## Linux

### Debian/Ubuntu
```bash
# Download the .deb package
wget https://github.com/rygel/needlecast/releases/download/v0.6.17/needlecast-0.6.17-linux-amd64.deb

# Install with dpkg
sudo dpkg -i needlecast-*.deb

# Install dependencies if needed
sudo apt-get install -f
```

### AppImage
1. Download `needlecast-VERSION-linux.AppImage`
2. Make it executable:
   ```bash
   chmod +x needlecast-*.AppImage
   ```
3. Run directly:
   ```bash
   ./needlecast-*.AppImage
   ```

## From Source

If you prefer to build from source:

### Prerequisites
- Java 21 or later
- Maven 3.9+

### Build and Run
```bash
git clone https://github.com/rygel/needlecast.git
cd needlecast
mvn -pl needlecast-desktop compile exec:java
```

## System Requirements

- **Java**: Version 21 or later
- **Memory**: 512MB minimum, 1GB recommended
- **Storage**: 100MB for application, plus space for projects
- **OS**: Windows 10+, macOS 10.15+, or modern Linux distribution

## Next Steps

After installation, see [First Launch](/docs/getting-started/first-launch/) to set up your first projects.