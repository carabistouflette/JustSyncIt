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

package com.justsyncit.simd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * SIMD detector for x86/x64 architectures.
 * Follows Single Responsibility Principle by focusing only on x86 detection.
 */
public class X86SimdDetector implements SimdDetector {

    private static final Logger logger = LoggerFactory.getLogger(X86SimdDetector.class);

    @Override
    public boolean supports(String architecture) {
        return architecture.contains("amd64") || architecture.contains("x86_64");
    }

    @Override
    public SimdInfo detectCapabilities() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        
        SimdInfoImpl.Builder builder = new SimdInfoImpl.Builder()
                .setOperatingSystem(osName)
                .setArchitecture(System.getProperty("os.arch", "").toLowerCase(Locale.ROOT))
                .setJavaVersion(System.getProperty("java.version"));

        // Try to detect SIMD capabilities
        if (hasAvx512Support()) {
            builder.setAvx512Supported(true)
                   .setBestSimdInstructionSet("AVX-512");
            logger.info("AVX-512 support detected");
        } else if (hasAvx2Support()) {
            builder.setAvx2Supported(true)
                   .setBestSimdInstructionSet("AVX2");
            logger.info("AVX2 support detected");
        } else if (hasAvxSupport()) {
            builder.setAvxSupported(true)
                   .setBestSimdInstructionSet("AVX");
            logger.info("AVX support detected");
        } else if (hasSse4Support()) {
            builder.setSse4Supported(true)
                   .setBestSimdInstructionSet("SSE4");
            logger.info("SSE4 support detected");
        } else if (hasSse2Support()) {
            builder.setSse2Supported(true)
                   .setBestSimdInstructionSet("SSE2");
            logger.info("SSE2 support detected");
        } else {
            logger.info("No SIMD extensions detected, falling back to scalar implementation");
            builder.setBestSimdInstructionSet("NONE");
        }

        return builder.build();
    }

    /**
     * Checks for AVX-512 support on Linux systems.
     */
    private boolean hasAvx512Support() {
        try {
            String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (osName.contains("linux")) {
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
    private boolean hasAvx2Support() {
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
    private boolean hasAvxSupport() {
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
    private boolean hasSse4Support() {
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
    private boolean hasSse2Support() {
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
}