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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.justsyncit.scanner.fastcdc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FastCDCTest {

    @Test
    public void testCutPoints() {
        int min = 16;
        int avg = 64;
        int max = 256;
        FastCDC cdc = new FastCDC(min, avg, max);

        byte[] data = new byte[1024];
        // Fill with random data
        new java.util.Random(12345).nextBytes(data);

        int offset = 0;
        int count = 0;
        while (offset < data.length) {
            int chunkLen = cdc.nextChunk(data, offset, data.length - offset);
            assertTrue(chunkLen >= Math.min(min, data.length - offset));
            assertTrue(chunkLen <= max);
            offset += chunkLen;
            count++;
        }
        System.out.println("Generated " + count + " chunks from 1024 bytes");
    }

    @Test
    public void testDeterministic() {
        // Same data should yield same chunks
        int min = 64;
        int avg = 256;
        int max = 1024;
        FastCDC cdc = new FastCDC(min, avg, max);

        byte[] data = new byte[4096];
        new java.util.Random(54321).nextBytes(data);

        java.util.List<Integer> chunks1 = new java.util.ArrayList<>();
        int offset = 0;
        while (offset < data.length) {
            int len = cdc.nextChunk(data, offset, data.length - offset);
            chunks1.add(len);
            offset += len;
        }

        java.util.List<Integer> chunks2 = new java.util.ArrayList<>();
        offset = 0;
        while (offset < data.length) {
            int len = cdc.nextChunk(data, offset, data.length - offset);
            chunks2.add(len);
            offset += len;
        }

        assertEquals(chunks1, chunks2);
    }

    @Test
    public void testShiftResistance() {
        // Insert a byte, check if boundaries recover
        int min = 64;
        int avg = 256;
        int max = 1024;
        FastCDC cdc = new FastCDC(min, avg, max);

        byte[] data = new byte[10000];
        new java.util.Random(999).nextBytes(data);

        // Base chunks
        java.util.List<Integer> chunks1 = new java.util.ArrayList<>();
        int offset = 0;
        while (offset < data.length) {
            int len = cdc.nextChunk(data, offset, data.length - offset);
            chunks1.add(len);
            offset += len;
        }

        // Data with insert
        byte[] data2 = new byte[data.length + 1];
        System.arraycopy(data, 0, data2, 0, 500);
        data2[500] = (byte) 0xFF; // Insert
        System.arraycopy(data, 500, data2, 501, data.length - 500);

        java.util.List<Integer> chunks2 = new java.util.ArrayList<>();
        offset = 0;
        while (offset < data2.length) {
            int len = cdc.nextChunk(data2, offset, data2.length - offset);
            chunks2.add(len);
            offset += len;
        }

        // Verify that after the affected chunk(s), the lengths sync up again
        // We look for matching sequence at the end
        int matched = 0;
        for (int i = 1; i < 10; i++) {
            int c1 = chunks1.get(chunks1.size() - i);
            int c2 = chunks2.get(chunks2.size() - i);
            if (c1 == c2)
                matched++;
        }
        assertTrue(matched >= 5, "Chunks should resynchronize after insertion");
    }
}
