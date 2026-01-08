package com.justsyncit.backup;

import com.justsyncit.scanner.ChunkingOptions;
import com.justsyncit.scanner.FileChunker;
import com.justsyncit.scanner.FileProcessor;
import com.justsyncit.scanner.FilesystemScanner;
import com.justsyncit.scanner.ScanOptions;
import com.justsyncit.storage.ContentStore;
import com.justsyncit.hash.Blake3Service;
import com.justsyncit.storage.metadata.FileMetadata;
import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.storage.snapshot.MerkleNode;
import com.justsyncit.storage.snapshot.MerkleTree;
import com.justsyncit.backup.cbt.ChangedBlockTrackingService;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for backing up directories to content store.
 * Orchestrates complete backup workflow: scan → chunk → hash → store.
 */
public class BackupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackupService.class);

    private final ContentStore contentStore;
    private final MetadataService metadataService;
    private final FilesystemScanner scanner;
    private final FileChunker chunker;
    private final ChangedBlockTrackingService cbtService;
    private final Blake3Service blake3Service;
    private volatile FileProcessor.EventListener eventListener;

    public BackupService(ContentStore contentStore, MetadataService metadataService,
            FilesystemScanner scanner, FileChunker chunker,
            ChangedBlockTrackingService cbtService, Blake3Service blake3Service) {
        this.contentStore = contentStore;
        this.metadataService = metadataService;
        this.scanner = scanner;
        this.chunker = chunker;
        this.cbtService = cbtService;
        this.blake3Service = blake3Service;
    }

    public void setEventListener(FileProcessor.EventListener eventListener) {
        this.eventListener = eventListener;
    }

    public CompletableFuture<BackupResult> backup(Path sourceDir, BackupOptions options) {
        return backupMultiple(List.of(sourceDir), options, null);
    }

    public CompletableFuture<BackupResult> backupMultiple(List<Path> sourceDirs, BackupOptions options) {
        return backupMultiple(sourceDirs, options, null);
    }

    public CompletableFuture<BackupResult> backup(Path sourceDir, BackupOptions options,
            java.util.function.Consumer<FileProcessor> progressListener) {
        return backupMultiple(List.of(sourceDir), options, progressListener);
    }

    public CompletableFuture<BackupResult> backupMultiple(List<Path> sourceDirs, BackupOptions options,
            java.util.function.Consumer<FileProcessor> progressListener) {
        try {
            if (sourceDirs == null || sourceDirs.isEmpty())
                throw new IllegalArgumentException("Source directories cannot be null or empty");
            for (Path dir : sourceDirs) {
                if (dir == null)
                    throw new IllegalArgumentException("Source directory cannot be null");
                if (!java.nio.file.Files.exists(dir) || !java.nio.file.Files.isDirectory(dir)) {
                    throw new IllegalArgumentException("Directory must exist and be a directory: " + dir);
                }
            }
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(e);
        }

        return CompletableFuture
                .runAsync(() -> LOGGER.info("Starting multi-source backup of {} sources", sourceDirs.size()))
                .thenCompose(v -> CompletableFuture.supplyAsync(() -> {
                    try {
                        ScanOptions scanOptions = new ScanOptions()
                                .withSymlinkStrategy(options.getSymlinkStrategy())
                                .withIncludeHiddenFiles(options.isIncludeHiddenFiles())
                                .withMaxDepth(options.getMaxDepth());

                        if (options.getExcludePatterns() != null && !options.getExcludePatterns().isEmpty()) {
                            try {
                                StringBuilder globBuilder = new StringBuilder("glob:{");
                                for (int i = 0; i < options.getExcludePatterns().size(); i++) {
                                    if (i > 0)
                                        globBuilder.append(",");
                                    globBuilder.append(options.getExcludePatterns().get(i));
                                }
                                globBuilder.append("}");
                                scanOptions.withExcludePattern(
                                        java.nio.file.FileSystems.getDefault().getPathMatcher(globBuilder.toString()));
                            } catch (Exception e) {
                                LOGGER.warn("Failed to parse exclude patterns, ignoring: {}",
                                        options.getExcludePatterns());
                            }
                        }

                        String snapshotId = options.getSnapshotName() != null ? options.getSnapshotName()
                                : "backup-" + Instant.now().toString();

                        metadataService.createSnapshot(snapshotId,
                                options.getDescription() != null ? options.getDescription() : "Multi-source backup");

                        ChunkingOptions chunkingOptions = new ChunkingOptions().withChunkSize(options.getChunkSize());
                        FileProcessor processor = FileProcessor.create(scanner, chunker, contentStore, metadataService);
                        processor.setSnapshotId(snapshotId);
                        if (progressListener != null)
                            processor.setProgressListener(progressListener);
                        if (eventListener != null)
                            processor.setEventListener(eventListener);

                        return new BackupContext(snapshotId, processor, scanOptions, chunkingOptions);
                    } catch (Exception e) {
                        throw new RuntimeException("Init failed", e);
                    }
                }))
                .thenCompose(ctx -> {
                    CompletableFuture<FileProcessor.ProcessingResult> future = CompletableFuture.completedFuture(null);
                    for (Path sourceDir : sourceDirs) {
                        future = future.thenCompose(prev -> ctx.processor.processDirectory(sourceDir, ctx.scanOptions,
                                ctx.chunkingOptions));
                    }
                    return future.thenApply(result -> new ProcessingContext(ctx, result));
                })
                .thenApply(pCtx -> {
                    try {
                        String sid = pCtx.backupCtx.snapshotId;
                        FileProcessor.ProcessingResult res = pCtx.result;
                        if (blake3Service != null) {
                            MerkleTree tree = new MerkleTree(blake3Service);
                            MerkleNode root = tree.build(metadataService.getFilesInSnapshot(sid, true));
                            persistMerkleTree(root);
                            metadataService.setSnapshotRoot(sid, root.getHash());
                        }
                        return BackupResult.success(sid, res.getProcessedFiles(), res.getTotalBytes(),
                                (int) (res.getTotalBytes() / options.getChunkSize()) + 1, res.getErrorFiles(),
                                options.isVerifyIntegrity());
                    } catch (Exception e) {
                        throw new RuntimeException("Post-processing failed", e);
                    }
                })
                .exceptionally(e -> {
                    Throwable c = e instanceof java.util.concurrent.CompletionException ? e.getCause() : e;
                    LOGGER.error("Backup failed: {}", c.getMessage());
                    throw new java.util.concurrent.CompletionException(c);
                });
    }

    public CompletableFuture<BackupResult> backupIncremental(Path sourceDir, BackupOptions options,
            String previousSnapshotId) {
        if (cbtService == null)
            return backup(sourceDir, options);
        return CompletableFuture.supplyAsync(() -> {
            try {
                return metadataService.getSnapshot(previousSnapshotId);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).thenCompose(metadataOpt -> {
            if (metadataOpt.isEmpty())
                return backup(sourceDir, options);
            Instant lastBackupTime = metadataOpt.get().getCreatedAt();
            return CompletableFuture.supplyAsync(() -> cbtService.getChangedFiles(sourceDir, lastBackupTime))
                    .thenCompose(changedFiles -> {
                        String sid = options.getSnapshotName() != null ? options.getSnapshotName()
                                : "backup-inc-" + Instant.now().toString();
                        try {
                            metadataService.createSnapshot(sid, "Incremental backup base on " + previousSnapshotId);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                        FileProcessor processor = FileProcessor.create(scanner, chunker, contentStore, metadataService);
                        processor.setSnapshotId(sid);
                        if (eventListener != null)
                            processor.setEventListener(eventListener);
                        ChunkingOptions copts = new ChunkingOptions().withChunkSize(options.getChunkSize());
                        List<CompletableFuture<FileProcessor.ProcessingResult>> futures = changedFiles.stream()
                                .filter(f -> java.nio.file.Files.exists(f) && java.nio.file.Files.isRegularFile(f))
                                .map(f -> processor.processFile(f, copts)).collect(Collectors.toList());
                        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                                .thenApply(v -> {
                                    long tb = 0;
                                    int pc = 0, ec = 0;
                                    for (var f : futures) {
                                        try {
                                            var r = f.join();
                                            if (r != null) {
                                                tb += r.getTotalBytes();
                                                pc++;
                                            }
                                        } catch (Exception e) {
                                            ec++;
                                        }
                                    }
                                    return FileProcessor.ProcessingResult.create(null, pc, 0, ec, tb, tb);
                                })
                                .thenCompose(res -> CompletableFuture.supplyAsync(() -> {
                                    try {
                                        metadataService.copyUnchangedFiles(previousSnapshotId, sid,
                                                changedFiles.stream().map(Path::toString).collect(Collectors.toList()));
                                        return res;
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                }))
                                .thenApply(res -> {
                                    try {
                                        if (blake3Service != null) {
                                            MerkleTree tree = new MerkleTree(blake3Service);
                                            MerkleNode root = tree
                                                    .build(metadataService.getFilesInSnapshot(sid, false));
                                            persistMerkleTree(root);
                                            metadataService.setSnapshotRoot(sid, root.getHash());
                                        }
                                        return BackupResult.success(sid, res.getProcessedFiles(), res.getTotalBytes(),
                                                (int) (res.getTotalBytes() / options.getChunkSize()) + 1,
                                                res.getErrorFiles(), false);
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                });
                    });
        });
    }

    private void persistMerkleTree(MerkleNode root) throws java.io.IOException {
        if (root == null)
            return;
        java.util.Stack<MerkleNode> stack = new java.util.Stack<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            MerkleNode node = stack.pop();
            metadataService.upsertMerkleNode(node);
            List<MerkleNode> children = node.getChildren();
            if (node.getType() == MerkleNode.Type.DIRECTORY && children != null) {
                for (MerkleNode child : children)
                    stack.push(child);
            }
        }
    }

    private static class BackupContext {
        final String snapshotId;
        final FileProcessor processor;
        final ScanOptions scanOptions;
        final ChunkingOptions chunkingOptions;

        BackupContext(String sid, FileProcessor p, ScanOptions so, ChunkingOptions co) {
            this.snapshotId = sid;
            this.processor = p;
            this.scanOptions = so;
            this.chunkingOptions = co;
        }
    }

    private static class ProcessingContext {
        final BackupContext backupCtx;
        final FileProcessor.ProcessingResult result;

        ProcessingContext(BackupContext bc, FileProcessor.ProcessingResult r) {
            this.backupCtx = bc;
            this.result = r;
        }
    }

    public static class BackupResult {
        private final String snapshotId;
        private final int filesProcessed;
        private final long totalBytesProcessed;
        private final int chunksCreated;
        private final int filesWithErrors;
        private final boolean integrityVerified;
        private final boolean success;
        private final String error;

        private BackupResult(String sid, int fp, long tbp, int cc, int fwe, boolean iv, boolean s, String e) {
            this.snapshotId = sid;
            this.filesProcessed = fp;
            this.totalBytesProcessed = tbp;
            this.chunksCreated = cc;
            this.filesWithErrors = fwe;
            this.integrityVerified = iv;
            this.success = s;
            this.error = e;
        }

        public static BackupResult success(String sid, int fp, long tbp, int cc, int fwe, boolean iv) {
            return new BackupResult(sid, fp, tbp, cc, fwe, iv, true, null);
        }

        public static BackupResult failure(String e) {
            return new BackupResult(null, 0, 0, 0, 0, false, false, e);
        }

        public String getSnapshotId() {
            return snapshotId;
        }

        public int getFilesProcessed() {
            return filesProcessed;
        }

        public long getTotalBytesProcessed() {
            return totalBytesProcessed;
        }

        public int getChunksCreated() {
            return chunksCreated;
        }

        public int getFilesWithErrors() {
            return filesWithErrors;
        }

        public boolean isIntegrityVerified() {
            return integrityVerified;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getError() {
            return error;
        }
    }
}