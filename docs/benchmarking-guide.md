# JustSyncIt Performance Benchmarking Guide

This guide provides comprehensive information about the JustSyncIt performance benchmarking system, including how to run benchmarks, interpret results, and integrate with CI/CD.

## Overview

The JustSyncIt benchmarking system provides comprehensive performance testing across multiple dimensions:

- **Throughput Benchmark**: Measures data processing throughput (MB/s)
- **Scalability Benchmark**: Tests performance with increasing dataset sizes
- **Network Benchmark**: Compares TCP vs QUIC performance
- **Deduplication Benchmark**: Measures deduplication efficiency and overhead
- **Concurrency Benchmark**: Tests performance with concurrent operations

## Performance Targets

The benchmarking system validates the following performance targets:

| Metric | Target | Description |
|---------|--------|-------------|
| Backup Throughput | >50 MB/s | Local backup operations |
| Restore Throughput | >100 MB/s | Local restore operations |
| Network Throughput | >80% bandwidth | Network-based operations |
| Memory Usage | <500 MB | Typical operations |
| Deduplication Overhead | <10% | Performance impact |
| GC Overhead | <10% | Garbage collection impact |

## Running Benchmarks

### Quick Start

```bash
# Run all benchmarks
./gradlew runBenchmarks

# Run specific benchmark categories
./gradlew runThroughputBenchmarks
./gradlew runScalabilityBenchmarks
./gradlew runNetworkBenchmarks
./gradlew runDeduplicationBenchmarks
./gradlew runConcurrencyBenchmarks

# Generate reports
./gradlew generateBenchmarkReports

# Verify performance targets
./gradlew verifyPerformance
```

### Individual Benchmark Classes

You can also run individual benchmark classes using JUnit:

```bash
# Run specific benchmark test class
./gradlew test --tests "com.justsyncit.performance.ThroughputBenchmark"

# Run comprehensive benchmark suite
./gradlew test --tests "com.justsyncit.performance.ComprehensiveBenchmarkSuite"
```

## Benchmark Components

### PerformanceMetrics

The `PerformanceMetrics` class provides standardized measurement capabilities:

```java
PerformanceMetrics metrics = new PerformanceMetrics("My Benchmark");

// Record throughput
metrics.recordThroughput(bytesProcessed, durationMs);

// Record operation rates
metrics.recordOperationRate(operations, durationMs, "files");

// Record deduplication efficiency
metrics.recordDeduplicationEfficiency(files, chunks, originalSize, storedSize);

// Record network performance
metrics.recordNetworkPerformance(bytesTransferred, durationMs, packetsLost, latencyMs);

// Finalize and get summary
metrics.finalizeMetrics();
String summary = metrics.generateSummary();
```

### PerformanceProfiler

The `PerformanceProfiler` class provides real-time resource monitoring:

```java
try (PerformanceProfiler profiler = new PerformanceProfiler("Benchmark Name", 100)) {
    profiler.start();
    
    // Set path to monitor for disk I/O
    profiler.setMonitoredPath(dataPath);
    
    // Run your benchmark operation
    runBenchmarkOperation();
    
    // Increment custom counters
    profiler.incrementCounter("custom_metric", value);
    
    profiler.stop();
    
    // Get comprehensive summary
    PerformanceProfiler.ProfilingSummary summary = profiler.getSummary();
    System.out.println(summary.generateSummary());
}
```

### BenchmarkDataGenerator

The `BenchmarkDataGenerator` class creates various test datasets:

```java
// Create mixed dataset with specified size
Path dataset = tempDir.resolve("test-data");
BenchmarkDataGenerator.createMixedDataset(dataset, 500); // 500MB

// Create dataset with high deduplication potential
BenchmarkDataGenerator.createDuplicateHeavyDataset(dataset, 100, 0.8); // 80% duplicates

// Create deep directory structure
BenchmarkDataGenerator.createDeepDirectoryDataset(dataset, 20, 10, 10240); // 20 levels

// Create incremental dataset
BenchmarkDataGenerator.DatasetInfo info = BenchmarkDataGenerator.createIncrementalDataset(dataset, 200);
BenchmarkDataGenerator.modifyIncrementalDataset(info, 0.2, 0.1, 0.05); // 20% modify, 10% add, 5% delete
```

