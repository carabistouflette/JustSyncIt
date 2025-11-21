# JustSyncIt Snapshot Management Guide

## Table of Contents

- [Understanding Snapshots](#understanding-snapshots)
- [Listing and Examining Snapshots](#listing-and-examining-snapshots)
- [Snapshot Verification and Integrity Checking](#snapshot-verification-and-integrity-checking)
- [Snapshot Cleanup and Maintenance](#snapshot-cleanup-and-maintenance)
- [Best Practices for Snapshot Organization](#best-practices-for-snapshot-organization)
- [Advanced Snapshot Operations](#advanced-snapshot-operations)
- [Snapshot Lifecycle Management](#snapshot-lifecycle-management)
- [Troubleshooting Snapshot Issues](#troubleshooting-snapshot-issues)

## Understanding Snapshots

### What is a Snapshot?

A JustSyncIt snapshot represents a point-in-time backup of a directory or dataset. It contains:

- **Metadata**: Information about when and what was backed up
- **File List**: Complete inventory of all files in the backup
- **Chunk References**: Pointers to the actual data chunks
- **Integrity Data**: Hashes for verification and corruption detection

### Snapshot Structure

```
Snapshot
├── Metadata
│   ├── ID: Unique identifier
│   ├── Name: Human-readable name
│   ├── Description: Optional description
│   ├── Created: Timestamp
│   ├── Total Files: File count
│   └── Total Size: Size in bytes
├── File Index
│   ├── File paths and metadata
│   ├── File permissions and attributes
│   └── Chunk references
└── Chunk References
    ├── Chunk hashes
    ├── Chunk sizes
    └── Integrity checksums
```

### Snapshot Types

#### Full Snapshots
- Complete backup of all specified data
- Independent of other snapshots
- Larger storage requirements
- Faster restore times

#### Incremental Snapshots
- Only changes since last backup
- Reference chunks from previous snapshots
- Smaller storage requirements
- May require multiple snapshots for full restore

### Snapshot Metadata

Each snapshot contains rich metadata:

```json
{
  "id": "abc123def456789abcdef123456789abcdef123456789abcdef123456789abcdef1234567890",
  "name": "Daily Backup - Documents",
  "description": "Automatic daily backup of user documents",
  "createdAt": "2023-12-01T10:30:15Z",
  "totalFiles": 1256,
  "totalSize": 2147483648,
  "chunkCount": 892,
  "parentSnapshot": null,
  "backupOptions": {
    "chunkSize": 65536,
    "includeHidden": false,
    "symlinkStrategy": "SKIP",
    "verifyIntegrity": true
  }
}
```

## Listing and Examining Snapshots

### Basic Snapshot Listing

#### List All Snapshots

```bash
# Basic listing
java -jar justsyncit.jar snapshots list

# Output format
ID              Name                    Created              Files    Size
abc123def456   Daily Backup            2023-12-01 10:30:15  1256     2.0 GB
def456ghi789   Weekly Backup           2023-11-28 15:45:22  3421     5.2 GB
```

#### Detailed Listing

```bash
# Verbose listing with descriptions
java -jar justsyncit.jar snapshots list --verbose

# Sort by size (largest first)
java -jar justsyncit.jar snapshots list --sort-by-size

# Sort by date (newest first - default)
java -jar justsyncit.jar snapshots list --sort-by-date
```

#### Filtering Snapshots

```bash
# Filter by name pattern
java -jar justsyncit.jar snapshots list --filter "*Daily*"

# Filter by date range
java -jar justsyncit.jar snapshots list --from "2023-11-01" --to "2023-12-01"

# Filter by size range
java -jar justsyncit.jar snapshots list --min-size "1GB" --max-size "10GB"
```

### Detailed Snapshot Information

#### Basic Snapshot Info

```bash
# Get basic information
java -jar justsyncit.jar snapshots info abc123def456

# Output
Snapshot ID: abc123def456789abcdef123456789abcdef123456789abcdef123456789abcdef1234567890
Name: Daily Backup - Documents
Description: Automatic daily backup of user documents
Created: 2023-12-01T10:30:15Z
Total Files: 1256
Total Size: 2.0 GB
Chunk Count: 892
```

#### File Listing

```bash
# Show files in snapshot
java -jar justsyncit.jar snapshots info abc123def456 --show-files

# Limit file listing
java -jar justsyncit.jar snapshots info abc123def456 --show-files --file-limit 50

# Hide statistics
java -jar justsyncit.jar snapshots info abc123def456 --show-files --no-statistics
```

#### File Details Output

```
Files in snapshot abc123def456:
/documents/
  readme.txt                    1.2 KB    2023-11-30 14:20:15
  important.docx               15.6 MB    2023-11-29 09:15:30
  config.yaml                  2.1 KB     2023-11-28 16:45:22
/photos/
  vacation.jpg                 3.4 MB     2023-11-25 12:10:05
  family.png                   2.8 MB     2023-11-20 18:30:12
/projects/
  myapp/                      -           2023-11-15 10:00:00
    src/
      main.java                 8.5 KB     2023-11-15 10:00:00
    build/
      app.jar                  1.2 MB     2023-11-15 11:30:00

Total: 1256 files, 2.0 GB
```

#### Advanced Information

```bash
# Show chunk information
java -jar justsyncit.jar snapshots info abc123def456 --show-chunks

# Show deduplication statistics
java -jar justsyncit.jar snapshots info abc123def456 --show-dedup-stats

# Show backup options used
java -jar justsyncit.jar snapshots info abc123def456 --show-backup-options
```

### Snapshot Comparison

#### Compare Two Snapshots

```bash
# Compare snapshots
java -jar justsyncit.jar snapshots compare abc123def456 def456ghi789

# Detailed comparison
java -jar justsyncit.jar snapshots compare abc123def456 def456ghi789 --detailed

# Show only differences
java -jar justsyncit.jar snapshots compare abc123def456 def456ghi789 --diff-only
```

#### Comparison Output

```
Comparing snapshots:
Source: abc123def456 (Daily Backup - Documents)
Target: def456ghi789 (Weekly Backup)

Files added: 45
  /documents/new-file.txt
  /photos/new-photo.jpg
  /projects/new-project/

Files modified: 12
  /documents/readme.txt (size changed: 1.2 KB → 1.5 KB)
  /documents/important.docx (modified: 2023-11-29 → 2023-12-01)

Files deleted: 8
  /documents/old-file.txt
  /photos/obsolete.jpg

Size difference: +156.3 MB
```

## Snapshot Verification and Integrity Checking

### Verification Types

#### Full Verification

Verifies both file integrity and chunk integrity:

```bash
# Full verification
java -jar justsyncit.jar snapshots verify abc123def456

# With progress
java -jar justsyncit.jar snapshots verify abc123def456 --progress

# Verbose output
java -jar justsyncit.jar snapshots verify abc123def456 --verbose
```

#### Chunk-Only Verification

Faster verification that only checks chunk integrity:

```bash
# Skip file hash verification
java -jar justsyncit.jar snapshots verify abc123def456 --no-file-hash-verify

# Quick verification
java -jar justsyncit.jar snapshots verify abc123def456 --quick
```

#### File-Only Verification

Verifies file metadata and structure:

```bash
# Skip chunk integrity verification
java -jar justsyncit.jar snapshots verify abc123def456 --no-chunk-verify

# Metadata verification only
java -jar justsyncit.jar snapshots verify abc123def456 --metadata-only
```

### Verification Process

The verification process follows these steps:

1. **Snapshot Metadata Validation**
   - Check snapshot ID format
   - Validate metadata integrity
   - Verify timestamps and file counts

2. **File Index Verification**
   - Validate file path references
   - Check file metadata consistency
   - Verify chunk references exist

3. **Chunk Integrity Verification**
   - Read each chunk from storage
   - Calculate BLAKE3 hash
   - Compare with expected hash
   - Report corrupted chunks

4. **Cross-Reference Validation**
   - Ensure all referenced chunks exist
   - Check for orphaned chunks
   - Validate deduplication consistency

### Verification Output

#### Successful Verification

```
Verifying snapshot: abc123def456
Checking snapshot metadata... OK
Checking file index... OK (1256 files)
Checking chunk integrity... OK (892 chunks)
Checking cross-references... OK

Verification completed successfully!
✅ All files and chunks verified.
✅ No integrity issues found.
✅ Snapshot is healthy.

Verification summary:
- Files verified: 1256
- Chunks verified: 892
- Data verified: 2.0 GB
- Verification time: 2m 15s
```

#### Verification with Issues

```
Verifying snapshot: abc123def456
Checking snapshot metadata... OK
Checking file index... OK (1256 files)
Checking chunk integrity... ⚠️  ISSUES FOUND

❌ Chunk integrity issues:
  Chunk: def789ghi012 - Expected hash mismatch
  Chunk: ghi345jkl345 - Data corruption detected
  Chunk: jkl678mno456 - Chunk not found in storage

❌ File integrity issues:
  File: /documents/important.docx - References corrupted chunk
  File: /photos/vacation.jpg - Missing chunk data

Verification completed with issues!
⚠️ 3 chunks have integrity problems
⚠️ 2 files affected by corruption
⚠️ 156.3 MB of data may be lost

Recommendations:
1. Run garbage collection to clean up orphaned chunks
2. Restore from a known-good snapshot
3. Create a new backup of the affected data
```

### Batch Verification

#### Verify Multiple Snapshots

```bash
# Verify all snapshots
java -jar justsyncit.jar snapshots verify --all

# Verify snapshots matching pattern
java -jar justsyncit.jar snapshots verify --pattern "*Daily*"

# Verify recent snapshots
java -jar justsyncit.jar snapshots verify --days 7
```

#### Scheduled Verification

```bash
# Create verification script
cat > verify-snapshots.sh <<'EOF'
#!/bin/bash
LOG_FILE="/var/log/justsyncit-verify.log"
DATE=$(date '+%Y-%m-%d %H:%M:%S')

echo "[$DATE] Starting snapshot verification" >> $LOG_FILE

# Verify snapshots created in last 7 days
SNAPSHOTS=$(java -jar justsyncit.jar snapshots list --days 7 --format json | jq -r '.[].id')

for snapshot in $SNAPSHOTS; do
    echo "[$DATE] Verifying snapshot: $snapshot" >> $LOG_FILE
    
    if java -jar justsyncit.jar snapshots verify "$snapshot" --quiet; then
        echo "[$DATE] ✅ Snapshot $snapshot verified successfully" >> $LOG_FILE
    else
        echo "[$DATE] ❌ Snapshot $snapshot verification failed" >> $LOG_FILE
        # Send alert
        echo "Snapshot verification failed for $snapshot" | mail -s "JustSyncIt Alert" admin@example.com
    fi
done

echo "[$DATE] Snapshot verification completed" >> $LOG_FILE
EOF

chmod +x verify-snapshots.sh

# Add to crontab (weekly verification)
echo "0 2 * * 0 /path/to/verify-snapshots.sh" | crontab -
```

## Snapshot Cleanup and Maintenance

### Garbage Collection

#### Understanding Garbage Collection

Garbage collection removes unused chunks from storage:

- **Orphaned Chunks**: Chunks not referenced by any snapshot
- **Duplicated Data**: Multiple copies of identical content
- **Temporary Files**: Leftover from interrupted operations

#### Manual Garbage Collection

```bash
# Run garbage collection
java -jar justsyncit.jar gc

# Dry run (show what would be deleted)
java -jar justsyncit.jar gc --dry-run

# Verbose garbage collection
java -jar justsyncit.jar gc --verbose

# Force garbage collection
java -jar justsyncit.jar gc --force
```

#### Garbage Collection Output

```
Starting garbage collection...
Scanning snapshots... Found 15 snapshots
Scanning chunks... Found 8920 chunks
Identifying orphaned chunks... Found 234 orphaned chunks
Calculating space to reclaim... 1.2 GB can be reclaimed

Garbage collection completed!
✅ Removed 234 orphaned chunks
✅ Reclaimed 1.2 GB of storage space
✅ 8686 chunks remain in use
✅ Storage optimization: 13.2% space saved

Duration: 3m 45s
```

#### Scheduled Garbage Collection

```bash
# Create GC script
cat > cleanup-snapshots.sh <<'EOF'
#!/bin/bash
LOG_FILE="/var/log/justsyncit-gc.log"
DATE=$(date '+%Y-%m-%d %H:%M:%S')

echo "[$DATE] Starting garbage collection" >> $LOG_FILE

# Run garbage collection with dry run first
echo "[$DATE] Dry run:" >> $LOG_FILE
java -jar justsyncit.jar gc --dry-run --verbose >> $LOG_FILE

# Run actual garbage collection
echo "[$DATE] Running garbage collection:" >> $LOG_FILE
java -jar justsyncit.jar gc --verbose >> $LOG_FILE

echo "[$DATE] Garbage collection completed" >> $LOG_FILE
EOF

chmod +x cleanup-snapshots.sh

# Schedule weekly garbage collection
echo "0 3 * * 0 /path/to/cleanup-snapshots.sh" | crontab -
```

### Snapshot Retention Policies

#### Automated Cleanup

```bash
# Set retention policy
export JUSTSYNCIT_RETENTION_POLICY="daily:7,weekly:4,monthly:12"

# Apply retention policy
java -jar justsyncit.jar snapshots cleanup --apply-retention

# Preview what would be deleted
java -jar justsyncit.jar snapshots cleanup --apply-retention --dry-run
```

#### Custom Retention Rules

```bash
# Keep daily snapshots for 30 days
java -jar justsyncit.jar snapshots cleanup --keep-daily 30

# Keep weekly snapshots for 12 weeks
java -jar justsyncit.jar snapshots cleanup --keep-weekly 12

# Keep monthly snapshots for 12 months
java -jar justsyncit.jar snapshots cleanup --keep-monthly 12

# Keep all snapshots from first of month
java -jar justsyncit.jar snapshots cleanup --keep-first-of-month

# Keep snapshots larger than specified size
java -jar justsyncit.jar snapshots cleanup --min-size "1GB"
```

### Snapshot Deletion

#### Safe Deletion

```bash
# Interactive deletion with confirmation
java -jar justsyncit.jar snapshots delete abc123def456

# Confirmation prompt:
Are you sure you want to delete snapshot 'abc123def456'?
This will permanently remove the snapshot and free associated storage space.
Type 'DELETE' to confirm: DELETE
```

#### Force Deletion

```bash
# Delete without confirmation
java -jar justsyncit.jar snapshots delete abc123def456 --force

# Delete multiple snapshots
java -jar justsyncit.jar snapshots delete abc123def456,def456ghi789,ghi789jkl012 --force

# Delete snapshots matching pattern
java -jar justsyncit.jar snapshots delete --pattern "*test*" --force
```

#### Batch Deletion

```bash
# Delete old snapshots
java -jar justsyncit.jar snapshots delete --older-than "2023-01-01" --force

# Delete snapshots by size
java -jar justsyncit.jar snapshots delete --smaller-than "100MB" --force

# Delete failed snapshots
java -jar justsyncit.jar snapshots delete --failed --force
```

### Storage Optimization

#### Deduplication Analysis

```bash
# Analyze deduplication efficiency
java -jar justsyncit.jar snapshots analyze-deduplication

# Detailed analysis
java -jar justsyncit.jar snapshots analyze-deduplication --detailed

# Generate report
java -jar justsyncit.jar snapshots analyze-deduplication --report-file dedup-report.html
```

#### Storage Usage Reports

```bash
# Generate storage report
java -jar justsyncit.jar snapshots storage-report

# Detailed report with charts
java -jar justsyncit.jar snapshots storage-report --detailed --output report.html

# JSON format for integration
java -jar justsyncit.jar snapshots storage-report --format json --output report.json
```

## Best Practices for Snapshot Organization

### Naming Conventions

#### Consistent Naming

Use a consistent naming scheme for easy identification:

```bash
# Date-based naming
java -jar justsyncit.jar backup /data --name "Backup-2023-12-01"

# Environment-based naming
java -jar justsyncit.jar backup /data --name "Production-Daily-2023-12-01"
java -jar justsyncit.jar backup /data --name "Development-Weekly-2023-12-01"

# Content-based naming
java -jar justsyncit.jar backup /documents --name "Documents-Daily"
java -jar justsyncit.jar backup /photos --name "Photos-Weekly"
java -jar justsyncit.jar backup /database --name "Database-Incremental"
```

#### Hierarchical Naming

Use hierarchical naming for complex environments:

```bash
# Format: <Environment>-<Type>-<Frequency>-<Date>
Production-Daily-2023-12-01
Production-Weekly-2023-12-01
Production-Monthly-2023-12-01

Development-Daily-2023-12-01
Testing-Weekly-2023-12-01
Staging-Monthly-2023-12-01
```

### Description Standards

#### Meaningful Descriptions

Add descriptive information to snapshots:

```bash
# Create backup with description
java -jar justsyncit.jar backup /data \
  --name "Production-Daily-2023-12-01" \
  --description "Daily backup of production database and application files"

# Include context in description
java -jar justsyncit.jar backup /data \
  --name "Pre-Deployment-2023-12-01" \
  --description "Backup taken before deploying version 2.1.0"
```

#### Description Templates

Use standardized description templates:

```bash
# Daily backup template
"DAILY: Automatic backup of {environment} data on {date}"

# Pre-deployment template
"PRE-DEPLOY: Backup before deploying {version} to {environment}"

# Post-maintenance template
"POST-MAINTENANCE: Backup after {maintenance_type} on {date}"
```

### Organization Strategies

#### Environment Separation

Keep different environments separate:

```bash
# Production snapshots
/prod-backups/
  ├── daily/
  ├── weekly/
  └── monthly/

# Development snapshots
/dev-backups/
  ├── daily/
  ├── weekly/
  └── monthly/

# Testing snapshots
/test-backups/
  ├── daily/
  ├── weekly/
  └── monthly/
```

#### Time-based Organization

Organize snapshots by time periods:

```bash
# Yearly organization
/backups/
  ├── 2023/
  │   ├── 01-January/
  │   ├── 02-February/
  │   └── 12-December/
  ├── 2024/
  └── 2025/

# Monthly organization
/backups/
  ├── 2023-12/
  │   ├── daily/
  │   ├── weekly/
  │   └── monthly/
  └── 2024-01/
```

#### Tag-based Organization

Use tags for flexible organization:

```bash
# Create backup with tags
java -jar justsyncit.jar backup /data \
  --name "Production-Backup-2023-12-01" \
  --tags "production,daily,critical"

# Filter by tags
java -jar justsyncit.jar snapshots list --tags "production"
java -jar justsyncit.jar snapshots list --tags "daily,critical"
java -jar justsyncit.jar snapshots list --tags "production,monthly"
```

### Retention Management

#### Tiered Retention

Implement tiered retention policies:

```bash
# Tier 1: Keep daily backups for 30 days
java -jar justsyncit.jar snapshots cleanup --keep-daily 30

# Tier 2: Keep weekly backups for 12 weeks
java -jar justsyncit.jar snapshots cleanup --keep-weekly 12

# Tier 3: Keep monthly backups for 12 months
java -jar justsyncit.jar snapshots cleanup --keep-monthly 12

# Tier 4: Keep yearly backups for 7 years
java -jar justsyncit.jar snapshots cleanup --keep-yearly 7
```

#### Smart Retention

Use intelligent retention based on importance:

```bash
# Keep all critical snapshots
java -jar justsyncit.jar snapshots cleanup --keep-tagged "critical"

# Keep snapshots larger than threshold
java -jar justsyncit.jar snapshots cleanup --min-size "10GB"

# Keep manually created snapshots
java -jar justsyncit.jar snapshots cleanup --keep-manual

# Keep snapshots with specific descriptions
java -jar justsyncit.jar snapshots cleanup --keep-description "*pre-deployment*"
```

## Advanced Snapshot Operations

### Snapshot Cloning

#### Create Snapshot Clone

```bash
# Clone existing snapshot
java -jar justsyncit.jar snapshots clone abc123def456 --name "Cloned-Snapshot"

# Clone with new description
java -jar justsyncit.jar snapshots clone abc123def456 \
  --name "Test-Restore-Point" \
  --description "Clone for testing restore procedures"

# Clone to different storage
java -jar justsyncit.jar snapshots clone abc123def456 \
  --name "Offsite-Copy" \
  --storage "/mnt/offsite-storage"
```

### Snapshot Export/Import

#### Export Snapshot Metadata

```bash
# Export snapshot information
java -jar justsyncit.jar snapshots export abc123def456 --output snapshot-info.json

# Export file list
java -jar justsyncit.jar snapshots export abc123def456 --file-list-only --output files.txt

# Export in different formats
java -jar justsyncit.jar snapshots export abc123def456 --format csv --output snapshot.csv
java -jar justsyncit.jar snapshots export abc123def456 --format xml --output snapshot.xml
```

#### Import Snapshot

```bash
# Import snapshot from metadata
java -jar justsyncit.jar snapshots import snapshot-info.json

# Import with validation
java -jar justsyncit.jar snapshots import snapshot-info.json --validate

# Import to different location
java -jar justsyncit.jar snapshots import snapshot-info.json --storage "/new/storage/path"
```

### Snapshot Analysis

#### Content Analysis

```bash
# Analyze snapshot content
java -jar justsyncit.jar snapshots analyze abc123def456

# File type analysis
java -jar justsyncit.jar snapshots analyze abc123def456 --file-types

# Size distribution analysis
java -jar justsyncit.jar snapshots analyze abc123def456 --size-distribution

# Date distribution analysis
java -jar justsyncit.jar snapshots analyze abc123def456 --date-distribution
```

#### Comparison Analysis

```bash
# Compare multiple snapshots
java -jar justsyncit.jar snapshots compare abc123def456 def456ghi789 ghi789jkl012

# Generate comparison report
java -jar justsyncit.jar snapshots compare abc123def456 def456ghi789 \
  --report-file comparison.html

# Visual comparison
java -jar justsyncit.jar snapshots compare abc123def456 def456ghi789 \
  --visual --output chart.png
```

### Snapshot Recovery

#### Partial Recovery

```bash
# Recover specific files
java -jar justsyncit.jar snapshots recover abc123def456 \
  --files "/documents/important.txt,/photos/vacation.jpg" \
  --output /recovery/partial

# Recover by pattern
java -jar justsyncit.jar snapshots recover abc123def456 \
  --pattern "*.docx" \
  --output /recovery/documents

# Recover by date
java -jar justsyncit.jar snapshots recover abc123def456 \
  --from-date "2023-11-01" \
  --to-date "2023-11-30" \
  --output /recovery/november
```

#### Emergency Recovery

```bash
# Recover from corrupted snapshot
java -jar justsyncit.jar snapshots emergency-recover abc123def456 \
  --output /emergency/recovery

# Recover with maximum tolerance
java -jar justsyncit.jar snapshots emergency-recover abc123def456 \
  --ignore-corruption \
  --max-tolerance 10 \
  --output /emergency/recovery
```

## Snapshot Lifecycle Management

### Creation Phase

#### Pre-Backup Validation

```bash
# Validate source before backup
java -jar justsyncit.jar validate /path/to/source

# Check disk space
java -jar justsyncit.jar check-space /path/to/source

# Estimate backup size
java -jar justsyncit.jar estimate /path/to/source
```

#### Backup Optimization

```bash
# Optimize chunk size for data type
java -jar justsyncit.jar backup /documents --chunk-size 262144
java -jar justsyncit.jar backup /media --chunk-size 4194304

# Enable parallel processing
java -jar justsyncit.jar backup /data --parallel-threads 4

# Optimize for network backup
java -jar justsyncit.jar backup /data --network-optimized
```

### Active Phase

#### Monitoring

```bash
# Monitor backup progress
java -jar justsyncit.jar backup /data --monitor

# Set up alerts
java -jar justsyncit.jar backup /data --alert-on-error --email admin@example.com

# Real-time statistics
java -jar justsyncit.jar backup /data --real-time-stats
```

#### Maintenance

```bash
# Periodic verification
java -jar justsyncit.jar snapshots verify --all --schedule "daily"

# Automatic cleanup
java -jar justsyncit.jar snapshots cleanup --auto --retention-policy "daily:30,weekly:12"

# Health monitoring
java -jar justsyncit.jar monitor --health-check --interval 3600
```

### Archival Phase

#### Long-term Storage

```bash
# Archive old snapshots
java -jar justsyncit.jar snapshots archive --older-than "1year" --to "/archive/storage"

# Compress archives
java -jar justsyncit.jar snapshots archive --compress --to "/archive/storage"

# Create archive index
java -jar justsyncit.jar snapshots archive --create-index --to "/archive/storage"
```

#### Migration

```bash
# Migrate to new storage
java -jar justsyncit.jar snapshots migrate --to "/new/storage/location"

# Migrate with verification
java -jar justsyncit.jar snapshots migrate --to "/new/storage" --verify-after-migrate

# Incremental migration
java -jar justsyncit.jar snapshots migrate --to "/new/storage" --incremental
```

## Troubleshooting Snapshot Issues

### Common Problems

#### Snapshot Not Found

**Symptoms:**
```
Error: Snapshot not found: abc123def456
```

**Solutions:**
```bash
# List all snapshots
java -jar justsyncit.jar snapshots list

# Search for partial ID
java -jar justsyncit.jar snapshots list --filter "abc123"

# Check storage location
ls -la ~/.justsyncit/data/snapshots/
```

#### Corruption Issues

**Symptoms:**
```
Verification failed: Chunk integrity issues detected
```

**Solutions:**
```bash
# Identify corrupted chunks
java -jar justsyncit.jar snapshots verify abc123def456 --verbose

# Recover what's possible
java -jar justsyncit.jar snapshots emergency-recover abc123def456 --ignore-corruption

# Create new backup from remaining data
java -jar justsyncit.jar backup /recovered/data --name "Recovery-Backup"
```

#### Storage Space Issues

**Symptoms:**
```
Error: Insufficient disk space
```

**Solutions:**
```bash
# Check available space
df -h ~/.justsyncit/data/

# Run garbage collection
java -jar justsyncit.jar gc --verbose

# Clean up old snapshots
java -jar justsyncit.jar snapshots cleanup --older-than "6months" --force

# Move to larger storage
java -jar justsyncit.jar snapshots migrate --to "/larger/storage"
```

### Recovery Procedures

#### Metadata Corruption

```bash
# Rebuild metadata from chunks
java -jar justsyncit.jar snapshots rebuild-metadata --from-chunks

# Rebuild from file index
java -jar justsyncit.jar snapshots rebuild-metadata --from-index

# Verify rebuilt metadata
java -jar justsyncit.jar snapshots verify --all --verbose
```

#### Chunk Loss

```bash
# Identify missing chunks
java -jar justsyncit.jar snapshots check-chunks --missing-only

# Attempt recovery from other snapshots
java -jar justsyncit.jar snapshots recover-chunks --from-other-snapshots

# Mark missing chunks as unrecoverable
java -jar justsyncit.jar snapshots mark-chunks-lost --chunk-ids "chunk1,chunk2,chunk3"
```

This comprehensive snapshot management guide provides everything you need to effectively manage JustSyncIt snapshots. For additional information, refer to the [Getting Started Guide](getting-started.md) and [CLI Reference](cli-reference.md).