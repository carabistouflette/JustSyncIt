#!/bin/bash

# JustSyncIt Weekly Full Backup Script
# This script performs comprehensive weekly backups with verification and reporting

# Configuration
SOURCE_DIRS=(
    "/home/user/documents"
    "/home/user/projects"
    "/home/user/media"
)
BACKUP_DIR="/backup/justsyncit"
LOG_FILE="/var/log/justsyncit-weekly-backup.log"
RETENTION_WEEKS=12
EMAIL_ALERT="admin@example.com"
JUSTSYNCIT_JAR="/opt/justsyncit/justsyncit.jar"
TEMP_REPORT="/tmp/justsyncit-weekly-report.txt"

# Ensure backup directory exists
mkdir -p "$BACKUP_DIR"
mkdir -p "$(dirname "$LOG_FILE")"

# Logging function
log_message() {
    echo "$(date '+%Y-%m-%d %H:%M:%S') - $1" >> "$LOG_FILE"
}

# Error handling function
handle_error() {
    local exit_code=$?
    local line_number=$1
    log_message "ERROR: Script failed at line $line_number with exit code $exit_code"
    
    # Send email alert
    if command -v mail; then
        echo "JustSyncIt weekly backup failed with exit code $exit_code at line $line_number" | \
            mail -s "JustSyncIt Weekly Backup Failure" "$EMAIL_ALERT"
    fi
    
    exit $exit_code
}

# Set up error trapping
trap 'handle_error $LINENO' ERR

log_message "Starting weekly full backup routine"

# Check if JustSyncIt JAR exists
if [ ! -f "$JUSTSYNCIT_JAR" ]; then
    log_message "ERROR: JustSyncIt JAR not found at $JUSTSYNCIT_JAR"
    exit 1
fi

# Check available disk space
TOTAL_SIZE=0
for dir in "${SOURCE_DIRS[@]}"; do
    if [ -d "$dir" ]; then
        dir_size=$(du -sBG "$dir" | awk '{print $1}' | sed 's/G//')
        TOTAL_SIZE=$((TOTAL_SIZE + dir_size))
    else
        log_message "WARNING: Source directory $dir does not exist, skipping"
    fi
done

AVAILABLE_SPACE=$(df -BG "$BACKUP_DIR" | awk '{print $4}' | sed 's/G//')
REQUIRED_SPACE=$((TOTAL_SIZE * 2))  # Account for deduplication overhead

if [ "$AVAILABLE_SPACE" -lt "$REQUIRED_SPACE" ]; then
    log_message "ERROR: Insufficient disk space. Available: ${AVAILABLE_SPACE}GB, Required: ${REQUIRED_SPACE}GB"
    exit 1
fi

# Create backup name with timestamp
BACKUP_NAME="Weekly-Full-$(date '+%Y-%m-%d')"
BACKUP_DESCRIPTION="Automated weekly full backup of multiple directories"

log_message "Starting weekly full backup with name: $BACKUP_NAME"
log_message "Source directories: ${SOURCE_DIRS[*]}"

# Initialize report
cat > "$TEMP_REPORT" << EOF
JustSyncIt Weekly Full Backup Report
====================================
Date: $(date '+%Y-%m-%d %H:%M:%S')
Backup Name: $BACKUP_NAME
Description: $BACKUP_DESCRIPTION

Source Directories:
EOF

for dir in "${SOURCE_DIRS[@]}"; do
    if [ -d "$dir" ]; then
        echo "- $dir ($(du -sh "$dir" | awk '{print $1}'))" >> "$TEMP_REPORT"
    else
        echo "- $dir (NOT FOUND)" >> "$TEMP_REPORT"
    fi
done

echo "" >> "$TEMP_REPORT"

# Perform backup for each source directory
BACKUP_START=$(date +%s)
SNAPSHOT_IDS=()

