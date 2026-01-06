package com.justsyncit.hash;

import com.justsyncit.simd.SimdDetectionService;
import com.justsyncit.simd.SimdDetectionServiceImpl;
import com.justsyncit.simd.SimdInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for detecting SIMD instruction set support on current platform.
 * Provides runtime detection of AVX2, AVX-512, and other SIMD capabilities.
 */
public final class SimdUtils {

    /** Logger instance. */
    private static final Logger logger = LoggerFactory.getLogger(SimdUtils.class);

    /** Delegate to the newer SIMD detection service. */
    private static final SimdDetectionService simdDetectionService = new SimdDetectionServiceImpl();

    // Private constructor to prevent instantiation
    private SimdUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Gets SIMD information for current platform.
     * Results are cached for performance.
     *
     * @return SimdInfo containing detected SIMD capabilities
     */
    public static SimdInfo getSimdInfo() {
        // DELEGATE: Use the newer, more efficient SIMD detection service
        logger.debug("Delegating SIMD detection to com.justsyncit.simd package");
        return simdDetectionService.getSimdInfo();
    }

    /**
     * Detects SIMD capabilities on current platform.
     *
     * @return SimdInfo with detected capabilities
     */
    // REMOVED: detectSimdCapabilities() - now delegated to SimdDetectionService

    // REMOVED: detectX86Features() - now delegated to X86SimdDetector

    // REMOVED: detectArmFeatures() - now delegated to ArmSimdDetector

    // REMOVED: All has*Support() methods - now delegated to appropriate detectors
    // This eliminates the performance issue of repeated /proc/cpuinfo reads

    // REMOVED: SimdInfo class and Builder - now using com.justsyncit.simd.SimdInfo
    // interface
    // This eliminates code duplication and ensures consistency across the codebase
}