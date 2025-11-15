---
layout: guide
title: Installation Guide
nav_order: 1
---

# Installation Guide

This guide covers various ways to install JustSyncIt on different platforms.

## Prerequisites

- Java 21 or later
- At least 512MB of RAM
- 100MB of free disk space

## Installation Methods

### Method 1: Download Pre-built JAR

1. Download the latest release from [GitHub Releases](https://github.com/carabistouflette/justsyncit/releases)
2. Extract the downloaded archive
3. Run the application:
   ```bash
   java -jar justsyncit-x.x.x.jar
   ```

### Method 2: Using Docker

1. Install Docker on your system
2. Pull the latest image:
   ```bash
   docker pull justsyncit/app:latest
   ```
3. Run the container:
   ```bash
   docker run -p 8080:8080 -v /path/to/data:/app/data justsyncit/app:latest
   ```

### Method 3: Build from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/carabistouflette/justsyncit.git
   cd justsyncit
   ```

2. Build the application:
   ```bash
   ./gradlew build
   ```

3. Run the application:
   ```bash
   java -jar build/libs/justsyncit-1.0-SNAPSHOT.jar
   ```

## Platform-Specific Instructions

### Windows

1. Download and install [Java 21](https://adoptium.net/)
2. Download the latest JustSyncIt release
3. Create a shortcut to run the JAR file
4. Optional: Add JustSyncIt to PATH for easy access

### macOS

1. Install Java 21 using [Homebrew](https://brew.sh/):
   ```bash
   brew install openjdk@21
   ```

2. Download the latest JustSyncIt release
3. Make the JAR executable:
   ```bash
   chmod +x justsyncit-x.x.x.jar
   ```

4. Run the application

### Linux

#### Ubuntu/Debian
```bash
sudo apt update
sudo apt install openjdk-21-jre
```

#### Fedora/CentOS
```bash
sudo dnf install java-21-openjdk
```

#### Arch Linux
```bash
sudo pacman -S jdk21-openjdk
```

## Verification

After installation, verify that JustSyncIt is working correctly:

```bash
java -jar justsyncit-x.x.x.jar --version
```

You should see the version information displayed.

## Configuration

The first time you run JustSyncIt, it will create a configuration directory:

- **Windows**: `%APPDATA%/JustSyncIt`
- **macOS**: `~/Library/Application Support/JustSyncIt`
- **Linux**: `~/.config/justsyncit`

## Troubleshooting

### Java Not Found
Ensure Java 21 is installed and in your PATH:
```bash
java -version
```

### Permission Denied
Make sure the JAR file has execute permissions:
```bash
chmod +x justsyncit-x.x.x.jar
```

### Out of Memory
Increase heap size if needed:
```bash
java -Xmx1g -jar justsyncit-x.x.x.jar
```

## Next Steps

After successful installation, proceed to the [Quick Start Guide](quick-start.md) to learn how to use JustSyncIt.