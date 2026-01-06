package com.justsyncit.simd;

/**
 * Interface for SIMD capability detection.
 * Follows Open/Closed Principle by allowing new detectors to be added without
 * modifying existing code.
 */
public interface SimdDetector {

    /**
     * Checks if this detector supports the current architecture.
     *
     * @param architecture the system architecture (e.g., "x86_64", "aarch64")
     * @return true if this detector can handle the architecture, false otherwise
     */
    boolean supports(String architecture);

    /**
     * Detects SIMD capabilities for the supported architecture.
     *
     * @return SimdInfo with detected capabilities
     */
    SimdInfo detectCapabilities();
}