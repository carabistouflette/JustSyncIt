package com.justsyncit.simd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorSpecies;

import java.util.Locale;

/**
 * SIMD detector for x86/x64 architectures using Java Vector API.
 * This modern implementation avoids fragile file parsing.
 */
public class X86SimdDetector implements SimdDetector {

    /** Logger instance. */
    private static final Logger logger = LoggerFactory.getLogger(X86SimdDetector.class);

    @Override
    public boolean supports(String architecture) {
        return architecture.contains("amd64") || architecture.contains("x86_64");
    }

    @Override
    public SimdInfo detectCapabilities() {
        String osName = System.getProperty("os.name", "unknown").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "unknown").toLowerCase(Locale.ROOT);
        String javaVersion = System.getProperty("java.version", "unknown");

        SimdInfoImpl.Builder builder = new SimdInfoImpl.Builder()
                .setOperatingSystem(osName)
                .setArchitecture(arch)
                .setJavaVersion(javaVersion);

        try {
            // Use Vector API to detect preferred vector size
            VectorSpecies<Byte> species = ByteVector.SPECIES_PREFERRED;
            int bitSize = species.vectorBitSize();

            logger.info("Vector API detected preferred bit size: {}", bitSize);

            if (bitSize >= 512) {
                builder.setAvx512Supported(true)
                        .setAvx2Supported(true)
                        .setAvxSupported(true)
                        .setSse4Supported(true)
                        .setSse2Supported(true)
                        .setBestSimdInstructionSet("AVX-512");
                logger.info("AVX-512 support detected via Vector API");
            } else if (bitSize >= 256) {
                builder.setAvx2Supported(true)
                        .setAvxSupported(true)
                        .setSse4Supported(true)
                        .setSse2Supported(true)
                        .setBestSimdInstructionSet("AVX2");
                logger.info("AVX2 support detected via Vector API");
            } else if (bitSize >= 128) {
                // On x86, 128-bit usually means SSE (at least SSE2 is mandatory for x64)
                builder.setAvxSupported(false) // Can't be sure about AVX-128, assume SSE
                        .setSse4Supported(true) // Reasonable assumption for modern hardware
                        .setSse2Supported(true)
                        .setBestSimdInstructionSet("SSE4");
                logger.info("SSE/128-bit vector support detected via Vector API");
            } else {
                logger.info("No significant SIMD vector size detected ({}), falling back to scalar", bitSize);
                builder.setBestSimdInstructionSet("NONE");
            }
        } catch (Throwable e) {
            // Fallback if Vector API is missing or fails (e.g. module not added)
            logger.warn("Vector API detection failed, falling back to safe defaults: {}", e.getMessage());
            // Assume SSE2 for x86_64 as it's part of the spec
            builder.setSse2Supported(true)
                    .setBestSimdInstructionSet("SSE2 (Fallback)");
        }

        return builder.build();
    }
}