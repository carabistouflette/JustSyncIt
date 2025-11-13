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
 * SIMD detector for ARM architectures.
 * Follows Single Responsibility Principle by focusing only on ARM detection.
 */
public class ArmSimdDetector implements SimdDetector {

    /** Logger instance. */
    private static final Logger logger = LoggerFactory.getLogger(ArmSimdDetector.class);

    @Override
    public boolean supports(String architecture) {
        return architecture.contains("aarch64") || architecture.contains("arm64");
    }

    @Override
    public SimdInfo detectCapabilities() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

        SimdInfoImpl.Builder builder = new SimdInfoImpl.Builder()
                .setOperatingSystem(osName)
                .setArchitecture(System.getProperty("os.arch", "").toLowerCase(Locale.ROOT))
                .setJavaVersion(System.getProperty("java.version"));

        // Check for NEON support
        if (hasNeonSupport()) {
            builder.setNeonSupported(true)
                    .setBestSimdInstructionSet("NEON");
            logger.info("ARM NEON support detected");
        } else {
            logger.info("No ARM SIMD extensions detected");
            builder.setBestSimdInstructionSet("NONE");
        }

        return builder.build();
    }

    /**
     * Checks for ARM NEON support.
     */
    private boolean hasNeonSupport() {
        try {
            String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (osName.contains("linux")) {
                String cpuInfo = new String(java.nio.file.Files.readAllBytes(
                        java.nio.file.Paths.get("/proc/cpuinfo")), java.nio.charset.StandardCharsets.UTF_8);
                return cpuInfo.contains("neon");
            }
        } catch (Exception e) {
            logger.debug("Could not check NEON support via /proc/cpuinfo", e);
        }
        return false;
    }
}