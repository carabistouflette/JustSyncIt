package com.justsyncit.network.transfer.pipeline;

import java.nio.file.Path;

/**
 * Data carrier for a file chunk through the pipeline.
 */
public class ChunkTask {
    private final String transferId;
    private final Path filePath;
    private final long offset;
    private final int length;
    private final long totalFileSize;

    // Data filled in by stages
    private byte[] rawData;
    private byte[] processedData; // Compressed or otherwise transformed
    private String checksum;

    // Encryption metadata
    private boolean encrypted;
    private String keyAlias;

    public ChunkTask(String transferId, Path filePath, long offset, int length, long totalFileSize) {
        this.transferId = transferId;
        this.filePath = filePath;
        this.offset = offset;
        this.length = length;
        this.totalFileSize = totalFileSize;
    }

    public String getTransferId() {
        return transferId;
    }

    public Path getFilePath() {
        return filePath;
    }

    public long getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    public long getTotalFileSize() {
        return totalFileSize;
    }

    public byte[] getRawData() {
        return rawData;
    }

    public void setRawData(byte[] rawData) {
        this.rawData = rawData;
    }

    public byte[] getProcessedData() {
        return processedData;
    }

    public void setProcessedData(byte[] processedData) {
        this.processedData = processedData;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public boolean isEncrypted() {
        return encrypted;
    }

    public void setEncrypted(boolean encrypted) {
        this.encrypted = encrypted;
    }

    public String getKeyAlias() {
        return keyAlias;
    }

    public void setKeyAlias(String keyAlias) {
        this.keyAlias = keyAlias;
    }
}
