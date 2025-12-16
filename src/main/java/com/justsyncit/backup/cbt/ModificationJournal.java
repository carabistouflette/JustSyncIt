package com.justsyncit.backup.cbt;

import com.justsyncit.scanner.FileChangeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Persistent append-only journal for file modification events.
 * Used to recover change history after application restarts.
 */
public class ModificationJournal implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(ModificationJournal.class);
    private static final int CURRENT_VERSION = 1;

    private final Path journalDir;
    private final Path journalFile;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private DataOutputStream outputStream;

    /**
     * Creates a new ModificationJournal.
     *
     * @param journalDir directory to store journal files
     */
    public ModificationJournal(Path journalDir) {
        this.journalDir = journalDir;
        this.journalFile = journalDir.resolve("modifications.journal");
    }

    /**
     * Initializes the journal.
     * Creates the file if it doesn't exist and prepares it for writing.
     */
    public void init() throws IOException {
        Files.createDirectories(journalDir);

        boolean append = Files.exists(journalFile);

        // Open for appending
        this.outputStream = new DataOutputStream(new BufferedOutputStream(
                Files.newOutputStream(journalFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND)));

        // If new file, write header
        if (!append) {
            writeHeader();
        }
    }

    private void writeHeader() throws IOException {
        outputStream.writeInt(0xDEADBEEF); // Magic
        outputStream.writeInt(CURRENT_VERSION);
        outputStream.flush();
    }

    /**
     * Records a file change event to the persistent journal.
     *
     * @param event the event to record
     */
    public void recordEvent(FileChangeEvent event) {
        lock.writeLock().lock();
        try {
            if (outputStream == null) {
                throw new IllegalStateException("Journal not initialized");
            }

            // Simple binary format:
            // [Type ordinal byte] [Timestamp long] [Path UTF] [RegistrationId UTF]
            outputStream.writeByte(event.getEventType().ordinal());
            outputStream.writeLong(event.getEventTime().toEpochMilli());
            outputStream.writeUTF(event.getFilePath().toString());
            // Write registration ID gracefully handled if null (though it shouldn't be)
            outputStream.writeUTF(event.getRegistrationId() != null ? event.getRegistrationId() : "");

            outputStream.flush(); // Ensure durability (could optimize to flush periodically)

        } catch (IOException e) {
            logger.error("Failed to persist file change event: {}", event, e);
            // We log but don't throw, as we don't want to crash the whole watcher thread.
            // In a real reckless system we might panic here.
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Replays all events from the journal.
     * Useful on startup to restore state.
     *
     * @return list of historical events
     */
    public List<FileChangeEvent> replayEvents() throws IOException {
        lock.readLock().lock();
        List<FileChangeEvent> events = new ArrayList<>();

        if (!Files.exists(journalFile)) {
            lock.readLock().unlock();
            return events;
        }

        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(journalFile)))) {
            // Check header
            if (dis.available() > 0) { // Check if empty
                int magic = dis.readInt();
                if (magic != 0xDEADBEEF) {
                    logger.error("Invalid journal magic: " + Integer.toHexString(magic));
                    // Corrupt journal? We could either fail or start fresh.
                    // For now, let's treat as empty/corrupt and return what we have (nothing).
                    return events;
                }
                int version = dis.readInt();
                if (version != CURRENT_VERSION) {
                    logger.warn("Unknown journal version: " + version);
                    // Decide upgrade logic here. For now, we only have version 1.
                    return events;
                }
            }

            while (dis.available() > 0) {
                try {
                    int typeOrdinal = dis.readByte();
                    long timestamp = dis.readLong();
                    String pathStr = dis.readUTF();
                    String regId = dis.readUTF();

                    // Reconstruct event
                    FileChangeEvent.EventType type = FileChangeEvent.EventType.values()[typeOrdinal];
                    Path path = Path.of(pathStr);
                    Instant time = Instant.ofEpochMilli(timestamp);

                    // Note: We don't persist isDirectory or fileSize or metadata for now as they
                    // are transient/discoverable.
                    // We treat everything as generic entry modify unless we want to recreate the
                    // exact object.
                    // The core requirement is to know *what* changed and *when*.

                    events.add(new FileChangeEvent(type, path, time, false, -1, regId.isEmpty() ? null : regId));

                } catch (EOFException e) {
                    break; // Unexpected end
                }
            }
        } finally {
            lock.readLock().unlock();
        }
        return events;
    }

    /**
     * Compacts the journal by removing old events.
     * Rewrites the journal file, keeping only events after the specified timestamp.
     *
     * @param beforeTimestamp remove events older than this (exclusive)
     */
    public void compact(Instant beforeTimestamp) throws IOException {
        lock.writeLock().lock();
        try {
            logger.info("Compacting journal before {}", beforeTimestamp);

            // 1. Read all valid events
            List<FileChangeEvent> validEvents = new ArrayList<>();
            // We can reuse replayEvents but we're already holding the write lock,
            // so we shouldn't call a method that acquires the read lock if it's not
            // reentrant upgradable (it isn't).
            // Actually ReentrantReadWriteLock: "The writer lock is exclusive".
            // So we can just read the file directly since we have exclusive access.

            if (!Files.exists(journalFile)) {
                return;
            }

            try (DataInputStream dis = new DataInputStream(
                    new BufferedInputStream(Files.newInputStream(journalFile)))) {
                if (dis.available() > 0) {
                    int magic = dis.readInt();
                    int version = dis.readInt();
                    if (magic == 0xDEADBEEF && version == CURRENT_VERSION) {
                        while (dis.available() > 0) {
                            try {
                                int typeOrdinal = dis.readByte();
                                long timestamp = dis.readLong();
                                String pathStr = dis.readUTF();
                                String regId = dis.readUTF();

                                Instant time = Instant.ofEpochMilli(timestamp);
                                if (!time.isBefore(beforeTimestamp)) { // Keep if >= beforeTimestamp
                                    validEvents.add(new FileChangeEvent(
                                            FileChangeEvent.EventType.values()[typeOrdinal],
                                            Path.of(pathStr),
                                            time,
                                            false, -1, regId.isEmpty() ? null : regId));
                                }
                            } catch (EOFException e) {
                                break;
                            }
                        }
                    }
                }
            } catch (IOException e) {
                logger.error("Error reading journal during compaction", e);
                // If read fails, abort compaction to avoid data loss
                return;
            }

            if (validEvents.isEmpty()) {
                logger.info("Journal compaction: No events to keep. Truncating file.");
            } else {
                logger.info("Journal compaction: Keeping {} events.", validEvents.size());
            }

            // 2. Write to temp file
            Path tempFile = journalDir.resolve("modifications.journal.tmp");
            try (DataOutputStream dos = new DataOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(tempFile, StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING)))) {
                dos.writeInt(0xDEADBEEF);
                dos.writeInt(CURRENT_VERSION);

                for (FileChangeEvent event : validEvents) {
                    dos.writeByte(event.getEventType().ordinal());
                    dos.writeLong(event.getEventTime().toEpochMilli());
                    dos.writeUTF(event.getFilePath().toString());
                    dos.writeUTF(event.getRegistrationId() != null ? event.getRegistrationId() : "");
                }
                dos.flush();
            }

            // 3. Close current output stream to release file handle
            if (outputStream != null) {
                outputStream.close();
            }

            // 4. Atomically replace
            Files.move(tempFile, journalFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);

            // 5. Re-open output stream
            this.outputStream = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(journalFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND)));

            logger.info("Journal compaction completed.");

        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void close() throws IOException {
        lock.writeLock().lock();
        try {
            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
}
