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
 * Service interface for SIMD detection operations.
 * Provides abstraction for SIMD capability detection.
 */
public interface SimdDetectionService {

    /**
     * Gets SIMD information for the current platform.
     * Results are cached for performance.
     *
     * @return SimdInfo containing detected SIMD capabilities
     */
    SimdInfo getSimdInfo();
}