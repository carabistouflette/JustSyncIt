#!/bin/bash

# JustSyncIt Daily Backup Script
# This script performs automated daily backups with proper error handling and logging

# Configuration
SOURCE_DIR="/home/user/documents"
BACKUP_DIR="/backup/justsyncit"
LOG_FILE="/var/log/justsyncit-daily-backup.log"
RETENTION_DAYS=30
EMAIL_ALERT="admin@example.com"
JUSTSYNCIT_JAR="/opt/justsyncit/justsyncit.jar"

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
        echo "JustSyncIt daily backup failed with exit code $exit_code at line $line_number" | \
            mail -s "JustSyncIt Backup Failure" "$EMAIL_ALERT"
    fi
    
    exit $exit_code
}

# Set up error trapping
trap 'handle_error $LINENO' ERR

log_message "Starting daily backup routine"

# Check if JustSyncIt JAR exists
if [ ! -f "$JUSTSYNCIT_JAR" ]; then
    log_message "ERROR: JustSyncIt JAR not found at $JUSTSYNCIT_JAR"
    exit 1
fi

# Check if source directory exists
if [ ! -d "$SOURCE_DIR" ]; then
    log_message "ERROR: Source directory $SOURCE_DIR does not exist"
    exit 1
fi

# Check available disk space
AVAILABLE_SPACE=$(df -BG "$BACKUP_DIR" | awk '{print $4}')
REQUIRED_SPACE=$(du -sBG "$SOURCE_DIR" | awk '{print $1}')

if [ "$AVAILABLE_SPACE" -lt "$REQUIRED_SPACE" ]; then
    log_message "ERROR: Insufficient disk space. Available: ${AVAILABLE_SPACE}GB, Required: ${REQUIRED_SPACE}GB"
    exit 1
fi

# Create backup name with timestamp
BACKUP_NAME="Daily-$(date '+%Y-%m-%d')"
BACKUP_DESCRIPTION="Automated daily backup of $SOURCE_DIR"

log_message "Starting backup of $SOURCE_DIR with name: $BACKUP_NAME"

# Perform backup
BACKUP_START=$(date +%s)
java -jar "$JUSTSYNCIT_JAR" backup "$SOURCE_DIR" \
    --name "$BACKUP_NAME" \
    --description "$BACKUP_DESCRIPTION" \
    --include-hidden \
    --chunk-size 1048576 \
    --verify-integrity \
    --verbose \
    2>&1 | tee -a "$LOG_FILE"
BACKUP_EXIT_CODE=$?
BACKUP_END=$(date +%s)

# Check backup result
if [ $BACKUP_EXIT_CODE -eq 0 ]; then
    BACKUP_DURATION=$((BACKUP_END - BACKUP_START))
    log_message "Backup completed successfully in ${BACKUP_DURATION} seconds"
    
    # Get snapshot ID for verification
    SNAPSHOT_ID=$(tail -n 50 "$LOG_FILE" | grep "Snapshot ID:" | tail -n 1 | awk '{print $3}')
    
    if [ -n "$SNAPSHOT_ID" ]; then
        log_message "Verifying snapshot: $SNAPSHOT_ID"
        
        # Verify backup integrity
        if java -jar "$JUSTSYNCIT_JAR" snapshots verify "$SNAPSHOT_ID" --quiet >> "$LOG_FILE" 2>&1; then
            log_message "Snapshot verification successful: $SNAPSHOT_ID"
        else
            log_message "ERROR: Snapshot verification failed: $SNAPSHOT_ID"
            echo "JustSyncIt snapshot verification failed for $SNAPSHOT_ID" | \
                mail -s "JustSyncIt Verification Failure" "$EMAIL_ALERT"
        fi
    fi
    
    # Clean up old backups
    log_message "Cleaning up backups older than $RETENTION_DAYS days"
    java -jar "$JUSTSYNCIT_JAR" snapshots delete --older-than "$RETENTION_DAYS days" --force >> "$LOG_FILE" 2>&1
    
    # Run garbage collection
    log_message "Running garbage collection"
    java -jar "$JUSTSYNCIT_JAR" gc --verbose >> "$LOG_FILE" 2>&1
    
else
    log_message "ERROR: Backup failed with exit code $BACKUP_EXIT_CODE"
    echo "JustSyncIt daily backup failed with exit code $BACKUP_EXIT_CODE" | \
        mail -s "JustSyncIt Backup Failure" "$EMAIL_ALERT"
fi

log_message "Daily backup routine completed"

# Generate summary report
SUMMARY_FILE="/tmp/justsyncit-daily-summary.txt"
cat > "$SUMMARY_FILE" << EOF
JustSyncIt Daily Backup Summary
===============================
Date: $(date '+%Y-%m-%d %H:%M:%S')
Source Directory: $SOURCE_DIR
Backup Name: $BACKUP_NAME
Exit Code: $BACKUP_EXIT_CODE
Duration: ${BACKUP_DURATION} seconds
Log File: $LOG_FILE

Recent Snapshots:
$(java -jar "$JUSTSYNCIT_JAR" snapshots list --sort-by-date | head -10)

Storage Usage:
$(df -h "$BACKUP_DIR")
EOF

# Email summary (if mail command available)
if command -v mail; then
    mail -s "JustSyncIt Daily Backup Summary - $(date '+%Y-%m-%d')" "$EMAIL_ALERT" < "$SUMMARY_FILE"
fi

log_message "Backup routine completed successfully"
exit 0