### BenchmarkReportGenerator

The `BenchmarkReportGenerator` class creates comprehensive reports:

```java
List<PerformanceMetrics> allMetrics = Arrays.asList(metrics1, metrics2, metrics3);
Path reportDir = Paths.get("benchmark-reports");

BenchmarkReportGenerator generator = new BenchmarkReportGenerator(allMetrics, reportDir);

// Generate different report formats
Path htmlReport = generator.generateHtmlReport();
Path jsonReport = generator.generateJsonReport();
Path csvReport = generator.generateCsvReport();
Path textSummary = generator.generateTextSummaryReport();
Path chartData = generator.generateChartDataFile();
```

## CI/CD Integration

### GitHub Actions

The benchmarking system includes comprehensive GitHub Actions workflow (`.github/workflows/performance-benchmarks.yml`) that:

- Runs benchmarks on push/PR to main/develop branches
- Runs daily scheduled benchmarks
- Supports manual triggering with specific benchmark types
- Generates and uploads reports as artifacts
- Comments on PRs with performance results
- Validates performance targets and fails on regression

### Workflow Features

- **Multi-Java Version Support**: Tests on Java 17 and 21
- **Caching**: Caches dependencies and benchmark data for faster runs
- **Conditional Execution**: Quick tests for PRs, full tests for pushes
- **Artifact Upload**: Preserves benchmark results and reports
- **Performance Regression Detection**: Automatically detects performance regressions
- **Comprehensive Reporting**: Generates HTML, JSON, CSV, and text reports

## Benchmark Categories

### Throughput Benchmarks

Measure data processing throughput for various dataset sizes:

- **Small Datasets**: 1-100 MB (typical user documents)
- **Medium Datasets**: 100 MB - 1 GB (project directories)
- **Large Datasets**: 1-10 GB (media collections)
- **Chunk Size Impact**: Tests different chunk sizes
- **Integrity Verification**: Measures overhead of integrity checks

### Scalability Benchmarks

Test how performance changes with increasing scale:

- **Linear Data Size**: Performance vs dataset size
- **File Count**: Performance vs number of files
- **Directory Depth**: Performance vs directory structure depth
- **Memory Usage**: Memory consumption scaling
- **Incremental Backups**: Scalability of incremental operations

### Network Benchmarks

Compare network transport performance:

- **TCP vs QUIC**: Protocol comparison
- **File Size Impact**: Small vs large files
- **Latency Sensitivity**: Performance under various latencies
- **Packet Loss**: Performance with packet loss
- **Concurrent Connections**: Multiple simultaneous connections

### Deduplication Benchmarks

Measure deduplication efficiency:

- **Perfect Deduplication**: Identical files
- **Partial Deduplication**: Some duplicate content
- **No Deduplication**: All unique content
- **Chunk Size Impact**: Different chunk sizes
- **Incremental Efficiency**: Deduplication in incremental backups

### Concurrency Benchmarks

Test concurrent operation performance:

- **Concurrent Backups**: Multiple simultaneous backups
- **Concurrent Restores**: Multiple simultaneous restores
- **Mixed Operations**: Backup and restore together
- **Resource Contention**: Performance under load

## Interpreting Results

### Key Metrics

- **Throughput (MB/s)**: Data processing speed
- **Memory Usage (MB)**: Memory consumption during operations
- **CPU Usage (%)**: Processor utilization
- **GC Overhead (%)**: Time spent in garbage collection
- **Deduplication Ratio**: Space savings from deduplication
- **Network Latency (ms)**: Network operation latency
- **Operation Rate (ops/s)**: Operations per second

