package com.justsyncit.storage.retention;

import com.justsyncit.storage.metadata.Snapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgeRetentionPolicyTest {

    @Test
    void shouldPruneOldSnapshots() {
        AgeRetentionPolicy policy = new AgeRetentionPolicy(30); // Keep last 30 days
        List<Snapshot> snapshots = new ArrayList<>();

        Instant now = Instant.now();

        // New snapshot (today)
        snapshots.add(new Snapshot("new", "new", "", now, 0, 0));
        // Old snapshot (60 days ago)
        snapshots.add(new Snapshot("old", "old", "", now.minus(60, ChronoUnit.DAYS), 0, 0));

        List<Snapshot> toPrune = policy.getSnapshotsToPrune(snapshots);

        assertEquals(1, toPrune.size());
        assertEquals("old", toPrune.get(0).getId());
    }
}
