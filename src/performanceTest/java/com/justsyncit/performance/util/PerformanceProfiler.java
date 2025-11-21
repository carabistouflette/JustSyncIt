/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.justsyncit.performance.util;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Advanced performance profiler for monitoring system resources during benchmarks.
 * Provides real-time monitoring of CPU, memory, disk I/O, and network usage.
 */
public class PerformanceProfiler implements AutoCloseable {
    
    private final String benchmarkName;
    private final long samplingIntervalMs;
    private final ScheduledExecutorService scheduler;
    private final List<ResourceSnapshot> snapshots;
    private final Map<String, AtomicLong> customCounters;
    
    private final OperatingSystemMXBean osBean;
    private final MemoryMXBean memoryBean;
    private final ThreadMXBean threadBean;
    private final List<GarbageCollectorMXBean> gcBeans;
    
    private volatile boolean isRunning = false;
    private Instant startTime;
    private Instant endTime;
    private Path monitoredPath;
    
    // Disk I/O tracking
    private long initialDiskReads;
    private long initialDiskWrites;
    private long initialDiskReadBytes;
    private long initialDiskWriteBytes;
    
    /**
     * Creates a new performance profiler.
     *
     * @param benchmarkName name of the benchmark being profiled
     * @param samplingIntervalMs sampling interval in milliseconds
     */
    public PerformanceProfiler(String benchmarkName, long samplingIntervalMs) {
        this.benchmarkName = benchmarkName;
        this.samplingIntervalMs = samplingIntervalMs;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PerformanceProfiler-" + benchmarkName);
            t.setDaemon(true);
            return t;
        });
        this.snapshots = new ArrayList<>();
        this.customCounters = new ConcurrentHashMap<>();
        
        // Initialize JMX beans
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.threadBean = ManagementFactory.getThreadMXBean();
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        
        // Initialize disk I/O tracking
        initializeDiskIoTracking();
    }
    
    /**
     * Starts the performance profiler.
     */
    public void start() {
        if (isRunning) {
            throw new IllegalStateException("Profiler is already running");
        }
        
        isRunning = true;
        startTime = Instant.now();
        
        // Record initial state
        recordInitialDiskIoState();
        
        // Start periodic sampling
        scheduler.scheduleAtFixedRate(this::takeSnapshot, 0, samplingIntervalMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Stops the performance profiler.
     */
    public void stop() {
        if (!isRunning) {
            return;
        }
        
        isRunning = false;
        endTime = Instant.now();
        
        // Take final snapshot
        takeSnapshot();
        
        // Shutdown scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
    }
    
    /**
     * Sets the path to monitor for disk I/O operations.
     *
     * @param path the path to monitor
     */
    public void setMonitoredPath(Path path) {
        this.monitoredPath = path;
    }
    
    /**
     * Increments a custom counter.
     *
     * @param counterName name of the counter
     * @param value value to add
     */
    public void incrementCounter(String counterName, long value) {
        customCounters.computeIfAbsent(counterName, k -> new AtomicLong(0)).addAndGet(value);
    }
    
    /**
     * Gets the value of a custom counter.
     *
     * @param counterName name of the counter
     * @return counter value
     */
    public long getCounter(String counterName) {
        AtomicLong counter = customCounters.get(counterName);
        return counter != null ? counter.get() : 0;
    }
    
    /**
     * Gets all resource snapshots.
     *
     * @return list of resource snapshots
     */
    public List<ResourceSnapshot> getSnapshots() {
        return new ArrayList<>(snapshots);
    }
    
    /**
     * Gets the profiling summary.
     *
     * @return profiling summary
     */
    public ProfilingSummary getSummary() {
        if (snapshots.isEmpty()) {
            throw new IllegalStateException("No snapshots available");
        }
        
        return new ProfilingSummary(benchmarkName, snapshots, customCounters, startTime, endTime);
    }
    
    /**
     * Takes a resource snapshot.
     */
    private void takeSnapshot() {
        try {
            ResourceSnapshot snapshot = new ResourceSnapshot();
            
            // CPU usage
            snapshot.cpuUsage = getCpuUsage();
            snapshot.systemLoadAverage = osBean.getSystemLoadAverage();
            snapshot.availableProcessors = osBean.getAvailableProcessors();
            
            // Memory usage
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
            
            snapshot.heapUsed = heapUsage.getUsed();
            snapshot.heapMax = heapUsage.getMax();
            snapshot.heapCommitted = heapUsage.getCommitted();
            snapshot.nonHeapUsed = nonHeapUsage.getUsed();
            snapshot.nonHeapMax = nonHeapUsage.getMax();
            snapshot.nonHeapCommitted = nonHeapUsage.getCommitted();
            
            // Thread information
            snapshot.threadCount = threadBean.getThreadCount();
            snapshot.daemonThreadCount = threadBean.getDaemonThreadCount();
            snapshot.peakThreadCount = threadBean.getPeakThreadCount();
            
            // Garbage collection
            snapshot.gcCount = 0;
            snapshot.gcTime = 0;
            for (GarbageCollectorMXBean gcBean : gcBeans) {
                snapshot.gcCount += gcBean.getCollectionCount();
                snapshot.gcTime += gcBean.getCollectionTime();
            }
            
            // Disk I/O
            if (monitoredPath != null) {
                snapshot.diskUsage = getDiskUsage(monitoredPath);
            }
            
            // Timestamp
            snapshot.timestamp = Instant.now();
            
            snapshots.add(snapshot);
            
        } catch (Exception e) {
            // Log error but don't stop profiling
            System.err.println("Error taking resource snapshot: " + e.getMessage());
        }
    }
    
    /**
     * Gets current CPU usage.
     */
    private double getCpuUsage() {
        try {
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sunOsBean = 
                    (com.sun.management.OperatingSystemMXBean) osBean;
                return sunOsBean.getProcessCpuLoad() * 100.0;
            }
        } catch (Exception e) {
            // Fallback to system load average
            double loadAverage = osBean.getSystemLoadAverage();
            if (loadAverage >= 0) {
                return (loadAverage / osBean.getAvailableProcessors()) * 100.0;
            }
        }
        return 0.0;
    }
    
    /**
     * Gets disk usage information.
     */
    private DiskUsage getDiskUsage(Path path) {
        try {
            FileStore store = Files.getFileStore(path);
            long total = store.getTotalSpace();
            long free = store.getUsableSpace();
            long used = total - free;
            
            DiskUsage usage = new DiskUsage();
            usage.totalSpace = total;
            usage.freeSpace = free;
            usage.usedSpace = used;
            usage.usagePercent = (double) used / total * 100.0;
            
            return usage;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Initializes disk I/O tracking.
     */
    private void initializeDiskIoTracking() {
        try {
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sunOsBean = 
                    (com.sun.management.OperatingSystemMXBean) osBean;
                
                // These methods may not be available on all platforms
                initialDiskReads = getLongMethod(sunOsBean, "getProcessReadCount");
                initialDiskWrites = getLongMethod(sunOsBean, "getProcessWriteCount");
                initialDiskReadBytes = getLongMethod(sunOsBean, "getProcessReadBytes");
                initialDiskWriteBytes = getLongMethod(sunOsBean, "getProcessWriteBytes");
            }
        } catch (Exception e) {
            // Disk I/O tracking not available
            initialDiskReads = 0;
            initialDiskWrites = 0;
            initialDiskReadBytes = 0;
            initialDiskWriteBytes = 0;
        }
    }
    
    /**
     * Records initial disk I/O state.
     */
    private void recordInitialDiskIoState() {
        initializeDiskIoTracking();
    }
    
    /**
     * Gets long value from method using reflection.
     */
    private long getLongMethod(Object obj, String methodName) {
        try {
            java.lang.reflect.Method method = obj.getClass().getMethod(methodName);
            return (Long) method.invoke(obj);
        } catch (Exception e) {
            return 0;
        }
    }
    
    @Override
    public void close() {
        stop();
    }
    
    /**
     * Resource snapshot captured at a point in time.
     */
    public static class ResourceSnapshot {
        public Instant timestamp;
        
        // CPU metrics
        public double cpuUsage; // percentage
        public double systemLoadAverage;
        public int availableProcessors;
        
        // Memory metrics (bytes)
        public long heapUsed;
        public long heapMax;
        public long heapCommitted;
        public long nonHeapUsed;
        public long nonHeapMax;
        public long nonHeapCommitted;
        
        // Thread metrics
        public int threadCount;
        public int daemonThreadCount;
        public int peakThreadCount;
        
        // Garbage collection metrics
        public long gcCount;
        public long gcTime; // milliseconds
        
        // Disk metrics
        public DiskUsage diskUsage;
        
        /**
         * Gets heap memory usage percentage.
         */
        public double getHeapUsagePercent() {
            return heapMax > 0 ? (double) heapUsed / heapMax * 100.0 : 0.0;
        }
        
        /**
         * Gets non-heap memory usage percentage.
         */
        public double getNonHeapUsagePercent() {
            return nonHeapMax > 0 ? (double) nonHeapUsed / nonHeapMax * 100.0 : 0.0;
        }
    }
    
    /**
     * Disk usage information.
     */
    public static class DiskUsage {
        public long totalSpace;
        public long usedSpace;
        public long freeSpace;
        public double usagePercent;
    }
    
    /**
     * Comprehensive profiling summary.
     */
    public static class ProfilingSummary {
        private final String benchmarkName;
        private final List<ResourceSnapshot> snapshots;
        private final Map<String, AtomicLong> customCounters;
        private final Instant startTime;
        private final Instant endTime;
        
        public ProfilingSummary(String benchmarkName, List<ResourceSnapshot> snapshots, 
                               Map<String, AtomicLong> customCounters, Instant startTime, Instant endTime) {
            this.benchmarkName = benchmarkName;
            this.snapshots = new ArrayList<>(snapshots);
            this.customCounters = new HashMap<>(customCounters);
            this.startTime = startTime;
            this.endTime = endTime;
        }
        
        /**
         * Gets the duration of the profiling session.
         */
        public long getDurationMs() {
            return ChronoUnit.MILLIS.between(startTime, endTime);
        }
        
        /**
         * Gets average CPU usage.
         */
        public double getAverageCpuUsage() {
            return snapshots.stream()
                .mapToDouble(s -> s.cpuUsage)
                .average()
                .orElse(0.0);
        }
        
        /**
         * Gets peak CPU usage.
         */
        public double getPeakCpuUsage() {
            return snapshots.stream()
                .mapToDouble(s -> s.cpuUsage)
                .max()
                .orElse(0.0);
        }
        
        /**
         * Gets average heap memory usage.
         */
        public double getAverageHeapUsageMB() {
            return snapshots.stream()
                .mapToLong(s -> s.heapUsed)
                .average()
                .orElse(0.0) / (1024.0 * 1024.0);
        }
        
        /**
         * Gets peak heap memory usage.
         */
        public double getPeakHeapUsageMB() {
            return snapshots.stream()
                .mapToLong(s -> s.heapUsed)
                .max()
                .orElse(0L) / (1024.0 * 1024.0);
        }
        
        /**
         * Gets total GC time.
         */
        public long getTotalGcTimeMs() {
            return snapshots.stream()
                .mapToLong(s -> s.gcTime)
                .max()
                .orElse(0L);
        }
        
        /**
         * Gets GC overhead percentage.
         */
        public double getGcOverheadPercent() {
            long totalGcTime = getTotalGcTimeMs();
            long duration = getDurationMs();
            return duration > 0 ? (double) totalGcTime / duration * 100.0 : 0.0;
        }
        
        /**
         * Gets peak thread count.
         */
        public int getPeakThreadCount() {
            return snapshots.stream()
                .mapToInt(s -> s.threadCount)
                .max()
                .orElse(0);
        }
        
        /**
         * Gets custom counter value.
         */
        public long getCustomCounter(String name) {
            AtomicLong counter = customCounters.get(name);
            return counter != null ? counter.get() : 0;
        }
        
        /**
         * Gets all custom counters.
         */
        public Map<String, Long> getCustomCounters() {
            Map<String, Long> result = new HashMap<>();
            customCounters.forEach((k, v) -> result.put(k, v.get()));
            return result;
        }
        
        /**
         * Generates a summary report.
         */
        public String generateSummary() {
            StringBuilder sb = new StringBuilder();
            
            sb.append("Performance Profiling Summary for: ").append(benchmarkName).append("\n");
            sb.append("Duration: ").append(getDurationMs()).append(" ms\n");
            sb.append("Snapshots: ").append(snapshots.size()).append("\n\n");
            
            sb.append("CPU Performance:\n");
            sb.append("  Average Usage: ").append(String.format("%.2f%%", getAverageCpuUsage())).append("\n");
            sb.append("  Peak Usage: ").append(String.format("%.2f%%", getPeakCpuUsage())).append("\n\n");
            
            sb.append("Memory Performance:\n");
            sb.append("  Average Heap Usage: ").append(String.format("%.2f MB", getAverageHeapUsageMB())).append("\n");
            sb.append("  Peak Heap Usage: ").append(String.format("%.2f MB", getPeakHeapUsageMB())).append("\n\n");
            
            sb.append("Garbage Collection:\n");
            sb.append("  Total GC Time: ").append(getTotalGcTimeMs()).append(" ms\n");
            sb.append("  GC Overhead: ").append(String.format("%.2f%%", getGcOverheadPercent())).append("\n\n");
            
            sb.append("Thread Performance:\n");
            sb.append("  Peak Thread Count: ").append(getPeakThreadCount()).append("\n\n");
            
            if (!customCounters.isEmpty()) {
                sb.append("Custom Counters:\n");
                customCounters.forEach((k, v) -> 
                    sb.append("  ").append(k).append(": ").append(v.get()).append("\n"));
            }
            
            return sb.toString();
        }
    }
}