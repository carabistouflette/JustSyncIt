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

package com.justsyncit.performance.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Utility class for generating benchmark datasets with various characteristics.
 * Provides methods to create realistic test data for performance testing.
 */
public class BenchmarkDataGenerator {

    private static final Random RANDOM = new Random(42); // Fixed seed for reproducible tests

    /**
     * Dataset size categories for benchmarking.
     */
    public enum DatasetSize {
        SMALL(1, 100), // 1-100 MB
        MEDIUM(100, 1024), // 100 MB - 1 GB
        LARGE(1024, 10240), // 1-10 GB
        VERY_LARGE(10240, 102400); // 10+ GB

        private final int minSizeMB;
        private final int maxSizeMB;

        DatasetSize(int minSizeMB, int maxSizeMB) {
            this.minSizeMB = minSizeMB;
            this.maxSizeMB = maxSizeMB;
        }

        public int getMinSizeMB() {
            return minSizeMB;
        }

        public int getMaxSizeMB() {
            return maxSizeMB;
        }
    }

    /**
     * File type categories for mixed datasets.
     */
    public enum FileType {
        TEXT("txt", 1024, 10240), // 1KB-10KB
        DOCUMENT("docx", 10240, 102400), // 10KB-100KB
        IMAGE("jpg", 51200, 512000), // 50KB-500KB
        VIDEO("mp4", 1024000, 10240000), // 1MB-10MB
        ARCHIVE("zip", 102400, 1024000), // 100KB-1MB
        BINARY("bin", 2048, 20480); // 2KB-20KB

        private final String extension;
        private final int minSize;
        private final int maxSize;

        FileType(String extension, int minSize, int maxSize) {
            this.extension = extension;
            this.minSize = minSize;
            this.maxSize = maxSize;
        }

        public String getExtension() {
            return extension;
        }

        public int getMinSize() {
            return minSize;
        }

        public int getMaxSize() {
            return maxSize;
        }
    }

    /**
     * Creates a dataset of specified size with mixed file types.
     *
     * @param baseDir      base directory for test data
     * @param targetSizeMB target size in MB
     * @throws IOException if file creation fails
     */
    public static void createMixedDataset(Path baseDir, int targetSizeMB) throws IOException {
        Files.createDirectories(baseDir);

        long targetSizeBytes = targetSizeMB * 1024L * 1024L;
        long currentSize = 0;
        int fileIndex = 0;

        while (currentSize < targetSizeBytes) {
            FileType fileType = FileType.values()[RANDOM.nextInt(FileType.values().length)];
            int fileSize = RANDOM.nextInt(fileType.getMaxSize() - fileType.getMinSize()) + fileType.getMinSize();

            // Ensure we don't exceed target size
            if (currentSize + fileSize > targetSizeBytes) {
                fileSize = (int) (targetSizeBytes - currentSize);
                if (fileSize < 1024)
                    break; // Don't create tiny files at the end
            }

            Path filePath = baseDir.resolve(String.format("mixed_%04d.%s", fileIndex++, fileType.getExtension()));

            switch (fileType) {
                case TEXT:
                case DOCUMENT:
                    createTextFile(filePath, fileSize);
                    break;
                case IMAGE:
                case VIDEO:
                case ARCHIVE:
                case BINARY:
                default:
                    createBinaryFile(filePath, fileSize);
                    break;
            }

            currentSize += fileSize;
        }
    }

    /**
     * Creates a dataset with many small files (typical user documents).
     *
     * @param baseDir      base directory for test data
     * @param targetSizeMB target size in MB
     * @throws IOException if file creation fails
     */
    public static void createSmallFilesDataset(Path baseDir, int targetSizeMB) throws IOException {
        Files.createDirectories(baseDir);

        int targetSizeBytes = targetSizeMB * 1024 * 1024;
        int fileSize = 1024 + RANDOM.nextInt(1024 * 10); // 1KB-11KB
        int fileCount = targetSizeBytes / fileSize;

        for (int i = 0; i < fileCount; i++) {
            Path filePath = baseDir.resolve(String.format("small_%04d.txt", i));
            createTextFile(filePath, fileSize);
        }
    }

    /**
     * Creates a dataset with few large files (media collections).
     *
     * @param baseDir      base directory for test data
     * @param targetSizeMB target size in MB
     * @throws IOException if file creation fails
     */
    public static void createLargeFilesDataset(Path baseDir, int targetSizeMB) throws IOException {
        Files.createDirectories(baseDir);

        int targetSizeBytes = targetSizeMB * 1024 * 1024;
        int fileSize = 1024 * 1024 + RANDOM.nextInt(1024 * 1024 * 4); // 1MB-5MB
        int fileCount = Math.max(1, targetSizeBytes / fileSize);

        for (int i = 0; i < fileCount; i++) {
            Path filePath = baseDir.resolve(String.format("large_%04d.dat", i));
            createBinaryFile(filePath, fileSize);
        }
    }

