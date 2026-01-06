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