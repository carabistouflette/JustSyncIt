# Deduplication Strategy

## Overview
JustSyncIt uses an intelligent, multi-layered deduplication engine designed to maximize storage efficiency while maintaining high performance. The strategy combines content-defined chunking, probabilistic indexing, and similarity detection.

## Architecture

### 1. Content-Defined Chunking (FastCDC)
We use the FastCDC algorithm to split files into variable-sized chunks. This is resistant to insertions/deletions/shifts in content, unlike fixed-size chunking.
- **Min Size**: 4KB
- **Avg Size**: 64KB
- **Max Size**: 256KB

### 2. Similarity Detection (MinHash)
To detect near-duplicate files (e.g., a document with minor edits), we compute a MinHash signature for every file.
- **Algorithm**: MinHash with 256 hash functions.
- **Permutation**: XOR-based permutation with `fmix32` for high performance.
- **Usage**: Signatures are stored with file metadata. Jaccard Index estimation allows finding files with >90% similarity instantly.

### 3. Super-Chunking
To reduce the overhead of indexing millions of small chunks, we group contiguous chunks into "SuperChunks".
- **Strategy**: Content-defined boundaries (rolling hash on chunk hashes).
- **Benefit**: Reduces index lookups by 10-100x for bulk operations.

### 4. Bloom Filter Indexing
We use a Bloom Filter for the first pass of existence checks.
- **Memory**: Extremely low (< 10 bits per item).
- **Performance**: Prevents expensive disk/database lookups for unique chunks (which are the majority in new writes).
- **Protection**: Double Hashing with Murmur3-like mixing ensures low false positive rates.

### 5. Semantic Deduplication (Archives)
Special handling for Archive files (ZIP, JAR, etc.).
- **Detection**: Magic byte analysis.
- **Strategy**: Files are identified as containers. Future expansions will chunk compressed streams independently to deduplicate content *inside* archives.

## Performance Targets
- **CPU Overhead**: < 5%
- **RAM Usage**: < 200MB for 10TB index
- **Throughput**: Limits impact on backup speed to < 10%.