    /**
     * Creates a dataset with high deduplication potential.
     *
     * @param baseDir        base directory for test data
     * @param targetSizeMB   target size in MB
     * @param duplicateRatio percentage of files that should be duplicates (0.0-1.0)
     * @throws IOException if file creation fails
     */
    public static void createDuplicateHeavyDataset(Path baseDir, int targetSizeMB, double duplicateRatio)
            throws IOException {
        Files.createDirectories(baseDir);

        int targetSizeBytes = targetSizeMB * 1024 * 1024;
        int baseFileSize = 10240; // 10KB base files
        int baseFileCount = (int) (targetSizeBytes * (1.0 - duplicateRatio) / baseFileSize);

        List<byte[]> baseContents = new ArrayList<>();

        // Create base files
        for (int i = 0; i < baseFileCount; i++) {
            byte[] content = generateRandomContent(baseFileSize);
            baseContents.add(content);

            Path filePath = baseDir.resolve(String.format("base_%04d.dat", i));
            Files.write(filePath, content);
        }

        // Create duplicate files
        int duplicateCount = (int) (baseFileCount * duplicateRatio / (1.0 - duplicateRatio));
        for (int i = 0; i < duplicateCount; i++) {
            byte[] content = baseContents.get(RANDOM.nextInt(baseContents.size()));
            Path filePath = baseDir.resolve(String.format("duplicate_%04d.dat", i));
            Files.write(filePath, content);
        }
    }

    /**
     * Creates a dataset with directory structure depth testing.
     *
     * @param baseDir       base directory for test data
     * @param maxDepth      maximum directory depth
     * @param filesPerLevel number of files per directory level
     * @param fileSize      size of each file in bytes
     * @throws IOException if file creation fails
     */
    public static void createDeepDirectoryDataset(Path baseDir, int maxDepth, int filesPerLevel, int fileSize)
            throws IOException {
        Files.createDirectories(baseDir);

        for (int depth = 0; depth < maxDepth; depth++) {
            Path levelDir = baseDir;

            // Create nested directory structure
            for (int i = 0; i <= depth; i++) {
                levelDir = levelDir.resolve("level" + i);
            }
            Files.createDirectories(levelDir);

            // Create files at this level
            for (int i = 0; i < filesPerLevel; i++) {
                Path filePath = levelDir.resolve(String.format("file_%04d.dat", i));
                createBinaryFile(filePath, fileSize);
            }
        }
    }

    /**
     * Creates a dataset with files having special characters in names.
     *
     * @param baseDir   base directory for test data
     * @param fileCount number of files to create
     * @param fileSize  size of each file in bytes
     * @throws IOException if file creation fails
     */
    public static void createSpecialCharacterDataset(Path baseDir, int fileCount, int fileSize) throws IOException {
        Files.createDirectories(baseDir);

        String[] specialNames = {
                "file with spaces.txt",
                "file-with-dashes.txt",
                "file_with_underscores.txt",
                "file.with.dots.txt",
                "файл.txt", // Cyrillic
                "文件.txt", // Chinese
                "fichier_éàç.txt", // French accents
                "テスト.txt", // Japanese
                "파일.txt", // Korean
                "ملف.txt", // Arabic
                "test(1).txt",
                "test[2].txt",
                "test{3}.txt",
                "file@company.com.txt",
                "file#hashtag.txt",
                "file%percent.txt",
                "file+plus.txt",
                "file=equals.txt",
                "file$dollar.txt",
                "file^caret.txt"
        };

        for (int i = 0; i < fileCount; i++) {
            String fileName = specialNames[i % specialNames.length];
            if (fileCount > specialNames.length) {
                fileName = i + "_" + fileName;
            }

            Path filePath = baseDir.resolve(fileName);
            createTextFile(filePath, fileSize);
        }
    }

    /**
     * Creates a sparse file dataset (files with large gaps of zeros).
     *
     * @param baseDir        base directory for test data
     * @param fileCount      number of sparse files to create
     * @param apparentSizeMB apparent size of each file in MB
     * @param actualSizeMB   actual data size in each file in MB
     * @throws IOException if file creation fails
     */
    public static void createSparseFileDataset(Path baseDir, int fileCount, int apparentSizeMB, int actualSizeMB)
            throws IOException {
        Files.createDirectories(baseDir);

        int apparentSize = apparentSizeMB * 1024 * 1024;
        int actualSize = actualSizeMB * 1024 * 1024;

        for (int i = 0; i < fileCount; i++) {
            Path filePath = baseDir.resolve(String.format("sparse_%04d.dat", i));
            createSparseFile(filePath, apparentSize, actualSize);
        }
    }

