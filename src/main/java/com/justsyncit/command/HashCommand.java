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
 * Command for hashing files.
 * Follows Single Responsibility Principle by focusing only on file hashing.
 */
public class HashCommand implements Command {

    /** BLAKE3 service instance. */
    private final Blake3Service blake3Service;

    /**
     * Creates a new HashCommand.
     *
     * @param blake3Service the BLAKE3 service
     */
    public HashCommand(Blake3Service blake3Service) {
        this.blake3Service = blake3Service;
    }

    @Override
    public boolean execute(String[] args, CommandContext context) {
        if (args.length < 2) {
            System.err.println("Error: --hash command requires a file path");
            return false;
        }

        String filePath = args[1];
        return hashFile(filePath);
    }

    @Override
    public String getName() {
        return "--hash";
    }

    @Override
    public String getDescription() {
        return "Calculate BLAKE3 hash of a file";
    }

    @Override
    public String getUsage() {
        return "--hash <file>";
    }

    /**
     * Hashes the specified file and displays the result.
     *
     * @param filePath the path to the file to hash
     * @return true if successful, false otherwise
     */
    private boolean hashFile(String filePath) {
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

            long startTime = System.currentTimeMillis();
            String hash = blake3Service.hashFile(path);
            long endTime = System.currentTimeMillis();

            System.out.println("File: " + filePath);
            System.out.println("Size: " + Files.size(path) + " bytes");
            System.out.println("BLAKE3 Hash: " + hash);
            System.out.println("Time: " + (endTime - startTime) + " ms");

            return true;

        } catch (IOException e) {
            System.err.println("Error hashing file: " + e.getMessage());
            return false;
        }
    }
}