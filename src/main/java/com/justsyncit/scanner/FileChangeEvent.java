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

package com.justsyncit.scanner;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;

/**
 * Represents a file system change event detected by WatchService.
 * Contains information about the type of change, affected file, and event
 * metadata.
 */
public class FileChangeEvent {

    /** Enumeration of file change event types. */
    public enum EventType {
        /** File or directory created. */
        ENTRY_CREATE("ENTRY_CREATE"),
        /** File or directory modified. */
        ENTRY_MODIFY("ENTRY_MODIFY"),
        /** File or directory deleted. */
        ENTRY_DELETE("ENTRY_DELETE"),
        /** Directory overflow (too many events). */
        OVERFLOW("OVERFLOW"),
        /** Unknown event type. */
        UNKNOWN("UNKNOWN");

        private final String name;

        EventType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** Type of the event. */
    private final EventType eventType;

    /** Path of the affected file or directory. */
    private final Path filePath;

    /** Timestamp when the event was detected. */
    private final Instant eventTime;

    /** Whether the affected path is a directory. */
    private final boolean isDirectory;

    /** Size of the file (if applicable, -1 if unknown). */
    private final long fileSize;

    /** Registration ID that detected this event. */
    private final String registrationId;

    /** Additional event metadata. */
    private final Map<String, Object> metadata;

    /**
     * Creates a new FileChangeEvent.
     *
     * @param eventType      type of the event
     * @param filePath       path of the affected file or directory
     * @param eventTime      timestamp when the event was detected
     * @param isDirectory    whether the affected path is a directory
     * @param fileSize       size of the file (if applicable, -1 if unknown)
     * @param registrationId registration ID that detected this event
     */
    public FileChangeEvent(EventType eventType, Path filePath, Instant eventTime,
            boolean isDirectory, long fileSize, String registrationId) {
        this.eventType = Objects.requireNonNull(eventType, "Event type cannot be null");
        this.filePath = Objects.requireNonNull(filePath, "File path cannot be null");
        this.eventTime = Objects.requireNonNull(eventTime, "Event time cannot be null");
        this.isDirectory = isDirectory;
        this.fileSize = fileSize;
        this.registrationId = registrationId;
        this.metadata = new HashMap<>();
    }

    /**
     * Creates a new FileChangeEvent with minimal information.
     *
     * @param eventType      type of the event
     * @param filePath       path of the affected file or directory
     * @param registrationId registration ID that detected this event
     */
    public FileChangeEvent(EventType eventType, Path filePath, String registrationId) {
        this(eventType, filePath, Instant.now(), false, -1, registrationId);
    }

    /**
     * Gets the type of the event.
     *
     * @return event type
     */
    public EventType getEventType() {
        return eventType;
    }

    /**
     * Gets the path of the affected file or directory.
     *
     * @return file path
     */
    public Path getFilePath() {
        return filePath;
    }

    /**
     * Gets the timestamp when the event was detected.
     *
     * @return event time
     */
    public Instant getEventTime() {
        return eventTime;
    }

    /**
     * Checks if the affected path is a directory.
     *
     * @return true if directory, false otherwise
     */
    public boolean isDirectory() {
        return isDirectory;
    }

    /**
     * Gets the size of the file (if applicable).
     *
     * @return file size, or -1 if unknown
     */
    public long getFileSize() {
        return fileSize;
    }

    /**
     * Gets the registration ID that detected this event.
     *
     * @return registration ID
     */
    public String getRegistrationId() {
        return registrationId;
    }

    /**
     * Gets additional event metadata.
     *
     * @return metadata map
     */
    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }

    /**
     * Adds metadata to this event.
     *
     * @param key   metadata key
     * @param value metadata value
     */
    public void addMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    /**
     * Gets metadata value by key.
     *
     * @param key metadata key
     * @return metadata value, or null if not found
     */
    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    /**
     * Gets the file name from the file path.
     *
     * @return file name, or null if path has no file name
     */
    public String getFileName() {
        Path fileName = filePath.getFileName();
        return fileName != null ? fileName.toString() : null;
    }

    /**
     * Gets the parent directory of the affected file.
     *
     * @return parent directory, or null if no parent
     */
    public Path getParentDirectory() {
        return filePath.getParent();
    }

    /**
     * Gets the file extension (if applicable).
     *
     * @return file extension, or null if not a file or no extension
     */
    public String getFileExtension() {
        if (isDirectory) {
            return null;
        }
        Path fileName = filePath.getFileName();
        if (fileName == null) {
            return null;
        }
        String name = fileName.toString();
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(lastDot + 1) : null;
    }

    /**
     * Creates a copy of this event with updated file size.
     *
     * @param newFileSize new file size
     * @return new event with updated size
     */
    public FileChangeEvent withFileSize(long newFileSize) {
        return new FileChangeEvent(eventType, filePath, eventTime, isDirectory, newFileSize, registrationId);
    }

    /**
     * Creates a copy of this event with additional metadata.
     *
     * @param additionalMetadata additional metadata to add
     * @return new event with combined metadata
     */
    public FileChangeEvent withMetadata(Map<String, Object> additionalMetadata) {
        FileChangeEvent newEvent = new FileChangeEvent(eventType, filePath, eventTime, isDirectory, fileSize,
                registrationId);
        newEvent.metadata.putAll(this.metadata);
        newEvent.metadata.putAll(additionalMetadata);
        return newEvent;
    }

    /**
     * Converts this event to a string representation.
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return String.format(
                "FileChangeEvent{type=%s, path=%s, time=%s, directory=%b, size=%d, registrationId='%s'}",
                eventType, filePath, eventTime, isDirectory, fileSize, registrationId);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        FileChangeEvent that = (FileChangeEvent) obj;
        return eventType == that.eventType
                &&
                filePath.equals(that.filePath)
                &&
                eventTime.equals(that.eventTime)
                &&
                isDirectory == that.isDirectory
                &&
                fileSize == that.fileSize
                &&
                Objects.equals(registrationId, that.registrationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventType, filePath, eventTime, isDirectory, fileSize, registrationId);
    }

    /**
     * Creates a creation event.
     *
     * @param filePath       path of created file/directory
     * @param registrationId registration ID
     * @return creation event
     */
    public static FileChangeEvent createEntryCreate(Path filePath, String registrationId) {
        return new FileChangeEvent(EventType.ENTRY_CREATE, filePath, registrationId);
    }

    /**
     * Creates a modification event.
     *
     * @param filePath       path of modified file/directory
     * @param registrationId registration ID
     * @return modification event
     */
    public static FileChangeEvent createEntryModify(Path filePath, String registrationId) {
        return new FileChangeEvent(EventType.ENTRY_MODIFY, filePath, registrationId);
    }

    /**
     * Creates a deletion event.
     *
     * @param filePath       path of deleted file/directory
     * @param registrationId registration ID
     * @return deletion event
     */
    public static FileChangeEvent createEntryDelete(Path filePath, String registrationId) {
        return new FileChangeEvent(EventType.ENTRY_DELETE, filePath, registrationId);
    }

    /**
     * Creates an overflow event.
     *
     * @param filePath       path where overflow occurred
     * @param registrationId registration ID
     * @return overflow event
     */
    public static FileChangeEvent createOverflow(Path filePath, String registrationId) {
        return new FileChangeEvent(EventType.OVERFLOW, filePath, registrationId);
    }
}