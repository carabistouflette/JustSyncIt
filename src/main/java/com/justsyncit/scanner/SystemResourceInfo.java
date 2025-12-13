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

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

/**
 * System resource information for thread pool optimization.
 * Provides CPU, memory, and NUMA information.
 */
public class SystemResourceInfo {

    private final int availableProcessors;
    private final long totalMemory;
    private final long maxMemory;
    private final String osName;
    private final String osVersion;
    private final String osArch;
    private final boolean isNumaAware;
    private final int numaNodes;

    /**
     * Creates a new SystemResourceInfo.
     */
    public SystemResourceInfo() {
        this.availableProcessors = Runtime.getRuntime().availableProcessors();
        this.totalMemory = Runtime.getRuntime().totalMemory();
        this.maxMemory = Runtime.getRuntime().maxMemory();

        // Get OS information
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        this.osName = osBean.getName();
        this.osVersion = osBean.getVersion();
        this.osArch = osBean.getArch();

        // Detect NUMA support (simplified)
        this.isNumaAware = detectNumaSupport();
        this.numaNodes = isNumaAware ? detectNumaNodes() : 1;
    }

    /**
     * Detects NUMA support (simplified detection).
     */
    private boolean detectNumaSupport() {
        // Check for NUMA-related system properties
        String numaNodeCount = System.getProperty("numa.node.count");
        if (numaNodeCount != null) {
            try {
                int nodes = Integer.parseInt(numaNodeCount);
                return nodes > 1;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        // Check for NUMA in OS name
        return osName != null && osName.toLowerCase(java.util.Locale.ROOT).contains("linux");
    }

    /**
     * Detects number of NUMA nodes (simplified).
     */
    private int detectNumaNodes() {
        try {
            // Try to read from system using a shell command
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", "lscpu | grep 'NUMA node(s)'");
            Process process = pb.start();
            process.waitFor();
            // Simplified - just return processor count for now
            return availableProcessors;
        } catch (Exception e) {
            return availableProcessors;
        }
    }

    // Getters
    public int getAvailableProcessors() {
        return availableProcessors;
    }

    public long getTotalMemory() {
        return totalMemory;
    }

    public long getMaxMemory() {
        return maxMemory;
    }

    public String getOsName() {
        return osName;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public String getOsArch() {
        return osArch;
    }

    public boolean isNumaAware() {
        return isNumaAware;
    }

    public int getNumaNodes() {
        return numaNodes;
    }

    @Override
    public String toString() {
        return String.format(
                "SystemResourceInfo{processors=%d, totalMemory=%dMB, maxMemory=%dMB, "
                        + "os=%s %s, arch=%s, numa=%b, numaNodes=%d}",
                availableProcessors, totalMemory / 1024 / 1024, maxMemory / 1024 / 1024,
                osName, osVersion, osArch, isNumaAware, numaNodes);
    }
}