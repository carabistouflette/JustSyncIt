package com.justsyncit.web.model;

import com.justsyncit.network.encryption.Argon2idKeyDerivationService;
import com.justsyncit.network.encryption.EncryptionException;
import java.util.Base64;

/**
 * User model class.
 */
public class User {
    private static final String ARGON2_PREFIX = "$ARGON2ID$";
    private static final Argon2idKeyDerivationService SHARED_ARGON2_SERVICE = new Argon2idKeyDerivationService();

    private String id;
    private String username;
    private String displayName;
    private String role;
    private String passwordHash;
    private String salt;

    // Default constructor for Jackson
    public User() {
    }

    public User(String id, String username, String displayName, String role) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.role = role;
    }

    public void setPassword(String password) {
        // [SEC-002] Use shared service instance instead of creating new one
        byte[] saltBytes = SHARED_ARGON2_SERVICE.generateSalt();
        this.salt = Base64.getEncoder().encodeToString(saltBytes);
        try {
            byte[] hash = SHARED_ARGON2_SERVICE.deriveKey(password.toCharArray(), saltBytes, 32);
            this.passwordHash = ARGON2_PREFIX + Base64.getEncoder().encodeToString(hash);
        } catch (EncryptionException e) {
            throw new RuntimeException(e);
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }
}
