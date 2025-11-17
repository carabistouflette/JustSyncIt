package com.justsyncit.scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * NIO-based filesystem scanner implementation.
 * Provides recursive directory walking with filtering, symlink handling, and sparse file detection.
 */
public class NioFilesystemScanner implements FilesystemScanner {
    private static final Logger logger = LoggerFactory.getLogger(NioFilesystemScanner.class);
    
    private FileVisitor fileVisitor;
    private ProgressListener progressListener;
    
    @Override
    public CompletableFuture<ScanResult> scanDirectory(Path directory, ScanOptions options) {
        return CompletableFuture.supplyAsync(() -> {
            final ScanOptions finalOptions = options != null ? options : new ScanOptions();
            
            if (directory == null) {
                throw new IllegalArgumentException("Directory cannot be null");
            }
            
            // Check if directory exists and is actually a directory
            if (!Files.exists(directory)) {
                throw new IllegalArgumentException("Directory does not exist: " + directory);
            }
            if (!Files.isDirectory(directory)) {
                throw new IllegalArgumentException("Path is not a directory: " + directory);
            }
            
            logger.info("Starting scan of directory: {} with options: {}", directory, finalOptions);
            
            Instant startTime = Instant.now();
            List<ScanResult.ScannedFile> scannedFiles = new ArrayList<>();
            List<ScanResult.ScanError> errors = new ArrayList<>();
            Map<String, Object> metadata = new HashMap<>();
            AtomicLong filesProcessed = new AtomicLong(0);
            
            if (progressListener != null) {
                progressListener.onScanStarted(directory);
            }
            
            try {
                NioFileVisitor nioVisitor = new NioFileVisitor(finalOptions, scannedFiles, errors, filesProcessed);
                
                Set<FileVisitOption> visitOptions = EnumSet.noneOf(FileVisitOption.class);
                if (finalOptions.getSymlinkStrategy() == SymlinkStrategy.FOLLOW) {
                    visitOptions.add(FileVisitOption.FOLLOW_LINKS);
                }
                
                Files.walkFileTree(directory, visitOptions, finalOptions.getMaxDepth(), nioVisitor);
                
                Instant endTime = Instant.now();
                ScanResult result = new ScanResult(directory, scannedFiles, errors, startTime, endTime, metadata);
                
                if (progressListener != null) {
                    progressListener.onScanCompleted(result);
                }
                
                logger.info("Scan completed. Files: {}, Errors: {}, Duration: {}ms", 
                    scannedFiles.size(), errors.size(), result.getDurationMillis());
                
                return result;
                
            } catch (IOException e) {
                logger.error("Error scanning directory: {}", directory, e);
                errors.add(new ScanResult.ScanError(directory, e, e.getMessage()));
                Instant endTime = Instant.now();
                ScanResult result = new ScanResult(directory, scannedFiles, errors, startTime, endTime, metadata);
                
                if (progressListener != null) {
                    progressListener.onScanError(directory, e);
                }
                
                return result;
            }
        });
    }
    
    @Override
    public void setFileVisitor(FileVisitor visitor) {
        this.fileVisitor = visitor;
    }
    
    @Override
    public void setProgressListener(ProgressListener listener) {
        this.progressListener = listener;
    }
    
    /**
     * Internal NIO file visitor implementation.
     */
    private class NioFileVisitor extends SimpleFileVisitor<Path> {
        private final ScanOptions options;
        private final List<ScanResult.ScannedFile> scannedFiles;
        private final List<ScanResult.ScanError> errors;
        private final AtomicLong filesProcessed;
        private final Set<Path> visitedPaths = ConcurrentHashMap.newKeySet();
        
        public NioFileVisitor(ScanOptions options, List<ScanResult.ScannedFile> scannedFiles, 
                            List<ScanResult.ScanError> errors, AtomicLong filesProcessed) {
            this.options = options;
            this.scannedFiles = scannedFiles;
            this.errors = errors;
            this.filesProcessed = filesProcessed;
        }
        
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            if (!isRunning()) {
                return FileVisitResult.TERMINATE;
            }
            
