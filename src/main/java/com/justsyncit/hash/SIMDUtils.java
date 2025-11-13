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

package com.justsyncit.hash;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Utility class for detecting SIMD instruction set support on the current platform.
 * Provides runtime detection of AVX2, AVX-512, and other SIMD capabilities.
 */
public final class SIMDUtils {

    private static final Logger logger = LoggerFactory.getLogger(SIMDUtils.class);
    
    private static volatile SimdInfo cachedSimdInfo;
    private static final Object cacheLock = new Object();

    // Private constructor to prevent instantiation
    private SIMDUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Gets SIMD information for the current platform.
     * Results are cached for performance.
     *
     * @return SimdInfo containing detected SIMD capabilities
     */
    public static SimdInfo getSimdInfo() {
        if (cachedSimdInfo == null) {
            synchronized (cacheLock) {
                if (cachedSimdInfo == null) {
                    cachedSimdInfo = detectSimdCapabilities();
                }
            }
        }
        return cachedSimdInfo;
    }

    /**
     * Detects SIMD capabilities on the current platform.
     *
     * @return SimdInfo with detected capabilities
     */
    private static SimdInfo detectSimdCapabilities() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        
        logger.debug("Detecting SIMD capabilities for OS: {}, Arch: {}", osName, arch);

        SimdInfo.Builder builder = new SimdInfo.Builder();

        // Set basic platform info
        builder.setOperatingSystem(osName)
               .setArchitecture(arch)
               .setJavaVersion(System.getProperty("java.version"));

        // Detect CPU features based on architecture
        if (arch.contains("amd64") || arch.contains("x86_64")) {
            detectX86Features(builder);
        } else if (arch.contains("aarch64") || arch.contains("arm64")) {
            detectArmFeatures(builder);
        } else {
            logger.info("Unsupported architecture for SIMD detection: {}", arch);
            builder.setBestSimdInstructionSet("NONE");
        }

