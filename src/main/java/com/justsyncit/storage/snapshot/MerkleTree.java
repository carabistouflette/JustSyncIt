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

import com.justsyncit.storage.metadata.FileMetadata;
import com.justsyncit.hash.Blake3Service;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.IOException;

/**
 * Service for building and manipulating Merkle Trees for incremental snapshots.
 */
public class MerkleTree {

    private final Blake3Service blake3Service;

    public MerkleTree(Blake3Service blake3Service) {
        this.blake3Service = blake3Service;
    }

    /**
     * Builds a Merkle Tree from a list of FileMetadata.
     * The input list does not need to be sorted.
     *
     * @param files list of file metadata
     * @return the root MerkleNode
     * @throws IOException if hashing fails
     */
    public MerkleNode build(List<FileMetadata> files) throws IOException {
        if (files == null || files.isEmpty()) {
            return MerkleNode.createDirectoryNode(
                    computeEmptyDirectoryHash(),
                    "/",
                    0,
                    Collections.emptyList());
        }

        // Sort files by path to ensure deterministic tree construction
        List<FileMetadata> sortedFiles = new ArrayList<>(files);
        sortedFiles.sort(Comparator.comparing(FileMetadata::getPath));

        // Group files by directory depth to build bottom-up
        // Map<DirectoryPath, List<ChildNodes>>
        Map<String, List<MerkleNode>> dirContents = new HashMap<>();

        // 1. Create LEAF nodes for all files and add to their parent directory
        for (FileMetadata file : sortedFiles) {
            MerkleNode fileNode = MerkleNode.createFileNode(
                    file.getFileHash(),
                    getFileName(file.getPath()),
                    file.getSize(),
                    file.getId());
            String parentPath = getParentPath(file.getPath());
            dirContents.computeIfAbsent(parentPath, k -> new ArrayList<>()).add(fileNode);
        }

        // 2. Build tree nodes bottom-up
        // Get all unique directory paths and sort by length descending (deepest first)
        List<String> allDirs = new ArrayList<>(dirContents.keySet());
        allDirs.sort((a, b) -> Integer.compare(b.length(), a.length()));

        // We need to ensure we process all intermediate directories even if they have
        // no files directly (only subdirs)
        // But the set of directories strictly implied by file paths might handle this
        // if we propagate up.
        // Actually, 'dirContents' will be populated with subdirectories as we process
        // them.

        // A better approach:
        // We have populated 'dirContents' with files.
        // Now we iterate depths.
        // But a directory might not be in 'allDirs' yet if it only contains other
        // directories.
        // So we need a dynamic approach or a Trie structure.

        // Let's use a simpler Trie-like construction since we have sorted paths.
        DirectoryBuilder rootBuilder = new DirectoryBuilder("/", "/");

        for (FileMetadata file : sortedFiles) {
            String[] parts = file.getPath().split("/");
            // Handle root/empty path if necessary, but assuming paths start with something
            // or are relative
            // remove empty leading strings if starts with /
            int startIndex = 0;
            if (parts.length > 0 && parts[0].isEmpty())
                startIndex = 1;

            DirectoryBuilder current = rootBuilder;
            for (int i = startIndex; i < parts.length - 1; i++) {
                current = current.getOrCreateChild(parts[i]);
            }

            // Add file to the leaf directory
            String fileName = parts[parts.length - 1];
            MerkleNode fileNode = MerkleNode.createFileNode(
                    file.getFileHash(),
                    fileName,
                    file.getSize(),
                    file.getId());
            current.addFile(fileNode);
        }

        return buildNodeRecursive(rootBuilder);
    }

    private MerkleNode buildNodeRecursive(DirectoryBuilder builder) throws IOException {
        List<MerkleNode> children = new ArrayList<>();

        // Add file children
        children.addAll(builder.files);

        // Add directory children (recursively build them)
        for (DirectoryBuilder childBuilder : builder.subDirectories.values()) {
            children.add(buildNodeRecursive(childBuilder));
        }

        // Sort children by name for deterministic hashing
        children.sort(Comparator.comparing(MerkleNode::getName));

        // Compute hash
        String hash = computeDirectoryHash(children, builder.name);
        long totalSize = children.stream().mapToLong(MerkleNode::getSize).sum();

        return MerkleNode.createDirectoryNode(hash, builder.name, totalSize, children);
    }

    private String computeDirectoryHash(List<MerkleNode> children, String dirName) throws IOException {
        // Hash = Blake3(dirName + concat(child.hash + child.name + child.type))
        // This ensures structure, names, and content are all verified.
        StringBuilder sb = new StringBuilder();
        sb.append("DIR:").append(dirName).append("|");
        for (MerkleNode child : children) {
            sb.append(child.getName()).append(":")
                    .append(child.getType()).append(":")
                    .append(child.getHash()).append("|");
        }
        return blake3Service.hashBuffer(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String computeEmptyDirectoryHash() throws IOException {
        return blake3Service.hashBuffer("DIR:/|".getBytes(StandardCharsets.UTF_8));
    }

    private String getFileName(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    private String getParentPath(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(0, lastSlash) : "";
    }

    // Helper class for building the structure
    private static class DirectoryBuilder {
        final String name;
        final String fullPath;
        final List<MerkleNode> files = new ArrayList<>();
        final Map<String, DirectoryBuilder> subDirectories = new HashMap<>();

        DirectoryBuilder(String name, String fullPath) {
            this.name = name;
            this.fullPath = fullPath;
        }

        DirectoryBuilder getOrCreateChild(String childName) {
            return subDirectories.computeIfAbsent(childName, k -> new DirectoryBuilder(childName,
                    fullPath.endsWith("/") ? fullPath + childName : fullPath + "/" + childName));
        }

        void addFile(MerkleNode file) {
            files.add(file);
        }
    }
}
