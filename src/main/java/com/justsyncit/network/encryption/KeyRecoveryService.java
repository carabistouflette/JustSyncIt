package com.justsyncit.network.encryption;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Service for securely handling encryption key recovery and export.
 * Provides mechanisms to convert raw keys to human-readable recovery codes
 * and export/import keys to/from files.
 */
public class KeyRecoveryService {

    private static final HexFormat HEX_FORMAT = HexFormat.of().withUpperCase();
    // Pattern to match recovery code format (Hex with optional dashes)
    private static final Pattern RECOVERY_CODE_PATTERN = Pattern.compile("^[0-9A-F\\-]+$");

    /**
     * Generates a human-readable recovery code from the master key.
     * The code is formatted as groups of 4 hex characters separated by dashes.
     *
     * @param key the master key bytes
     * @return the formatted recovery code
     */
    public String generateRecoveryCode(byte[] key) {
        if (key == null || key.length == 0) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }

        String hex = HEX_FORMAT.formatHex(key);
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < hex.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                formatted.append('-');
            }
            formatted.append(hex.charAt(i));
        }
        return formatted.toString();
    }

    /**
     * Recovers the master key from a recovery code.
     * Tolerates dashes and case insensitivity.
     *
     * @param recoveryCode the recovery code
     * @return the master key bytes
     * @throws IllegalArgumentException if the code is invalid
     */
    public byte[] recoverKey(String recoveryCode) {
        if (recoveryCode == null || recoveryCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Recovery code cannot be null or empty");
        }

        String normalized = recoveryCode.toUpperCase().replace("-", "").trim();
        if (!RECOVERY_CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid characters in recovery code");
        }

        try {
            return HEX_FORMAT.parseHex(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid recovery code format", e);
        }
    }

    /**
     * Exports the master key to a file.
     * The key is stored as the raw recovery code string.
     * 
     * @param key         the master key bytes
     * @param destination the destination file path
     * @throws IOException if an I/O error occurs
     */
    public void exportKeyToFile(byte[] key, Path destination) throws IOException {
        String code = generateRecoveryCode(key);
        Files.writeString(destination, code, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * Imports the master key from a file containing the recovery code.
     *
     * @param source the source file path
     * @return the master key bytes
     * @throws IOException if an I/O error occurs
     */
    public byte[] importKeyFromFile(Path source) throws IOException {
        String code = Files.readString(source);
        return recoverKey(code);
    }
}
