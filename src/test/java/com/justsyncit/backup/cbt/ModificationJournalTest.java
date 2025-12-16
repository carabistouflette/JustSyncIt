package com.justsyncit.backup.cbt;

import com.justsyncit.scanner.FileChangeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModificationJournalTest {

    @TempDir
    Path tempDir;

    private ModificationJournal journal;

    @BeforeEach
    void setUp() throws IOException {
        journal = new ModificationJournal(tempDir);
        journal.init();
    }

    @AfterEach
    void tearDown() throws IOException {
        journal.close();
    }

    @Test
    void testRecordAndReplay() throws IOException {
        Instant now = Instant.now();
        FileChangeEvent event1 = new FileChangeEvent(
                FileChangeEvent.EventType.ENTRY_MODIFY,
                Path.of("/foo/bar"),
                now,
                false, -1, null);

        journal.recordEvent(event1);

        // Replay in same instance (need to flush? recordEvent flushes)
        List<FileChangeEvent> events = journal.replayEvents();
        assertEquals(1, events.size());
        assertEquals(Path.of("/foo/bar"), events.get(0).getFilePath());
    }

    @Test
    void testCompaction() throws IOException {
        Instant t1 = Instant.parse("2023-01-01T10:00:00Z");
        Instant t2 = Instant.parse("2023-01-02T10:00:00Z");
        Instant t3 = Instant.parse("2023-01-03T10:00:00Z");

        journal.recordEvent(createEvent("/file1", t1));
        journal.recordEvent(createEvent("/file2", t2));
        journal.recordEvent(createEvent("/file3", t3));

        // Compact before t2 (should remove t1, keep t2 and t3)
        // Note: compaction is typically strictly before.
        // Code: if (!time.isBefore(beforeTimestamp)) keep
        // So t2 is NOT before t2, so it is kept. t1 IS before t2, so removed.
        journal.compact(t2);

        List<FileChangeEvent> events = journal.replayEvents();
        assertEquals(2, events.size());
        assertTrue(events.stream().anyMatch(e -> e.getFilePath().toString().equals("/file2")));
        assertTrue(events.stream().anyMatch(e -> e.getFilePath().toString().equals("/file3")));
        assertFalse(events.stream().anyMatch(e -> e.getFilePath().toString().equals("/file1")));
    }

    private FileChangeEvent createEvent(String path, Instant time) {
        return new FileChangeEvent(
                FileChangeEvent.EventType.ENTRY_MODIFY,
                Path.of(path),
                time,
                false, -1, null);
    }
}
