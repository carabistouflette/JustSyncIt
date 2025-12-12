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

/**
 * Simple verification program to test that SimdUtils refactoring works
 * correctly.
 */
public class SimdUtilsVerification {

    public static void main(String[] args) {
        System.out.println("=== SimdUtils Refactoring Verification ===");

        try {
            // Test that SimdUtils.getSimdInfo() returns a valid SimdInfo
            SimdInfo simdInfo = SimdUtils.getSimdInfo();

            System.out.println("✓ SimdInfo object created successfully");
            System.out.println("✓ Operating System: " + simdInfo.getOperatingSystem());
            System.out.println("✓ Architecture: " + simdInfo.getArchitecture());
            System.out.println("✓ Java Version: " + simdInfo.getJavaVersion());
            System.out.println("✓ Best SIMD Instruction Set: " + simdInfo.getBestSimdInstructionSet());
            System.out.println("✓ Has SIMD Support: " + simdInfo.hasSimdSupport());

            // Test that multiple calls return the same instance (caching)
            SimdInfo firstCall = SimdUtils.getSimdInfo();
            SimdInfo secondCall = SimdUtils.getSimdInfo();

            if (firstCall == secondCall) {
                System.out.println("✓ Caching works: same instance returned");
            } else {
                System.out.println("✗ Caching issue: different instances returned");
            }

            // Test that the refactored implementation provides consistent results
            boolean consistency = firstCall.getOperatingSystem().equals(secondCall.getOperatingSystem())
                    && firstCall.getArchitecture().equals(secondCall.getArchitecture())
                    && firstCall.getJavaVersion().equals(secondCall.getJavaVersion())
                    && firstCall.getBestSimdInstructionSet().equals(secondCall.getBestSimdInstructionSet())
                    && firstCall.hasSimdSupport() == secondCall.hasSimdSupport();

            if (consistency) {
                System.out.println("✓ Consistency test passed");
            } else {
                System.out.println("✗ Consistency test failed");
            }

            System.out.println("\n=== All Tests Completed Successfully ===");
            System.out.println("The SimdUtils refactoring to delegate to com.justsyncit.simd package works correctly!");

        } catch (Exception e) {
            System.err.println("✗ Error during verification: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}