/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 */

package com.justsyncit.hash;

import com.justsyncit.TestServiceFactory;
import org.junit.jupiter.api.Test;
import java.util.Arrays;

/**
 * Test to get actual hash values for our implementation.
 */
class HashValueTest {

    @Test
    void getHashValues() {
        Blake3Service service = TestServiceFactory.createBlake3Service();
        
        // Empty input
        byte[] empty = new byte[0];
        System.out.println("Empty: " + service.hashBuffer(empty));
        
        // Single byte 0xff
        byte[] singleByte = {(byte) 0xff};
        System.out.println("Single 0xff: " + service.hashBuffer(singleByte));
        
        // "abc"
        byte[] abc = "abc".getBytes();
        System.out.println("abc: " + service.hashBuffer(abc));
        
        // 1KB of zeros
        byte[] oneKZeros = new byte[1024];
        Arrays.fill(oneKZeros, (byte) 0);
        System.out.println("1KB zeros: " + service.hashBuffer(oneKZeros));
    }
}