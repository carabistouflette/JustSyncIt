# Personal Data Backup Use Case

This use case demonstrates how to set up JustSyncIt for personal data backup, including documents, photos, and other important files.

## Scenario

A home user wants to:
- Back up personal documents, photos, and media files
- Maintain version history for important files
- Access backups from multiple devices
- Ensure data privacy and security

## Solution Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Laptop        │    │   Desktop       │    │   JustSyncIt    │
│                 │    │                 │    │   Server        │
│ ┌─────────────┐ │    │ ┌─────────────┐ │    │                 │
│ │ Documents   │ │    │ │ Photos      │ │    │ ┌─────────────┐ │
│ │ Photos      │ │    │ │ Music       │ │    │ │ Backup      │ │
│ │ Projects    │ │    │ │ Videos      │ │    │ │ Storage     │ │
│ └─────────────┘ │    │ └─────────────┘ │    │ └─────────────┘ │
│                 │    │                 │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                         ┌─────────────────┐
                         │   Home Network  │
                         │   Router/Firewall│
                         └─────────────────┘
```

## Implementation Steps

### 1. Server Setup

Install JustSyncIt server on a dedicated machine or NAS:

```bash
# Download and install JustSyncIt
wget https://github.com/justsyncit/justsyncit/releases/latest/justsyncit-server.jar
sudo mkdir -p /opt/justsyncit
sudo cp justsyncit-server.jar /opt/justsyncit/
sudo mkdir -p /var/lib/justsyncit/storage
sudo mkdir -p /var/lib/justsyncit/metadata

# Create server configuration
sudo cp examples/server-configs/personal-server.properties /etc/justsyncit/server.properties

# Create systemd service
sudo tee /etc/systemd/system/justsyncit.service > /dev/null <<EOF
[Unit]
Description=JustSyncIt Server
After=network.target

[Service]
Type=simple
User=justsyncit
WorkingDirectory=/opt/justsyncit
ExecStart=/usr/bin/java -jar justsyncit-server.jar --config /etc/justsyncit/server.properties
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

# Enable and start service
sudo systemctl enable justsyncit
sudo systemctl start justsyncit
```

### 2. Client Configuration

Configure backup on each device:

#### Laptop Backup Script

```bash
#!/bin/bash
# laptop-backup.sh - Personal laptop backup script

JUSTSYNCIT_JAR="/opt/justsyncit/justsyncit.jar"
SERVER="homeserver.local:8080"
USERNAME="laptop_user"
PASSWORD="secure_password"

# Backup directories
DOCUMENTS_DIR="/home/user/Documents"
PHOTOS_DIR="/home/user/Pictures"
PROJECTS_DIR="/home/user/Projects"

# Function to backup directory
backup_directory() {
    local dir_path=$1
    local dir_name=$2
    
    echo "Backing up $dir_name..."
    
    java -jar "$JUSTSYNCIT_JAR" backup "$dir_path" \
        --name "Laptop-${dir_name}-$(date '+%Y-%m-%d')" \
        --description "Automatic backup of $dir_name from laptop" \
        --remote-server "$SERVER" \
        --remote-username "$USERNAME" \
        --remote-password "$PASSWORD" \
        --transport tcp \
        --compression-level 6 \
        --include-hidden \
        --verify-integrity
}

# Backup each directory
backup_directory "$DOCUMENTS_DIR" "Documents"
backup_directory "$PHOTOS_DIR" "Photos"
backup_directory "$PROJECTS_DIR" "Projects"

echo "Laptop backup completed"
```

#### Desktop Backup Script

```bash
#!/bin/bash
# desktop-backup.sh - Personal desktop backup script

JUSTSYNCIT_JAR="/opt/justsyncit/justsyncit.jar"
SERVER="homeserver.local:8080"
USERNAME="desktop_user"
PASSWORD="secure_password"

# Backup directories
PHOTOS_DIR="/home/user/Pictures"
MUSIC_DIR="/home/user/Music"
VIDEOS_DIR="/home/user/Videos"

# Function to backup directory
backup_directory() {
    local dir_path=$1
    local dir_name=$2
    
    echo "Backing up $dir_name..."
    
    java -jar "$JUSTSYNCIT_JAR" backup "$dir_path" \
        --name "Desktop-${dir_name}-$(date '+%Y-%m-%d')" \
        --description "Automatic backup of $dir_name from desktop" \
        --remote-server "$SERVER" \
        --remote-username "$USERNAME" \
        --remote-password "$PASSWORD" \
        --transport tcp \
        --compression-level 4 \
        --include-hidden \
        --verify-integrity
}

# Backup each directory
backup_directory "$PHOTOS_DIR" "Photos"
backup_directory "$MUSIC_DIR" "Music"
backup_directory "$VIDEOS_DIR" "Videos"

echo "Desktop backup completed"
```

### 3. Scheduled Backups

Set up automated backups using cron:

```bash
# Edit crontab
crontab -e

# Add backup schedules
# Daily laptop backup at 2 AM
0 2 * * * /home/user/scripts/laptop-backup.sh >> /var/log/laptop-backup.log 2>&1

