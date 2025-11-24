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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.justsyncit.scanner;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration for thread pool behavior and adaptive sizing.
 * Provides fine-grained control over thread pool parameters.
 */
public class ThreadPoolConfiguration {
    
    // Pool-specific configurations
    private final Map<ThreadPoolManager.PoolType, PoolConfig> poolConfigs;
    
    // Global configuration
    private final boolean enableAdaptiveSizing;
    private final boolean enableThreadAffinity;
    private final boolean enableNumaAwareness;
    private final boolean enableWarmup;
    private final boolean enableBackpressure;
    private final long adaptiveSizingIntervalMs;
    private final double maxCpuUsageThreshold;
    private final double memoryPressureThreshold;
    private final int minThreadsPerPool;
    private final int maxThreadsPerPool;
    
    /**
     * Configuration for a specific thread pool.
     */
    public static class PoolConfig {
        private final int corePoolSize;
        private final int maximumPoolSize;
        private final long keepAliveTimeMs;
        private final ThreadPoolManager.ThreadPriority priority;
        private final boolean allowCoreThreadTimeout;
        private final int queueCapacity;
        private final String threadNamePrefix;
        private final boolean enableAffinity;
        private final int[] affinityCores;
        
        private PoolConfig(Builder builder) {
            this.corePoolSize = builder.corePoolSize;
            this.maximumPoolSize = builder.maximumPoolSize;
            this.keepAliveTimeMs = builder.keepAliveTimeMs;
            this.priority = builder.priority;
            this.allowCoreThreadTimeout = builder.allowCoreThreadTimeout;
            this.queueCapacity = builder.queueCapacity;
            this.threadNamePrefix = builder.threadNamePrefix;
            this.enableAffinity = builder.enableAffinity;
            this.affinityCores = builder.affinityCores != null ? builder.affinityCores.clone() : null;
        }
        
        public static class Builder {
            private int corePoolSize = 2;
            private int maximumPoolSize = 8;
            private long keepAliveTimeMs = 60000; // 1 minute
            private ThreadPoolManager.ThreadPriority priority = ThreadPoolManager.ThreadPriority.NORMAL;
            private boolean allowCoreThreadTimeout = false;
            private int queueCapacity = 1000;
            private String threadNamePrefix = "ThreadPool";
            private boolean enableAffinity = false;
            private int[] affinityCores = null;
            
            public Builder corePoolSize(int corePoolSize) {
                this.corePoolSize = corePoolSize;
                return this;
            }
            
            public Builder maximumPoolSize(int maximumPoolSize) {
                this.maximumPoolSize = maximumPoolSize;
                return this;
            }
            
            public Builder keepAliveTimeMs(long keepAliveTimeMs) {
                this.keepAliveTimeMs = keepAliveTimeMs;
                return this;
            }
            
            public Builder priority(ThreadPoolManager.ThreadPriority priority) {
                this.priority = priority;
                return this;
            }
            
            public Builder allowCoreThreadTimeout(boolean allowCoreThreadTimeout) {
                this.allowCoreThreadTimeout = allowCoreThreadTimeout;
                return this;
            }
            
            public Builder queueCapacity(int queueCapacity) {
                this.queueCapacity = queueCapacity;
                return this;
            }
            
            public Builder threadNamePrefix(String threadNamePrefix) {
                this.threadNamePrefix = threadNamePrefix;
                return this;
            }
            
            public Builder enableAffinity(boolean enableAffinity) {
                this.enableAffinity = enableAffinity;
                return this;
            }
            
            public Builder affinityCores(int[] affinityCores) {
                this.affinityCores = affinityCores;
                return this;
            }
            
            public PoolConfig build() {
                validate();
                return new PoolConfig(this);
            }
            
            private void validate() {
                if (corePoolSize <= 0) {
                    throw new IllegalArgumentException("Core pool size must be positive");
                }
                if (maximumPoolSize < corePoolSize) {
                    throw new IllegalArgumentException("Maximum pool size must be >= core pool size");
                }
                if (keepAliveTimeMs < 0) {
                    throw new IllegalArgumentException("Keep alive time must be non-negative");
                }
                if (queueCapacity <= 0) {
                    throw new IllegalArgumentException("Queue capacity must be positive");
                }
                if (threadNamePrefix == null || threadNamePrefix.trim().isEmpty()) {
                    throw new IllegalArgumentException("Thread name prefix cannot be null or empty");
                }
            }
        }
        
