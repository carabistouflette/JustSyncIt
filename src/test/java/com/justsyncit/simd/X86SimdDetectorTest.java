package com.justsyncit.simd;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for X86SimdDetector.
 */
public class X86SimdDetectorTest {

    @Test
    public void testSupports() {
        X86SimdDetector detector = new X86SimdDetector();
        assertTrue(detector.supports("amd64"));
        assertTrue(detector.supports("x86_64"));
        Assertions.assertFalse(detector.supports("aarch64"));
    }

    @Test
    public void testDetectCapabilities() {
        X86SimdDetector detector = new X86SimdDetector();
        SimdInfo info = detector.detectCapabilities();

        assertNotNull(info);
        assertNotNull(info.getBestSimdInstructionSet());

        System.out.println("Detected SIMD: " + info.getBestSimdInstructionSet());
        System.out.println("Vector Bit Size: " + jdk.incubator.vector.ByteVector.SPECIES_PREFERRED.vectorBitSize());

        // We can't strictly assert AVX support since it depends on the build machine
        // hardware.
        // But we can ensure it runs without crashing and returns a valid SimdInfo
        // object.
        // And if we know ensuring at least SSE2 on x86_64
        String arch = System.getProperty("os.arch").toLowerCase();
        if (arch.contains("amd64") || arch.contains("x86_64")) {
            assertTrue(info.isSse2Supported(), "SSE2 should be supported on x86_64");
        }
    }
}
