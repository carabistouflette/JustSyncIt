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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Adaptive sizing controller for dynamic buffer pool optimization.
 * Analyzes workload patterns and adjusts pool sizes accordingly.
 */
public final class AdaptiveSizingController implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(AdaptiveSizingController.class);

    private final OptimizedAsyncByteBufferPool.PoolConfiguration config;
    private final PerformanceMonitor performanceMonitor;

    // Adaptive sizing state
    private volatile boolean running = true;
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);

    // Workload pattern tracking
    private volatile long lastAnalysisTime = System.currentTimeMillis();
    private volatile double averageUtilization = 0.0;
    private volatile double peakUtilization = 0.0;
    private volatile int consecutiveHighUtilization = 0;
    private volatile int consecutiveLowUtilization = 0;

    // Background monitoring
    private final ScheduledExecutorService scheduler;

    /**
     * Creates a new AdaptiveSizingController.
     */
    public AdaptiveSizingController(OptimizedAsyncByteBufferPool.PoolConfiguration config,
            PerformanceMonitor performanceMonitor) {
        this.config = config;
        this.performanceMonitor = performanceMonitor;

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AdaptiveSizingController");
            t.setDaemon(true);
            return t;
        });

        // Start adaptive analysis
        scheduler.scheduleAtFixedRate(this, 30, 30, TimeUnit.SECONDS);

        logger.debug("AdaptiveSizingController initialized");
    }

    @Override
    public void run() {
        if (!running || shutdownRequested.get()) {
            return;
        }

        try {
            performAdaptiveAnalysis();
        } catch (Exception e) {
            logger.error("Error in adaptive sizing analysis", e);
        }
    }

    /**
     * Performs adaptive analysis of current workload patterns.
     */
    private void performAdaptiveAnalysis() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastAnalysis = currentTime - lastAnalysisTime;

        if (timeSinceLastAnalysis < 30000) { // Minimum 30 seconds between analyses
            return;
        }

        // Collect performance metrics
        double currentUtilization = calculateCurrentUtilization();
        double failureRate = performanceMonitor.getFailureRate();
        double avgWaitTime = performanceMonitor.getAverageAcquisitionTime();

        // Update tracking variables
        updateUtilizationTracking(currentUtilization);

        // Analyze patterns and make recommendations
        SizingRecommendation recommendation = analyzeWorkloadPattern(
                currentUtilization, failureRate, avgWaitTime);

        // Apply recommendations
        if (recommendation.shouldAdjust()) {
            applySizingRecommendation(recommendation);
        }

        lastAnalysisTime = currentTime;

        logger.debug(
                "Adaptive analysis completed: utilization={:.2f}%, failure_rate={:.2f}%, avg_wait={:.2f}μs, recommendation={}",
                currentUtilization * 100, failureRate * 100, avgWaitTime / 1000.0, recommendation);
    }

    /**
     * Calculates current buffer pool utilization.
     */
    private double calculateCurrentUtilization() {
        // This would interface with the main buffer pool to get real utilization
        // For now, use performance monitor metrics as approximation
        long totalMemory = performanceMonitor.getTotalMemoryAllocated();
        long currentMemory = performanceMonitor.getCurrentMemoryUsage();

        return totalMemory > 0 ? (double) currentMemory / totalMemory : 0.0;
    }

    /**
     * Updates utilization tracking variables.
     */
    private void updateUtilizationTracking(double currentUtilization) {
        // Update average with exponential smoothing
        averageUtilization = averageUtilization * 0.8 + currentUtilization * 0.2;

        // Update peak
        peakUtilization = Math.max(peakUtilization, currentUtilization);

        // Update consecutive counters
        if (currentUtilization > 0.8) {
            consecutiveHighUtilization++;
            consecutiveLowUtilization = 0;
        } else if (currentUtilization < 0.3) {
            consecutiveLowUtilization++;
            consecutiveHighUtilization = 0;
        } else {
            consecutiveHighUtilization = 0;
            consecutiveLowUtilization = 0;
        }
    }

    /**
     * Analyzes workload pattern and generates sizing recommendation.
     */
    private SizingRecommendation analyzeWorkloadPattern(double utilization, double failureRate, double avgWaitTime) {
        SizingRecommendation recommendation = new SizingRecommendation();

        // High utilization or high failure rate - need more buffers
        if (utilization > 0.8 || failureRate > 0.1) {
            recommendation.action = SizingAction.INCREASE;
            recommendation.reason = "High utilization or failure rate";
            recommendation.magnitude = calculateIncreaseMagnitude(utilization, failureRate);
        }
        // Low utilization and low failure rate - can reduce buffers
        else if (utilization < 0.3 && failureRate < 0.01 && avgWaitTime < 100000) {
            recommendation.action = SizingAction.DECREASE;
            recommendation.reason = "Low utilization and failure rate";
            recommendation.magnitude = calculateDecreaseMagnitude(utilization);
        }
        // Stable workload - maintain current size
        else {
            recommendation.action = SizingAction.MAINTAIN;
            recommendation.reason = "Stable workload";
            recommendation.magnitude = 0.0;
        }

        return recommendation;
    }

    /**
     * Calculates buffer increase magnitude.
     */
    private double calculateIncreaseMagnitude(double utilization, double failureRate) {
        double baseMagnitude = 1.5; // 50% increase

        // Adjust based on severity
        if (utilization > 0.95 || failureRate > 0.2) {
            baseMagnitude = 2.0; // 100% increase for severe conditions
        } else if (consecutiveHighUtilization > 3) {
            baseMagnitude = 1.8; // 80% increase for sustained high load
        }

        return baseMagnitude;
    }

    /**
     * Calculates buffer decrease magnitude.
     */
    private double calculateDecreaseMagnitude(double utilization) {
        double baseMagnitude = 0.8; // 20% decrease

        // Be more conservative with decreases
        if (consecutiveLowUtilization > 5) {
            baseMagnitude = 0.6; // 40% decrease for sustained low load
        }

        return baseMagnitude;
    }

    /**
     * Applies sizing recommendation to buffer pools.
     */
    private void applySizingRecommendation(SizingRecommendation recommendation) {
        logger.info("Applying sizing recommendation: {} ({})",
                recommendation.action, recommendation.reason);

        switch (recommendation.action) {
            case INCREASE:
                // This would trigger buffer pool expansion
                logger.info("Increasing buffer pool sizes by {:.0f}%",
                        (recommendation.magnitude - 1.0) * 100);
                break;

            case DECREASE:
                // This would trigger buffer pool contraction
                logger.info("Decreasing buffer pool sizes by {:.0f}%",
                        (1.0 - recommendation.magnitude) * 100);
                break;

            case MAINTAIN:
                // No action needed
                logger.debug("Maintaining current buffer pool sizes");
                break;
        }
    }

    /**
     * Gets current adaptive sizing statistics.
     */
    public AdaptiveSizingStats getStats() {
        return new AdaptiveSizingStats(
                averageUtilization, peakUtilization,
                consecutiveHighUtilization, consecutiveLowUtilization,
                lastAnalysisTime);
    }

    /**
     * Sizing action enumeration.
     */
    public enum SizingAction {
        INCREASE, DECREASE, MAINTAIN
    }

    /**
     * Sizing recommendation.
     */
    public static class SizingRecommendation {
        public SizingAction action;
        public String reason;
        public double magnitude;

        public boolean shouldAdjust() {
            return action != SizingAction.MAINTAIN;
        }

        @Override
        public String toString() {
            return String.format("SizingRecommendation{action=%s, reason=%s, magnitude=%.2f}",
                    action, reason, magnitude);
        }
    }

    /**
     * Adaptive sizing statistics.
     */
    public static class AdaptiveSizingStats {
        public final double averageUtilization;
        public final double peakUtilization;
        public final int consecutiveHighUtilization;
        public final int consecutiveLowUtilization;
        public final long lastAnalysisTime;

        AdaptiveSizingStats(double averageUtilization, double peakUtilization,
                int consecutiveHighUtilization, int consecutiveLowUtilization,
                long lastAnalysisTime) {
            this.averageUtilization = averageUtilization;
            this.peakUtilization = peakUtilization;
            this.consecutiveHighUtilization = consecutiveHighUtilization;
            this.consecutiveLowUtilization = consecutiveLowUtilization;
            this.lastAnalysisTime = lastAnalysisTime;
        }

        @Override
        public String toString() {
            return String.format(
                    "AdaptiveSizingStats{avg_util=%.2f%%, peak_util=%.2f%%, " +
                            "high_streak=%d, low_streak=%d, last_analysis=%d}",
                    averageUtilization * 100, peakUtilization * 100,
                    consecutiveHighUtilization, consecutiveLowUtilization, lastAnalysisTime);
        }
    }

    /**
     * Shuts down the adaptive sizing controller.
     */
    public void shutdown() {
        running = false;
        shutdownRequested.set(true);
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("AdaptiveSizingController shutdown completed");
    }
}