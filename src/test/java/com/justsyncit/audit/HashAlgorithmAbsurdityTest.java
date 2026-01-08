package com.justsyncit.audit;

import com.justsyncit.hash.Blake3HashAlgorithm;
import com.justsyncit.hash.Blake3IncrementalHasherFactory;
import com.justsyncit.hash.HashAlgorithm;
import com.justsyncit.hash.Sha256HashAlgorithm;
import com.justsyncit.hash.IncrementalHasherFactory.IncrementalHasher;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class HashAlgorithmAbsurdityTest {

    @Test
    void verifyHashAlgorithmCorrectness() throws Exception {
        byte[] input = "JustSyncIt".getBytes(StandardCharsets.UTF_8);

        // 1. Calculate Expected BLAKE3 Hash
        HashAlgorithm blake3 = Blake3HashAlgorithm.create();
        blake3.update(input);
        String expectedBlake3 = HexFormat.of().formatHex(blake3.digest());

        // 2. Calculate Expected SHA-256 Hash
        MessageDigest sha256Digest = MessageDigest.getInstance("SHA-256");
        byte[] sha256Bytes = sha256Digest.digest(input);
        String expectedSha256 = HexFormat.of().formatHex(sha256Bytes);

        // 3. Use the Factory currently used in FixedSizeFileChunker
        // Note: We need to instantiate it exactly as the app does now.
        // FileTransferManagerImpl now does: new Blake3IncrementalHasherFactory(Blake3HashAlgorithm.create())
        // But verifying the factory itself is good.
        // We want to verify that *if* we ask for Blake3, we get Blake3.
        
        // Use the default constructor for factory if available, or inject the correct algo
        // Based on my fix, I injected Blake3HashAlgorithm.create()
        Blake3IncrementalHasherFactory factory = new Blake3IncrementalHasherFactory(Blake3HashAlgorithm.create());
        IncrementalHasher hasher = factory.createIncrementalHasher();
        hasher.update(input);
        String actualHash = hasher.digest();

        System.out.println("Expected BLAKE3:  " + expectedBlake3);
        System.out.println("Expected SHA-256: " + expectedSha256);
        System.out.println("Actual Factory:   " + actualHash);

        // Verification: The factory MUST produce BLAKE3
        assertEquals(expectedBlake3, actualHash,
                "Remediation verification: Blake3IncrementalHasherFactory produced correct BLAKE3 hash.");

        assertNotEquals(expectedSha256, actualHash,
                "Remediation verification: Blake3IncrementalHasherFactory did NOT produce SHA-256 hash.");
    }
}
