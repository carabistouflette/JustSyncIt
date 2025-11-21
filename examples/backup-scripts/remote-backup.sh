#!/bin/bash

# JustSyncIt Remote Backup Script
# This script performs backups to a remote JustSyncIt server

# Configuration
SOURCE_DIR="/home/user/important-data"
REMOTE_SERVER="backup.example.com"
REMOTE_PORT=8080
REMOTE_USERNAME="backup_user"
REMOTE_PASSWORD="secure_password"  # In production, use a more secure method
BACKUP_NAME="Remote-$(date '+%Y-%m-%d-%H%M%S')"
BACKUP_DESCRIPTION="Remote backup to $REMOTE_SERVER"
LOG_FILE="/var/log/justsyncit-remote-backup.log"
JUSTSYNCIT_JAR="/opt/justsyncit/justsyncit.jar"
TRANSPORT_PROTOCOL="tcp"  # Options: tcp, quic
COMPRESSION_LEVEL=6
MAX_RETRIES=3
RETRY_DELAY=30

# Ensure log directory exists
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
    
    # Send email alert if mail command is available
    if command -v mail; then
        echo "JustSyncIt remote backup failed with exit code $exit_code at line $line_number" | \
            mail -s "JustSyncIt Remote Backup Failure" "admin@example.com"
    fi
    
    exit $exit_code
}

# Set up error trapping
trap 'handle_error $LINENO' ERR

log_message "Starting remote backup routine to $REMOTE_SERVER"

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

# Test network connectivity to remote server
log_message "Testing network connectivity to $REMOTE_SERVER:$REMOTE_PORT"
if ! nc -z "$REMOTE_SERVER" "$REMOTE_PORT" 2>/dev/null; then
    log_message "ERROR: Cannot connect to remote server $REMOTE_SERVER:$REMOTE_PORT"
    exit 1
fi

log_message "Network connectivity test successful"

# Check source directory size
SOURCE_SIZE=$(du -sBG "$SOURCE_DIR" | awk '{print $1}' | sed 's/G//')
log_message "Source directory size: ${SOURCE_SIZE}GB"

# Function to attempt backup with retries
attempt_backup() {
    local attempt=$1
    local max_attempts=$2
    
    log_message "Backup attempt $attempt of $max_attempts"
    
    # Create temporary directory for backup metadata
    TEMP_DIR=$(mktemp -d)
    METADATA_FILE="$TEMP_DIR/backup-metadata.json"
    
    # Create backup metadata
    cat > "$METADATA_FILE" << EOF
{
    "backup_name": "$BACKUP_NAME",
    "description": "$BACKUP_DESCRIPTION",
    "source_directory": "$SOURCE_DIR",
    "timestamp": "$(date -Iseconds)",
    "hostname": "$(hostname)",
    "username": "$(whoami)",
    "transport_protocol": "$TRANSPORT_PROTOCOL",
    "compression_level": $COMPRESSION_LEVEL
}
EOF
    
    # Perform backup with retry logic
    BACKUP_START=$(date +%s)
    
    if java -jar "$JUSTSYNCIT_JAR" backup "$SOURCE_DIR" \
        --name "$BACKUP_NAME" \
        --description "$BACKUP_DESCRIPTION" \
        --remote-server "$REMOTE_SERVER" \
        --remote-port "$REMOTE_PORT" \
        --remote-username "$REMOTE_USERNAME" \
        --remote-password "$REMOTE_PASSWORD" \
        --transport "$TRANSPORT_PROTOCOL" \
        --compression-level "$COMPRESSION_LEVEL" \
        --include-hidden \
        --chunk-size 1048576 \
        --verify-integrity \
        --verbose \
        --metadata-file "$METADATA_FILE" \
        2>&1 | tee -a "$LOG_FILE"; then
        
        BACKUP_END=$(date +%s)
        BACKUP_DURATION=$((BACKUP_END - BACKUP_START))
        log_message "Remote backup completed successfully in ${BACKUP_DURATION} seconds"
        
        # Get snapshot ID for verification
        SNAPSHOT_ID=$(tail -n 50 "$LOG_FILE" | grep "Snapshot ID:" | tail -n 1 | awk '{print $3}')
        
        if [ -n "$SNAPSHOT_ID" ]; then
            log_message "Verifying remote snapshot: $SNAPSHOT_ID"
            
            # Verify backup integrity on remote server
            if java -jar "$JUSTSYNCIT_JAR" snapshots verify "$SNAPSHOT_ID" \
                --remote-server "$REMOTE_SERVER" \
                --remote-port "$REMOTE_PORT" \
                --remote-username "$REMOTE_USERNAME" \
                --remote-password "$REMOTE_PASSWORD" \
                --quiet >> "$LOG_FILE" 2>&1; then
                
                log_message "Remote snapshot verification successful: $SNAPSHOT_ID"
                
                # Get snapshot information
                log_message "Retrieving snapshot information"
                java -jar "$JUSTSYNCIT_JAR" snapshots info "$SNAPSHOT_ID" \
                    --remote-server "$REMOTE_SERVER" \
                    --remote-port "$REMOTE_PORT" \
                    --remote-username "$REMOTE_USERNAME" \
                    --remote-password "$REMOTE_PASSWORD" >> "$LOG_FILE" 2>&1
                
                # Cleanup temporary directory
                rm -rf "$TEMP_DIR"
                
                return 0
            else
                log_message "ERROR: Remote snapshot verification failed: $SNAPSHOT_ID"
                rm -rf "$TEMP_DIR"
                return 1
            fi
        else
            log_message "ERROR: Could not retrieve snapshot ID for verification"
            rm -rf "$TEMP_DIR"
            return 1
        fi
    else
        BACKUP_END=$(date +%s)
        BACKUP_DURATION=$((BACKUP_END - BACKUP_START))
        log_message "Remote backup failed after ${BACKUP_DURATION} seconds"
        rm -rf "$TEMP_DIR"
        return 1
    fi
}

