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
    private static final String PROP_WRAPPED_KEY = "master.wrapped_key";
    private static final String PROP_SALT = "master.salt";
    private static final String PROP_IV = "master.iv";

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

        try {
            // 1. Generate a random 32-byte Master Key
            byte[] masterKey = new byte[32];
            java.security.SecureRandom secureRandom = new java.security.SecureRandom();
            secureRandom.nextBytes(masterKey);

            // 2. Derive Key Encryption Key (KEK) from password
            byte[] salt = kdfService.generateSalt();
            byte[] kek = kdfService.deriveKey(password, salt, 32);

            // 3. Wrap (Encrypt) the Master Key using AES-GCM
            byte[] iv = new byte[12]; // 96-bit IV for GCM
            secureRandom.nextBytes(iv);

            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            javax.crypto.spec.GCMParameterSpec gcmSpec = new javax.crypto.spec.GCMParameterSpec(128, iv);
            javax.crypto.spec.SecretKeySpec kekSpec = new javax.crypto.spec.SecretKeySpec(kek, "AES");

            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, kekSpec, gcmSpec);
            byte[] wrappedKey = cipher.doFinal(masterKey);

            saveAuthConfig(wrappedKey, salt, iv);
            this.derivedMasterKey = masterKey;

            // Clean up KEK from memory
            Arrays.fill(kek, (byte) 0);

        } catch (Exception e) {
            throw new EncryptionException("Failed to setup password", e);
        }
    }

    /**
     * Verifies the master password and unwraps the master key.
     */
    public boolean verifyAndDerive(char[] password) throws EncryptionException {
        if (!isPasswordSet()) {
            return false;
        }

        try {
            Properties props = loadProps();
            if (!props.containsKey(PROP_WRAPPED_KEY)) {
                // Determine if this is legacy config or corrupted
                throw new EncryptionException("Invalid auth configuration: missing wrapped key");
            }

            byte[] wrappedKey = Base64.getDecoder().decode(props.getProperty(PROP_WRAPPED_KEY));
            byte[] salt = Base64.getDecoder().decode(props.getProperty(PROP_SALT));
            byte[] iv = Base64.getDecoder().decode(props.getProperty(PROP_IV));

            byte[] kek = kdfService.deriveKey(password, salt, 32);

            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            javax.crypto.spec.GCMParameterSpec gcmSpec = new javax.crypto.spec.GCMParameterSpec(128, iv);
            javax.crypto.spec.SecretKeySpec kekSpec = new javax.crypto.spec.SecretKeySpec(kek, "AES");

            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, kekSpec, gcmSpec);

            try {
                this.derivedMasterKey = cipher.doFinal(wrappedKey);
                Arrays.fill(kek, (byte) 0);
                return true;
            } catch (javax.crypto.AEADBadTagException e) {
                Arrays.fill(kek, (byte) 0);
                return false;
            }

        } catch (IOException e) {
            throw new EncryptionException("Failed to read auth config", e);
        } catch (Exception e) {
            throw new EncryptionException("Failed to verify/derive key", e);
        }
    }

    /**
     * Returns the derived master key for encryption.
     */
    public byte[] getMasterKey() {
        return derivedMasterKey != null ? derivedMasterKey.clone() : null;
    }

    private void saveAuthConfig(byte[] wrappedKey, byte[] salt, byte[] iv) throws EncryptionException {
        try {
            Files.createDirectories(configPath.getParent());
            Properties props = new Properties();
            props.setProperty(PROP_WRAPPED_KEY, Base64.getEncoder().encodeToString(wrappedKey));
            props.setProperty(PROP_SALT, Base64.getEncoder().encodeToString(salt));
            props.setProperty(PROP_IV, Base64.getEncoder().encodeToString(iv));

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
