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

package com.justsyncit.command;

import com.justsyncit.hash.Blake3Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command for verifying file integrity.
 * Follows Single Responsibility Principle by focusing only on file verification.
 */
public class VerifyCommand implements Command {

    /** Logger for verify command operations. */
    private static final Logger logger = LoggerFactory.getLogger(VerifyCommand.class);

    /** BLAKE3 service instance. */
    private final Blake3Service blake3Service;

    /**
     * Creates a new VerifyCommand.
     *
     * @param blake3Service BLAKE3 service
     */
    public VerifyCommand(Blake3Service blake3Service) {
        this.blake3Service = blake3Service;
    }

    @Override
    public boolean execute(String[] args, CommandContext context) {
        if (args.length < 3) {
            logger.error("--verify command requires a file path and expected hash");
            return false;
        }

        String filePath = args[1];
        String expectedHash = args[2];
        return verifyFileIntegrity(filePath, expectedHash);
    }

    @Override
    public String getName() {
        return "--verify";
    }

    @Override
    public String getDescription() {
        return "Verify file integrity against a hash";
    }

    @Override
    public String getUsage() {
        return "--verify <file> <hash>";
    }

    /**
     * Verifies file integrity against expected hash.
     *
     * @param filePath path to file to verify
     * @param expectedHash expected hash value
     * @return true if verification succeeds, false otherwise
     */
    private boolean verifyFileIntegrity(String filePath, String expectedHash) {
        try {
            Path path = Paths.get(filePath);

            if (!Files.exists(path)) {
                logger.error("File does not exist: {}", filePath);
                return false;
            }

            if (!Files.isRegularFile(path)) {
                logger.error("Path is not a regular file: {}", filePath);
                return false;
            }

            logger.info("Verifying integrity of: {}", filePath);
            logger.info("Expected hash: {}", expectedHash);

            long startTime = System.currentTimeMillis();
            String actualHash = blake3Service.hashFile(path);
            long endTime = System.currentTimeMillis();

            boolean isValid = expectedHash.equals(actualHash);

            logger.info("Actual hash:   {}", actualHash);
            logger.info("Result: {}", isValid ? "VALID" : "INVALID");
            logger.info("Time: {} ms", (endTime - startTime));

            if (isValid) {
                logger.info("✓ File integrity is intact");
                return true;
            } else {
                logger.warn("✗ File integrity check FAILED - file may be corrupted");
                return false;
            }

        } catch (IOException e) {
            logger.error("Error verifying file: {}", e.getMessage(), e);
            return false;
        }
    }
}