    /**
     * Creates a dataset with incremental backup testing in mind.
     * Creates an initial dataset and then provides methods to modify it.
     *
     * @param baseDir       base directory for test data
     * @param initialSizeMB initial dataset size in MB
     * @return DatasetInfo containing information about created dataset
     * @throws IOException if file creation fails
     */
    public static DatasetInfo createIncrementalDataset(Path baseDir, int initialSizeMB) throws IOException {
        Files.createDirectories(baseDir);

        createMixedDataset(baseDir, initialSizeMB);

        // Record initial state
        List<Path> initialFiles = new ArrayList<>();
        try (java.util.stream.Stream<Path> stream = Files.walk(baseDir)) {
            stream
                    .filter(Files::isRegularFile)
                    .forEach(initialFiles::add);
        }

        return new DatasetInfo(baseDir, initialFiles);
    }

    /**
     * Modifies a dataset for incremental backup testing.
     *
     * @param datasetInfo   dataset information from createIncrementalDataset
     * @param modifyPercent percentage of files to modify (0.0-1.0)
     * @param addPercent    percentage of new files to add (0.0-1.0)
     * @param deletePercent percentage of files to delete (0.0-1.0)
     * @throws IOException if file modification fails
     */
    public static void modifyIncrementalDataset(DatasetInfo datasetInfo, double modifyPercent,
            double addPercent, double deletePercent) throws IOException {
        List<Path> files = new ArrayList<>(datasetInfo.getFiles());

        // Modify existing files
        int modifyCount = (int) (files.size() * modifyPercent);
        for (int i = 0; i < modifyCount; i++) {
            Path file = files.get(RANDOM.nextInt(files.size()));
            if (Files.exists(file)) {
                // Append some data to modify the file
                byte[] appendData = generateRandomContent(1024);
                Files.write(file, appendData, java.nio.file.StandardOpenOption.APPEND);
            }
        }

        // Add new files
        int addCount = (int) (files.size() * addPercent);
        for (int i = 0; i < addCount; i++) {
            Path newFile = datasetInfo.getBaseDir().resolve(String.format("new_%04d.dat", i));
            createBinaryFile(newFile, 10240); // 10KB new files
        }

        // Delete some files
        int deleteCount = (int) (files.size() * deletePercent);
        for (int i = 0; i < deleteCount; i++) {
            Path file = files.get(RANDOM.nextInt(files.size()));
            if (Files.exists(file)) {
                Files.delete(file);
            }
        }
    }

    // Helper methods for file creation

    private static void createTextFile(Path filePath, int size) throws IOException {
        StringBuilder content = new StringBuilder();
        String baseText = "Benchmark test file content with various text patterns. ";

        while (content.length() < size) {
            content.append(baseText).append(RANDOM.nextInt(1000)).append("\n");
        }

        String fileContent = content.substring(0, Math.min(size, content.length()));
        Files.write(filePath, fileContent.getBytes());
    }

    private static void createBinaryFile(Path filePath, int size) throws IOException {
        byte[] content = generateRandomContent(size);
        Files.write(filePath, content);
    }

    private static void createSparseFile(Path filePath, int apparentSize, int actualSize) throws IOException {
        // Create a file with sparse content
        byte[] data = generateRandomContent(actualSize);

        // Write data at the beginning and end to simulate sparse file
        int chunkSize = actualSize / 2;
        byte[] chunk = new byte[chunkSize];
        System.arraycopy(data, 0, chunk, 0, chunkSize);

        Files.write(filePath, chunk);

        // Seek to near the end and write the remaining data
        if (apparentSize > actualSize) {
            // In a real sparse file implementation, you would use seek
            // For this test, we'll just create a smaller file
            byte[] remainingData = new byte[actualSize - chunkSize];
            System.arraycopy(data, chunkSize, remainingData, 0, remainingData.length);
            Files.write(filePath, remainingData, java.nio.file.StandardOpenOption.APPEND);
        }
    }

    private static byte[] generateRandomContent(int size) {
        byte[] content = new byte[size];
        RANDOM.nextBytes(content);
        return content;
    }

    /**
     * Information about a created dataset.
     */
    public static class DatasetInfo {
        private final Path baseDir;
        private final List<Path> files;

        public DatasetInfo(Path baseDir, List<Path> files) {
            this.baseDir = baseDir;
            this.files = new ArrayList<>(files);
        }

        public Path getBaseDir() {
            return baseDir;
        }

        public List<Path> getFiles() {
            return new ArrayList<>(files);
        }
    }
}