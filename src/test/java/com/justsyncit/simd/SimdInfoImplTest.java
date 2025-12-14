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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for SimdInfoImpl.
 */
@DisplayName("SimdInfoImpl Tests")
class SimdInfoImplTest {

    @Test
    @Timeout(5)
    @DisplayName("Should build SimdInfo with all properties")
    void shouldBuildSimdInfoWithAllProperties() {
        SimdInfoImpl info = new SimdInfoImpl.Builder()
                .setOperatingSystem("linux")
                .setArchitecture("amd64")
                .setJavaVersion("21.0.1")
                .setAvx512Supported(true)
                .setAvx2Supported(true)
                .setAvxSupported(true)
                .setSse4Supported(true)
                .setSse2Supported(true)
                .setNeonSupported(false)
                .setBestSimdInstructionSet("AVX512")
                .build();

        assertEquals("linux", info.getOperatingSystem());
        assertEquals("amd64", info.getArchitecture());
        assertEquals("21.0.1", info.getJavaVersion());
        assertTrue(info.isAvx512Supported());
        assertTrue(info.isAvx2Supported());
        assertTrue(info.isAvxSupported());
        assertTrue(info.isSse4Supported());
        assertTrue(info.isSse2Supported());
        assertFalse(info.isNeonSupported());
        assertEquals("AVX512", info.getBestSimdInstructionSet());
    }

    @Test
    @Timeout(5)
    @DisplayName("Should report SIMD support when available")
    void shouldReportSimdSupportWhenAvailable() {
        SimdInfoImpl info = new SimdInfoImpl.Builder()
                .setBestSimdInstructionSet("AVX2")
                .build();

        assertTrue(info.hasSimdSupport());
    }

    @Test
    @Timeout(5)
    @DisplayName("Should report no SIMD support when instruction set is NONE")
    void shouldReportNoSimdSupportWhenNone() {
        SimdInfoImpl info = new SimdInfoImpl.Builder()
                .setBestSimdInstructionSet("NONE")
                .build();

        assertFalse(info.hasSimdSupport());
    }

    @Test
    @Timeout(5)
    @DisplayName("Should generate correct toString output")
    void shouldGenerateCorrectToStringOutput() {
        SimdInfoImpl info = new SimdInfoImpl.Builder()
                .setOperatingSystem("linux")
                .setArchitecture("amd64")
                .setAvx2Supported(true)
                .setBestSimdInstructionSet("AVX2")
                .build();

        String result = info.toString();
        assertNotNull(result);
        assertTrue(result.contains("linux"));
        assertTrue(result.contains("amd64"));
        assertTrue(result.contains("AVX2"));
    }

    @Test
    @Timeout(5)
    @DisplayName("Should build with ARM NEON support")
    void shouldBuildWithArmNeonSupport() {
        SimdInfoImpl info = new SimdInfoImpl.Builder()
                .setOperatingSystem("linux")
                .setArchitecture("aarch64")
                .setNeonSupported(true)
                .setBestSimdInstructionSet("NEON")
                .build();

        assertTrue(info.isNeonSupported());
        assertEquals("NEON", info.getBestSimdInstructionSet());
        assertTrue(info.hasSimdSupport());
    }

    @Test
    @Timeout(5)
    @DisplayName("Builder should use NONE as default instruction set")
    void builderShouldUseNoneAsDefaultInstructionSet() {
        SimdInfoImpl info = new SimdInfoImpl.Builder().build();

        assertEquals("NONE", info.getBestSimdInstructionSet());
        assertFalse(info.hasSimdSupport());
    }
}
