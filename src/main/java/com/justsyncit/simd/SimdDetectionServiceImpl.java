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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Implementation of SimdDetectionService that uses multiple detectors.
 * Follows Open/Closed Principle by using a list of detectors that can be extended.
 */
public class SimdDetectionServiceImpl implements SimdDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(SimdDetectionServiceImpl.class);
    
    private volatile SimdInfo cachedSimdInfo;
    private final Object cacheLock = new Object();
    private final List<SimdDetector> detectors;

    /**
     * Creates a new SimdDetectionServiceImpl with default detectors.
     */
    public SimdDetectionServiceImpl() {
        this.detectors = createDefaultDetectors();
    }

    /**
     * Creates a new SimdDetectionServiceImpl with custom detectors.
     *
     * @param detectors list of detectors to use
     */
    public SimdDetectionServiceImpl(List<SimdDetector> detectors) {
        this.detectors = new ArrayList<>(detectors);
    }

    @Override
    public SimdInfo getSimdInfo() {
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
     * Detects SIMD capabilities using available detectors.
     *
     * @return SimdInfo with detected capabilities
     */
    private SimdInfo detectSimdCapabilities() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        
        logger.debug("Detecting SIMD capabilities for architecture: {}", arch);

        // Find a detector that supports this architecture
        for (SimdDetector detector : detectors) {
            if (detector.supports(arch)) {
                try {
                    SimdInfo info = detector.detectCapabilities();
                    logger.info("SIMD detection completed using {}: {}", detector.getClass().getSimpleName(), info);
                    return info;
                } catch (Exception e) {
                    logger.warn("Detector {} failed", detector.getClass().getSimpleName(), e);
                }
            }
        }

        // Fallback to no SIMD support
        logger.info("No SIMD detector found for architecture: {}", arch);
        return new SimdInfoImpl.Builder()
                .setArchitecture(arch)
                .setOperatingSystem(System.getProperty("os.name", "").toLowerCase(Locale.ROOT))
                .setJavaVersion(System.getProperty("java.version"))
                .setBestSimdInstructionSet("NONE")
                .build();
    }

    /**
     * Creates the default list of detectors.
     *
     * @return list of default detectors
     */
    private List<SimdDetector> createDefaultDetectors() {
        List<SimdDetector> defaultDetectors = new ArrayList<>();
        defaultDetectors.add(new X86SimdDetector());
        defaultDetectors.add(new ArmSimdDetector());
        return defaultDetectors;
    }
}