# Weekly desktop backup on Sunday at 3 AM
0 3 * * 0 /home/user/scripts/desktop-backup.sh >> /var/log/desktop-backup.log 2>&1

# Monthly cleanup of old backups (keep last 30 days)
0 4 1 * * java -jar /opt/justsyncit/justsyncit.jar snapshots delete --older-than "30 days" --remote-server homeserver.local:8080 --remote-username admin --remote-password admin_password --force
```

### 4. Restore Examples

#### Restore Specific Files

```bash
# List available snapshots
java -jar justsyncit.jar snapshots list --remote-server homeserver.local:8080 --remote-username laptop_user --remote-password secure_password

# Restore specific documents
java -jar justsyncit.jar restore \
    --snapshot-id "abc123..." \
    --source-path "Documents/important-project/" \
    --target-path "/home/user/restored-docs/" \
    --remote-server homeserver.local:8080 \
    --remote-username laptop_user \
    --remote-password secure_password

# Restore entire photos directory
java -jar justsyncit.jar restore \
    --snapshot-id "def456..." \
    --target-path "/home/user/restored-photos/" \
    --remote-server homeserver.local:8080 \
    --remote-username desktop_user \
    --remote-password secure_password
```

### 5. Monitoring and Maintenance

#### Check Backup Status

```bash
#!/bin/bash
# check-backup-status.sh - Monitor backup health

JUSTSYNCIT_JAR="/opt/justsyncit/justsyncit.jar"
SERVER="homeserver.local:8080"
ADMIN_USER="admin"
ADMIN_PASS="admin_password"

echo "=== JustSyncit Backup Status Report ==="
echo "Generated: $(date)"
echo

# Check server status
echo "Server Status:"
java -jar "$JUSTSYNCIT_JAR" server status \
    --remote-server "$SERVER" \
    --remote-username "$ADMIN_USER" \
    --remote-password "$ADMIN_PASS"
echo

# List recent snapshots
echo "Recent Snapshots:"
java -jar "$JUSTSYNCIT_JAR" snapshots list \
    --remote-server "$SERVER" \
    --remote-username "$ADMIN_USER" \
    --remote-password "$ADMIN_PASS" \
    --sort-by-date \
    --limit 10
echo

# Check storage usage
echo "Storage Usage:"
df -h /var/lib/justsyncit/storage
echo

# Verify recent snapshots
echo "Verifying recent snapshots..."
RECENT_SNAPSHOTS=$(java -jar "$JUSTSYNCIT_JAR" snapshots list \
    --remote-server "$SERVER" \
    --remote-username "$ADMIN_USER" \
    --remote-password "$ADMIN_PASS" \
    --sort-by-date \
    --limit 5 | grep -E "^[a-f0-9]{64}" | awk '{print $1}')

for snapshot in $RECENT_SNAPSHOTS; do
    echo "Verifying $snapshot..."
    if java -jar "$JUSTSYNCIT_JAR" snapshots verify "$snapshot" \
        --remote-server "$SERVER" \
        --remote-username "$ADMIN_USER" \
        --remote-password "$ADMIN_PASS" --quiet; then
        echo "✓ $snapshot - OK"
    else
        echo "✗ $snapshot - FAILED"
    fi
done
```

## Best Practices

### 1. Data Organization

- Use consistent naming conventions for backups
- Separate different types of data (documents, media, projects)
- Maintain regular backup schedules

### 2. Security

- Use strong passwords for authentication
- Enable SSL/TLS for network transfers
- Regularly update JustSyncIt to latest version
- Monitor access logs for unauthorized access

### 3. Performance

- Use appropriate compression levels based on data type
- Schedule backups during off-peak hours
- Monitor storage usage and clean up old backups

### 4. Disaster Recovery

- Maintain off-site backup of critical data
- Test restore procedures regularly
- Keep documentation of backup configuration

## Troubleshooting

### Common Issues

1. **Connection Failures**
   - Check network connectivity to server
   - Verify firewall settings
   - Confirm server is running

2. **Authentication Errors**
   - Verify username and password
   - Check user permissions
   - Ensure user account is active

3. **Slow Performance**
   - Check network bandwidth
   - Verify disk I/O performance
   - Adjust compression level

4. **Storage Full**
   - Clean up old snapshots
   - Add more storage capacity
   - Review retention policies

### Recovery Procedures

1. **File Recovery**
   ```bash
   # Find the snapshot containing the file
   java -jar justsyncit.jar snapshots list --search "filename"
   
   # Restore the specific file
   java -jar justsyncit.jar restore --snapshot-id "xxx" --source-path "path/to/file" --target-path "/restore/location/"
   ```

2. **System Recovery**
   ```bash
   # List all snapshots for a device
   java -jar justsyncit.jar snapshots list --remote-server server --remote-username user --remote-password pass
   
   # Restore entire system backup
   java -jar justsyncit.jar restore --snapshot-id "xxx" --target-path "/restore/location/"
   ```

## Conclusion

This use case demonstrates how JustSyncIt can provide a comprehensive personal backup solution with:

- Automated, scheduled backups
- Cross-device synchronization
- Version history and file recovery
- Secure data transmission
- Monitoring and maintenance tools

The solution can be easily extended to include mobile devices, cloud storage, or additional family members' computers.