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

/**
 * Interface for SIMD capability information.
 * Provides abstraction for SIMD detection results.
 */
public interface SimdInfo {

    /**
     * @return the operating system name
     */
    String getOperatingSystem();

    /**
     * @return the system architecture
     */
    String getArchitecture();

    /**
     * @return the Java version
     */
    String getJavaVersion();

    /**
     * @return true if AVX-512 is supported
     */
    boolean isAvx512Supported();

    /**
     * @return true if AVX2 is supported
     */
    boolean isAvx2Supported();

    /**
     * @return true if AVX is supported
     */
    boolean isAvxSupported();

    /**
     * @return true if SSE4 is supported
     */
    boolean isSse4Supported();

    /**
     * @return true if SSE2 is supported
     */
    boolean isSse2Supported();

    /**
     * @return true if NEON is supported
     */
    boolean isNeonSupported();

    /**
     * @return the best SIMD instruction set available
     */
    String getBestSimdInstructionSet();

    /**
     * @return true if any SIMD support is available
     */
    boolean hasSimdSupport();
}