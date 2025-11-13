/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.justsyncit.hash;

import org.junit.jupiter.api.Test;
import java.util.Arrays;

/**
 * Temporary test to verify correct BLAKE3 hash values.
 */
class Blake3TestVectorCheck {

    @Test
    void checkHashValues() {
        Blake3Service service = new Blake3ServiceImpl();
        
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