package com.justsyncit.modules;

import com.justsyncit.ServiceException;
import com.justsyncit.hash.Blake3Service;
import com.justsyncit.hash.HashingException;
import com.justsyncit.scanner.AsyncBatchProcessor;
import com.justsyncit.scanner.AsyncByteBufferPool;
import com.justsyncit.scanner.AsyncByteBufferPoolImpl;
import com.justsyncit.scanner.AsyncFileBatchProcessorImpl;
import com.justsyncit.scanner.AsyncFileChunker;
import com.justsyncit.scanner.AsyncFileChunkerImpl;
import com.justsyncit.scanner.AsyncFilesystemScanner;
import com.justsyncit.scanner.AsyncFilesystemScannerImpl;
import com.justsyncit.scanner.BatchAwareAsyncFileChunker;
import com.justsyncit.scanner.BatchConfiguration;
import com.justsyncit.scanner.ChunkingOptions;
import com.justsyncit.scanner.FastCDCFileChunker;
import com.justsyncit.scanner.FileChunker;
import com.justsyncit.scanner.FixedSizeFileChunker;
import com.justsyncit.scanner.ThreadPoolManager;

/**
 * Module responsible for creating scanner and chunking related services.
 */
public class ScannerModule {

    /**
     * Creates a thread pool manager.
     */
    public ThreadPoolManager createThreadPoolManager() throws ServiceException {
        try {
            return ThreadPoolManager.getInstance();
        } catch (RuntimeException e) {
            throw new ServiceException("Failed to create thread pool manager", e);
        }
    }

    /**
     * Creates an async buffer pool.
     */
    public AsyncByteBufferPool createAsyncByteBufferPool() throws ServiceException {
        try {
            return AsyncByteBufferPoolImpl.create();
        } catch (RuntimeException e) {
            throw new ServiceException("Failed to create async buffer pool", e);
        }
    }

    /**
     * Creates an async filesystem scanner.
     */
    public AsyncFilesystemScanner createAsyncFilesystemScanner() throws ServiceException {
        try {
            AsyncByteBufferPool bufferPool = createAsyncByteBufferPool();
            ThreadPoolManager threadPoolManager = createThreadPoolManager();
            return new AsyncFilesystemScannerImpl(threadPoolManager, bufferPool);
        } catch (RuntimeException e) {
            throw new ServiceException("Failed to create async filesystem scanner", e);
        }
    }

    /**
     * Creates an async file chunker.
     */
    public AsyncFileChunker createAsyncFileChunker(Blake3Service blake3Service) throws ServiceException {
        try {
            return AsyncFileChunkerImpl.create(blake3Service);
        } catch (HashingException e) {
            throw new ServiceException("Failed to create async file chunker", e);
        }
    }

    /**
     * Creates a batch-aware async file chunker.
     */
    public AsyncFileChunker createBatchAsyncFileChunker(Blake3Service blake3Service) throws ServiceException {
        try {
            AsyncByteBufferPool bufferPool = createAsyncByteBufferPool();
            ThreadPoolManager threadPoolManager = createThreadPoolManager();
            AsyncFileChunker delegateChunker = AsyncFileChunkerImpl.create(blake3Service);
            AsyncBatchProcessor batchProcessor = AsyncFileBatchProcessorImpl.create(delegateChunker, bufferPool,
                    threadPoolManager);
            BatchConfiguration batchConfig = new BatchConfiguration();
            return new BatchAwareAsyncFileChunker(delegateChunker, batchProcessor, batchConfig);
        } catch (HashingException e) {
            throw new ServiceException("Failed to create batch async file chunker", e);
        }
    }

    /**
     * Creates a file chunker based on the provided algorithm.
     */
    public FileChunker createFileChunker(Blake3Service blake3Service, ChunkingOptions.ChunkingAlgorithm algorithm)
            throws ServiceException {
        if (algorithm == ChunkingOptions.ChunkingAlgorithm.CDC) {
            return createFastCDCFileChunker(blake3Service);
        } else {
            return FixedSizeFileChunker.create(blake3Service);
        }
    }

    /**
     * Creates a FastCDC file chunker.
     */
    public FileChunker createFastCDCFileChunker(Blake3Service blake3Service) throws ServiceException {
        try {
            return FastCDCFileChunker.create(blake3Service);
        } catch (Exception e) {
            throw new ServiceException("Failed to create FastCDC file chunker", e);
        }
    }
}
