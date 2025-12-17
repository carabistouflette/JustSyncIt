package com.justsyncit.storage.snapshot;

import com.justsyncit.storage.metadata.FileMetadata;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Utility for comparing two Merkle Trees to find differences.
 */
public class MerkleTreeDiffer {

    public enum ChangeType {
        ADDED,
        DELETED,
        MODIFIED
    }

    public static class DiffEntry {
        private final String path;
        private final ChangeType type;
        private final FileMetadata oldMetadata;
        private final FileMetadata newMetadata;

        public DiffEntry(String path, ChangeType type, FileMetadata oldMetadata, FileMetadata newMetadata) {
            this.path = path;
            this.type = type;
            this.oldMetadata = oldMetadata;
            this.newMetadata = newMetadata;
        }

        public String getPath() {
            return path;
        }

        public ChangeType getType() {
            return type;
        }

        public FileMetadata getOldMetadata() {
            return oldMetadata;
        }

        public FileMetadata getNewMetadata() {
            return newMetadata;
        }

        @Override
        public String toString() {
            return String.format("[%s] %s", type, path);
        }
    }

    /**
     * Compares two Merkle Trees and returns a list of differences.
     *
     * @param oldRoot The root of the older snapshot tree (can be null if new
     *                snapshot is first).
     * @param newRoot The root of the newer snapshot tree (can be null if snapshot
     *                deleted, though unlikely in this context).
     * @return List of differences.
     */
    public List<DiffEntry> diff(MerkleNode oldRoot, MerkleNode newRoot) throws IOException {
        List<DiffEntry> diffs = new ArrayList<>();
        compareNodes(oldRoot, newRoot, Paths.get(""), diffs);
        return diffs;
    }

    private void compareNodes(MerkleNode oldNode, MerkleNode newNode, Path currentPath, List<DiffEntry> diffs)
            throws IOException {
        // 1. Both null -> no change (shouldn't happen in traversal)
        if (oldNode == null && newNode == null) {
            return;
        }

        // 2. Old null, New not null -> Added
        if (oldNode == null) {
            collectAll(newNode, currentPath, ChangeType.ADDED, diffs);
            return;
        }

        // 3. Old not null, New null -> Deleted
        if (newNode == null) {
            collectAll(oldNode, currentPath, ChangeType.DELETED, diffs);
            return;
        }

        // 4. Hashes match -> Identical (skip subtree)
        if (Objects.equals(oldNode.getHash(), newNode.getHash())) {
            return;
        }

        // 5. Types differ -> Deleted then Added (effectively modified type, but simpler
        // to treat as del+add or just mod?)
        // If one is file and other is dir, it's a structural change.
        if (oldNode.getType() != newNode.getType()) {
            // Treat as delete old and add new
            collectAll(oldNode, currentPath, ChangeType.DELETED, diffs);
            collectAll(newNode, currentPath, ChangeType.ADDED, diffs);
            return;
        }

        // 6. Both FILES, hashes differ -> Modified
        if (oldNode.getType() == MerkleNode.Type.FILE) {
            // Need metadata to confirm detailed file diff?
            // MerkleNode stores limited info. The user might want size/time diffs.
            // For now, simple modification entry.
            // Assuming MetadataService will fill in details if needed, or we attach limited
            // metadata to MerkleNode if accessible?
            // Current MerkleNode DOES NOT store full FileMetadata, only hash.
            // But we typically want to list the file path.
            // The path is built up via recursion.

            // To provide full FileMetadata, we'd need to fetch it.
            // But the diff command can fetch metadata lazily or we just return paths.
            // Let's pass null metadata for now and let the caller enrich if needed,
            // OR we assume the diff is just structural for now.

            diffs.add(new DiffEntry(currentPath.toString(), ChangeType.MODIFIED, null, null));
            return;
        }

        // 7. Both DIRECTORIES, hashes differ -> Recurse into children
        if (oldNode.getType() == MerkleNode.Type.DIRECTORY) {
            Map<String, MerkleNode> oldChildren = getChildrenMap(oldNode);
            Map<String, MerkleNode> newChildren = getChildrenMap(newNode);

            // Union of all child names
            List<String> allNames = new ArrayList<>();
            allNames.addAll(oldChildren.keySet());
            for (String name : newChildren.keySet()) {
                if (!oldChildren.containsKey(name)) {
                    allNames.add(name);
                }
            }
            Collections.sort(allNames);

            for (String name : allNames) {
                MerkleNode oldChild = oldChildren.get(name);
                MerkleNode newChild = newChildren.get(name);
                Path childPath = currentPath.resolve(name); // Or use node name? The node name is just the segment.
                // Wait, currentPath is parent path. Child path is currentPath + name.
                // However, the roots might have names "." or similar.
                // Typically root has no name or name is root directory name.
                // Let's assume currentPath starts empty.
                // If we are at root level, and root name is "backup", we might want to skip it
                // if it's common prefix,
                // or include it.
                // Usually diff is relative to the snapshot root.
                // If we pass in roots, their names don't matter much for relativity, only
                // children.
                // BUT, if we are comparing two roots, we are comparing their contents.
                // If roots themselves are passed to this function, we entered here because
                // roots differed.
                // Logic above handles "Are these two specific nodes different?"
                // If they are dirs, we iterate children.

                compareNodes(oldChild, newChild, childPath, diffs); // Bug: childPath logic?
                // If currentPath is "", and child name is "docs", path is "docs".
                // Correct.
            }
        }
    }

    private void collectAll(MerkleNode node, Path currentPath, ChangeType type, List<DiffEntry> diffs)
            throws IOException {
        if (node == null)
            return;

        // Use node name for path.
        // Wait, currentPath passed in from caller normally includes the node's name IF
        // it was a child iteration.
        // But here we are calling recursively.
        // Let's refine the path logic.
        // When recursing for children map, we constructed childPath =
        // currentPath.resolve(name).
        // So 'currentPath' passed here IS the path of 'node'.

        if (node.getType() == MerkleNode.Type.FILE) {
            diffs.add(new DiffEntry(currentPath.toString(), type, null, null));
        } else {
            // It's a directory. Added/Deleted directory implies all children are
            // added/deleted.
            // Often in diffs we just show the directory itself as added/deleted, OR we list
            // all files.
            // Listing all files is more granular and usually expected for exact file
            // counts.
            // Let's list the directory AND its content? Or just content?
            // "git status" shows untracked dir. "git diff" might show all files.
            // Let's show all files for completeness.

            // Also add the directory entry itself?
            diffs.add(new DiffEntry(currentPath.toString(), type, null, null)); // Add the dir itself

            if (node.getChildren() != null) {
                for (MerkleNode child : node.getChildren()) {
                    collectAll(child, currentPath.resolve(child.getName()), type, diffs);
                }
            }
        }
    }

    private Map<String, MerkleNode> getChildrenMap(MerkleNode node) throws IOException {
        Map<String, MerkleNode> map = new HashMap<>();
        List<MerkleNode> children = node.getChildren();
        if (children != null) {
            for (MerkleNode child : children) {
                map.put(child.getName(), child);
            }
        }
        return map;
    }
}
