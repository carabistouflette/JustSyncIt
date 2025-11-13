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

/**
 * Command for verifying file integrity.
 * Follows Single Responsibility Principle by focusing only on file verification.
 */
public class VerifyCommand implements Command {

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
            System.err.println("Error: --verify command requires a file path and expected hash");
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
                System.err.println("Error: File does not exist: " + filePath);
                return false;
            }
            
            if (!Files.isRegularFile(path)) {
                System.err.println("Error: Path is not a regular file: " + filePath);
                return false;
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
                System.out.println("✓ File integrity is intact");
                return true;
            } else {
                System.out.println("✗ File integrity check FAILED - file may be corrupted");
                return false;
            }
            
        } catch (IOException e) {
            System.err.println("Error verifying file: " + e.getMessage());
            return false;
        }
    }
}