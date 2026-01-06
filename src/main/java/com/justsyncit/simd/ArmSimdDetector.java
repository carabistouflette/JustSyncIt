package com.justsyncit.simd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * SIMD detector for ARM architectures.
 * Follows Single Responsibility Principle by focusing only on ARM detection.
 */
public class ArmSimdDetector implements SimdDetector {

    /** Logger instance. */
    private static final Logger logger = LoggerFactory.getLogger(ArmSimdDetector.class);

    @Override
    public boolean supports(String architecture) {
        return architecture.contains("aarch64") || architecture.contains("arm64");
    }

    @Override
    public SimdInfo detectCapabilities() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

        SimdInfoImpl.Builder builder = new SimdInfoImpl.Builder()
                .setOperatingSystem(osName)
                .setArchitecture(System.getProperty("os.arch", "").toLowerCase(Locale.ROOT))
                .setJavaVersion(System.getProperty("java.version"));

        // Check for NEON support
        if (hasNeonSupport()) {
            builder.setNeonSupported(true)
                    .setBestSimdInstructionSet("NEON");
            logger.info("ARM NEON support detected");
        } else {
            logger.info("No ARM SIMD extensions detected");
            builder.setBestSimdInstructionSet("NONE");
        }

        return builder.build();
    }

    /**
     * Checks for ARM NEON support.
     */
    private boolean hasNeonSupport() {
        try {
            String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (osName.contains("linux")) {
                String cpuInfo = new String(java.nio.file.Files.readAllBytes(
                        java.nio.file.Paths.get("/proc/cpuinfo")), java.nio.charset.StandardCharsets.UTF_8);
                return cpuInfo.contains("neon");
            }
        } catch (Exception e) {
            logger.debug("Could not check NEON support via /proc/cpuinfo", e);
        }
        return false;
    }
}