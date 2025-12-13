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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.justsyncit.scanner;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generator for test files with various sizes and content patterns.
 * Supports both synchronous and asynchronous file generation for testing
 * scenarios.
 */
public final class AsyncTestFileGenerator {

    private static final Random RANDOM = new Random(42); // Fixed seed for reproducible tests

    private AsyncTestFileGenerator() {
        // Utility class - prevent instantiation
    }

    /**
     * Content patterns for test file generation.
     */
    public enum ContentPattern {
        /** Empty file (0 bytes) */
        EMPTY,
        /** All zeros */
        ZEROS,
        /** All ones (0xFF) */
        ONES,
        /** Sequential bytes (0, 1, 2, ...) */
        SEQUENTIAL,
        /** Random bytes */
        RANDOM,
        /** Repeating pattern */
        REPEATING,
        /** ASCII text content */
        TEXT,
        /** Mixed patterns */
        MIXED,
        /** Sparse file with holes */
        SPARSE
    }

    /**
     * Configuration for test file generation.
     */
    public static class FileGenerationConfig {
        private final long fileSize;
        private final ContentPattern pattern;
        private final int chunkSize;
        private final boolean useAsyncIO;
        private final String repeatingPattern;
        private final String textContent;

        public FileGenerationConfig(long fileSize, ContentPattern pattern) {
            this(fileSize, pattern, 8192, true, "TEST", "Lorem ipsum dolor sit amet, consectetur adipiscing elit.");
        }

        public FileGenerationConfig(long fileSize, ContentPattern pattern, int chunkSize, boolean useAsyncIO,
                String repeatingPattern, String textContent) {
            this.fileSize = fileSize;
            this.pattern = pattern;
            this.chunkSize = chunkSize;
            this.useAsyncIO = useAsyncIO;
            this.repeatingPattern = repeatingPattern;
            this.textContent = textContent;
        }

        public long getFileSize() {
            return fileSize;
        }

        public ContentPattern getPattern() {
            return pattern;
        }

        public int getChunkSize() {
            return chunkSize;
        }

        public boolean isUseAsyncIO() {
            return useAsyncIO;
        }

        public String getRepeatingPattern() {
            return repeatingPattern;
        }

        public String getTextContent() {
            return textContent;
        }

        public static FileGenerationConfig empty() {
            return new FileGenerationConfig(0, ContentPattern.EMPTY);
        }

        public static FileGenerationConfig small(ContentPattern pattern) {
            return new FileGenerationConfig(1024, pattern);
        }

        public static FileGenerationConfig medium(ContentPattern pattern) {
            return new FileGenerationConfig(65536, pattern);
        }

        public static FileGenerationConfig large(ContentPattern pattern) {
            return new FileGenerationConfig(1048576, pattern);
        }

        public static FileGenerationConfig huge(ContentPattern pattern) {
            return new FileGenerationConfig(16777216, pattern);
        }
    }

    /**
     * Result of file generation operation.
     */
    public static class FileGenerationResult {
        private final Path filePath;
        private final long fileSize;
        private final ContentPattern pattern;
        private final long generationTimeMs;
        private final String checksum;

        public FileGenerationResult(Path filePath, long fileSize, ContentPattern pattern,
                long generationTimeMs, String checksum) {
            this.filePath = filePath;
            this.fileSize = fileSize;
            this.pattern = pattern;
            this.generationTimeMs = generationTimeMs;
            this.checksum = checksum;
        }

        public Path getFilePath() {
            return filePath;
        }

        public long getFileSize() {
            return fileSize;
        }

        public ContentPattern getPattern() {
            return pattern;
        }

        public long getGenerationTimeMs() {
            return generationTimeMs;
        }

        public String getChecksum() {
            return checksum;
        }
    }

    /**
     * Generates a test file with the specified configuration.
     */
    public static CompletableFuture<FileGenerationResult> generateFileAsync(Path filePath,
            FileGenerationConfig config) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();

                if (config.getPattern() == ContentPattern.EMPTY) {
                    Files.createFile(filePath);
                    long endTime = System.currentTimeMillis();
                    return new FileGenerationResult(filePath, 0, ContentPattern.EMPTY,
                            endTime - startTime, "empty");
                }