        // Getters
        public int getCorePoolSize() { return corePoolSize; }
        public int getMaximumPoolSize() { return maximumPoolSize; }
        public long getKeepAliveTimeMs() { return keepAliveTimeMs; }
        public ThreadPoolManager.ThreadPriority getPriority() { return priority; }
        public boolean isAllowCoreThreadTimeout() { return allowCoreThreadTimeout; }
        public int getQueueCapacity() { return queueCapacity; }
        public String getThreadNamePrefix() { return threadNamePrefix; }
        public boolean isEnableAffinity() { return enableAffinity; }
        public int[] getAffinityCores() { return affinityCores != null ? affinityCores.clone() : null; }
        
        @Override
        public String toString() {
            return String.format(
                "PoolConfig{core=%d, max=%d, keepAlive=%dms, priority=%s, " +
                "allowCoreTimeout=%b, queueCapacity=%d, namePrefix=%s, affinity=%b}",
                corePoolSize, maximumPoolSize, keepAliveTimeMs, priority,
                allowCoreThreadTimeout, queueCapacity, threadNamePrefix, enableAffinity
            );
        }
    }
    
    /**
     * Builder for ThreadPoolConfiguration.
     */
    public static class Builder {
        private boolean enableAdaptiveSizing = true;
        private boolean enableThreadAffinity = false;
        private boolean enableNumaAwareness = false;
        private boolean enableWarmup = true;
        private boolean enableBackpressure = true;
        private long adaptiveSizingIntervalMs = 30000; // 30 seconds
        private double maxCpuUsageThreshold = 0.8; // 80%
        private double memoryPressureThreshold = 0.8; // 80%
        private int minThreadsPerPool = 1;
        private int maxThreadsPerPool = Runtime.getRuntime().availableProcessors() * 2;
        
        // Pool-specific configurations
        private final Map<ThreadPoolManager.PoolType, PoolConfig> poolConfigs = new ConcurrentHashMap<>();
        
        public Builder enableAdaptiveSizing(boolean enableAdaptiveSizing) {
            this.enableAdaptiveSizing = enableAdaptiveSizing;
            return this;
        }
        
        public Builder enableThreadAffinity(boolean enableThreadAffinity) {
            this.enableThreadAffinity = enableThreadAffinity;
            return this;
        }
        
        public Builder enableNumaAwareness(boolean enableNumaAwareness) {
            this.enableNumaAwareness = enableNumaAwareness;
            return this;
        }
        
        public Builder enableWarmup(boolean enableWarmup) {
            this.enableWarmup = enableWarmup;
            return this;
        }
        
        public Builder enableBackpressure(boolean enableBackpressure) {
            this.enableBackpressure = enableBackpressure;
            return this;
        }
        
        public Builder adaptiveSizingIntervalMs(long adaptiveSizingIntervalMs) {
            this.adaptiveSizingIntervalMs = adaptiveSizingIntervalMs;
            return this;
        }
        
        public Builder maxCpuUsageThreshold(double maxCpuUsageThreshold) {
            this.maxCpuUsageThreshold = maxCpuUsageThreshold;
            return this;
        }
        
        public Builder memoryPressureThreshold(double memoryPressureThreshold) {
            this.memoryPressureThreshold = memoryPressureThreshold;
            return this;
        }
        
        public Builder minThreadsPerPool(int minThreadsPerPool) {
            this.minThreadsPerPool = minThreadsPerPool;
            return this;
        }
        
        public Builder maxThreadsPerPool(int maxThreadsPerPool) {
            this.maxThreadsPerPool = maxThreadsPerPool;
            return this;
        }
        
        public Builder poolConfig(ThreadPoolManager.PoolType type, PoolConfig config) {
            this.poolConfigs.put(type, config);
            return this;
        }
        
        public ThreadPoolConfiguration build() {
            validate();
            return new ThreadPoolConfiguration(this);
        }
        
