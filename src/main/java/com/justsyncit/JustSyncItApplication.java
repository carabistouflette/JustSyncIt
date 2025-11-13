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

package com.justsyncit;

import com.justsyncit.hash.Blake3Service;
import com.justsyncit.hash.Blake3ServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;

/**
 * Main application class for JustSyncIt backup solution.
 */
public class JustSyncItApplication {

    /** Logger for this application class. */
    private static final Logger logger = LoggerFactory.getLogger(JustSyncItApplication.class);
    
    /** BLAKE3 service for file integrity verification. */
    private final Blake3Service blake3Service;

    /**
     * Default constructor.
     */
    public JustSyncItApplication() {
        this.blake3Service = new Blake3ServiceImpl();
    }

    /**
     * Main entry point for the application.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        logger.info("Starting JustSyncIt backup solution");

        try {
            JustSyncItApplication app = new JustSyncItApplication();
            app.run(args);
        } catch (Exception e) {
            logger.error("Application failed to start", e);
            System.exit(1);
        }
    }

    /**
     * Runs the application with the provided arguments.
     *
     * @param args command line arguments
     */
    public void run(String[] args) {
        logger.info("JustSyncIt is running");
        
        // Display BLAKE3 implementation info
        displayBlake3Info();

        System.out.println("JustSyncIt - Backup Solution");
        System.out.println("Version: 1.0-SNAPSHOT");

        if (args.length > 0) {
            logger.info("Running with {} arguments", args.length);
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                System.out.println("Arg " + i + ": " + arg);
                
                // Handle file hashing arguments
                if (arg.equals("--hash") && i + 1 < args.length) {
                    String filePath = args[i + 1];
                    hashFile(filePath);
                } else if (arg.equals("--verify") && i + 2 < args.length) {
                    String filePath = args[i + 1];
                    String expectedHash = args[i + 2];
                    verifyFileIntegrity(filePath, expectedHash);
                }
            }
        } else {
            logger.info("Running with no arguments");
            System.out.println("Usage: java -jar JustSyncIt.jar [options]");
            System.out.println("Options:");
            System.out.println("  --hash <file>     Calculate BLAKE3 hash of file");
            System.out.println("  --verify <file> <hash>  Verify file integrity against hash");
        }
    }
    
    /**
     * Displays BLAKE3 implementation information.
     */
    private void displayBlake3Info() {
        Blake3Service.Blake3Info info = blake3Service.getInfo();
        
        System.out.println("\n=== BLAKE3 Implementation Information ===");
        System.out.println("Version: " + info.getVersion());
        System.out.println("SIMD Support: " + (info.hasSimdSupport() ? "Yes" : "No"));
        System.out.println("Instruction Set: " + info.getSimdInstructionSet());
        System.out.println("JNI Implementation: " + (info.isJniImplementation() ? "Yes" : "No"));
        System.out.println("=====================================\n");
        
        logger.info("BLAKE3 service initialized - Version: {}, SIMD: {}, Instruction Set: {}",
            info.getVersion(), info.hasSimdSupport(), info.getSimdInstructionSet());
    }
    
    /**
     * Calculates and displays the BLAKE3 hash of a file.
     *
     * @param filePath path to the file
     */
    private void hashFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            
            if (!Files.exists(path)) {
                System.err.println("Error: File does not exist: " + filePath);
                return;
            }
            
            if (!Files.isRegularFile(path)) {
                System.err.println("Error: Path is not a regular file: " + filePath);
                return;
            }
            
            long startTime = System.currentTimeMillis();
            String hash = blake3Service.hashFile(path);
            long endTime = System.currentTimeMillis();
            
            System.out.println("File: " + filePath);
            System.out.println("Size: " + Files.size(path) + " bytes");
            System.out.println("BLAKE3 Hash: " + hash);
            System.out.println("Time: " + (endTime - startTime) + " ms");
            
            logger.info("Hashed file {} with hash {} in {} ms", filePath, hash, endTime - startTime);
            
        } catch (IOException e) {
            System.err.println("Error hashing file: " + e.getMessage());
            logger.error("Failed to hash file: " + filePath, e);
        }
    }
    
    /**
     * Verifies file integrity against an expected hash.
     *
     * @param filePath path to the file
     * @param expectedHash expected BLAKE3 hash
     */
    private void verifyFileIntegrity(String filePath, String expectedHash) {
        try {
            Path path = Paths.get(filePath);
            
            if (!Files.exists(path)) {
                System.err.println("Error: File does not exist: " + filePath);
                return;
            }
            
            if (!Files.isRegularFile(path)) {
                System.err.println("Error: Path is not a regular file: " + filePath);
                return;
            }
            
            System.out.println("Verifying integrity of: " + filePath);
            System.out.println("Expected hash: " + expectedHash);
            
            long startTime = System.currentTimeMillis();
            String actualHash = blake3Service.hashFile(path);
            long endTime = System.currentTimeMillis();
            
            boolean isValid = expectedHash.equals(actualHash);
            
            System.out.println("Actual hash:   " + actualHash);
            System.out.println("Result: " + (isValid ? "VALID" : "INVALID"));
            System.out.println("Time: " + (endTime - startTime) + " ms");
            
            if (isValid) {
                logger.info("File integrity verified successfully: {}", filePath);
                System.out.println("✓ File integrity is intact");
            } else {
                logger.warn("File integrity verification failed: {}", filePath);
                System.out.println("✗ File integrity check FAILED - file may be corrupted");
            }
            
        } catch (IOException e) {
            System.err.println("Error verifying file: " + e.getMessage());
            logger.error("Failed to verify file integrity: " + filePath, e);
        }
    }
}