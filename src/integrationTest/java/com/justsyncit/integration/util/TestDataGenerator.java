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

package com.justsyncit.integration.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * Utility class for generating test data with various characteristics.
 * Provides methods to create realistic test datasets for E2E testing.
 */
public class TestDataGenerator {

    private static final Random RANDOM = new Random(42); // Fixed seed for reproducible tests

    /**
     * Creates a basic test dataset with various file types and sizes.
     *
     * @param baseDir base directory for test data
     * @throws IOException if file creation fails
     */
    public static void createBasicDataset(Path baseDir) throws IOException {
        Files.createDirectories(baseDir);

        // Small text files
        createTextFile(baseDir.resolve("small1.txt"), 1024, "Small text file 1");
        createTextFile(baseDir.resolve("small2.txt"), 2048, "Small text file 2");

        // Medium files
        createBinaryFile(baseDir.resolve("medium1.dat"), 1024 * 100); // 100KB
        createTextFile(baseDir.resolve("medium2.txt"), 1024 * 50, "Medium text file 2"); // 50KB

        // Large file
        createBinaryFile(baseDir.resolve("large.dat"), 1024 * 1024 * 5); // 5MB

        // Subdirectory with files
        Path subDir = baseDir.resolve("subdir");
        Files.createDirectories(subDir);
        createTextFile(subDir.resolve("nested1.txt"), 4096, "Nested file 1");
        createBinaryFile(subDir.resolve("nested2.dat"), 8192);

        // Deep directory structure
        Path deepDir = baseDir.resolve("level1").resolve("level2").resolve("level3");
        Files.createDirectories(deepDir);
        createTextFile(deepDir.resolve("deep.txt"), 1024, "Deep nested file");
    }

    /**
     * Creates a dataset with duplicate files for deduplication testing.
     *
     * @param baseDir base directory for test data
     * @throws IOException if file creation fails
     */
    public static void createDuplicateDataset(Path baseDir) throws IOException {
        Files.createDirectories(baseDir);

        // Create duplicate content
        byte[] duplicateContent = generateRandomContent(10240); // 10KB
        String duplicateText = "This is duplicate content that appears in multiple files to test deduplication.";

        // Multiple files with identical binary content
        Files.write(baseDir.resolve("duplicate1.dat"), duplicateContent);
        Files.write(baseDir.resolve("duplicate2.dat"), duplicateContent);
        Files.write(baseDir.resolve("duplicate3.dat"), duplicateContent);

        // Multiple files with identical text content
        Files.write(baseDir.resolve("text_duplicate1.txt"), duplicateText.getBytes());
        Files.write(baseDir.resolve("text_duplicate2.txt"), duplicateText.getBytes());
        Files.write(baseDir.resolve("text_duplicate3.txt"), duplicateText.getBytes());

        // Some unique files
        createBinaryFile(baseDir.resolve("unique1.dat"), 5120);
        createTextFile(baseDir.resolve("unique2.txt"), 2048, "Unique content");

        // Subdirectory with duplicates
        Path subDir = baseDir.resolve("subdir");
        Files.createDirectories(subDir);
        Files.write(subDir.resolve("sub_duplicate1.dat"), duplicateContent);
        Files.write(subDir.resolve("sub_duplicate2.txt"), duplicateText.getBytes());
    }