            // Check for symlink cycles
            if (Files.isSymbolicLink(dir)) {
                try {
                    Path realPath = dir.toRealPath();
                    if (visitedPaths.contains(realPath)) {
                        logger.warn("Symlink cycle detected: {} -> {}", dir, realPath);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    visitedPaths.add(realPath);
                } catch (IOException e) {
                    logger.warn("Cannot resolve symlink: {}", dir, e);
                    return FileVisitResult.SKIP_SUBTREE;
                }
            }
            
            // Don't apply pattern matching to directories, only to files
            // This allows pattern matching to work on files within directories
            
            // Handle hidden directories
            if (!options.isIncludeHiddenFiles() && Files.isHidden(dir)) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            
            // Call custom file visitor if set
            if (fileVisitor != null) {
                FileVisitor.FileVisitResult customResult = fileVisitor.visitDirectory(dir, attrs);
                switch (customResult) {
                    case SKIP_SUBTREE:
                        return FileVisitResult.SKIP_SUBTREE;
                    case SKIP:
                        return FileVisitResult.SKIP_SIBLINGS;
                    case TERMINATE:
                        return FileVisitResult.TERMINATE;
                    case CONTINUE:
                    default:
                        break;
                }
            }
            
            return FileVisitResult.CONTINUE;
        }
        
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (!isRunning()) {
                return FileVisitResult.TERMINATE;
            }
            
            try {
                // Apply filtering
                if (!matchesIncludePattern(file) || matchesExcludePattern(file)) {
                    return FileVisitResult.CONTINUE;
                }
                
                // Handle hidden files
                if (!options.isIncludeHiddenFiles() && Files.isHidden(file)) {
                    return FileVisitResult.CONTINUE;
                }
                
                // Check file size limits
                long fileSize = attrs.size();
                if (options.getMinFileSize() > 0 && fileSize < options.getMinFileSize()) {
                    return FileVisitResult.CONTINUE;
                }
                if (options.getMaxFileSize() > 0 && fileSize > options.getMaxFileSize()) {
                    return FileVisitResult.CONTINUE;
                }
                
                // Handle symlinks
                boolean isSymlink = Files.isSymbolicLink(file);
                Path linkTarget = null;
                
                if (isSymlink) {
                    try {
                        linkTarget = Files.readSymbolicLink(file);
                        
                        switch (options.getSymlinkStrategy()) {
                            case SKIP:
                                return FileVisitResult.CONTINUE;
                            case FOLLOW:
                                Path realPath = file.toRealPath();
                                if (visitedPaths.contains(realPath)) {
                                    logger.warn("Symlink cycle detected: {} -> {}", file, realPath);
                                    return FileVisitResult.CONTINUE;
                                }
                                visitedPaths.add(realPath);
                                break;
                            case RECORD:
                                // Record symlink but don't follow
                                break;
                        }
                    } catch (IOException e) {
                        logger.warn("Cannot read symlink: {}", file, e);
                        errors.add(new ScanResult.ScanError(file, e, "Cannot read symlink"));
                        return FileVisitResult.CONTINUE;
                    }
                }
                
                // Detect sparse files
                boolean isSparse = detectSparseFile(file, attrs);
                
                // Create scanned file record
                ScanResult.ScannedFile scannedFile = new ScanResult.ScannedFile(
                    file, fileSize, attrs.lastModifiedTime().toInstant(), isSymlink, isSparse, linkTarget
                );
                scannedFiles.add(scannedFile);
                
                // Call custom file visitor if set
                if (fileVisitor != null) {
                    FileVisitor.FileVisitResult customResult = fileVisitor.visitFile(file, attrs);
                    switch (customResult) {
                        case SKIP_SUBTREE:
                            return FileVisitResult.SKIP_SIBLINGS;
                        case SKIP:
                            return FileVisitResult.CONTINUE;
                        case TERMINATE:
                            return FileVisitResult.TERMINATE;
                        case CONTINUE:
                        default:
                            break;
                    }
                }
                
                // Update progress
                long processed = filesProcessed.incrementAndGet();
                if (progressListener != null) {
                    progressListener.onFileProcessed(file, processed, -1); // Unknown total
                }
                
            } catch (Exception e) {
                logger.error("Error visiting file: {}", file, e);
                errors.add(new ScanResult.ScanError(file, e, e.getMessage()));
                
                if (progressListener != null) {
                    progressListener.onScanError(file, e);
                }
            }
            
