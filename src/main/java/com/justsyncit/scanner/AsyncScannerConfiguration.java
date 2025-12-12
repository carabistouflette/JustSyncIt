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

package com.justsyncit.scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;
import java.util.Properties;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Configuration management for async directory scanning operations.
 * Provides configurable parameters, profiles, and runtime configuration
 * updates.
 */
public class AsyncScannerConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(AsyncScannerConfiguration.class);

    /** Default configuration file name. */
    private static final String DEFAULT_CONFIG_FILE = "async-scanner.properties";

    /** Configuration profiles for different scenarios. */
    private final Map<String, ConfigurationProfile> profiles = new ConcurrentHashMap<>();

    /** Current active configuration profile. */
    private final AtomicReference<ConfigurationProfile> activeProfile = new AtomicReference<>();

    /** Runtime configuration overrides. */
    private final AtomicReference<RuntimeConfiguration> runtimeConfig = new AtomicReference<>();

    /**
     * Configuration profile for specific scanning scenarios.
     */
    public static class ConfigurationProfile {
        private String name;
        private String description;
        private AsyncScanOptions scanOptions;
        private AsyncDirectoryScanningOptimizer.OptimizationConfig optimizationConfig;
        private PerformanceConfiguration performanceConfig;
        private ResourceConfiguration resourceConfig;

        public ConfigurationProfile(String name, String description) {
            this.name = name;
            this.description = description;
            this.scanOptions = new AsyncScanOptions();
            this.optimizationConfig = new AsyncDirectoryScanningOptimizer.OptimizationConfig();
            this.performanceConfig = new PerformanceConfiguration();
            this.resourceConfig = new ResourceConfiguration();
        }

        // Builder pattern methods
        public ConfigurationProfile withScanOptions(AsyncScanOptions options) {
            this.scanOptions = options;
            return this;
        }

        public ConfigurationProfile withOptimizationConfig(AsyncDirectoryScanningOptimizer.OptimizationConfig config) {
            this.optimizationConfig = config;
            return this;
        }

        public ConfigurationProfile withPerformanceConfig(PerformanceConfiguration config) {
            this.performanceConfig = config;
            return this;
        }

        public ConfigurationProfile withResourceConfig(ResourceConfiguration config) {
            this.resourceConfig = config;
            return this;
        }

        // Getters
        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public AsyncScanOptions getScanOptions() {
            return scanOptions;
        }

        public AsyncDirectoryScanningOptimizer.OptimizationConfig getOptimizationConfig() {
            return optimizationConfig;
        }

        public PerformanceConfiguration getPerformanceConfig() {
            return performanceConfig;
        }

        public ResourceConfiguration getResourceConfig() {
            return resourceConfig;
        }

        @Override
        public String toString() {
            return String.format("ConfigurationProfile{name='%s', description='%s'}", name, description);
        }
    }

    /**
     * Performance monitoring configuration.
     */
    public static class PerformanceConfiguration {
        private boolean enableMetrics = true;
        private boolean enableDetailedLogging = false;
        private long metricsUpdateIntervalMs = 5000; // 5 seconds
        private boolean enableAdaptiveTuning = true;
        private double performanceThreshold = 0.8; // 80% threshold
        private boolean enableHealthMonitoring = true;
        private long healthCheckIntervalMs = 30000; // 30 seconds

        // Builder pattern methods
        public PerformanceConfiguration withMetricsEnabled(boolean enabled) {
            this.enableMetrics = enabled;
            return this;
        }

        public PerformanceConfiguration withDetailedLogging(boolean enabled) {
            this.enableDetailedLogging = enabled;
            return this;
        }

        public PerformanceConfiguration withMetricsUpdateInterval(long intervalMs) {
            this.metricsUpdateIntervalMs = Math.max(1000, intervalMs);
            return this;
        }

        public PerformanceConfiguration withAdaptiveTuning(boolean enabled) {
            this.enableAdaptiveTuning = enabled;
            return this;
        }

        public PerformanceConfiguration withPerformanceThreshold(double threshold) {
            this.performanceThreshold = Math.max(0.0, Math.min(1.0, threshold));
            return this;
        }

        public PerformanceConfiguration withHealthMonitoring(boolean enabled) {
            this.enableHealthMonitoring = enabled;
            return this;
        }

        public PerformanceConfiguration withHealthCheckInterval(long intervalMs) {
            this.healthCheckIntervalMs = Math.max(5000, intervalMs);
            return this;
        }

        // Getters
        public boolean isMetricsEnabled() {
            return enableMetrics;
        }

        public boolean isDetailedLoggingEnabled() {
            return enableDetailedLogging;
        }

        public long getMetricsUpdateIntervalMs() {
            return metricsUpdateIntervalMs;
        }

        public boolean isAdaptiveTuningEnabled() {
            return enableAdaptiveTuning;
        }

        public double getPerformanceThreshold() {
            return performanceThreshold;
        }

        public boolean isHealthMonitoringEnabled() {
            return enableHealthMonitoring;
        }

        public long getHealthCheckIntervalMs() {
            return healthCheckIntervalMs;
        }
    }

    /**
     * Resource management configuration.
     */
    public static class ResourceConfiguration {
        private long maxMemoryUsageBytes = Runtime.getRuntime().maxMemory() / 2; // 50% of max
        private int maxThreadPoolSize = Runtime.getRuntime().availableProcessors() * 4;
        private long threadKeepAliveTimeMs = 60000; // 1 minute
        private boolean enableResourceMonitoring = true;
        private long resourceCleanupIntervalMs = 300000; // 5 minutes
        private double memoryPressureThreshold = 0.8; // 80%
        private boolean enableGarbageCollectionTuning = true;

        // Builder pattern methods
        public ResourceConfiguration withMaxMemoryUsage(long bytes) {
            this.maxMemoryUsageBytes = Math.max(1024 * 1024, bytes);
            return this;
        }

        public ResourceConfiguration withMaxThreadPoolSize(int size) {
            this.maxThreadPoolSize = Math.max(1, size);
            return this;
        }

        public ResourceConfiguration withThreadKeepAliveTime(long timeMs) {
            this.threadKeepAliveTimeMs = Math.max(1000, timeMs);
            return this;
        }

        public ResourceConfiguration withResourceMonitoring(boolean enabled) {
            this.enableResourceMonitoring = enabled;
            return this;
        }

        public ResourceConfiguration withResourceCleanupInterval(long intervalMs) {
            this.resourceCleanupIntervalMs = Math.max(10000, intervalMs);
            return this;
        }

        public ResourceConfiguration withMemoryPressureThreshold(double threshold) {
            this.memoryPressureThreshold = Math.max(0.0, Math.min(1.0, threshold));
            return this;
        }

        public ResourceConfiguration withGarbageCollectionTuning(boolean enabled) {
            this.enableGarbageCollectionTuning = enabled;
            return this;
        }

        // Getters
        public long getMaxMemoryUsageBytes() {
            return maxMemoryUsageBytes;
        }

        public int getMaxThreadPoolSize() {
            return maxThreadPoolSize;
        }

        public long getThreadKeepAliveTimeMs() {
            return threadKeepAliveTimeMs;
        }

        public boolean isResourceMonitoringEnabled() {
            return enableResourceMonitoring;
        }

        public long getResourceCleanupIntervalMs() {
            return resourceCleanupIntervalMs;
        }

        public double getMemoryPressureThreshold() {
            return memoryPressureThreshold;
        }

        public boolean isGarbageCollectionTuningEnabled() {
            return enableGarbageCollectionTuning;
        }
    }

    /**
     * Runtime configuration overrides.
     */
    public static class RuntimeConfiguration {
        private final Map<String, String> overrides = new ConcurrentHashMap<>();

        public void setOverride(String key, String value) {
            overrides.put(key, value);
            logger.debug("Set runtime override: {} = {}", key, value);
        }

        public String getOverride(String key) {
            return overrides.get(key);
        }

        public String getOverride(String key, String defaultValue) {
            return overrides.getOrDefault(key, defaultValue);
        }

        public void removeOverride(String key) {
            overrides.remove(key);
            logger.debug("Removed runtime override: {}", key);
        }

        public void clearOverrides() {
            overrides.clear();
            logger.debug("Cleared all runtime overrides");
        }

        public Map<String, String> getAllOverrides() {
            return new ConcurrentHashMap<>(overrides);
        }
    }

    /**
     * Creates a new AsyncScannerConfiguration with default profiles.
     */
    public AsyncScannerConfiguration() {
        initializeDefaultProfiles();
        loadConfiguration();
    }

    /**
     * Initializes default configuration profiles.
     */
    private void initializeDefaultProfiles() {
        // High performance profile
        profiles.put("high-performance", new ConfigurationProfile("high-performance",
                "Optimized for maximum throughput with high resource usage")
                .withScanOptions(new AsyncScanOptions()
                        .withParallelism(Runtime.getRuntime().availableProcessors() * 2)
                        .withBatchSize(200)
                        .withStreamingEnabled(true)
                        .withPrefetchingEnabled(true)
                        .withPrefetchDepth(5)
                        .withZeroCopyEnabled(true))
                .withOptimizationConfig(new AsyncDirectoryScanningOptimizer.OptimizationConfig()
                        .withPrefetching(true)
                        .withPrediction(true)
                        .withMemoryOptimization(true)
                        .withNumaAwareness(true)
                        .withAdaptiveSizing(true))
                .withPerformanceConfig(new PerformanceConfiguration()
                        .withMetricsEnabled(true)
                        .withAdaptiveTuning(true)
                        .withHealthMonitoring(true))
                .withResourceConfig(new ResourceConfiguration()
                        .withMaxMemoryUsage((long) (Runtime.getRuntime().maxMemory() * 0.8))
                        .withMaxThreadPoolSize(Runtime.getRuntime().availableProcessors() * 4)));

        // Low resource profile
        profiles.put("low-resource", new ConfigurationProfile("low-resource",
                "Optimized for minimal resource usage with reduced performance")
                .withScanOptions(new AsyncScanOptions()
                        .withParallelism(Math.max(1, Runtime.getRuntime().availableProcessors() / 2))
                        .withBatchSize(50)
                        .withStreamingEnabled(false)
                        .withPrefetchingEnabled(false)
                        .withZeroCopyEnabled(false))
                .withOptimizationConfig(new AsyncDirectoryScanningOptimizer.OptimizationConfig()
                        .withPrefetching(false)
                        .withPrediction(false)
                        .withMemoryOptimization(true)
                        .withNumaAwareness(false)
                        .withAdaptiveSizing(false))
                .withPerformanceConfig(new PerformanceConfiguration()
                        .withMetricsEnabled(false)
                        .withDetailedLogging(false)
                        .withAdaptiveTuning(false))
                .withResourceConfig(new ResourceConfiguration()
                        .withMaxMemoryUsage((long) (Runtime.getRuntime().maxMemory() * 0.2))
                        .withMaxThreadPoolSize(Math.max(1, Runtime.getRuntime().availableProcessors() / 2))));

        // Balanced profile (default)
        profiles.put("balanced", new ConfigurationProfile("balanced",
                "Balanced performance and resource usage")
                .withScanOptions(new AsyncScanOptions()
                        .withParallelism(Runtime.getRuntime().availableProcessors())
                        .withBatchSize(100)
                        .withStreamingEnabled(true)
                        .withPrefetchingEnabled(true)
                        .withPrefetchDepth(2))
                .withOptimizationConfig(new AsyncDirectoryScanningOptimizer.OptimizationConfig()
                        .withPrefetching(true)
                        .withPrediction(true)
                        .withMemoryOptimization(true)
                        .withNumaAwareness(false)
                        .withAdaptiveSizing(true))
                .withPerformanceConfig(new PerformanceConfiguration()
                        .withMetricsEnabled(true)
                        .withAdaptiveTuning(true)
                        .withHealthMonitoring(true))
                .withResourceConfig(new ResourceConfiguration()
                        .withMaxMemoryUsage((long) (Runtime.getRuntime().maxMemory() * 0.5))
                        .withMaxThreadPoolSize(Runtime.getRuntime().availableProcessors() * 2)));

        // Set default profile
        activeProfile.set(profiles.get("balanced"));
        runtimeConfig.set(new RuntimeConfiguration());
    }

    /**
     * Loads configuration from file and system properties.
     */
    private void loadConfiguration() {
        try {
            // Load from properties file
            loadFromFile();

            // Load from system properties
            loadFromSystemProperties();

            logger.info("Configuration loaded successfully");

        } catch (Exception e) {
            logger.warn("Failed to load configuration, using defaults", e);
        }
    }

    /**
     * Loads configuration from properties file.
     */
    private void loadFromFile() {
        try (FileInputStream fis = new FileInputStream(DEFAULT_CONFIG_FILE)) {
            Properties props = new Properties();
            props.load(fis);

            // Apply configuration from file
            String profileName = props.getProperty("scanner.profile", "balanced");
            setActiveProfile(profileName);

            logger.debug("Loaded configuration from file: {}", DEFAULT_CONFIG_FILE);

        } catch (IOException e) {
            logger.debug("Configuration file not found, using defaults: {}", DEFAULT_CONFIG_FILE);
        }
    }

    /**
     * Loads configuration from system properties.
     */
    private void loadFromSystemProperties() {
        String profileName = System.getProperty("justsyncit.scanner.profile");
        if (profileName != null) {
            setActiveProfile(profileName);
            logger.debug("Set profile from system property: {}", profileName);
        }

        // Load runtime overrides from system properties
        RuntimeConfiguration runtime = runtimeConfig.get();
        System.getProperties().forEach((key, value) -> {
            if (key.toString().startsWith("justsyncit.scanner.override.")) {
                String overrideKey = key.toString().substring("justsyncit.scanner.override.".length());
                runtime.setOverride(overrideKey, value.toString());
            }
        });
    }

    /**
     * Saves current configuration to file.
     */
    public void saveConfiguration() {
        try (FileOutputStream fos = new FileOutputStream(DEFAULT_CONFIG_FILE)) {
            Properties props = new Properties();

            // Save active profile
            ConfigurationProfile profile = activeProfile.get();
            if (profile != null) {
                props.setProperty("scanner.profile", profile.getName());
            }

            // Save runtime overrides
            RuntimeConfiguration runtime = runtimeConfig.get();
            if (runtime != null) {
                runtime.getAllOverrides().forEach((key, value) -> {
                    props.setProperty("scanner.override." + key, value);
                });
            }

            props.store(fos, "Async Scanner Configuration");
            logger.info("Configuration saved to file: {}", DEFAULT_CONFIG_FILE);

        } catch (IOException e) {
            logger.error("Failed to save configuration to file: {}", DEFAULT_CONFIG_FILE, e);
        }
    }

    /**
     * Sets the active configuration profile.
     *
     * @param profileName name of the profile to activate
     * @return true if profile was found and activated, false otherwise
     */
    public final boolean setActiveProfile(String profileName) {
        ConfigurationProfile profile = profiles.get(profileName);
        if (profile != null) {
            activeProfile.set(profile);
            logger.info("Activated configuration profile: {} - {}", profileName, profile.getDescription());
            return true;
        } else {
            logger.warn("Configuration profile not found: {}", profileName);
            return false;
        }
    }

    /**
     * Gets the active configuration profile.
     *
     * @return active profile, or null if none is set
     */
    public ConfigurationProfile getActiveProfile() {
        return activeProfile.get();
    }

    /**
     * Gets a specific configuration profile.
     *
     * @param profileName name of the profile
     * @return the profile, or null if not found
     */
    public ConfigurationProfile getProfile(String profileName) {
        return profiles.get(profileName);
    }

    /**
     * Gets all available configuration profiles.
     *
     * @return map of profile names to profiles
     */
    public Map<String, ConfigurationProfile> getAllProfiles() {
        return new ConcurrentHashMap<>(profiles);
    }

    /**
     * Adds a new configuration profile.
     *
     * @param profile the profile to add
     */
    public void addProfile(ConfigurationProfile profile) {
        profiles.put(profile.getName(), profile);
        logger.info("Added configuration profile: {} - {}", profile.getName(), profile.getDescription());
    }

    /**
     * Removes a configuration profile.
     *
     * @param profileName name of the profile to remove
     * @return true if profile was removed, false if not found
     */
    public boolean removeProfile(String profileName) {
        ConfigurationProfile removed = profiles.remove(profileName);
        if (removed != null) {
            logger.info("Removed configuration profile: {} - {}", profileName, removed.getDescription());

            // If the active profile was removed, switch to balanced
            if (removed == activeProfile.get()) {
                setActiveProfile("balanced");
            }
            return true;
        }
        return false;
    }

    /**
     * Gets the runtime configuration.
     *
     * @return runtime configuration
     */
    public RuntimeConfiguration getRuntimeConfiguration() {
        return runtimeConfig.get();
    }

    /**
     * Applies runtime configuration overrides to scan options.
     *
     * @param baseOptions base scan options
     * @return options with runtime overrides applied
     */
    public AsyncScanOptions applyRuntimeOverrides(AsyncScanOptions baseOptions) {
        AsyncScanOptions result = new AsyncScanOptions(baseOptions);
        RuntimeConfiguration runtime = runtimeConfig.get();

        if (runtime == null) {
            return result;
        }

        // Apply common overrides
        String parallelismOverride = runtime.getOverride("parallelism");
        if (parallelismOverride != null) {
            try {
                result = result.withParallelism(Integer.parseInt(parallelismOverride));
            } catch (NumberFormatException e) {
                logger.warn("Invalid parallelism override: {}", parallelismOverride);
            }
        }

        String batchSizeOverride = runtime.getOverride("batchSize");
        if (batchSizeOverride != null) {
            try {
                result = result.withBatchSize(Integer.parseInt(batchSizeOverride));
            } catch (NumberFormatException e) {
                logger.warn("Invalid batch size override: {}", batchSizeOverride);
            }
        }

        String prefetchingOverride = runtime.getOverride("prefetching");
        if (prefetchingOverride != null) {
            result = result.withPrefetchingEnabled(Boolean.parseBoolean(prefetchingOverride));
        }

        return result;
    }

    /**
     * Gets configuration summary.
     *
     * @return configuration summary string
     */
    public String getConfigurationSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Async Scanner Configuration ===\n");

        ConfigurationProfile active = activeProfile.get();
        if (active != null) {
            sb.append("Active Profile: ").append(active.getName())
                    .append(" - ").append(active.getDescription()).append("\n");
        }

        sb.append("Available Profiles: ");
        profiles.keySet().forEach(name -> sb.append(name).append(", "));
        sb.setLength(sb.length() - 2); // Remove last comma
        sb.append("\n");

        RuntimeConfiguration runtime = runtimeConfig.get();
        if (runtime != null && !runtime.getAllOverrides().isEmpty()) {
            sb.append("Runtime Overrides:\n");
            runtime.getAllOverrides()
                    .forEach((key, value) -> sb.append("  ").append(key).append(" = ").append(value).append("\n"));
        }

        return sb.toString();
    }

    /**
     * Validates configuration.
     *
     * @return true if configuration is valid, false otherwise
     */
    public boolean validateConfiguration() {
        try {
            ConfigurationProfile active = activeProfile.get();
            if (active == null) {
                logger.error("No active configuration profile");
                return false;
            }

            // Validate scan options
            AsyncScanOptions scanOptions = active.getScanOptions();
            if (scanOptions.getParallelism() <= 0) {
                logger.error("Invalid parallelism: {}", scanOptions.getParallelism());
                return false;
            }

            if (scanOptions.getBatchSize() <= 0) {
                logger.error("Invalid batch size: {}", scanOptions.getBatchSize());
                return false;
            }

            // Validate resource configuration
            ResourceConfiguration resourceConfig = active.getResourceConfig();
            if (resourceConfig.getMaxMemoryUsageBytes() <= 0) {
                logger.error("Invalid max memory usage: {}", resourceConfig.getMaxMemoryUsageBytes());
                return false;
            }

            if (resourceConfig.getMaxThreadPoolSize() <= 0) {
                logger.error("Invalid max thread pool size: {}", resourceConfig.getMaxThreadPoolSize());
                return false;
            }

            logger.debug("Configuration validation passed");
            return true;

        } catch (Exception e) {
            logger.error("Configuration validation failed", e);
            return false;
        }
    }
}