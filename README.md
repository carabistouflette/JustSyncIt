# JustSyncIt

A modern, reliable backup solution built with Java 21+.

## Overview

JustSyncIt is a comprehensive backup solution designed to provide reliable and efficient data synchronization and backup capabilities. Built with modern Java practices and a focus on code quality, maintainability, and security.

## Features

- **Cross-platform compatibility** - Runs on Windows, macOS, and Linux
- **Incremental backups** - Only backup changed files to save time and space
- **Encryption support** - Optional encryption for sensitive data
- **Scheduling** - Automated backup scheduling
- **Multiple backup destinations** - Local drives, network shares, cloud storage
- **Compression** - Optional compression to reduce storage requirements
- **Logging and monitoring** - Comprehensive logging for troubleshooting

## Requirements

- Java 21 or higher
- Gradle 9.2+ (for development)
- Git (for version control)

## Quick Start

### Prerequisites

Ensure you have Java 21+ installed:

```bash
java -version
```

### Building the Project

1. Clone the repository:
```bash
git clone https://github.com/your-username/JustSyncIt.git
cd JustSyncIt
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
java -jar build/libs/JustSyncIt-1.0-SNAPSHOT.jar
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
- `./gradlew checkstyleMain checkstyleTest` - Run code style checks
- `./gradlew spotbugsMain spotbugsTest` - Run static analysis

## Code Quality

This project maintains high code quality standards:

- **Checkstyle** - Enforces coding standards and formatting
- **SpotBugs** - Static analysis for bug detection
- **JUnit 5** - Comprehensive unit testing
- **EditorConfig** - Consistent editor configuration across team members

## Dependencies

### Core Dependencies
- **SLF4J + Logback** - Logging framework
- **JUnit 5** - Testing framework
- **Mockito** - Mocking framework for tests

### Build Tools
- **Gradle 9.2** - Build automation and dependency management
- **Checkstyle** - Code style enforcement
- **SpotBugs** - Static code analysis

## Project Structure

```
JustSyncIt/
├── src/
│   ├── main/
│   │   ├── java/com/justsyncit/
│   │   │   ├── config/          # Configuration classes
│   │   │   ├── exception/       # Custom exceptions
│   │   │   ├── model/          # Data models
│   │   │   ├── service/        # Business logic
│   │   │   └── JustSyncItApplication.java  # Main application
│   │   └── resources/          # Configuration files
│   └── test/
│       └── java/com/justsyncit/  # Unit tests
├── config/
│   ├── checkstyle/            # Checkstyle configuration
│   └── spotbugs/             # SpotBugs configuration
├── .github/workflows/        # CI/CD pipeline
├── docs/                     # Documentation
├── gradle/                   # Gradle wrapper
├── build.gradle              # Gradle build script
├── settings.gradle           # Gradle settings
└── README.md                 # This file
```

## Testing

Run all tests:
```bash
./gradlew test
```

Run tests with coverage:
```bash
./gradlew test jacocoTestReport
```

## CI/CD

This project uses GitHub Actions for continuous integration and deployment:

- **Automated testing** on every push and pull request
- **Code quality checks** using Checkstyle and SpotBugs
- **Security scanning** using Trivy
- **Artifact building** on main branch

## License

This project is licensed under the GNU General Public License v3.0. See the [LICENSE](LICENSE) file for details.

## Support

For support, please:
1. Check the [documentation](docs/)
2. Search existing [issues](https://github.com/your-username/JustSyncIt/issues)
3. Create a new issue if needed

## Roadmap

- [ ] Real-time synchronization

## Authors

- JustSyncIt Team - *Initial work* - [JustSyncIt](https://github.com/your-username/JustSyncIt)

See also the list of [contributors](https://github.com/your-username/JustSyncIt/contributors) who participated in this project.
