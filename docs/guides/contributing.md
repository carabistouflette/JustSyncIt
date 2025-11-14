---
layout: guide
title: Contributing Guide
nav_order: 10
---

# Contributing Guide

We welcome contributions to JustSyncIt! This guide will help you get started with contributing to the project.

## Ways to Contribute

- 🐛 **Report Bugs** - Found a bug? [Create an issue](https://github.com/carabistouflette/justsyncit/issues/new?template=bug_report.md)
- 💡 **Feature Requests** - Have an idea? [Start a discussion](https://github.com/carabistouflette/justsyncit/discussions)
- 📝 **Documentation** - Improve documentation
- 🔧 **Code Contributions** - Fix bugs or implement features
- 🧪 **Testing** - Improve test coverage
- 🌐 **Localization** - Help translate the application

## Development Setup

### Prerequisites

- Java 21 or later
- Git
- IDE (IntelliJ IDEA, Eclipse, or VS Code)
- Docker (optional, for testing)

### Setup Steps

1. **Fork the Repository**
   ```bash
   # Fork on GitHub, then clone your fork
   git clone https://github.com/your-username/justsyncit.git
   cd justsyncit
   ```

2. **Add Upstream Remote**
   ```bash
   git remote add upstream https://github.com/carabistouflette/justsyncit.git
   ```

3. **Import into IDE**
   - Import as Gradle project
   - Ensure code style settings are applied

4. **Build and Test**
   ```bash
   ./gradlew build
   ./gradlew test
   ```

## Development Workflow

### 1. Create a Branch

```bash
# Sync with upstream
git fetch upstream
git checkout main
git merge upstream/main

# Create feature branch
git checkout -b feature/your-feature-name
```

### 2. Make Changes

- Follow the [Code Style Guide](#code-style)
- Write tests for new functionality
- Update documentation as needed

### 3. Test Your Changes

```bash
# Run all tests
./gradlew test

# Run integration tests
./gradlew integrationTest

# Run performance benchmarks
./gradlew jmh

# Check code quality
./gradlew check
```

### 4. Submit Pull Request

```bash
# Commit changes
git add .
git commit -m "feat: add new feature description"

# Push to your fork
git push origin feature/your-feature-name
```

Then [create a pull request](https://github.com/carabistouflette/justsyncit/compare) with:

- Clear title and description
- Reference related issues
- Screenshots if applicable
- Testing instructions

## Code Style

### Java Style

We follow Google Java Style Guide with these modifications:

- **Indentation**: 2 spaces (no tabs)
- **Line Length**: 120 characters
- **Imports**: Organized and no wildcards
- **Naming**: CamelCase for classes, snake_case for constants

### Example

```java
package com.justsyncit.example;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Example class demonstrating code style.
 * 
 * @author JustSyncIt Team
 * @since 1.0
 */
public class ExampleClass {
    
    private static final int MAX_RETRIES = 3;
    private final String serviceName;
    
    public ExampleClass(String serviceName) {
        this.serviceName = serviceName;
    }
    
    /**
     * Processes the given items asynchronously.
     * 
     * @param items the items to process
     * @return a CompletableFuture containing the results
     * @throws IllegalArgumentException if items is null
     */
    public CompletableFuture<List<String>> processItems(List<String> items) {
        if (items == null) {
            throw new IllegalArgumentException("Items cannot be null");
        }
        
        return CompletableFuture.supplyAsync(() -> {
            // Implementation here
            return items.stream()
                     .filter(item -> item != null)
                     .map(String::toUpperCase)
                     .toList();
        });
    }
}
```

### Testing Style

```java
class ExampleClassTest {
    
    private ExampleClass exampleClass;
    
    @BeforeEach
    void setUp() {
        exampleClass = new ExampleClass("test-service");
    }
    
    @Test
    @DisplayName("should process items correctly")
    void shouldProcessItemsCorrectly() {
        // Given
        List<String> items = List.of("item1", "item2", "item3");
        
        // When
        CompletableFuture<List<String>> result = exampleClass.processItems(items);
        
        // Then
        List<String> processed = result.join();
        assertThat(processed)
            .containsExactly("ITEM1", "ITEM2", "ITEM3");
    }
    
    @Test
    @DisplayName("should throw exception for null items")
    void shouldThrowExceptionForNullItems() {
        // When & Then
        assertThatThrownBy(() -> exampleClass.processItems(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Items cannot be null");
    }
}
```

## Project Structure

```
src/main/java/com/justsyncit/
├── hash/           # BLAKE3 hashing implementation
├── network/        # Network protocol and transfer
├── storage/        # Content-addressable storage
├── simd/           # SIMD detection and optimization
└── command/        # Command-line interface

src/test/java/com/justsyncit/
├── hash/           # Hash-related tests
├── network/        # Network tests
├── storage/        # Storage tests
└── integration/     # Integration tests
```

## Testing Guidelines

### Test Coverage

- Aim for >80% line coverage
- Test public APIs thoroughly
- Include edge cases and error conditions
- Use meaningful test names

### Test Types

- **Unit Tests**: Fast, isolated tests
- **Integration Tests**: Component interaction
- **Performance Tests**: Benchmark critical paths
- **Security Tests**: Input validation and permissions

### Test Data

- Use realistic test data
- Clean up test files
- Use temporary directories
- Avoid hard-coded paths

## Pull Request Process

### Before Submitting

1. **Code Review Checklist**
   - [ ] Code follows style guide
   - [ ] Tests pass locally
   - [ ] Documentation updated
   - [ ] No TODO comments left
   - [ ] Performance impact considered

2. **Quality Gates**
   - [ ] All tests pass
   - [ ] Code coverage ≥80%
   - [ ] No Checkstyle violations
   - [ ] No SpotBugs warnings
   - [ ] Security scan passes

### Review Process

1. **Automated Checks**
   - CI/CD pipeline runs automatically
   - All checks must pass

2. **Code Review**
   - At least one maintainer approval
   - Address all review comments
   - Update based on feedback

3. **Merge**
   - Squash and merge to main
   - Update CHANGELOG.md
   - Create release if needed

## Release Process

### Versioning

We use [Semantic Versioning](https://semver.org/):

- **MAJOR**: Breaking changes
- **MINOR**: New features (backward compatible)
- **PATCH**: Bug fixes (backward compatible)

### Release Steps

1. Update version in `build.gradle`
2. Update `CHANGELOG.md`
3. Create release tag
4. Automated release process handles:
   - Building artifacts
   - Creating GitHub release
   - Publishing Docker images
   - Updating documentation

## Community Guidelines

### Code of Conduct

- Be respectful and inclusive
- Welcome newcomers and help them learn
- Focus on constructive feedback
- Assume good intentions

### Communication

- Use GitHub issues for bug reports
- Use discussions for questions
- Be patient with response times
- Provide clear, reproducible examples

## Getting Help

- **Documentation**: [Project docs](https://justsyncit.github.io/justsyncit/)
- **Issues**: [GitHub Issues](https://github.com/carabistouflette/justsyncit/issues)
- **Discussions**: [GitHub Discussions](https://github.com/carabistouflette/justsyncit/discussions)
- **Email**: justsyncit@example.com

## Recognition

Contributors are recognized in:

- README.md contributors section
- Release notes
- Annual community report
- Special contributor badges

Thank you for contributing to JustSyncIt! 🎉