            return FileVisitResult.CONTINUE;
        }
        
        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            logger.warn("Failed to visit file: {}", file, exc);
            errors.add(new ScanResult.ScanError(file, exc, exc.getMessage()));
            
            if (progressListener != null) {
                progressListener.onScanError(file, exc);
            }
            
            // Call custom file visitor if set
            if (fileVisitor != null) {
                FileVisitor.FileVisitResult customResult = fileVisitor.visitFailed(file, exc);
                switch (customResult) {
                    case TERMINATE:
                        return FileVisitResult.TERMINATE;
                    case SKIP_SUBTREE:
                        return FileVisitResult.SKIP_SIBLINGS;
                    case SKIP:
                    case CONTINUE:
                    default:
                        break;
                }
            }
            
            return FileVisitResult.CONTINUE;
        }
        
        private boolean matchesIncludePattern(Path path) {
            if (options.getIncludePattern() == null) {
                return true;
            }
            return options.getIncludePattern().matches(path);
        }
        
        private boolean matchesExcludePattern(Path path) {
            if (options.getExcludePattern() == null) {
                return false;
            }
            return options.getExcludePattern().matches(path);
        }
        
        private boolean detectSparseFile(Path file, BasicFileAttributes attrs) {
            if (!options.isDetectSparseFiles()) {
                return false;
            }
            
            try {
                // On Unix systems, check for sparse file attribute
                if (System.getProperty("os.name").toLowerCase().contains("linux") ||
                    System.getProperty("os.name").toLowerCase().contains("mac")) {
                    
                    try {
                        Object sparseAttr = Files.getAttribute(file, "unix:sparse");
                        if (sparseAttr instanceof Boolean) {
                            return (Boolean) sparseAttr;
                        }
                    } catch (IllegalArgumentException e) {
                        // 'sparse' attribute not supported on this filesystem
                        logger.debug("Sparse attribute not supported for: {}", file);
                    }
                }
                
                // Fallback: compare logical size with actual block allocation
                try {
                    long logicalSize = attrs.size();
                    Object blockSize = Files.getAttribute(file, "unix:blocksize");
                    Object blocks = Files.getAttribute(file, "unix:blocks");
                    
                    if (blockSize instanceof Integer && blocks instanceof Long) {
                        long allocatedSize = (Long) blocks * (Integer) blockSize;
                        // If allocated size is significantly less than logical size, it's likely sparse
                        return allocatedSize < logicalSize * 0.9;
                    }
                } catch (IllegalArgumentException e) {
                    // Block attributes not supported on this filesystem
                    logger.debug("Block attributes not supported for: {}", file);
                }
                
                // Windows-specific sparse file detection
                if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                    try {
                        // For Windows, we can use a heuristic approach
                        // Check if the file has sparse characteristics by reading sample data
                        long fileSize = attrs.size();
                        if (fileSize > 64 * 1024) { // Only check files larger than 64KB
                            try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file.toFile(), "r")) {
                                // Sample different parts of the file
                                long[] positions = {0, fileSize / 4, fileSize / 2, fileSize * 3 / 4, Math.max(fileSize - 1024, 0)};
                                int zeroRegions = 0;
                                
                                for (long pos : positions) {
                                    if (pos < fileSize) {
                                        raf.seek(pos);
                                        byte[] buffer = new byte[1024];
                                        int bytesRead = raf.read(buffer);
                                        
                                        if (bytesRead > 0) {
                                            boolean allZeros = true;
                                            for (int i = 0; i < bytesRead; i++) {
                                                if (buffer[i] != 0) {
                                                    allZeros = false;
                                                    break;
                                                }
                                            }
                                            if (allZeros) {
                                                zeroRegions++;
                                            }
                                        }
                                    }
                                }
                                
                                // If most sampled regions are zeros, likely sparse
                                return zeroRegions >= positions.length * 0.6;
                            }
                        }
                    } catch (Exception e) {
                        logger.debug("Windows sparse detection failed for: {}", file, e);
                    }
                }
                
            } catch (UnsupportedOperationException e) {
                // Attribute not supported on this platform
                logger.debug("Sparse file detection not supported for: {}", file);
            } catch (IOException e) {
                logger.debug("Error checking sparse file attributes for: {}", file, e);
            }
            
            return false;
        }
        
        private boolean isRunning() {
            // This could be enhanced with external cancellation support
            return true;
        }
    }
}