# Attempt backup with retries
BACKUP_SUCCESS=false
for ((i=1; i<=MAX_RETRIES; i++)); do
    if attempt_backup "$i" "$MAX_RETRIES"; then
        BACKUP_SUCCESS=true
        break
    else
        if [ "$i" -lt "$MAX_RETRIES" ]; then
            log_message "Backup attempt $i failed, retrying in $RETRY_DELAY seconds..."
            sleep "$RETRY_DELAY"
        fi
    fi
done

# Check final result
if [ "$BACKUP_SUCCESS" = true ]; then
    log_message "Remote backup completed successfully"
    
    # Generate summary report
    SUMMARY_FILE="/tmp/justsyncit-remote-summary.txt"
    cat > "$SUMMARY_FILE" << EOF
JustSyncIt Remote Backup Summary
================================
Date: $(date '+%Y-%m-%d %H:%M:%S')
Source Directory: $SOURCE_DIR
Remote Server: $REMOTE_SERVER:$REMOTE_PORT
Backup Name: $BACKUP_NAME
Transport Protocol: $TRANSPORT_PROTOCOL
Compression Level: $COMPRESSION_LEVEL
Exit Code: 0
Duration: ${BACKUP_DURATION} seconds
Log File: $LOG_FILE

Recent Remote Snapshots:
$(java -jar "$JUSTSYNCIT_JAR" snapshots list \
    --remote-server "$REMOTE_SERVER" \
    --remote-port "$REMOTE_PORT" \
    --remote-username "$REMOTE_USERNAME" \
    --remote-password "$REMOTE_PASSWORD" \
    --sort-by-date | head -10)

Network Statistics:
- Server: $REMOTE_SERVER
- Port: $REMOTE_PORT
- Protocol: $TRANSPORT_PROTOCOL
- Compression: Level $COMPRESSION_LEVEL
EOF
    
    # Email summary (if mail command available)
    if command -v mail; then
        mail -s "JustSyncIt Remote Backup Success - $(date '+%Y-%m-%d')" "admin@example.com" < "$SUMMARY_FILE"
    fi
    
    rm -f "$SUMMARY_FILE"
else
    log_message "ERROR: All backup attempts failed"
    
    # Send failure alert
    if command -v mail; then
        echo "JustSyncIt remote backup failed after $MAX_RETRIES attempts" | \
            mail -s "JustSyncIt Remote Backup Failure" "admin@example.com"
    fi
    
    exit 1
fi

log_message "Remote backup routine completed successfully"
exit 0