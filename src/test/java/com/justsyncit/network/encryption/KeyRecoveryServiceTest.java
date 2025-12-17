package com.justsyncit.network.encryption;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class KeyRecoveryServiceTest {

    private KeyRecoveryService service;

    @BeforeEach
    void setUp() {
        service = new KeyRecoveryService();
    }

    @Test
    void testGenerateAndRecoverCode() {
        byte[] key = new byte[32];
        new Random().nextBytes(key);

        String code = service.generateRecoveryCode(key);
        assertNotNull(code);
        assertTrue(code.contains("-")); // Should separate groups
        assertEquals(code.toUpperCase(), code); // Should be uppercase

        byte[] recovered = service.recoverKey(code);
        assertArrayEquals(key, recovered);
    }

    @Test
    void testRecoverWithVariations() {
        byte[] key = new byte[] { (byte) 0xAB, (byte) 0xCD };
        // Expected code: "ABCD"
        // But service formats with dashes: "ABCD" (less than 4? loop says >0 && %4==0.
        // "ABCD" is length 4. index 0,1,2,3. No dash).

        // Let's use simpler manual check
        String validCode = "ABCD-EF01";
        byte[] expected = new byte[] { (byte) 0xAB, (byte) 0xCD, (byte) 0xEF, (byte) 0x01 };

        // Standard
        assertArrayEquals(expected, service.recoverKey("ABCD-EF01"));
        // Lowercase
        assertArrayEquals(expected, service.recoverKey("abcd-ef01"));
        // No dashes
        assertArrayEquals(expected, service.recoverKey("ABCDEF01"));
        // Mixed
        assertArrayEquals(expected, service.recoverKey("AbCd-Ef01"));
    }

    @Test
    void testExportAndImportKey(@TempDir Path tempDir) throws IOException {
        byte[] key = new byte[32];
        new Random().nextBytes(key);
        Path keyFile = tempDir.resolve("backup.key");

        service.exportKeyToFile(key, keyFile);
        assertTrue(Files.exists(keyFile));

        byte[] imported = service.importKeyFromFile(keyFile);
        assertArrayEquals(key, imported);

        // Verify content is text
        String content = Files.readString(keyFile);
        assertFalse(content.isEmpty());
    }

    @Test
    void testInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> service.generateRecoveryCode(null));
        assertThrows(IllegalArgumentException.class, () -> service.generateRecoveryCode(new byte[0]));

        assertThrows(IllegalArgumentException.class, () -> service.recoverKey(null));
        assertThrows(IllegalArgumentException.class, () -> service.recoverKey(""));
        assertThrows(IllegalArgumentException.class, () -> service.recoverKey("Invalid@Char"));
        assertThrows(IllegalArgumentException.class, () -> service.recoverKey("OddLength")); // Hex must be even length
    }
}
