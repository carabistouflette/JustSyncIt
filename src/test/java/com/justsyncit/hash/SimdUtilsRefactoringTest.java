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

import com.justsyncit.simd.SimdInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.TimeUnit;

/**
 * Test to verify that SimdUtils refactoring works correctly.
 * This test validates that the delegation to the newer SIMD package works as expected.
 */
class SimdUtilsRefactoringTest {

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testSimdUtilsDelegationWorks() {
        // Test that SimdUtils.getSimdInfo() returns a valid SimdInfo
        SimdInfo simdInfo = SimdUtils.getSimdInfo();

        assertNotNull(simdInfo, "SimdInfo should not be null");
        assertNotNull(simdInfo.getOperatingSystem(), "Operating system should not be null");
        assertNotNull(simdInfo.getArchitecture(), "Architecture should not be null");
        assertNotNull(simdInfo.getJavaVersion(), "Java version should not be null");
        assertNotNull(simdInfo.getBestSimdInstructionSet(), "Best SIMD instruction set should not be null");

        // Verify that the values are reasonable
        assertTrue(simdInfo.getOperatingSystem().length() > 0, "Operating system should not be empty");
        assertTrue(simdInfo.getArchitecture().length() > 0, "Architecture should not be empty");
        assertTrue(simdInfo.getJavaVersion().length() > 0, "Java version should not be empty");
        assertTrue(simdInfo.getBestSimdInstructionSet().length() > 0, "Best SIMD instruction set should not be empty");

        // Test that hasSimdSupport() works
        boolean hasSimd = simdInfo.hasSimdSupport();
        // This should be either true or false, but shouldn't throw an exception
        assertTrue(hasSimd == true || hasSimd == false, "hasSimdSupport() should return true or false");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testSimdUtilsCachingWorks() {
        // Test that multiple calls return the same instance (caching)
        SimdInfo firstCall = SimdUtils.getSimdInfo();
        SimdInfo secondCall = SimdUtils.getSimdInfo();

        // Should be the same object due to caching in the underlying service
        assertSame(firstCall, secondCall, "SimdUtils should return cached SimdInfo instance");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testSimdUtilsConsistency() {
        // Test that the refactored implementation provides consistent results
        SimdInfo simdInfo1 = SimdUtils.getSimdInfo();
        SimdInfo simdInfo2 = SimdUtils.getSimdInfo();

        // All properties should be consistent between calls
        assertEquals(simdInfo1.getOperatingSystem(), simdInfo2.getOperatingSystem());
        assertEquals(simdInfo1.getArchitecture(), simdInfo2.getArchitecture());
        assertEquals(simdInfo1.getJavaVersion(), simdInfo2.getJavaVersion());
        assertEquals(simdInfo1.getBestSimdInstructionSet(), simdInfo2.getBestSimdInstructionSet());
        assertEquals(simdInfo1.hasSimdSupport(), simdInfo2.hasSimdSupport());
    }
}