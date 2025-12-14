/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.justsyncit.simd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Unit tests for SimdDetectionServiceImpl.
 */
@DisplayName("SimdDetectionServiceImpl Tests")
class SimdDetectionServiceImplTest {

    @Test
    @Timeout(5)
    @DisplayName("Should detect SIMD capabilities on current platform")
    void shouldDetectSimdCapabilitiesOnCurrentPlatform() {
        SimdDetectionService service = new SimdDetectionServiceImpl();

        SimdInfo info = service.getSimdInfo();

        assertNotNull(info);
        assertNotNull(info.getOperatingSystem());
        assertNotNull(info.getArchitecture());
        assertNotNull(info.getBestSimdInstructionSet());
    }

    @Test
    @Timeout(5)
    @DisplayName("Should cache detection results")
    void shouldCacheDetectionResults() {
        SimdDetectionService service = new SimdDetectionServiceImpl();

        SimdInfo firstCall = service.getSimdInfo();
        SimdInfo secondCall = service.getSimdInfo();

        assertSame(firstCall, secondCall, "Should return cached instance");
    }

    @Test
    @Timeout(5)
    @DisplayName("Should work with custom empty detectors list")
    void shouldWorkWithCustomEmptyDetectorsList() {
        List<SimdDetector> emptyDetectors = new ArrayList<>();
        SimdDetectionService service = new SimdDetectionServiceImpl(emptyDetectors);

        SimdInfo info = service.getSimdInfo();

        assertNotNull(info);
        assertFalse(info.hasSimdSupport());
    }

    @Test
    @Timeout(5)
    @DisplayName("Should work with custom detector")
    void shouldWorkWithCustomDetector() {
        // Mock detector that always returns NEON support
        SimdDetector mockDetector = new SimdDetector() {
            @Override
            public boolean supports(String architecture) {
                return true; // Support all architectures
            }

            @Override
            public SimdInfo detectCapabilities() {
                return new SimdInfoImpl.Builder()
                        .setOperatingSystem("mock-os")
                        .setArchitecture("mock-arch")
                        .setBestSimdInstructionSet("MOCK_SIMD")
                        .build();
            }
        };

        List<SimdDetector> detectors = new ArrayList<>();
        detectors.add(mockDetector);
        SimdDetectionService service = new SimdDetectionServiceImpl(detectors);

        SimdInfo info = service.getSimdInfo();

        assertNotNull(info);
        assertNotNull(info.getBestSimdInstructionSet());
    }
}
