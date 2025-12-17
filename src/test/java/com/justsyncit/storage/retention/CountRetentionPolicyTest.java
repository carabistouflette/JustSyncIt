package com.justsyncit.storage.retention;

import com.justsyncit.storage.metadata.Snapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CountRetentionPolicyTest {

    @Test
    void shouldKeepRequestedNumber() {
        CountRetentionPolicy policy = new CountRetentionPolicy(3);
        List<Snapshot> snapshots = new ArrayList<>();

        // Add 5 snapshots, time increasing
        Instant now = Instant.now();
        for (int i = 0; i < 5; i++) {
            snapshots.add(new Snapshot("id" + i, "snap" + i, "", now.plusSeconds(i * 1000), 0, 0));
        }

        List<Snapshot> toPrune = policy.getSnapshotsToPrune(snapshots);

        // Should prune oldest 2 (id0, id1) because we keep 3 newest (id4, id3, id2)
        assertEquals(2, toPrune.size());
        assertTrue(toPrune.stream().anyMatch(s -> s.getId().equals("id0")));
        assertTrue(toPrune.stream().anyMatch(s -> s.getId().equals("id1")));
    }

    @Test
    void shouldPruneNothingIfCountIsEnough() {
        CountRetentionPolicy policy = new CountRetentionPolicy(10);
        List<Snapshot> snapshots = new ArrayList<>();
        snapshots.add(new Snapshot("id1", "snap1", "", Instant.now(), 0, 0));

        List<Snapshot> toPrune = policy.getSnapshotsToPrune(snapshots);
        assertTrue(toPrune.isEmpty());
    }
}
