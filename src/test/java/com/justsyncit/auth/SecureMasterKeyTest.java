package com.justsyncit.auth;

import com.justsyncit.network.encryption.Argon2idKeyDerivationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class SecureMasterKeyTest {

    @TempDir
    Path tempDir;

    private MasterPasswordService service;
    private Path configPath;

    @BeforeEach
    void setUp() {
        configPath = tempDir.resolve("auth.config");
        service = new MasterPasswordService(new Argon2idKeyDerivationService(), configPath);
    }

    @Test
    void testSetupPasswordEncrypted() throws Exception {
        char[] password = "strong-password-123".toCharArray();
        service.setupPassword(password);

        assertTrue(Files.exists(configPath));

        Properties props = new Properties();
        try (var in = Files.newInputStream(configPath)) {
            props.load(in);
        }

        // Verify SECURITY: Hash should NOT be present
        assertFalse(props.containsKey("master.hash"), "Plain hash should not be stored");

        // Verify correct keys are present
        assertTrue(props.containsKey("master.wrapped_key"), "Wrapped key should be present");
        assertTrue(props.containsKey("master.salt"), "Salt should be present");
        assertTrue(props.containsKey("master.iv"), "IV should be present");
    }

    @Test
    void testVerifyAndDeriveSuccess() throws Exception {
        char[] password = "correct-password".toCharArray();
        service.setupPassword(password);

        assertTrue(service.verifyAndDerive(password));
        assertNotNull(service.getMasterKey());
        assertEquals(32, service.getMasterKey().length);
    }

    @Test
    void testVerifyAndDeriveFailure() throws Exception {
        char[] password = "correct-password".toCharArray();
        service.setupPassword(password);

        assertFalse(service.verifyAndDerive("wrong-password".toCharArray()));
    }
}
