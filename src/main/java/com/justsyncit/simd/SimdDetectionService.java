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