        private void validate() {
            if (adaptiveSizingIntervalMs <= 0) {
                throw new IllegalArgumentException("Adaptive sizing interval must be positive");
            }
            if (maxCpuUsageThreshold <= 0.0 || maxCpuUsageThreshold > 1.0) {
                throw new IllegalArgumentException("Max CPU usage threshold must be between 0.0 and 1.0");
            }
            if (memoryPressureThreshold <= 0.0 || memoryPressureThreshold > 1.0) {
                throw new IllegalArgumentException("Memory pressure threshold must be between 0.0 and 1.0");
            }
            if (minThreadsPerPool <= 0) {
                throw new IllegalArgumentException("Min threads per pool must be positive");
            }
            if (maxThreadsPerPool < minThreadsPerPool) {
                throw new IllegalArgumentException("Max threads per pool must be >= min threads per pool");
            }
            
            // Set default pool configs if not provided
            setDefaultPoolConfigs();
        }
        
        private void setDefaultPoolConfigs() {
            int processors = Runtime.getRuntime().availableProcessors();
            
            // I/O pool - more threads for I/O bound operations
            if (!poolConfigs.containsKey(ThreadPoolManager.PoolType.IO)) {
                poolConfigs.put(ThreadPoolManager.PoolType.IO, new PoolConfig.Builder()
                    .corePoolSize(Math.max(2, processors / 2))
                    .maximumPoolSize(processors * 2)
                    .keepAliveTimeMs(120000) // 2 minutes
                    .priority(ThreadPoolManager.ThreadPriority.NORMAL)
                    .allowCoreThreadTimeout(true)
                    .queueCapacity(500)
                    .threadNamePrefix("IO-ThreadPool")
                    .enableAffinity(enableThreadAffinity)
                    .build());
            }
            
            // CPU pool - fewer threads for CPU-bound operations
            if (!poolConfigs.containsKey(ThreadPoolManager.PoolType.CPU)) {
                poolConfigs.put(ThreadPoolManager.PoolType.CPU, new PoolConfig.Builder()
                    .corePoolSize(processors)
                    .maximumPoolSize(processors)
                    .keepAliveTimeMs(300000) // 5 minutes
                    .priority(ThreadPoolManager.ThreadPriority.HIGH)
                    .allowCoreThreadTimeout(false)
                    .queueCapacity(1000) // Increased queue capacity for better efficiency
                    .threadNamePrefix("CPU-ThreadPool")
                    .enableAffinity(enableThreadAffinity)
                    .build());
            }
            
            // CompletionHandler pool - high priority for callbacks
            if (!poolConfigs.containsKey(ThreadPoolManager.PoolType.COMPLETION_HANDLER)) {
                poolConfigs.put(ThreadPoolManager.PoolType.COMPLETION_HANDLER, new PoolConfig.Builder()
                    .corePoolSize(4)
                    .maximumPoolSize(8)
                    .keepAliveTimeMs(60000) // 1 minute
                    .priority(ThreadPoolManager.ThreadPriority.HIGH)
                    .allowCoreThreadTimeout(true)
                    .queueCapacity(1000)
                    .threadNamePrefix("CompletionHandler-ThreadPool")
                    .enableAffinity(enableThreadAffinity)
                    .build());
            }
            
            // Batch processing pool - medium priority for batch operations
            if (!poolConfigs.containsKey(ThreadPoolManager.PoolType.BATCH_PROCESSING)) {
                poolConfigs.put(ThreadPoolManager.PoolType.BATCH_PROCESSING, new PoolConfig.Builder()
                    .corePoolSize(Math.max(2, processors / 4))
                    .maximumPoolSize(processors)
                    .keepAliveTimeMs(180000) // 3 minutes
                    .priority(ThreadPoolManager.ThreadPriority.NORMAL)
                    .allowCoreThreadTimeout(true)
                    .queueCapacity(200)
                    .threadNamePrefix("Batch-ThreadPool")
                    .enableAffinity(enableThreadAffinity)
                    .build());
            }
            
            // WatchService pool - low priority for monitoring
            if (!poolConfigs.containsKey(ThreadPoolManager.PoolType.WATCH_SERVICE)) {
                poolConfigs.put(ThreadPoolManager.PoolType.WATCH_SERVICE, new PoolConfig.Builder()
                    .corePoolSize(2)
                    .maximumPoolSize(4)
                    .keepAliveTimeMs(300000) // 5 minutes
                    .priority(ThreadPoolManager.ThreadPriority.LOW)
                    .allowCoreThreadTimeout(true)
                    .queueCapacity(50)
                    .threadNamePrefix("WatchService-ThreadPool")
                    .enableAffinity(enableThreadAffinity)
                    .build());
            }
            
            // Management pool - low priority for internal operations
            if (!poolConfigs.containsKey(ThreadPoolManager.PoolType.MANAGEMENT)) {
                poolConfigs.put(ThreadPoolManager.PoolType.MANAGEMENT, new PoolConfig.Builder()
                    .corePoolSize(2)
                    .maximumPoolSize(4)
                    .keepAliveTimeMs(300000) // 5 minutes
                    .priority(ThreadPoolManager.ThreadPriority.LOW)
                    .allowCoreThreadTimeout(true)
                    .queueCapacity(100)
                    .threadNamePrefix("Management-ThreadPool")
                    .enableAffinity(enableThreadAffinity)
                    .build());
            }
        }
    }
    