        SimdInfo info = builder.build();
        logger.info("SIMD detection completed: {}", info);
        return info;
    }

    /**
     * Detects x86/x64 SIMD features.
     */
    private static void detectX86Features(SimdInfo.Builder builder) {
        try {
            // Try to use CPUID through JNI if available
            // For now, we'll use system properties and heuristics
            
            // Check for AVX-512 support (Linux-specific check)
            if (hasAvx512Support()) {
                builder.setAvx512Supported(true)
                       .setBestSimdInstructionSet("AVX-512");
                logger.info("AVX-512 support detected");
                return;
            }

            // Check for AVX2 support
            if (hasAvx2Support()) {
                builder.setAvx2Supported(true)
                       .setBestSimdInstructionSet("AVX2");
                logger.info("AVX2 support detected");
                return;
            }

            // Check for AVX support
            if (hasAvxSupport()) {
                builder.setAvxSupported(true)
                       .setBestSimdInstructionSet("AVX");
                logger.info("AVX support detected");
                return;
            }

            // Check for SSE4.1/4.2 support
            if (hasSse4Support()) {
                builder.setSse4Supported(true)
                       .setBestSimdInstructionSet("SSE4");
                logger.info("SSE4 support detected");
                return;
            }

            // Check for basic SSE2 support (most x86 CPUs have this)
            if (hasSse2Support()) {
                builder.setSse2Supported(true)
                       .setBestSimdInstructionSet("SSE2");
                logger.info("SSE2 support detected");
                return;
            }

            logger.info("No SIMD extensions detected, falling back to scalar implementation");
            builder.setBestSimdInstructionSet("NONE");

        } catch (Exception e) {
            logger.warn("Error during SIMD detection", e);
            builder.setBestSimdInstructionSet("NONE");
        }
    }

    /**
     * Detects ARM SIMD features.
     */
    private static void detectArmFeatures(SimdInfo.Builder builder) {
        try {
            // Check for NEON support (most modern ARM CPUs have this)
            if (hasNeonSupport()) {
                builder.setNeonSupported(true)
                       .setBestSimdInstructionSet("NEON");
                logger.info("ARM NEON support detected");
                return;
            }

            logger.info("No ARM SIMD extensions detected");
            builder.setBestSimdInstructionSet("NONE");

        } catch (Exception e) {
            logger.warn("Error during ARM SIMD detection", e);
            builder.setBestSimdInstructionSet("NONE");
        }
    }

    /**
     * Checks for AVX-512 support on Linux systems.
     */
    private static boolean hasAvx512Support() {
        try {
            String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (osName.contains("linux")) {
                // Check /proc/cpuinfo for AVX-512 flags
                String cpuInfo = new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get("/proc/cpuinfo")));
                return cpuInfo.contains("avx512f") && 
                       cpuInfo.contains("avx512bw") && 
                       cpuInfo.contains("avx512vl");
            }
        } catch (Exception e) {
            logger.debug("Could not check AVX-512 support via /proc/cpuinfo", e);
        }
        return false;
    }

    /**
     * Checks for AVX2 support.
     */
    private static boolean hasAvx2Support() {
        try {
            String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (osName.contains("linux")) {
                String cpuInfo = new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get("/proc/cpuinfo")));
                return cpuInfo.contains("avx2");
            }
        } catch (Exception e) {
            logger.debug("Could not check AVX2 support via /proc/cpuinfo", e);
        }
        return false;
    }

    /**
     * Checks for AVX support.
     */
    private static boolean hasAvxSupport() {
        try {
            String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (osName.contains("linux")) {
                String cpuInfo = new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get("/proc/cpuinfo")));
                return cpuInfo.contains("avx");
            }
        } catch (Exception e) {
            logger.debug("Could not check AVX support via /proc/cpuinfo", e);
        }
        return false;
    }

    /**
     * Checks for SSE4.1/4.2 support.
     */
    private static boolean hasSse4Support() {
        try {
            String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (osName.contains("linux")) {
                String cpuInfo = new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get("/proc/cpuinfo")));
                return cpuInfo.contains("sse4_1") || cpuInfo.contains("sse4_2");
            }
        } catch (Exception e) {
            logger.debug("Could not check SSE4 support via /proc/cpuinfo", e);
        }
        return false;
    }

    /**
     * Checks for SSE2 support.
     */
    private static boolean hasSse2Support() {
        try {
            String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (osName.contains("linux")) {
                String cpuInfo = new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get("/proc/cpuinfo")));
                return cpuInfo.contains("sse2");
            }
        } catch (Exception e) {
            logger.debug("Could not check SSE2 support via /proc/cpuinfo", e);
        }
        return false;
    }

    /**
     * Checks for ARM NEON support.
     */
    private static boolean hasNeonSupport() {
        try {
            String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (osName.contains("linux")) {
                String cpuInfo = new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get("/proc/cpuinfo")));
                return cpuInfo.contains("neon");
            }
        } catch (Exception e) {
            logger.debug("Could not check NEON support via /proc/cpuinfo", e);
        }
        return false;
    }

    /**
     * Immutable class containing SIMD capability information.
     */
    public static final class SimdInfo {
        private final String operatingSystem;
        private final String architecture;
        private final String javaVersion;
        private final boolean avx512Supported;
        private final boolean avx2Supported;
        private final boolean avxSupported;
        private final boolean sse4Supported;
        private final boolean sse2Supported;
        private final boolean neonSupported;
        private final String bestSimdInstructionSet;

        private SimdInfo(Builder builder) {
            this.operatingSystem = builder.operatingSystem;
            this.architecture = builder.architecture;
            this.javaVersion = builder.javaVersion;
            this.avx512Supported = builder.avx512Supported;
            this.avx2Supported = builder.avx2Supported;
            this.avxSupported = builder.avxSupported;
            this.sse4Supported = builder.sse4Supported;
            this.sse2Supported = builder.sse2Supported;
            this.neonSupported = builder.neonSupported;
            this.bestSimdInstructionSet = builder.bestSimdInstructionSet;
        }

        public String getOperatingSystem() { return operatingSystem; }
        public String getArchitecture() { return architecture; }
        public String getJavaVersion() { return javaVersion; }
        public boolean isAvx512Supported() { return avx512Supported; }
        public boolean isAvx2Supported() { return avx2Supported; }
        public boolean isAvxSupported() { return avxSupported; }
        public boolean isSse4Supported() { return sse4Supported; }
        public boolean isSse2Supported() { return sse2Supported; }
        public boolean isNeonSupported() { return neonSupported; }
        public String getBestSimdInstructionSet() { return bestSimdInstructionSet; }

        public boolean hasSimdSupport() {
            return !"NONE".equals(bestSimdInstructionSet);
        }

        @Override
        public String toString() {
            return String.format("SimdInfo{os='%s', arch='%s', bestSIMD='%s', AVX512=%s, AVX2=%s, AVX=%s, SSE4=%s, SSE2=%s, NEON=%s}",
                operatingSystem, architecture, bestSimdInstructionSet, 
                avx512Supported, avx2Supported, avxSupported, sse4Supported, sse2Supported, neonSupported);
        }

        /**
         * Builder for SimdInfo.
         */
        public static final class Builder {
            private String operatingSystem;
            private String architecture;
            private String javaVersion;
            private boolean avx512Supported;
            private boolean avx2Supported;
            private boolean avxSupported;
            private boolean sse4Supported;
            private boolean sse2Supported;
            private boolean neonSupported;
            private String bestSimdInstructionSet = "NONE";

            public Builder setOperatingSystem(String operatingSystem) {
                this.operatingSystem = operatingSystem;
                return this;
            }

            public Builder setArchitecture(String architecture) {
                this.architecture = architecture;
                return this;
            }

            public Builder setJavaVersion(String javaVersion) {
                this.javaVersion = javaVersion;
                return this;
            }

            public Builder setAvx512Supported(boolean avx512Supported) {
                this.avx512Supported = avx512Supported;
                return this;
            }

            public Builder setAvx2Supported(boolean avx2Supported) {
                this.avx2Supported = avx2Supported;
                return this;
            }

            public Builder setAvxSupported(boolean avxSupported) {
                this.avxSupported = avxSupported;
                return this;
            }

            public Builder setSse4Supported(boolean sse4Supported) {
                this.sse4Supported = sse4Supported;
                return this;
            }

            public Builder setSse2Supported(boolean sse2Supported) {
                this.sse2Supported = sse2Supported;
                return this;
            }

            public Builder setNeonSupported(boolean neonSupported) {
                this.neonSupported = neonSupported;
                return this;
            }

            public Builder setBestSimdInstructionSet(String bestSimdInstructionSet) {
                this.bestSimdInstructionSet = bestSimdInstructionSet;
                return this;
            }

            public SimdInfo build() {
                return new SimdInfo(this);
            }
        }
    }
}