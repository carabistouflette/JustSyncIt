package com.justsyncit;

import com.justsyncit.hash.Blake3Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Console implementation of ApplicationInfoDisplay.
 * Follows Single Responsibility Principle by focusing only on console output.
 */
public class ConsoleInfoDisplay implements ApplicationInfoDisplay {

    /** Logger for console info display operations. */
    private static final Logger logger = LoggerFactory.getLogger(ConsoleInfoDisplay.class);

    @Override
    public void displayBlake3Info(Blake3Service blake3Service) {
        Blake3Service.Blake3Info info = blake3Service.getInfo();

        logger.info("\n=== BLAKE3 Implementation Information ===");
        logger.info("Version: {}", info.getVersion());
        logger.info("SIMD Support: {}", info.hasSimdSupport() ? "Yes" : "No");
        logger.info("Instruction Set: {}", info.getSimdInstructionSet());
        logger.info("JNI Implementation: {}", info.isJniImplementation() ? "Yes" : "No");
        logger.info("=====================================\n");
    }

    @Override
    public void displayInfo(String message) {
        logger.info(message);
    }

    @Override
    public void displayError(String message) {
        logger.error(message);
    }
}