    /**
     * Private constructor.
     */
    private ThreadPoolConfiguration(Builder builder) {
        this.enableAdaptiveSizing = builder.enableAdaptiveSizing;
        this.enableThreadAffinity = builder.enableThreadAffinity;
        this.enableNumaAwareness = builder.enableNumaAwareness;
        this.enableWarmup = builder.enableWarmup;
        this.enableBackpressure = builder.enableBackpressure;
        this.adaptiveSizingIntervalMs = builder.adaptiveSizingIntervalMs;
        this.maxCpuUsageThreshold = builder.maxCpuUsageThreshold;
        this.memoryPressureThreshold = builder.memoryPressureThreshold;
        this.minThreadsPerPool = builder.minThreadsPerPool;
        this.maxThreadsPerPool = builder.maxThreadsPerPool;
        this.poolConfigs = new ConcurrentHashMap<>(builder.poolConfigs);
    }
    
    /**
     * Gets configuration for a specific pool type.
     */
    public PoolConfig getPoolConfig(ThreadPoolManager.PoolType type) {
        return poolConfigs.get(type);
    }
    
    /**
     * Updates configuration from another configuration.
     */
    public void updateFrom(ThreadPoolConfiguration other) {
        // This would update all fields - simplified for now
        poolConfigs.clear();
        poolConfigs.putAll(other.poolConfigs);
    }
    
    // Getters
    public boolean isEnableAdaptiveSizing() { return enableAdaptiveSizing; }
    public boolean isEnableThreadAffinity() { return enableThreadAffinity; }
    public boolean isEnableNumaAwareness() { return enableNumaAwareness; }
    public boolean isEnableWarmup() { return enableWarmup; }
    public boolean isEnableBackpressure() { return enableBackpressure; }
    public long getAdaptiveSizingIntervalMs() { return adaptiveSizingIntervalMs; }
    public double getMaxCpuUsageThreshold() { return maxCpuUsageThreshold; }
    public double getMemoryPressureThreshold() { return memoryPressureThreshold; }
    public int getMinThreadsPerPool() { return minThreadsPerPool; }
    public int getMaxThreadsPerPool() { return maxThreadsPerPool; }
    
    @Override
    public String toString() {
        return String.format(
            "ThreadPoolConfiguration{adaptiveSizing=%b, threadAffinity=%b, numaAwareness=%b, " +
            "warmup=%b, backpressure=%b, adaptiveInterval=%dms, maxCpu=%.2f, " +
            "memoryThreshold=%.2f, minThreads=%d, maxThreads=%d}",
            enableAdaptiveSizing, enableThreadAffinity, enableNumaAwareness,
            enableWarmup, enableBackpressure, adaptiveSizingIntervalMs,
            maxCpuUsageThreshold, memoryPressureThreshold,
            minThreadsPerPool, maxThreadsPerPool
        );
    }
}