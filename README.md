# JustSyncIt

[![CI/CD Pipeline](https://github.com/carabistouflette/justsyncit/workflows/CI/CD%20Pipeline/badge.svg)](https://github.com/carabistouflette/justsyncit/actions)
[![codecov](https://codecov.io/gh/justsyncit/justsyncit/branch/main/graph/badge.svg)](https://codecov.io/gh/justsyncit/justsyncit)
[![SonarCloud](https://sonarcloud.io/api/project_badges/measure?project=justsyncit&metric=alert_status)](https://sonarcloud.io/dashboard?id=justsyncit)
[![Security Scan](https://github.com/carabistouflette/justsyncit/workflows/Security%20Scan/badge.svg)](https://github.com/carabistouflette/justsyncit/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Docker Hub](https://img.shields.io/badge/docker-ready-blue.svg)](https://hub.docker.com/r/justsyncit/app)

A modern, reliable backup solution built with Java 21+ featuring BLAKE3 hashing for efficient content verification and secure network transfers.

## Overview

JustSyncIt is a comprehensive backup solution designed to provide reliable and efficient data synchronization and backup capabilities. Built with modern Java practices and a focus on code quality, maintainability, and security.

## Features

- **BLAKE3 Hashing**: Fast and secure cryptographic hashing for content verification
- **Network Synchronization**: Secure file transfer over TCP with custom protocol
- **Chunked Storage**: Efficient content-addressable storage with deduplication
- **SIMD Optimization**: Hardware-accelerated hashing on supported platforms
- **Cross-platform compatibility** - Runs on Windows, macOS, and Linux
- **Incremental backups** - Only backup changed files to save time and space
- **Encryption support** - Optional encryption for sensitive data
- **Scheduling** - Automated backup scheduling
- **Multiple backup destinations** - Local drives, network shares, cloud storage
- **Compression** - Optional compression to reduce storage requirements
- **Logging and monitoring** - Comprehensive logging for troubleshooting
- **Docker Support**: Containerized deployment with multi-architecture support
- **Automated Releases**: Semantic versioning and automated GitHub releases

## Requirements

- Java 21 or higher
- Gradle 9.2+ (for development)
- Git (for version control)
- Docker (optional, for containerized deployment)

## Quick Start

### Prerequisites

Ensure you have Java 21+ installed:

```bash
java -version
```

### Building the Project

1. Clone the repository:
```bash
git clone https://github.com/carabistouflette/justsyncit.git
cd justsyncit
```

2. Build the project:
```bash
./gradlew build
```

3. Run the application:
```bash
./gradlew run
```

Or run the JAR directly:
```bash
java -jar build/libs/justsyncit-1.0-SNAPSHOT.jar
```

### Docker Deployment

```bash
# Pull the latest image
docker pull justsyncit/app:latest

# Run with Docker Compose
docker-compose up -d

# Or run standalone
docker run -p 8080:8080 justsyncit/app:latest
```

### Development Setup

1. Clone the repository as shown above
2. Import the project into your favorite IDE (IntelliJ IDEA, Eclipse, VS Code)
3. The project uses Gradle for dependency management and building

## Build Commands

- `./gradlew devBuild` - Quick development build
- `./gradlew testBuild` - Build with all quality checks
- `./gradlew releaseBuild` - Full release build with documentation
- `./gradlew test` - Run unit tests
- `./gradlew test jacocoTestReport` - Run tests with coverage
- `./gradlew jmh` - Run performance benchmarks
- `./gradlew dependencyCheckAggregate` - Run security checks
- `./gradlew checkstyleMain checkstyleTest` - Run code style checks
- `./gradlew spotbugsMain spotbugsTest` - Run static analysis

## Code Quality

This project maintains high code quality standards:

- **Checkstyle** - Enforces coding standards and formatting
- **SpotBugs** - Static analysis for bug detection
- **JUnit 5** - Comprehensive unit testing
- **JaCoCo** - Code coverage reporting
- **SonarCloud** - Continuous code quality analysis
- **OWASP Dependency Check** - Security vulnerability scanning
- **EditorConfig** - Consistent editor configuration across team members

## CI/CD Pipeline

This project includes a comprehensive CI/CD pipeline:

- **Multi-platform builds**: Linux, Windows, macOS
- **Multi-version testing**: Java 17, 21, 22
- **Code quality**: Checkstyle, SpotBugs, SonarCloud analysis
- **Security scanning**: OWASP Dependency Check, Trivy vulnerability scanner
- **Automated releases**: Semantic versioning with GitHub releases
- **Docker builds**: Multi-architecture container images
- **Documentation**: Automated Javadoc and API documentation

## Dependencies

### Core Dependencies
- **SLF4J + Logback** - Logging framework
- **JUnit 5** - Testing framework
- **Mockito** - Mocking framework for tests
- **JMH** - Java Microbenchmark Harness

### Build Tools
- **Gradle 9.2** - Build automation and dependency management
- **Checkstyle** - Code style enforcement
- **SpotBugs** - Static code analysis
- **JaCoCo** - Code coverage
- **SonarQube** - Code quality analysis
- **OWASP Dependency Check** - Security scanning

## Project Structure

```
JustSyncIt/
├── src/
│   ├── main/
│   │   ├── java/com/justsyncit/
│   │   │   ├── hash/           # BLAKE3 hashing implementation
│   │   │   ├── network/        # Network protocol and transfer
│   │   │   ├── storage/        # Content-addressable storage
│   │   │   ├── simd/           # SIMD detection and optimization
│   │   │   ├── command/        # Command-line interface
│   │   │   └── JustSyncItApplication.java  # Main application
│   │   └── resources/          # Configuration files
│   └── test/
│       └── java/com/justsyncit/  # Unit tests
├── config/
│   ├── checkstyle/            # Checkstyle configuration
│   ├── spotbugs/             # SpotBugs configuration
│   └── dependency-check/     # OWASP configuration
├── .github/workflows/        # CI/CD pipeline
├── docs/                     # Documentation
├── gradle/                   # Gradle wrapper
├── build.gradle              # Gradle build script
├── settings.gradle           # Gradle settings
├── Dockerfile               # Docker configuration
├── docker-compose.yml       # Docker Compose configuration
└── README.md                 # This file
```

## Architecture

JustSyncIt is built with a modular architecture:

- **Hash Module**: Provides BLAKE3 hashing with SIMD optimizations
- **Network Module**: Handles client-server communication and file transfers
- **Storage Module**: Manages content-addressable storage with integrity verification
- **SIMD Module**: Detects and utilizes hardware acceleration

## Performance

The application is optimized for performance with:

- BLAKE3's parallel hashing capabilities
- SIMD instructions on x86 and ARM platforms
- Efficient memory management and streaming
- Concurrent network operations

## Testing

Run all tests:
```bash
./gradlew test
```

Run tests with coverage:
```bash
./gradlew test jacocoTestReport
```

Run performance benchmarks:
```bash
./gradlew jmh
```

## Documentation

- [API Documentation](https://justsyncit.github.io/justsyncit/api/)
- [User Guide](https://justsyncit.github.io/justsyncit/guides/)
- [Development Guide](https://justsyncit.github.io/justsyncit/guides/contributing/)

## Monitoring

When deployed with monitoring profile:

- **Prometheus**: Metrics collection on port 9090
- **Grafana**: Visualization dashboard on port 3000

```bash
# Enable monitoring
docker-compose --profile monitoring up -d
```

## Security

This project takes security seriously:

- Regular dependency vulnerability scanning
- Security-focused code analysis
- Container security scanning
- Automated security updates

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Add tests for new functionality
5. Run the test suite (`./gradlew test`)
6. Ensure code quality checks pass (`./gradlew check`)
7. Submit a pull request

### Development Setup

```bash
# Install development dependencies
./gradlew downloadDependencies

# Run development build
./gradlew devBuild

# Run all quality checks
./gradlew check

# Generate documentation
./gradlew javadoc
```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

For support, please:
1. Check the [documentation](docs/)
2. Search existing [issues](https://github.com/carabistouflette/justsyncit/issues)
3. Create a new issue if needed

## Roadmap

- [ ] Real-time synchronization
- [ ] Web UI for management
- [ ] Cloud storage integrations
- [ ] Advanced encryption options

## Authors

- JustSyncIt Team - *Initial work* - [JustSyncIt](https://github.com/carabistouflette/justsyncit)

See also the list of [contributors](https://github.com/carabistouflette/justsyncit/contributors) who participated in this project.

## Acknowledgments

- [BLAKE3](https://github.com/BLAKE3-team/BLAKE3) for the fast cryptographic hash function
- [JUnit 5](https://junit.org/junit5/) for testing framework
- [Gradle](https://gradle.org/) for build automation
- [Docker](https://www.docker.com/) for containerization support