    /**
     * Creates a dataset with files containing special characters and various encodings.
     *
     * @param baseDir base directory for test data
     * @throws IOException if file creation fails
     */
    public static void createSpecialCharacterDataset(Path baseDir) throws IOException {
        Files.createDirectories(baseDir);

        // Files with special characters in names
        createTextFile(baseDir.resolve("file with spaces.txt"), 1024, "File with spaces");
        createTextFile(baseDir.resolve("file-with-dashes.txt"), 1024, "File with dashes");
        createTextFile(baseDir.resolve("file_with_underscores.txt"), 1024, "File with underscores");
        createTextFile(baseDir.resolve("file.with.dots.txt"), 1024, "File with dots");

        // Unicode characters
        createTextFile(baseDir.resolve("файл.txt"), 1024, "Cyrillic filename");
        createTextFile(baseDir.resolve("文件.txt"), 1024, "Chinese filename");
        createTextFile(baseDir.resolve("fichier_avec_éàç.txt"), 1024, "French accented filename");

        // Special characters in content
        String specialContent = "Special characters: éàçüöäß 中文 русский العربية עברית हिन्दी";
        Files.write(baseDir.resolve("special_content.txt"), specialContent.getBytes("UTF-8"));

        // Very long filename
        String longName = "a".repeat(200) + ".txt";
        createTextFile(baseDir.resolve(longName), 512, "Very long filename");
    }

    /**
     * Creates a dataset for performance testing with various file sizes.
     *
     * @param baseDir base directory for test data
     * @param fileCount number of files to create
     * @param maxFileSize maximum file size in bytes
     * @throws IOException if file creation fails
     */
    public static void createPerformanceDataset(Path baseDir, int fileCount, int maxFileSize) throws IOException {
        Files.createDirectories(baseDir);

        for (int i = 0; i < fileCount; i++) {
            int fileSize = RANDOM.nextInt(maxFileSize) + 1024; // At least 1KB
            String fileName = String.format("perf_file_%03d.dat", i);

            if (i % 3 == 0) {
                createBinaryFile(baseDir.resolve(fileName), fileSize);
            } else {
                createTextFile(baseDir.resolve(fileName), fileSize, "Performance test file " + i);
            }
        }
    }

    /**
     * Creates an empty directory structure for edge case testing.
     *
     * @param baseDir base directory for test data
     * @throws IOException if directory creation fails
     */
    public static void createEmptyDataset(Path baseDir) throws IOException {
        Files.createDirectories(baseDir);

        // Create empty directories
        Files.createDirectories(baseDir.resolve("empty1"));
        Files.createDirectories(baseDir.resolve("empty2"));
        Files.createDirectories(baseDir.resolve("nested").resolve("empty"));

        // Create a single empty file
        Files.createFile(baseDir.resolve("empty.txt"));
    }

    /**
     * Creates a dataset with various file permissions and attributes.
     *
     * @param baseDir base directory for test data
     * @throws IOException if file creation fails
     */
    public static void createPermissionDataset(Path baseDir) throws IOException {
        Files.createDirectories(baseDir);

        // Regular files
        createTextFile(baseDir.resolve("regular.txt"), 1024, "Regular file");

        // Note: In a real implementation, we would set different permissions
        // For cross-platform compatibility, we just create the files
        createTextFile(baseDir.resolve("readonly.txt"), 1024, "Read-only file simulation");
        createTextFile(baseDir.resolve("executable.sh"), 1024, "#!/bin/bash\necho 'Hello World'");
    }

    /**
     * Creates a text file with specified size and content.
     */
    private static void createTextFile(Path filePath, int size, String content) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(content);

        while (sb.length() < size) {
            sb.append(" ").append(content);
        }

        String fileContent = sb.substring(0, Math.min(size, sb.length()));
        Files.write(filePath, fileContent.getBytes());
    }

    /**
     * Creates a binary file with random content.
     */
    private static void createBinaryFile(Path filePath, int size) throws IOException {
        byte[] content = generateRandomContent(size);
        Files.write(filePath, content);
    }

    /**
     * Generates random content of specified size.
     */
    private static byte[] generateRandomContent(int size) {
        byte[] content = new byte[size];
        RANDOM.nextBytes(content);
        return content;
    }

    /**
     * Calculates expected hash for a file (simplified for testing).
     */
    public static String calculateExpectedHash(Path filePath) throws IOException {
        // In a real implementation, this would use the actual hash algorithm
        // For testing, we use a simple hash based on file size and name
        long size = Files.size(filePath);
        String name = filePath.getFileName().toString();
        return "hash_" + Math.abs((name + size).hashCode()) + "_" + size;
    }
}