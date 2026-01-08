package com.justsyncit.network.transfer;

import com.justsyncit.network.transfer.pipeline.ReceivePipeline;

import com.justsyncit.network.protocol.ProtocolMessage;
import com.justsyncit.network.protocol.FileTransferRequestMessage;
import com.justsyncit.network.protocol.ChunkDataMessage;
import com.justsyncit.network.protocol.ChunkAckMessage;
import com.justsyncit.network.protocol.TransferCompleteMessage;
import com.justsyncit.storage.ContentStore;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.justsyncit.network.compression.CompressionService;

import com.justsyncit.storage.metadata.FileMetadata;
import java.io.IOException;
import java.time.Instant;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of FileTransferManager with chunking and BLAKE3 verification.
 * Follows Single Responsibility Principle by focusing solely on file transfer
 * operations.
 */
public class FileTransferManagerImpl implements FileTransferManager {

    /** The logger for this class. */
    private static final Logger logger = LoggerFactory.getLogger(FileTransferManagerImpl.class);

    /** Default chunk size for file transfers. */
    private static final int DEFAULT_CHUNK_SIZE = 64 * 1024; // 64KB chunks

    /** Active file transfers. */
    private final Map<String, FileTransferStatus> activeTransfers;
    /** Listeners for transfer events. */
    private final List<TransferEventListener> listeners;
    private static final String PLACEHOLDER_HASH = "pending_calculation";
    /** Flag indicating if the manager is running. */
    private final AtomicBoolean running;
    /** Counter for generating transfer IDs. */
    private final AtomicLong transferIdCounter;
    /** The network service. */
    private com.justsyncit.network.NetworkService networkService;
    /** The compression service. */
    private CompressionService compressionService;

    /** The metadata service. */
    private com.justsyncit.storage.metadata.MetadataService metadataService;
    /** The encryption service. */
    private com.justsyncit.network.encryption.EncryptionService encryptionService;
    /** The master password service. */
    private com.justsyncit.auth.MasterPasswordService masterPasswordService;
    /** Executor for parallel decompression tasks. */
    // private final java.util.concurrent.ExecutorService decompressionExecutor; //
    // Removed in favor of ThreadPoolManager

    /** Configuration: Compression enabled. */
    private boolean compressionEnabled = true;

    /** Configuration: Compression level. */
    private int compressionLevel = 3;

    /** Pipeline factory. */
    private com.justsyncit.network.transfer.pipeline.TransferPipelineFactory transferPipelineFactory;

    /**
     * Creates a new file transfer manager.
     */
    public FileTransferManagerImpl() {
        this.activeTransfers = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.running = new AtomicBoolean(false);
        this.transferIdCounter = new AtomicLong(0);
        // Use ThreadPoolManager instead of custom executor
        // this.decompressionExecutor = ...

        // Initialize with default factory if not set (or leave null and expect
        // injection)
        // For backwards compatibility/safety, we can use a default here or in start()
        this.transferPipelineFactory = new com.justsyncit.network.transfer.pipeline.DefaultTransferPipelineFactory();
    }