                if (config.isUseAsyncIO()) {
                    return generateFileAsyncIO(filePath, config, startTime);
                } else {
                    return generateFileSyncIO(filePath, config, startTime);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate test file: " + filePath, e);
            }
        });
    }

    /**
     * Generates a test file using synchronous I/O.
     */
    private static FileGenerationResult generateFileSyncIO(Path filePath, FileGenerationConfig config, long startTime)
            throws IOException {
        ByteBuffer buffer = createContentBuffer(config.getChunkSize(), config);

        try (var channel = Files.newByteChannel(filePath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {

            long bytesWritten = 0;
            while (bytesWritten < config.getFileSize()) {
                buffer.clear();
                int bytesToWrite = (int) Math.min(config.getChunkSize(), config.getFileSize() - bytesWritten);
                buffer.limit(bytesToWrite);

                int written = channel.write(buffer);
                bytesWritten += written;
            }
        }

        long endTime = System.currentTimeMillis();
        String checksum = calculateSimpleChecksum(filePath);

        return new FileGenerationResult(filePath, config.getFileSize(), config.getPattern(),
                endTime - startTime, checksum);
    }

    /**
     * Generates a test file using asynchronous I/O.
     */
    private static FileGenerationResult generateFileAsyncIO(Path filePath, FileGenerationConfig config, long startTime)
            throws IOException {
        try (AsynchronousFileChannel channel = AsynchronousFileChannel.open(filePath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {

            ByteBuffer buffer = createContentBuffer(config.getChunkSize(), config);
            AtomicLong position = new AtomicLong(0);

            CompletableFuture<Void> writeFuture = new CompletableFuture<>();

            writeChunkAsync(channel, buffer, position, config.getFileSize(), writeFuture);

            try {
                writeFuture.get();
            } catch (Exception e) {
                throw new IOException("Async file generation failed", e);
            }
        }

        long endTime = System.currentTimeMillis();
        String checksum = calculateSimpleChecksum(filePath);

        return new FileGenerationResult(filePath, config.getFileSize(), config.getPattern(),
                endTime - startTime, checksum);
    }

    /**
     * Writes chunks asynchronously using CompletionHandler pattern.
     */
    private static void writeChunkAsync(AsynchronousFileChannel channel, ByteBuffer buffer,
            AtomicLong position, long fileSize, CompletableFuture<Void> completionFuture) {

        if (position.get() >= fileSize) {
            completionFuture.complete(null);
            return;
        }

        buffer.clear();
        long currentPos = position.get();
        int bytesToWrite = (int) Math.min(buffer.capacity(), fileSize - currentPos);
        buffer.limit(bytesToWrite);

        channel.write(buffer, currentPos, null, new CompletionHandler<Integer, Void>() {
            @Override
            public void completed(Integer result, Void attachment) {
                position.addAndGet(result);
                writeChunkAsync(channel, buffer, position, fileSize, completionFuture);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                completionFuture.completeExceptionally(exc);
            }
        });
    }

    /**
     * Creates a ByteBuffer with the specified content pattern.
     */
    private static ByteBuffer createContentBuffer(int size, FileGenerationConfig config) {
        ByteBuffer buffer = ByteBuffer.allocate(size);

        switch (config.getPattern()) {
            case ZEROS:
                while (buffer.hasRemaining()) {
                    buffer.put((byte) 0);
                }
                break;

            case ONES:
                while (buffer.hasRemaining()) {
                    buffer.put((byte) 0xFF);
                }
                break;

            case SEQUENTIAL:
                byte[] sequentialValue = {0};
                while (buffer.hasRemaining()) {
                    buffer.put(sequentialValue[0]++);
                }
                break;

            case RANDOM:
                byte[] randomBytes = new byte[size];
                RANDOM.nextBytes(randomBytes);
                buffer.put(randomBytes);
                break;

            case REPEATING:
                byte[] patternBytes = config.getRepeatingPattern().getBytes();
                int patternIndex = 0;
                while (buffer.hasRemaining()) {
                    buffer.put(patternBytes[patternIndex % patternBytes.length]);
                    patternIndex++;
                }
                break;

            case TEXT:
                String text = config.getTextContent();
                byte[] textBytes = text.getBytes();
                int textIndex = 0;
                while (buffer.hasRemaining()) {
                    buffer.put(textBytes[textIndex % textBytes.length]);
                    textIndex++;
                }
                break;

            case MIXED:
                // Mix of different patterns
                int quarter = size / 4;
                final byte[] mixedValue = {0};
                fillBufferRange(buffer, 0, quarter, (byte) 0);
                fillBufferRange(buffer, quarter, 2 * quarter, (byte) 0xFF);
                fillBufferRange(buffer, 2 * quarter, 3 * quarter, () -> {
                    byte result = mixedValue[0];
                    mixedValue[0]++;
                    return result;
                });
                fillBufferRange(buffer, 3 * quarter, size, RANDOM);
                break;

            case SPARSE:
                // Sparse file - mostly zeros with some data
                while (buffer.hasRemaining()) {
                    if (RANDOM.nextDouble() < 0.1) { // 10% chance of non-zero
                        buffer.put((byte) (RANDOM.nextInt(256) - 128));
                    } else {
                        buffer.put((byte) 0);
                    }
                }
                break;

            case EMPTY:
            default:
                // Already handled separately
                break;
        }

        buffer.flip();
        return buffer;
    }

    /**
     * Fills a range of the buffer with the specified pattern.
     */
    private static void fillBufferRange(ByteBuffer buffer, int start, int end, byte value) {
        int originalPosition = buffer.position();
        buffer.position(start);
        int limit = Math.min(end, buffer.capacity());
        buffer.limit(limit);

        while (buffer.hasRemaining()) {
            buffer.put(value);
        }

        buffer.limit(buffer.capacity());
        buffer.position(originalPosition);
    }

    /**
     * Fills a range of the buffer with sequential values.
     */
    private static void fillBufferRange(ByteBuffer buffer, int start, int end,
            java.util.function.IntSupplier valueSupplier) {
        int originalPosition = buffer.position();
        buffer.position(start);
        int limit = Math.min(end, buffer.capacity());
        buffer.limit(limit);

        while (buffer.hasRemaining()) {
            buffer.put((byte) valueSupplier.getAsInt());
        }

        buffer.limit(buffer.capacity());
        buffer.position(originalPosition);
    }

    /**
     * Fills a range of the buffer with random values.
     */
    private static void fillBufferRange(ByteBuffer buffer, int start, int end, Random random) {
        int originalPosition = buffer.position();
        buffer.position(start);
        int limit = Math.min(end, buffer.capacity());
        buffer.limit(limit);

        byte[] randomBytes = new byte[limit - start];
        random.nextBytes(randomBytes);
        buffer.put(randomBytes);

        buffer.limit(buffer.capacity());
        buffer.position(originalPosition);
    }

    /**
     * Calculates a simple checksum for the file.
     */
    private static String calculateSimpleChecksum(Path filePath) throws IOException {
        if (!Files.exists(filePath) || Files.size(filePath) == 0) {
            return "empty";
        }

        try (var channel = Files.newByteChannel(filePath, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            long checksum = 0;

            while (channel.read(buffer) > 0) {
                buffer.flip();
                while (buffer.hasRemaining()) {
                    checksum += buffer.get() & 0xFF;
                }
                buffer.clear();
            }

            return Long.toHexString(checksum);
        }
    }

    /**
     * Generates multiple test files with different configurations.
     */
    public static CompletableFuture<FileGenerationResult[]> generateFilesAsync(Path baseDir,
            FileGenerationConfig[] configs) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Files.createDirectories(baseDir);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create base directory: " + baseDir, e);
            }

            java.util.List<CompletableFuture<FileGenerationResult>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < configs.length; i++) {
                String fileName = "test_file_" + i + "_" + configs[i].getPattern().name().toLowerCase() + ".dat";
                Path filePath = baseDir.resolve(fileName);
                futures.add(generateFileAsync(filePath, configs[i]));
            }

            try {
                java.util.List<FileGenerationResult> results = AsyncTestUtils
                        .waitForAllAndGetResults(AsyncTestUtils.DEFAULT_TIMEOUT, futures);
                return results.toArray(new FileGenerationResult[0]);
            } catch (AsyncTestUtils.AsyncTestException e) {
                throw new RuntimeException("Failed to generate files", e);
            }
        });
    }

    /**
     * Creates predefined file generation configurations for testing.
     */
    public static FileGenerationConfig[] getPredefinedConfigs() {
        return new FileGenerationConfig[] {
                FileGenerationConfig.empty(),
                FileGenerationConfig.small(ContentPattern.ZEROS),
                FileGenerationConfig.small(ContentPattern.ONES),
                FileGenerationConfig.small(ContentPattern.SEQUENTIAL),
                FileGenerationConfig.small(ContentPattern.RANDOM),
                FileGenerationConfig.medium(ContentPattern.REPEATING),
                FileGenerationConfig.medium(ContentPattern.TEXT),
                FileGenerationConfig.large(ContentPattern.MIXED),
                FileGenerationConfig.huge(ContentPattern.SPARSE)
        };
    }

    /**
     * Creates edge case file generation configurations.
     */
    public static FileGenerationConfig[] getEdgeCaseConfigs() {
        return new FileGenerationConfig[] {
                new FileGenerationConfig(1, ContentPattern.RANDOM), // 1 byte
                new FileGenerationConfig(512, ContentPattern.SEQUENTIAL), // 512 bytes
                new FileGenerationConfig(1023, ContentPattern.RANDOM), // Just under 1KB
                new FileGenerationConfig(1024, ContentPattern.ZEROS), // Exactly 1KB
                new FileGenerationConfig(1025, ContentPattern.ONES), // Just over 1KB
                new FileGenerationConfig(65535, ContentPattern.RANDOM), // Just under 64KB
                new FileGenerationConfig(65536, ContentPattern.SEQUENTIAL), // Exactly 64KB
                new FileGenerationConfig(65537, ContentPattern.RANDOM) // Just over 64KB
        };
    }

    /**
     * Creates performance test file generation configurations.
     */
    public static FileGenerationConfig[] getPerformanceConfigs() {
        return new FileGenerationConfig[] {
                new FileGenerationConfig(1048576, ContentPattern.RANDOM, 65536, true, "PERF", "Performance test data."),
                new FileGenerationConfig(4194304, ContentPattern.SEQUENTIAL, 131072, true, "PERF",
                        "Performance test data."),
                new FileGenerationConfig(16777216, ContentPattern.MIXED, 262144, true, "PERF",
                        "Performance test data."),
                new FileGenerationConfig(67108864, ContentPattern.RANDOM, 524288, true, "PERF",
                        "Performance test data.")
        };
    }
}