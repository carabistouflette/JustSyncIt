/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 */

package com.justsyncit.hash;

import org.junit.jupiter.api.Test;
import java.util.Arrays;

/**
 * Simple test to get hash values.
 */
class SimpleHashTest {

    @Test
    void printHashValues() {
        Blake3Service service = new Blake3ServiceImpl();
        
        // Empty input
        byte[] empty = new byte[0];
        String emptyHash = service.hashBuffer(empty);
        System.out.println("Empty hash: " + emptyHash);
        
        // Single byte 0xff
        byte[] singleByte = {(byte) 0xff};
        String singleHash = service.hashBuffer(singleByte);
        System.out.println("Single 0xff hash: " + singleHash);
        
        // "abc"
        byte[] abc = "abc".getBytes();
        String abcHash = service.hashBuffer(abc);
        System.out.println("abc hash: " + abcHash);
        
        // 1KB of zeros
        byte[] oneKZeros = new byte[1024];
        Arrays.fill(oneKZeros, (byte) 0);
        String zerosHash = service.hashBuffer(oneKZeros);
        System.out.println("1KB zeros hash: " + zerosHash);
    }
}