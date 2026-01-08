package com.justsyncit.auth;

import com.justsyncit.network.encryption.EncryptionException;
import com.justsyncit.network.encryption.KeyDerivationService;
import com.justsyncit.network.encryption.Argon2idKeyDerivationService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.Properties;

/**
 * Service for managing the master password and deriving encryption keys.
 */
public class MasterPasswordService {
    // private static final Logger logger =
    // LoggerFactory.getLogger(MasterPasswordService.class);
    private static final String CONFIG_FILE = ".justsyncit/auth.config";
    private static final String PROP_HASH = "master.hash";
    private static final String PROP_SALT = "master.salt";

    private final KeyDerivationService kdfService;
    private final Path configPath;
    private byte[] derivedMasterKey;

    public MasterPasswordService() {
        this(new Argon2idKeyDerivationService(),
                Paths.get(System.getProperty("user.home"), CONFIG_FILE));
    }

    public MasterPasswordService(KeyDerivationService kdfService, Path configPath) {
        this.kdfService = kdfService;
        this.configPath = configPath;
    }

    /**
     * Checks if a master password has been set.
     */
    public boolean isPasswordSet() {
        return Files.exists(configPath);
    }

    /**
     * Sets the initial master password.
     */
    public void setupPassword(char[] password) throws EncryptionException {
        if (isPasswordSet()) {
            throw new IllegalStateException("Password already set");
        }

        byte[] salt = kdfService.generateSalt();
        byte[] hash = kdfService.deriveKey(password, salt);

        saveAuthBootstrap(hash, salt);
        this.derivedMasterKey = hash; // The first hash is used as the master key
    }

    /**
     * Verifies the master password and derives the master key.
     */
    public boolean verifyAndDerive(char[] password) throws EncryptionException {
        if (!isPasswordSet()) {
            return false;
        }

        try {
            Properties props = loadProps();
            byte[] storedHash = Base64.getDecoder().decode(props.getProperty(PROP_HASH));
            byte[] salt = Base64.getDecoder().decode(props.getProperty(PROP_SALT));

            byte[] computedHash = kdfService.deriveKey(password, salt);

            if (Arrays.equals(storedHash, computedHash)) {
                this.derivedMasterKey = computedHash;
                return true;
            }
            return false;
        } catch (IOException e) {
            throw new EncryptionException("Failed to read auth config", e);
        }
    }

    /**
     * Returns the derived master key for encryption.
     */
    public byte[] getMasterKey() {
        return derivedMasterKey != null ? derivedMasterKey.clone() : null;
    }

    private void saveAuthBootstrap(byte[] hash, byte[] salt) throws EncryptionException {
        try {
            Files.createDirectories(configPath.getParent());
            Properties props = new Properties();
            props.setProperty(PROP_HASH, Base64.getEncoder().encodeToString(hash));
            props.setProperty(PROP_SALT, Base64.getEncoder().encodeToString(salt));

            try (var out = Files.newOutputStream(configPath)) {
                props.store(out, "JustSyncIt Auth Configuration");
            }
        } catch (IOException e) {
            throw new EncryptionException("Failed to save auth config", e);
        }
    }

    private Properties loadProps() throws IOException {
        Properties props = new Properties();
        try (var in = Files.newInputStream(configPath)) {
            props.load(in);
        }
        return props;
    }

    public void logout() {
        if (derivedMasterKey != null) {
            Arrays.fill(derivedMasterKey, (byte) 0);
            derivedMasterKey = null;
        }
    }
}
