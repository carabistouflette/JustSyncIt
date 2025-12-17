package com.justsyncit.storage.snapshot;

import com.justsyncit.storage.metadata.FileMetadata;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MerkleTreeDifferTest {

    private MerkleNode createFileNode(String name, String hash) {
        return new MerkleNode(hash, MerkleNode.Type.FILE, name, 100, Collections.emptyList(), "file-id-" + name);
    }

    private MerkleNode createDirNode(String name, String hash, List<MerkleNode> children) {
        return new MerkleNode(hash, MerkleNode.Type.DIRECTORY, name, 0, children, null);
    }

    @Test
    public void testDiff_IdenticalTrees_ReturnsEmpty() throws IOException {
        MerkleNode file1 = createFileNode("file1.txt", "hash1");
        MerkleNode root1 = createDirNode("root", "rootHash", Collections.singletonList(file1));
        MerkleNode root2 = createDirNode("root", "rootHash", Collections.singletonList(file1));

        MerkleTreeDiffer differ = new MerkleTreeDiffer();
        List<MerkleTreeDiffer.DiffEntry> diffs = differ.diff(root1, root2);

        assertTrue(diffs.isEmpty());
    }

    @Test
    public void testDiff_AddedFile_ReturnsAdded() throws IOException {
        MerkleNode file1 = createFileNode("file1.txt", "hash1");
        MerkleNode root1 = createDirNode("root", "rootHash1", Collections.singletonList(file1));

        MerkleNode file2 = createFileNode("file2.txt", "hash2");
        // root2 has file1 and file2
        // Note: Children must be sorted for MerkleTree logic, but differ just comparing
        // lists?
        // MerkleTreeDiffer implementation compares children by name.
        MerkleNode root2 = createDirNode("root", "rootHash2", Arrays.asList(file1, file2));

        MerkleTreeDiffer differ = new MerkleTreeDiffer();
        List<MerkleTreeDiffer.DiffEntry> diffs = differ.diff(root1, root2);

        assertEquals(1, diffs.size());
        assertEquals("file2.txt", diffs.get(0).getPath());
        assertEquals(MerkleTreeDiffer.ChangeType.ADDED, diffs.get(0).getType());
    }

    @Test
    public void testDiff_DeletedFile_ReturnsDeleted() throws IOException {
        MerkleNode file1 = createFileNode("file1.txt", "hash1");
        MerkleNode file2 = createFileNode("file2.txt", "hash2");
        MerkleNode root1 = createDirNode("root", "rootHash1", Arrays.asList(file1, file2));

        MerkleNode root2 = createDirNode("root", "rootHash2", Collections.singletonList(file1));

        MerkleTreeDiffer differ = new MerkleTreeDiffer();
        List<MerkleTreeDiffer.DiffEntry> diffs = differ.diff(root1, root2);

        assertEquals(1, diffs.size());
        assertEquals("file2.txt", diffs.get(0).getPath());
        assertEquals(MerkleTreeDiffer.ChangeType.DELETED, diffs.get(0).getType());
    }

    @Test
    public void testDiff_ModifiedFile_ReturnsModified() throws IOException {
        MerkleNode file1Old = createFileNode("file1.txt", "hash1");
        MerkleNode root1 = createDirNode("root", "rootHash1", Collections.singletonList(file1Old));

        MerkleNode file1New = createFileNode("file1.txt", "hash1_modified");
        MerkleNode root2 = createDirNode("root", "rootHash2", Collections.singletonList(file1New));

        MerkleTreeDiffer differ = new MerkleTreeDiffer();
        List<MerkleTreeDiffer.DiffEntry> diffs = differ.diff(root1, root2);

        assertEquals(1, diffs.size());
        assertEquals("file1.txt", diffs.get(0).getPath());
        assertEquals(MerkleTreeDiffer.ChangeType.MODIFIED, diffs.get(0).getType());
    }

    @Test
    public void testDiff_NestedDirectory() throws IOException {
        // subdir/file.txt
        MerkleNode file = createFileNode("file.txt", "hashF");
        MerkleNode subdir1 = createDirNode("subdir", "hashS1", Collections.singletonList(file));
        MerkleNode root1 = createDirNode("root", "hashR1", Collections.singletonList(subdir1));

        // subdir/file.txt (modified)
        MerkleNode fileMod = createFileNode("file.txt", "hashF_mod");
        MerkleNode subdir2 = createDirNode("subdir", "hashS2", Collections.singletonList(fileMod));
        MerkleNode root2 = createDirNode("root", "hashR2", Collections.singletonList(subdir2));

        MerkleTreeDiffer differ = new MerkleTreeDiffer();
        List<MerkleTreeDiffer.DiffEntry> diffs = differ.diff(root1, root2);

        assertEquals(1, diffs.size());
        // Path should be subdir/file.txt
        // Wait, logic implementation:
        // root vs root (diff) -> children 'subdir' vs 'subdir'
        // subdir vs subdir (diff) -> children 'file.txt' vs 'file.txt'
        // file.txt vs file.txt (diff) -> Modified.
        // Path passed to compareNodes starts empty?
        // If I pass Paths.get(""), then it resolves name.
        // root children map: "subdir" -> subdirNode.
        // recurse call path: "" + "subdir" = "subdir".
        // subdir children map: "file.txt" -> fileNode.
        // recurse call path: "subdir" + "file.txt" = "subdir/file.txt".

        assertEquals("subdir/file.txt", diffs.get(0).getPath());
    }
}