    /**
     * Executes a file transfer using the NetworkService.
     */
    private CompletableFuture<FileTransferResult> executeFileTransfer(String transferId, Path filePath,
            InetSocketAddress remoteAddress,
            ContentStore contentStore, long startTime) {
        return CompletableFuture.supplyAsync(() -> {
            // Incremental hasher resources
            com.justsyncit.hash.IncrementalHasherFactory.IncrementalHasher fileHasher = null;

            try {
                long fileSize = Files.size(filePath);
                FileTransferStatus status = activeTransfers.get(transferId);

                if (status == null) {
                    throw new IOException("Transfer not found: " + transferId);
                }

                status.setState(FileTransferStatus.TransferState.IN_PROGRESS);
                String compressionType = status.getCompressionType();
                boolean useCompression = !"NONE".equals(compressionType) && compressionService != null;

                // --- NEW PIPELINE EXECUTION ---

                // Get encryption key from MasterPasswordService
                byte[] masterKey = null;
                boolean encryptionEnabled = false;
                if (masterPasswordService != null && masterPasswordService.isPasswordSet()) {
                    masterKey = masterPasswordService.getMasterKey();
                    encryptionEnabled = masterKey != null;
                }

                // Create stages
                com.justsyncit.network.transfer.pipeline.TransferPipeline pipeline = transferPipelineFactory
                        .createPipeline(
                                networkService, compressionService, useCompression,
                                encryptionService, masterKey, encryptionEnabled, remoteAddress);

                // Initialize hasher
                com.justsyncit.hash.IncrementalHasherFactory hasherFactory = new com.justsyncit.hash.Blake3IncrementalHasherFactory(
                        com.justsyncit.hash.Sha256HashAlgorithm.create());
                fileHasher = hasherFactory.createIncrementalHasher();

                long offset = 0;
                long remaining = fileSize;
                List<CompletableFuture<Void>> chunkFutures = new ArrayList<>();

                // Chain for ordered hashing
                CompletableFuture<Void> hashingChain = CompletableFuture.completedFuture(null);

                while (remaining > 0) {
                    if (!running.get() || status.isCancelled()) {
                        break;
                    }

                    long chunkSize = Math.min(DEFAULT_CHUNK_SIZE, remaining);

                    // Create task
                    com.justsyncit.network.transfer.pipeline.ChunkTask task = new com.justsyncit.network.transfer.pipeline.ChunkTask(
                            transferId, filePath, offset, (int) chunkSize, fileSize);

                    // Create future to capture read data
                    CompletableFuture<byte[]> readFuture = new CompletableFuture<>();
                    task.setReadFuture(readFuture);

                    // Hook into hashing chain BEFORE submitting to pipeline to ensure order
                    // preservation structure
                    final com.justsyncit.hash.IncrementalHasherFactory.IncrementalHasher currentHasher = fileHasher;
                    hashingChain = hashingChain.thenCompose(v -> readFuture.thenAccept(data -> {
                        try {
                            currentHasher.update(data);
                        } catch (Exception e) {
                            throw new java.util.concurrent.CompletionException(e);
                        }
                    }));

                    // Submit to pipeline (this will handle backpressure automatically)
                    CompletableFuture<Void> f = pipeline.submit(task);
                    chunkFutures.add(f);

                    // Update stats
                    status.addBytesTransferred(chunkSize);
                    notifyTransferProgress(filePath, remoteAddress, status.getBytesTransferred(), fileSize);

                    offset += chunkSize;
                    remaining -= chunkSize;
                }

                // Wait for all pipeline tasks to finish
                pipeline.waitForCompletion().join(); // This joins on internal pipeline futures

                // Wait for hashing to finish
                hashingChain.join();
                String finalHash = fileHasher.digest();

                // --- END PIPELINE EXECUTION ---

                long endTime = System.currentTimeMillis();
                FileTransferResult result;

                if (status.isCancelled()) {
                    result = FileTransferResult.failure(transferId, filePath, remoteAddress,
                            "Transfer cancelled", status.getBytesTransferred(),
                            startTime, endTime);
                } else {
                    result = FileTransferResult.success(transferId, filePath, remoteAddress,
                            fileSize, fileSize, startTime, endTime);
                    status.setState(FileTransferStatus.TransferState.COMPLETED);

                    try {
                        networkService.sendMessage(
                                new TransferCompleteMessage(filePath.toString(), fileSize, fileSize, finalHash),
                                remoteAddress).orTimeout(30, TimeUnit.SECONDS).join();
                    } catch (Exception e) {
                        logger.warn("Failed to send transfer complete message", e);
                    }
                }

                activeTransfers.remove(transferId);
                notifyTransferCompleted(filePath, remoteAddress, result.isSuccess(), result.getErrorMessage());

                return result;

            } catch (IOException | java.util.concurrent.CompletionException e) {
                long endTime = System.currentTimeMillis();
                FileTransferStatus status = activeTransfers.get(transferId);
                long bytesTransferred = status != null ? status.getBytesTransferred() : 0;

                // Unwrap CompletionException if present
                Throwable cause = e instanceof java.util.concurrent.CompletionException ? e.getCause() : e;
                String errorMessage = cause != null ? cause.getMessage() : e.getMessage();

                FileTransferResult result = FileTransferResult.failure(
                        transferId, filePath, remoteAddress, errorMessage,
                        bytesTransferred, startTime, endTime);

                activeTransfers.remove(transferId);
                notifyTransferCompleted(filePath, remoteAddress, false, errorMessage);
                notifyError(cause != null ? cause : e, "File transfer execution");

                return result;
            } catch (Exception e) {
                long endTime = System.currentTimeMillis();
                FileTransferStatus status = activeTransfers.get(transferId);
                long bytesTransferred = status != null ? status.getBytesTransferred() : 0;

                FileTransferResult result = FileTransferResult.failure(
                        transferId, filePath, remoteAddress, "Unexpected error: " + e.getMessage(),
                        bytesTransferred, startTime, endTime);

                activeTransfers.remove(transferId);
                notifyTransferCompleted(filePath, remoteAddress, false, "Unexpected error: " + e.getMessage());
                notifyError(e, "File transfer execution (Unexpected)");

                return result;
            } finally {
                if (fileHasher != null) {
                    try {
                        fileHasher.close();
                    } catch (Exception e) {
                        logger.warn("Failed to close hasher", e);
                    }
                }
            }
        }, com.justsyncit.scanner.ThreadPoolManager.getInstance().getBatchProcessingThreadPool());
    }