for source_dir in "${SOURCE_DIRS[@]}"; do
    if [ ! -d "$source_dir" ]; then
        log_message "Skipping non-existent directory: $source_dir"
        continue
    fi
    
    # Create a unique name for this directory backup
    dir_name=$(basename "$source_dir")
    snapshot_name="${BACKUP_NAME}-${dir_name}"
    
    log_message "Starting backup of $source_dir with snapshot name: $snapshot_name"
    
    # Perform backup
    if java -jar "$JUSTSYNCIT_JAR" backup "$source_dir" \
        --name "$snapshot_name" \
        --description "$BACKUP_DESCRIPTION - $dir_name" \
        --include-hidden \
        --follow-symlinks \
        --chunk-size 2097152 \
        --verify-integrity \
        --verbose \
        2>&1 | tee -a "$LOG_FILE"; then
        
        # Get snapshot ID
        snapshot_id=$(tail -n 50 "$LOG_FILE" | grep "Snapshot ID:" | tail -n 1 | awk '{print $3}')
        
        if [ -n "$snapshot_id" ]; then
            SNAPSHOT_IDS+=("$snapshot_id")
            log_message "Backup completed successfully for $source_dir, Snapshot ID: $snapshot_id"
            echo "✓ $source_dir - Snapshot ID: $snapshot_id" >> "$TEMP_REPORT"
        else
            log_message "WARNING: Could not retrieve snapshot ID for $source_dir"
            echo "⚠ $source_dir - Backup completed but snapshot ID not found" >> "$TEMP_REPORT"
        fi
    else
        log_message "ERROR: Backup failed for $source_dir"
        echo "✗ $source_dir - Backup failed" >> "$TEMP_REPORT"
        
        # Send immediate alert for this failure
        echo "JustSyncIt weekly backup failed for $source_dir" | \
            mail -s "JustSyncIt Backup Failure - $source_dir" "$EMAIL_ALERT"
    fi
done

BACKUP_END=$(date +%s)
BACKUP_DURATION=$((BACKUP_END - BACKUP_START))

echo "" >> "$TEMP_REPORT"
echo "Backup Duration: ${BACKUP_DURATION} seconds" >> "$TEMP_REPORT"
echo "" >> "$TEMP_REPORT"
echo "Snapshot Verification Results:" >> "$TEMP_REPORT"

# Verify all snapshots
VERIFICATION_FAILED=false
for snapshot_id in "${SNAPSHOT_IDS[@]}"; do
    if [ -n "$snapshot_id" ]; then
        log_message "Verifying snapshot: $snapshot_id"
        
        if java -jar "$JUSTSYNCIT_JAR" snapshots verify "$snapshot_id" --quiet >> "$LOG_FILE" 2>&1; then
            log_message "Snapshot verification successful: $snapshot_id"
            echo "✓ $snapshot_id - Verification successful" >> "$TEMP_REPORT"
        else
            log_message "ERROR: Snapshot verification failed: $snapshot_id"
            echo "✗ $snapshot_id - Verification failed" >> "$TEMP_REPORT"
            VERIFICATION_FAILED=true
        fi
    fi
done

# Clean up old backups
log_message "Cleaning up backups older than $RETENTION_WEEKS weeks"
if java -jar "$JUSTSYNCIT_JAR" snapshots delete --older-than "$RETENTION_WEEKS weeks" --force >> "$LOG_FILE" 2>&1; then
    echo "" >> "$TEMP_REPORT"
    echo "Old backups cleaned up successfully" >> "$TEMP_REPORT"
else
    echo "" >> "$TEMP_REPORT"
    echo "⚠ Warning: Old backup cleanup encountered issues" >> "$TEMP_REPORT"
fi

# Run garbage collection
log_message "Running garbage collection"
if java -jar "$JUSTSYNCIT_JAR" gc --verbose >> "$LOG_FILE" 2>&1; then
    echo "Garbage collection completed successfully" >> "$TEMP_REPORT"
else
    echo "⚠ Warning: Garbage collection encountered issues" >> "$TEMP_REPORT"
fi

# Add storage statistics to report
echo "" >> "$TEMP_REPORT"
echo "Storage Statistics:" >> "$TEMP_REPORT"
echo "------------------" >> "$TEMP_REPORT"
java -jar "$JUSTSYNCIT_JAR" snapshots list --statistics >> "$TEMP_REPORT" 2>&1

echo "" >> "$TEMP_REPORT"
echo "Disk Usage:" >> "$TEMP_REPORT"
df -h "$BACKUP_DIR" >> "$TEMP_REPORT"

# Send report via email
if command -v mail; then
    if [ "$VERIFICATION_FAILED" = true ]; then
        mail -s "JustSyncIt Weekly Backup Report - VERIFICATION ISSUES" "$EMAIL_ALERT" < "$TEMP_REPORT"
    else
        mail -s "JustSyncIt Weekly Backup Report - $(date '+%Y-%m-%d')" "$EMAIL_ALERT" < "$TEMP_REPORT"
    fi
fi

log_message "Weekly backup routine completed"

# Cleanup temporary report file
rm -f "$TEMP_REPORT"

exit 0