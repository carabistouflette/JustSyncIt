package com.justsyncit.storage.retention;

import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.storage.metadata.Snapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class RetentionServiceTest {

    @Mock
    private MetadataService metadataService;

    private RetentionService retentionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        retentionService = new RetentionService(metadataService);
    }

    @Test
    void shouldPruneBasedOnIntersectionOfPruneLists() throws IOException {
        // CASE: Keep last 1 OR Keep newer than 30 days.
        // We have 3 snapshots:
        // S1: 1 day old (Matches Age, Matches Count)
        // S2: 10 days old (Matches Age, NOT Matches Count)
        // S3: 100 days old (NOT Matches Age, NOT Matches Count)

        // CountPolicy(1) -> Prunes [S2, S3] (Keeps S1)
        // AgePolicy(30) -> Prunes [S3] (Keeps S1, S2)

        // Combined Logic (Union of Keeps):
        // Keep S1 (from Count)
        // Keep S1, S2 (from Age)
        // -> Keep Set: {S1, S2}
        // -> Prune Set: {S3}

        Instant now = Instant.now();
        Snapshot s1 = new Snapshot("s1", "s1", "", now.minus(1, ChronoUnit.DAYS), 0, 0);
        Snapshot s2 = new Snapshot("s2", "s2", "", now.minus(10, ChronoUnit.DAYS), 0, 0);
        Snapshot s3 = new Snapshot("s3", "s3", "", now.minus(100, ChronoUnit.DAYS), 0, 0);

        when(metadataService.listSnapshots()).thenReturn(Arrays.asList(s1, s2, s3));

        List<RetentionPolicy> policies = new ArrayList<>();
        policies.add(new CountRetentionPolicy(1));
        policies.add(new AgeRetentionPolicy(30));

        List<Snapshot> pruned = retentionService.pruneSnapshots(policies, true); // Dry run

        assertEquals(1, pruned.size());
        assertEquals("s3", pruned.get(0).getId());

        verify(metadataService, never()).deleteSnapshot(anyString());
    }

    @Test
    void shouldDeleteInRealRun() throws IOException {
        Instant now = Instant.now();
        Snapshot s1 = new Snapshot("s1", "s1", "", now.minus(100, ChronoUnit.DAYS), 0, 0);

        when(metadataService.listSnapshots()).thenReturn(Arrays.asList(s1));

        List<RetentionPolicy> policies = new ArrayList<>();
        policies.add(new AgeRetentionPolicy(30)); // delete > 30 days

        retentionService.pruneSnapshots(policies, false); // Real run

        verify(metadataService, times(1)).deleteSnapshot("s1");
    }
}
