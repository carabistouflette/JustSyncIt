/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.justsyncit.metadata;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility for generating blind indexes for encrypted search.
 * Tokenizes text and generates HMACs for each token.
 */
public class BlindIndexSearch {

    private static final Logger logger = LoggerFactory.getLogger(BlindIndexSearch.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final Supplier<byte[]> keySupplier;

    public BlindIndexSearch(Supplier<byte[]> keySupplier) {
        this.keySupplier = keySupplier;
    }

    /**
     * Generates a blind index (HMAC) for a single term.
     *
     * @param term the term to index
     * @return the hex string of the HMAC
     */
    public String generateBlindIndex(String term) {
        if (term == null || term.isEmpty()) {
            return null;
        }

        try {
            byte[] key = keySupplier.get();
            if (key == null) {
                logger.warn("Search key not available");
                return null;
            }

            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, HMAC_ALGORITHM);
            mac.init(secretKeySpec);

            byte[] hmacBytes = mac.doFinal(term.toLowerCase().getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hmacBytes);

        } catch (Exception e) {
            logger.error("Failed to generate blind index", e);
            return null; // Fail safe or throw? Fail safe for search.
        }
    }

    /**
     * Tokenizes text and generates blind indexes for each token.
     * Splitting by non-alphanumeric characters.
     *
     * @param text the text to tokenize (e.g., file path)
     * @return set of blind index hashes
     */
    public Set<String> tokenizeAndHash(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> hashes = new HashSet<>();
        // Split by non-alphanumeric characters
        String[] tokens = text.split("[^a-zA-Z0-9]+");

        for (String token : tokens) {
            if (!token.isEmpty()) {
                String hash = generateBlindIndex(token);
                if (hash != null) {
                    hashes.add(hash);
                }
            }
        }

        // Also index the full text if it's not too long?
        // Or specific parts like "extension".

        return hashes;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