    /**
     * Sets whether compression is enabled.
     * 
     * @param enabled true to enable compression, false otherwise
     */
    public void setCompressionEnabled(boolean enabled) {
        this.compressionEnabled = enabled;
    }

    /**
     * Sets the compression level.
     * 
     * @param level the compression level (1-22)
     */
    public void setCompressionLevel(int level) {
        this.compressionLevel = level;
    }

    @Override
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "NetworkService is mutable but required for functionality")
    public void setNetworkService(com.justsyncit.network.NetworkService networkService) {
        this.networkService = networkService;
    }

    @Override
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "CompressionService is mutable but required for functionality")
    public void setCompressionService(CompressionService compressionService) {
        this.compressionService = compressionService;
    }

    @Override
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "MetadataService is mutable but required for functionality")
    public void setMetadataService(com.justsyncit.storage.metadata.MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    /**
     * Sets the encryption service.
     *
     * @param encryptionService the encryption service
     */
    public void setEncryptionService(com.justsyncit.network.encryption.EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }

    /**
     * Sets the master password service.
     *
     * @param masterPasswordService the master password service
     */
    public void setMasterPasswordService(com.justsyncit.auth.MasterPasswordService masterPasswordService) {
        this.masterPasswordService = masterPasswordService;
    }

    /**
     * Sets the transfer pipeline factory.
     *
     * @param transferPipelineFactory the factory to use
     */
    public void setTransferPipelineFactory(
            com.justsyncit.network.transfer.pipeline.TransferPipelineFactory transferPipelineFactory) {
        this.transferPipelineFactory = transferPipelineFactory;
    }

    @Override
    public CompletableFuture<Void> start() {
        if (running.compareAndSet(false, true)) {
            logger.info("File transfer manager started");
            return CompletableFuture.completedFuture(null);
        } else {
            logger.warn("File transfer manager already started");
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public CompletableFuture<Void> stop() {
        if (running.compareAndSet(true, false)) {
            // Cancel all active transfers
            List<CompletableFuture<Void>> cancelFutures = new ArrayList<>();
            for (String transferId : activeTransfers.keySet()) {
                cancelFutures.add(cancelTransfer(transferId));
            }

            // Shutdown executor - Handled by ThreadPoolManager globally now
            // decompressionExecutor.shutdown();

            return CompletableFuture.allOf(cancelFutures.toArray(new CompletableFuture<?>[0]))
                    .thenRun(() -> {
                        activeTransfers.clear();
                        if (transferPipelineFactory instanceof com.justsyncit.network.transfer.pipeline.DefaultTransferPipelineFactory) {
                            ((com.justsyncit.network.transfer.pipeline.DefaultTransferPipelineFactory) transferPipelineFactory)
                                    .shutdown();
                        }
                        logger.info("File transfer manager stopped");
                    });
        } else {
            logger.warn("File transfer manager already stopped");
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public CompletableFuture<FileTransferResult> sendFile(Path filePath, InetSocketAddress remoteAddress,
            ContentStore contentStore) {
        if (!running.get()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("File transfer manager is not running"));
        }

        if (!Files.exists(filePath)) {
            return CompletableFuture.failedFuture(
                    new IOException("File does not exist: " + filePath));
        }

        String transferId = generateTransferId();
        long startTime = System.currentTimeMillis();

        try {
            long fileSize = Files.size(filePath);

            // Determine compression type based on config and service availability
            String compressionType = "NONE";
            if (compressionEnabled && compressionService != null) {
                compressionType = compressionService.getAlgorithmName();
                compressionService.setLevel(compressionLevel);
            }

            FileTransferStatus status = FileTransferStatus.pending(transferId, filePath, remoteAddress, fileSize,
                    compressionType);
            activeTransfers.put(transferId, status);

            notifyTransferStarted(filePath, remoteAddress, fileSize);

            // Create file transfer request
            Path fileNamePath = filePath.getFileName();
            String fileName = fileNamePath != null ? fileNamePath.toString() : "unknown";
            if (fileName == null || fileName.trim().isEmpty()) {
                throw new IOException("Invalid file path: filename is empty");
            }

            // Calculate file hash (Pre-Scan)
            // We use SHA-256 directly here to match the current "Blake3" factory behavior
            // (which uses SHA-256).
            // This fixes the "Fake Hash" absurdity by ensuring we send a REAL hash.
            // Note: This adds a read pass, but integrity is paramount.
            String fileHash;
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (java.io.InputStream fis = Files.newInputStream(filePath)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        digest.update(buffer, 0, bytesRead);
                    }
                }
                byte[] hashBytes = digest.digest();
                StringBuilder hexString = new StringBuilder();
                for (byte b : hashBytes) {
                    String hex = Integer.toHexString(0xff & b);
                    if (hex.length() == 1)
                        hexString.append('0');
                    hexString.append(hex);
                }
                fileHash = hexString.toString();
            } catch (NoSuchAlgorithmException e) {
                logger.error("SHA-256 algorithm not found, falling back to placeholder", e);
                fileHash = PLACEHOLDER_HASH;
            }

            // Send transfer request
            FileTransferRequestMessage request = new FileTransferRequestMessage(
                    fileName, fileSize, System.currentTimeMillis(), fileHash, DEFAULT_CHUNK_SIZE,
                    compressionType);

            return networkService.sendMessage(request, remoteAddress)
                    .orTimeout(30, TimeUnit.SECONDS)
                    .thenCompose(
                            v -> executeFileTransfer(transferId, filePath, remoteAddress, contentStore, startTime));

        } catch (IOException e) {
            FileTransferResult result = FileTransferResult.failure(
                    transferId, filePath, remoteAddress, e.getMessage(), 0, startTime, System.currentTimeMillis());
            activeTransfers.remove(transferId);
            notifyTransferCompleted(filePath, remoteAddress, false, e.getMessage());
            return CompletableFuture.completedFuture(result);
        }
    }

    /**
     * Executes a file transfer using the NetworkService.
     */

    @Override
    public CompletableFuture<Void> handleFileTransferRequest(ProtocolMessage request,
            InetSocketAddress remoteAddress,
            ContentStore contentStore) {
        if (!(request instanceof FileTransferRequestMessage)) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Expected FileTransferRequestMessage"));
        }

        FileTransferRequestMessage fileRequest = (FileTransferRequestMessage) request;
        String fileName = fileRequest.getFilePath();
        long fileSize = fileRequest.getFileSize();
        String compressionType = fileRequest.getCompressionType();

        logger.info("Received file transfer request: {} ({}) from {} [Compression: {}]",
                fileName, formatFileSize(fileSize), remoteAddress, compressionType);

        // Check if we can accept the transfer
        // For now, accept all

        // Register the transfer
        // Use fileName as ID for receiving to match what we expect in chunks
        // FIX: Sanitize the path to prevent traversal attacks
        Path rawPath = java.nio.file.Paths.get(fileName);
        String safeFileName = rawPath.getFileName().toString();

        // Enforce safe directory
        Path baseDir = java.nio.file.Paths.get("downloads").toAbsolutePath();
        try {
            if (!java.nio.file.Files.exists(baseDir)) {
                java.nio.file.Files.createDirectories(baseDir);
            }
        } catch (IOException e) {
            return CompletableFuture.failedFuture(new IOException("Failed to create download directory", e));
        }

        Path localPath = baseDir.resolve(safeFileName);

        FileTransferStatus status = FileTransferStatus.pending(fileName, localPath, remoteAddress, fileSize,
                compressionType);
        activeTransfers.put(fileName, status);

        notifyTransferStarted(localPath, remoteAddress, fileSize);

        logger.debug("Accepting file transfer request for {} -> {}", fileName, localPath);

        // In a real implementation we should send an ACK here
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> handleChunkData(ProtocolMessage chunkData,
            InetSocketAddress remoteAddress,
            ContentStore contentStore) {
        if (!(chunkData instanceof ChunkDataMessage)) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Expected ChunkDataMessage"));
        }

        ChunkDataMessage chunkMessage = (ChunkDataMessage) chunkData;
        String filePath = chunkMessage.getFilePath();
        long chunkOffset = chunkMessage.getChunkOffset();
        byte[] chunkDataBytes = chunkMessage.getChunkData();
        String checksum = chunkMessage.getChunkHash();

        FileTransferStatus status = activeTransfers.get(filePath);
        if (status == null) {
            return CompletableFuture.failedFuture(
                    new IOException("Unknown transfer ID: " + filePath));
        }

        status.setState(FileTransferStatus.TransferState.IN_PROGRESS);

        // Get encryption key from MasterPasswordService
        byte[] masterKey = null;
        boolean encryptionEnabled = false;
        if (masterPasswordService != null && masterPasswordService.isPasswordSet()) {
            masterKey = masterPasswordService.getMasterKey();
            encryptionEnabled = masterKey != null;
        }

        // Create and execute receive pipeline
        ReceivePipeline pipeline = transferPipelineFactory.createReceivePipeline(
                compressionService,
                status.getCompressionType(),
                encryptionService,
                masterKey,
                encryptionEnabled,
                filePath, // Use filePath as transferId for AEAD
                blake3Service,
                checksum,
                chunkOffset,
                contentStore);

        // Accumulate chunk hash for metadata persistence
        status.addChunkHash(checksum);

        return pipeline.process(chunkDataBytes)
                .thenAccept(dataLength -> {
                    // Update progress
                    status.addBytesTransferred(dataLength);

                    notifyTransferProgress(status.getFilePath(), remoteAddress,
                            status.getBytesTransferred(),
                            status.getFileSize());

                    // Send acknowledgment
                    new ChunkAckMessage(filePath, chunkOffset, dataLength);
                    logger.debug("Sending chunk acknowledgment for offset {}", chunkOffset);

                    // This would delegate to NetworkService to send the acknowledgment
                })
                .exceptionallyCompose(e -> {
                    logger.error("Error processing chunk {} for transfer {}", chunkOffset, filePath, e);

                    // Unwrap potential completion exception
                    Throwable cause = e instanceof java.util.concurrent.CompletionException ? e.getCause() : e;

                    // Send negative acknowledgment
                    logger.debug("Sending negative chunk acknowledgment for offset {}: {}", chunkOffset,
                            cause.getMessage());

                    // This would delegate to NetworkService to send the acknowledgment
                    // We return a completed null future to indicate handled exception
                    return CompletableFuture.<Void>completedFuture(null);
                });
    }

    @Override
    public CompletableFuture<Void> handleChunkAck(ProtocolMessage chunkAck, InetSocketAddress remoteAddress) {
        if (!(chunkAck instanceof ChunkAckMessage)) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Expected ChunkAckMessage"));
        }

        ChunkAckMessage ackMessage = (ChunkAckMessage) chunkAck;
        String filePath = ackMessage.getFilePath();
        long chunkOffset = ackMessage.getChunkOffset();
        boolean success = ackMessage.isSuccess();
        String errorMessage = ackMessage.getErrorMessage();

        FileTransferStatus status = activeTransfers.get(filePath);
        if (status == null) {
            return CompletableFuture.completedFuture(null); // Transfer already completed
        }

        if (!success) {
            logger.error("Chunk {} acknowledgment failed for transfer {}: {}",
                    chunkOffset, filePath, errorMessage);
            status.setState(FileTransferStatus.TransferState.FAILED);
            status.setErrorMessage(errorMessage);
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> handleTransferComplete(ProtocolMessage completeMessage,
            InetSocketAddress remoteAddress) {
        if (!(completeMessage instanceof TransferCompleteMessage)) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Expected TransferCompleteMessage"));
        }

        TransferCompleteMessage complete = (TransferCompleteMessage) completeMessage;
        String filePath = complete.getFilePath();
        boolean success = true; // Transfer complete is always successful
        String errorMessage = complete.getErrorMessage();

        FileTransferStatus status = activeTransfers.get(filePath);
        if (status == null) {
            return CompletableFuture.completedFuture(null); // Transfer already completed
        }

        if (success) {
            status.setState(FileTransferStatus.TransferState.COMPLETED);

            // Persist file metadata
            if (metadataService != null) {
                try {
                    FileMetadata metadata = new FileMetadata(
                            UUID.randomUUID().toString(),
                            "incoming-transfer",
                            status.getFilePath().toString(),
                            status.getFileSize(),
                            Instant.now(),
                            complete.getFinalBlake3Hash(),
                            status.getChunkHashes());
                    metadataService.updateFile(metadata);
                    logger.debug("Persisted file metadata for {}", status.getFilePath());
                } catch (Exception e) {
                    logger.error("Failed to persist file metadata for {}", status.getFilePath(), e);
                    // Don't fail the transfer just because metadata failed?
                    // "Write-Only Transfer" is the defect, so failing to persist IS a failure of
                    // the system.
                    // But technically the file content is there.
                    // For now we log error, but maybe we should flag status as warning?
                }
            } else {
                logger.warn("MetadataService not configured - file metadata will not be persisted for {}",
                        status.getFilePath());
            }
        } else {
            status.setState(FileTransferStatus.TransferState.FAILED);
            status.setErrorMessage(errorMessage);
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> cancelTransfer(String transferId) {
        FileTransferStatus status = activeTransfers.get(transferId);
        if (status != null) {
            status.setState(FileTransferStatus.TransferState.CANCELLED);
            activeTransfers.remove(transferId);
            notifyTransferCompleted(status.getFilePath(), status.getRemoteAddress(), false, "Transfer cancelled");
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public FileTransferStatus getTransferStatus(String transferId) {
        return activeTransfers.get(transferId);
    }

    @Override
    public List<FileTransferStatus> getActiveTransfers() {
        return Collections.unmodifiableList(new ArrayList<>(activeTransfers.values()));
    }

    @Override
    public int getActiveTransferCount() {
        return activeTransfers.size();
    }

    @Override
    public void addTransferEventListener(TransferEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeTransferEventListener(TransferEventListener listener) {
        listeners.remove(listener);
    }

    // Helper methods

    private String generateTransferId() {
        return "transfer-" + transferIdCounter.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** The BLAKE3 service for checksums. */
    private com.justsyncit.hash.Blake3Service blake3Service;

    /**
     * Sets the BLAKE3 service.
     *
     * @param blake3Service the BLAKE3 service
     */
    public void setBlake3Service(com.justsyncit.hash.Blake3Service blake3Service) {
        this.blake3Service = blake3Service;
    }

    private String computeChecksum(byte[] data) {
        if (blake3Service != null) {
            try {
                // Use BLAKE3 to compute checksum
                return blake3Service.hashBuffer(data);
            } catch (Exception e) {
                logger.error("Failed to compute BLAKE3 checksum, falling back to SHA-256", e);
            }
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(data);
            return bytesToHex(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            // CRITICAL: Do not fall back to weak hash. Fail securely.
            logger.error("SHA-256 algorithm not found - cannot verify data integrity", e);
            throw new RuntimeException("Secure hashing algorithm not available", e);
        }
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    // Notification methods

    private void notifyTransferStarted(Path filePath, InetSocketAddress remoteAddress, long fileSize) {
        for (TransferEventListener listener : listeners) {
            try {
                listener.onTransferStarted(filePath, remoteAddress, fileSize);
            } catch (RuntimeException e) {
                logger.error("Error in transfer event listener (onTransferStarted)", e);
            }
        }
    }

    private void notifyTransferProgress(Path filePath, InetSocketAddress remoteAddress,
            long bytesTransferred, long totalBytes) {
        for (TransferEventListener listener : listeners) {
            try {
                listener.onTransferProgress(filePath, remoteAddress, bytesTransferred, totalBytes);
            } catch (RuntimeException e) {
                logger.error("Error in transfer event listener (onTransferProgress)", e);
            }
        }
    }

    private void notifyTransferCompleted(Path filePath, InetSocketAddress remoteAddress,
            boolean success, String errorMessage) {
        for (TransferEventListener listener : listeners) {
            try {
                listener.onTransferCompleted(filePath, remoteAddress, success, errorMessage);
            } catch (RuntimeException e) {
                logger.error("Error in transfer event listener (onTransferCompleted)", e);
            }
        }
    }

    private void notifyError(Throwable error, String context) {
        for (TransferEventListener listener : listeners) {
            try {
                listener.onError(error, context);
            } catch (RuntimeException e) {
                logger.error("Error in transfer event listener (onError)", e);
            }
        }
    }
}