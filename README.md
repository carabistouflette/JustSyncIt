# JustSyncIt

[![CI/CD Pipeline](https://github.com/carabistouflette/justsyncit/workflows/CI/CD%20Pipeline/badge.svg)](https://github.com/carabistouflette/justsyncit/actions)
[![codecov](https://codecov.io/gh/justsyncit/justsyncit/branch/main/graph/badge.svg)](https://codecov.io/gh/justsyncit/justsyncit)
[![Security Scan](https://github.com/carabistouflette/justsyncit/workflows/Security%20Scan/badge.svg)](https://github.com/carabistouflette/justsyncit/actions)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](https://www.gnu.org/licenses/gpl-3.0.html)
[![Docker Hub](https://img.shields.io/badge/docker-ready-blue.svg)](https://hub.docker.com/r/justsyncit/app)

A modern, reliable backup solution built with Java 21+ featuring BLAKE3 hashing for efficient content verification and secure network transfers.

## Documentation

Detailed documentation is available in the `docs/` directory:

*   **[User Guide](docs/UserGuide.md)**: Installation, core workflows, and basic usage.
*   **[Technical Architecture](docs/TechnicalArchitecture.md)**: Deep dive into FastCDC, Changed Block Tracking (CBT), and the Network Protocol.
*   **[CLI Reference](docs/CLI_Reference.md)**: Comprehensive list of all commands and flags.

## Features

*   **Fast Content-Defined Chunking (FastCDC)**: Efficient deduplication using Gear hashing and variable chunk sizes.
*   **Changed Block Tracking (CBT)**: Instant incremental backups by tracking file changes in real-time.
*   **Network Backups**: Secure transfers over TCP or QUIC protocols.
*   **Integrity Verification**: End-to-end data validation using BLAKE3 hashes.
*   **Snapshot Management**: Point-in-time recovery with minimal storage overhead.

## Quick Start

### Installation

**Using Docker:**
```bash
docker run -v $(pwd)/data:/data justsyncit/app:latest backup /data
```

**Using Java:**
```bash
wget https://github.com/carabistouflette/justsyncit/releases/latest/download/justsyncit.jar
java -jar justsyncit.jar backup ~/documents
```

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.