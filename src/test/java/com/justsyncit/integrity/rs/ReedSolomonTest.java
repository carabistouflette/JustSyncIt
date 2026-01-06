package com.justsyncit.integrity.rs;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Random;

public class ReedSolomonTest {

    @Test
    public void testEncodingAndDecoding() {
        int dataShards = 4;
        int parityShards = 2;
        int shardSize = 16;

        ReedSolomon rs = new ReedSolomon(dataShards, parityShards);

        // Create random data
        byte[][] shards = new byte[dataShards + parityShards][shardSize];
        Random random = new Random(12345);
        for (int i = 0; i < dataShards; i++) {
            random.nextBytes(shards[i]);
        }

        // Encode parity
        rs.encodeParity(shards, shardSize);

        // Backup original shards
        byte[][] original = new byte[dataShards + parityShards][shardSize];
        for (int i = 0; i < dataShards + parityShards; i++) {
            System.arraycopy(shards[i], 0, original[i], 0, shardSize);
        }

        // Simulate loss: delete 2 shards (e.g., index 1 (data) and index 4 (parity))
        // Actually decodeMissing expects ALL buffers to be alloced.
        // It requires correct available shards and we can zero out missing ones to
        // simulate loss.
        boolean[] present = new boolean[dataShards + parityShards];
        Arrays.fill(present, true);

        present[1] = false; // Missing data shard 1
        present[4] = false; // Missing parity shard 0

        Arrays.fill(shards[1], (byte) 0);
        Arrays.fill(shards[4], (byte) 0);

        rs.decodeMissing(shards, present, shardSize);

        // Verify reconstruction
        assertArrayEquals(original[1], shards[1], "Reconstructed data shard 1 should match original");
        assertArrayEquals(original[4], shards[4], "Reconstructed parity shard 0 should match original");

        // Check other shards remained untouched
        assertArrayEquals(original[0], shards[0]);
    }

    @Test
    public void testMaxLoss() {
        // Test recovering when we lost 'parityShards' number of shards
        int dataShards = 6;
        int parityShards = 3;
        int shardSize = 10;

        ReedSolomon rs = new ReedSolomon(dataShards, parityShards);
        byte[][] shards = new byte[dataShards + parityShards][shardSize];
        Random random = new Random(67890);

        for (int i = 0; i < dataShards; i++) {
            random.nextBytes(shards[i]);
        }

        rs.encodeParity(shards, shardSize);

        byte[][] original = new byte[dataShards + parityShards][shardSize];
        for (int i = 0; i < shards.length; i++)
            System.arraycopy(shards[i], 0, original[i], 0, shardSize);

        // Lose 3 data shards
        boolean[] present = new boolean[dataShards + parityShards];
        Arrays.fill(present, true);

        present[0] = false;
        present[2] = false;
        present[5] = false;

        Arrays.fill(shards[0], (byte) 0);
        Arrays.fill(shards[2], (byte) 0);
        Arrays.fill(shards[5], (byte) 0);

        rs.decodeMissing(shards, present, shardSize);

        assertArrayEquals(original[0], shards[0]);
        assertArrayEquals(original[2], shards[2]);
        assertArrayEquals(original[5], shards[5]);
    }
}
