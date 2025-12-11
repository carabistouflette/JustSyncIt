/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 */

package com.justsyncit.hash;

import com.justsyncit.TestServiceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static java.util.Arrays.fill;
import java.util.concurrent.TimeUnit;

/**
 * Simple test to get hash values.
 */
class SimpleHashTest {

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void printHashValues() throws HashingException {
        Blake3Service service = TestServiceFactory.createBlake3Service();

        // Empty input
        byte[] empty = new byte[0];
        String emptyHash = service.hashBuffer(empty);
        System.out.println("Empty hash: " + emptyHash);

        // Single byte 0xff
        byte[] singleByte = {(byte) 0xff};
        String singleHash = service.hashBuffer(singleByte);
        System.out.println("Single 0xff hash: " + singleHash);
        // "abc"
        byte[] abc = "abc".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String abcHash = service.hashBuffer(abc);
        System.out.println("abc hash: " + abcHash);

        // 1KB of zeros
        byte[] oneKZeros = new byte[1024];
        fill(oneKZeros, (byte) 0);
        String zerosHash = service.hashBuffer(oneKZeros);
        System.out.println("1KB zeros hash: " + zerosHash);
    }
}