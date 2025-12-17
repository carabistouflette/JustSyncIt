/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2024 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.justsyncit.storage.snapshot;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a node in the Merkle Tree used for incremental snapshots.
 * <p>
 * A node can be a LEAF (representing a file) or a TREE (representing a
 * directory).
 * The hash of a TREE node is derived from the hashes of its children, allowing
 * for efficient comparison and change detection.
 */
public class MerkleNode {

    public enum Type {
        FILE,
        DIRECTORY
    }

    private final String hash;
    private final Type type;
    private final String name;
    private final long size;
    private final List<MerkleNode> children;
    private final String fileId; // Only relevant for FILE type, points to FileMetadata

    /**
     * Creates a new MerkleNode.
     *
     * @param hash     the calculated hash of the node
     * @param type     the type of the node (FILE or DIRECTORY)
     * @param name     the name of the file or directory
     * @param size     the size in bytes (for directories, sum of children)
     * @param children list of children nodes (empty for FILE)
     * @param fileId   optional ID referencing FileMetadata (null for DIRECTORY)
     */
    public MerkleNode(String hash, Type type, String name, long size, List<MerkleNode> children, String fileId) {
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("Hash cannot be null or empty");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        if (size < 0) {
            throw new IllegalArgumentException("Size cannot be negative");
        }
        if (type == Type.FILE && (children != null && !children.isEmpty())) {
            throw new IllegalArgumentException("File node cannot have children");
        }

        this.hash = hash;
        this.type = type;
        this.name = name;
        this.size = size;
        this.children = children; // Allow null for lazy loading
        this.fileId = fileId;
    }

    /**
     * Creates a LEAF node representing a file.
     */
    public static MerkleNode createFileNode(String hash, String name, long size, String fileId) {
        return new MerkleNode(hash, Type.FILE, name, size, null, fileId);
    }

    /**
     * Creates a TREE node representing a directory.
     */
    public static MerkleNode createDirectoryNode(String hash, String name, long size, List<MerkleNode> children) {
        return new MerkleNode(hash, Type.DIRECTORY, name, size, children, null);
    }

    public String getHash() {
        return hash;
    }

    public Type getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    public List<MerkleNode> getChildren() {
        return children != null ? Collections.unmodifiableList(children) : null;
    }

    public String getFileId() {
        return fileId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        MerkleNode that = (MerkleNode) o;
        return Objects.equals(hash, that.hash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hash);
    }

    @Override
    public String toString() {
        return "MerkleNode{" +
                "type=" + type +
                ", name='" + name + '\'' +
                ", hash='" + hash.substring(0, Math.min(8, hash.length())) + "..." + '\'' +
                ", size=" + size +
                ", children=" + children.size() +
                '}';
    }
}
