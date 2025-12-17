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

import com.justsyncit.hash.Blake3Service;
import com.justsyncit.storage.metadata.FileMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MerkleTreeTest {

    private Blake3Service blake3Service;
    private MerkleTree merkleTree;

    @BeforeEach
    void setUp() {
        blake3Service = mock(Blake3Service.class);
        // Mock hash behavior to be deterministic and simple
        try {
            when(blake3Service.hashBuffer(any(byte[].class))).thenAnswer(invocation -> {
                byte[] data = invocation.getArgument(0);
                String input = new String(data, StandardCharsets.UTF_8);
                // Simple hash: length + content string (truncated) to simulates hash
                return Integer.toHexString(input.hashCode());
            });
            when(blake3Service.hashBuffer(any(byte[].class))).thenAnswer(invocation -> {
                byte[] data = invocation.getArgument(0);
                String input = new String(data, StandardCharsets.UTF_8);
                return Integer.toHexString(input.hashCode());
            });
        } catch (Exception e) {
            fail("Mock setup failed");
        }

        merkleTree = new MerkleTree(blake3Service);
    }

    @Test
    void build_EmptyList_ReturnsEmptyDirectory() throws IOException {
        MerkleNode root = merkleTree.build(Collections.emptyList());

        assertNotNull(root);
        assertEquals(MerkleNode.Type.DIRECTORY, root.getType());
        assertEquals("/", root.getName());
        assertEquals(0, root.getSize());
        assertTrue(root.getChildren().isEmpty());
    }

    @Test
    void build_SingleFile_ReturnsDirectoryWithOneFile() throws IOException {
        FileMetadata file = createFile("file1.txt", 100, "hash1");

        MerkleNode root = merkleTree.build(Collections.singletonList(file));

        assertNotNull(root);
        assertEquals(MerkleNode.Type.DIRECTORY, root.getType());
        assertEquals("/", root.getName());
        assertEquals(1, root.getChildren().size());

        MerkleNode child = root.getChildren().get(0);
        assertEquals(MerkleNode.Type.FILE, child.getType());
        assertEquals("file1.txt", child.getName());
        assertEquals(100, child.getSize());
        assertEquals("hash1", child.getHash());
    }

    @Test
    void build_NestedFiles_ReturnsCorrectStructure() throws IOException {
        // /file1.txt
        // /dir1/file2.txt
        // /dir1/subdir/file3.txt
        FileMetadata f1 = createFile("file1.txt", 10, "h1");
        FileMetadata f2 = createFile("dir1/file2.txt", 20, "h2");
        FileMetadata f3 = createFile("dir1/subdir/file3.txt", 30, "h3");

        MerkleNode root = merkleTree.build(Arrays.asList(f1, f2, f3));

        assertNotNull(root);
        assertEquals("/", root.getName());
        // Root children: file1.txt, dir1
        assertEquals(2, root.getChildren().size());

        // Children sorted by name: dir1, file1.txt
        MerkleNode dir1 = root.getChildren().get(0);
        MerkleNode file1 = root.getChildren().get(1);

        assertEquals("dir1", dir1.getName());
        assertEquals(MerkleNode.Type.DIRECTORY, dir1.getType());
        assertEquals("file1.txt", file1.getName());
        assertEquals(MerkleNode.Type.FILE, file1.getType());

        // Check dir1 children: file2.txt, subdir
        assertEquals(2, dir1.getChildren().size());
        MerkleNode subdir = dir1.getChildren().get(1); // subdir (sorted after file2.txt ?) 's' > 'f'
        // file2.txt vs subdir. 'f' < 's'. so index 0 is file2.txt
        MerkleNode file2 = dir1.getChildren().get(0);

        assertEquals("file2.txt", file2.getName());
        assertEquals("subdir", subdir.getName());

        // Check subdir children: file3.txt
        assertEquals(1, subdir.getChildren().size());
        MerkleNode file3 = subdir.getChildren().get(0);
        assertEquals("file3.txt", file3.getName());
        assertEquals(30, file3.getSize());
    }

    @Test
    void build_CalculatesDirectorySizeCorrectly() throws IOException {
        FileMetadata f1 = createFile("f1", 100, "h1");
        FileMetadata f2 = createFile("d1/f2", 200, "h2"); // d1 size = 200
        FileMetadata f3 = createFile("d1/d2/f3", 300, "h3"); // d2 size = 300, d1 adds 300 = 500

        MerkleNode root = merkleTree.build(Arrays.asList(f1, f2, f3));

        // Root size = 100 + 500 = 600
        assertEquals(600, root.getSize());

        MerkleNode d1 = root.getChildren().get(0); // d1 comes before f1 ('d' < 'f')
        assertEquals(500, d1.getSize());

        MerkleNode d2 = d1.getChildren().get(0); // d2 comes before f2 ('d' < 'f')
        assertEquals(300, d2.getSize());
    }

    private FileMetadata createFile(String path, long size, String hash) {
        return new FileMetadata(
                "id_" + path,
                "snap1",
                path,
                size,
                Instant.now(),
                hash,
                Collections.singletonList(hash + "_chunk"));
    }
}
