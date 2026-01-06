package com.justsyncit.modules;

import com.justsyncit.ServiceException;
import com.justsyncit.hash.Blake3BufferHasher;
import com.justsyncit.hash.Blake3FileHasher;
import com.justsyncit.hash.Blake3IncrementalHasherFactory;
import com.justsyncit.hash.Blake3Service;
import com.justsyncit.hash.Blake3ServiceImpl;
import com.justsyncit.hash.Blake3StreamHasher;
import com.justsyncit.hash.BufferHasher;
import com.justsyncit.hash.FileHasher;
import com.justsyncit.hash.HashAlgorithm;
import com.justsyncit.hash.HashingException;
import com.justsyncit.hash.IncrementalHasherFactory;
import com.justsyncit.hash.StreamHasher;
import com.justsyncit.hash.Blake3HashAlgorithm;
import com.justsyncit.simd.SimdDetectionService;
import com.justsyncit.simd.SimdDetectionServiceImpl;

/**
 * Module responsible for creating security and cryptography services.
 */
public class SecurityModule {

    public Blake3Service createBlake3Service() throws ServiceException {
        try {
            HashAlgorithm bufferHasherAlgorithm = Blake3HashAlgorithm.create();
            HashAlgorithm incrementalHasherAlgorithm = Blake3HashAlgorithm.create();

            BufferHasher bufferHasher = new Blake3BufferHasher(bufferHasherAlgorithm);
            IncrementalHasherFactory incrementalHasherFactory = new Blake3IncrementalHasherFactory(
                    incrementalHasherAlgorithm);
            StreamHasher streamHasher = new Blake3StreamHasher(incrementalHasherFactory);
            FileHasher fileHasher = new Blake3FileHasher(streamHasher, bufferHasher);
            SimdDetectionService simdDetectionService = new SimdDetectionServiceImpl();

            return new Blake3ServiceImpl(
                    fileHasher, bufferHasher, streamHasher,
                    incrementalHasherFactory, simdDetectionService);
        } catch (HashingException e) {
            throw new ServiceException("Failed to create BLAKE3 service", e);
        }
    }

    public com.justsyncit.network.encryption.EncryptionService createEncryptionService() {
        return new com.justsyncit.network.encryption.AesGcmEncryptionService();
    }
}