### Performance Analysis

1. **Compare to Targets**: Check if metrics meet performance targets
2. **Look for Regressions**: Compare with previous results
3. **Identify Bottlenecks**: High CPU, memory, or I/O usage
4. **Analyze Trends**: Performance changes over time
5. **Optimization Opportunities**: Areas for improvement

### Report Formats

- **HTML Report**: Interactive web-based report with charts
- **JSON Report**: Machine-readable format for analysis
- **CSV Report**: Spreadsheet-compatible data
- **Text Summary**: Quick overview of key metrics
- **Chart Data**: JSON format for visualization

## Best Practices

### Running Benchmarks

1. **Consistent Environment**: Use consistent hardware and software
2. **Multiple Runs**: Run benchmarks multiple times for statistical significance
3. **Warm-up**: Allow JVM to warm up before measuring
4. **Isolation**: Minimize background processes during benchmarks
5. **Resource Monitoring**: Use PerformanceProfiler for detailed metrics

### Interpreting Results

1. **Statistical Analysis**: Use multiple runs for reliable results
2. **Baseline Comparison**: Compare with known good performance
3. **Trend Analysis**: Track performance over time
4. **Context Consideration**: Consider system load and environment
5. **Root Cause Analysis**: Investigate performance issues

### CI/CD Integration

1. **Automated Regression Testing**: Detect performance regressions automatically
2. **Performance Gates**: Fail builds on significant regressions
3. **Historical Tracking**: Maintain performance history
4. **Alerting**: Notify on performance issues
5. **Reporting**: Share results with team

## Troubleshooting

### Common Issues

1. **Out of Memory**: Increase heap size with `-Xmx` flag
2. **Slow Benchmarks**: Check disk I/O and system load
3. **Inconsistent Results**: Ensure consistent test environment
4. **Network Issues**: Check network configuration for network benchmarks
5. **Compilation Errors**: Ensure all dependencies are available

### Performance Issues

1. **High GC Overhead**: Optimize memory allocation patterns
2. **Low Throughput**: Check I/O bottlenecks and chunk sizes
3. **High Memory Usage**: Review memory management and pooling
4. **Poor Scalability**: Analyze algorithmic complexity
5. **Network Performance**: Check protocol configuration and tuning

## Extending the System

### Adding New Benchmarks

1. Create new benchmark class extending existing patterns
2. Use `PerformanceMetrics` for standardized measurements
3. Use `PerformanceProfiler` for resource monitoring
4. Use `BenchmarkDataGenerator` for test data
5. Add to `ComprehensiveBenchmarkSuite` for integration

### Custom Metrics

1. Add custom metrics using `PerformanceMetrics.recordMetric()`
2. Use `PerformanceProfiler.incrementCounter()` for custom counters
3. Extend report generators for new metric types
4. Update performance targets as needed

### New Report Formats

1. Extend `BenchmarkReportGenerator` for new formats
2. Add template files for new report types
3. Update CI/CD workflow to handle new formats
4. Document new format capabilities

## Performance Monitoring

### Continuous Monitoring

1. Set up scheduled benchmark runs
2. Monitor performance trends over time
3. Alert on performance regressions
4. Maintain performance dashboards
5. Regular performance reviews

### Performance Optimization

1. Profile application with detailed metrics
2. Identify performance bottlenecks
3. Optimize critical paths
4. Validate improvements with benchmarks
5. Monitor for regressions

## Resources

- [JUnit 5 Documentation](https://junit.org/junit5/docs/current/user-guide/)
- [JMH Documentation](https://openjdk.org/projects/code-tools/jmh/)
- [Gradle Testing Guide](https://docs.gradle.org/current/userguide/java_testing.html)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)

## Support

For questions or issues with the benchmarking system:

1. Check this documentation for common solutions
2. Review benchmark logs for error details
3. Check GitHub Issues for known problems
4. Create new issue with detailed information
5. Include benchmark results and system information