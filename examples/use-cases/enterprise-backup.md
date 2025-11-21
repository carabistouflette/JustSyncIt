# Enterprise Backup Use Case

This use case demonstrates how to deploy JustSyncIt in an enterprise environment for backing up critical business data, databases, and application servers.

## Scenario

A medium-sized enterprise needs to:
- Back up multiple application servers and databases
- Implement disaster recovery procedures
- Maintain compliance with data retention policies
- Provide secure access for different departments
- Monitor backup operations across the organization

## Solution Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Enterprise Network                                │
│                                                                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐       │
│  │   Web       │  │  Database   │  │ Application │  │   File      │       │
│  │   Servers   │  │   Servers   │  │   Servers   │  │   Servers   │       │
│  │             │  │             │  │             │  │             │       │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘       │
│         │                │                │                │             │
│         └────────────────┼────────────────┼────────────────┼─────────────┘
│                          │                │                │             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    DMZ / Backup Network                           │   │
│  │                                                                     │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                 │   │
│  │  │ Backup      │  │ JustSyncIt  │  │ Monitoring  │                 │   │
│  │  │ Gateway     │  │ Cluster     │  │ Server      │                 │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                          │                                                │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                     Secure Storage Zone                            │   │
│  │                                                                     │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                 │   │
│  │  │ Primary     │  │ Secondary   │  │ Off-site    │                 │   │
│  │  │ Storage     │  │ Storage     │  │ Replication │                 │   │
│  │  │ Array       │  │ Array       │  │             │                 │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Implementation Steps

### 1. Infrastructure Setup

#### Server Cluster Configuration

```bash
#!/bin/bash
# setup-cluster.sh - JustSyncIt cluster setup

# Configuration
CLUSTER_NODES=("backup1.company.com" "backup2.company.com" "backup3.company.com")
CLUSTER_PORT=8080
STORAGE_BASE="/data/justsyncit"
METADATA_BASE="/data/justsyncit/metadata"

# Create directories on each node
for node in "${CLUSTER_NODES[@]}"; do
    ssh "$node" "sudo mkdir -p $STORAGE_BASE $METADATA_BASE"
    ssh "$node" "sudo chown justsyncit:justsyncit $STORAGE_BASE $METADATA_BASE"
done

# Deploy JustSyncIt to each node
for node in "${CLUSTER_NODES[@]}"; do
    scp justsyncit-server.jar "$node:/opt/justsyncit/"
    scp enterprise-cluster.properties "$node:/etc/justsyncit/server.properties"
    
    # Configure systemd service
    ssh "$node" "sudo systemctl enable justsyncit && sudo systemctl start justsyncit"
done

# Initialize cluster
java -jar justsyncit.jar cluster init \
    --nodes "${CLUSTER_NODES[*]}" \
    --port "$CLUSTER_PORT" \
    --admin-password "secure_admin_password"
```

#### Load Balancer Configuration

```nginx
# /etc/nginx/sites-available/justsyncit-loadbalancer

upstream justsyncit_cluster {
    least_conn;
    server backup1.company.com:8080 max_fails=3 fail_timeout=30s;
    server backup2.company.com:8080 max_fails=3 fail_timeout=30s;
    server backup3.company.com:8080 max_fails=3 fail_timeout=30s;
}

server {
    listen 443 ssl http2;
    server_name backup.company.com;
    
    ssl_certificate /etc/ssl/certs/backup.company.com.crt;
    ssl_certificate_key /etc/ssl/private/backup.company.com.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-RSA-AES256-GCM-SHA512:DHE-RSA-AES256-GCM-SHA512;
    
    client_max_body_size 10G;
    proxy_read_timeout 3600s;
    proxy_send_timeout 3600s;
    
    location / {
        proxy_pass http://justsyncit_cluster;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
    
    # Health check endpoint
    location /health {
        access_log off;
        proxy_pass http://justsyncit_cluster/health;
    }
}
```

### 2. Departmental Backup Policies

#### Finance Department Backup

```bash
#!/bin/bash
# finance-backup.sh - Finance department backup script

JUSTSYNCIT_JAR="/opt/justsyncit/justsyncit.jar"
BACKUP_SERVER="backup.company.com"
DEPARTMENT="finance"
USERNAME="finance_backup"
PASSWORD="secure_finance_password"

# Backup sources
FINANCE_SYSTEMS=(
    "/data/finance/accounting"
    "/data/finance/payroll"
    "/data/finance/reports"
    "/data/finance/audits"
)

# Database backups
DATABASES=(
    "finance_accounting"
    "finance_payroll"
    "finance_reporting"
)

# Backup file systems
backup_filesystems() {
    echo "Starting filesystem backup for Finance department..."
    
    for system in "${FINANCE_SYSTEMS[@]}"; do
        system_name=$(basename "$system")
        backup_name="${DEPARTMENT}-${system_name}-$(date '+%Y-%m-%d-%H%M%S')"
        
        echo "Backing up $system..."
        
        java -jar "$JUSTSYNCIT_JAR" backup "$system" \
            --name "$backup_name" \
            --description "Finance department backup of $system_name" \
            --remote-server "$BACKUP_SERVER" \
            --remote-username "$USERNAME" \
            --remote-password "$PASSWORD" \
            --transport tcp \
            --compression-level 9 \
            --encryption \
            --retention-days 2555 \
            --verify-integrity \
            --metadata-department "$DEPARTMENT" \
            --metadata-compliance "SOX,GDPR" \
            --metadata-classification "confidential"
    done
}

# Backup databases
backup_databases() {
    echo "Starting database backup for Finance department..."
    
    for db in "${DATABASES[@]}"; do
        backup_dir="/tmp/finance-db-backup-$$"
        mkdir -p "$backup_dir"
        
        echo "Exporting database $db..."
        mysqldump -u finance_user -p"$DB_PASSWORD" "$db" > "$backup_dir/${db}.sql"
        
        backup_name="${DEPARTMENT}-database-${db}-$(date '+%Y-%m-%d-%H%M%S')"
        
        echo "Backing up database $db..."
        
        java -jar "$JUSTSYNCIT_JAR" backup "$backup_dir" \
            --name "$backup_name" \
            --description "Finance department database backup of $db" \
            --remote-server "$BACKUP_SERVER" \
            --remote-username "$USERNAME" \
            --remote-password "$PASSWORD" \
            --transport tcp \
            --compression-level 9 \
            --encryption \
            --retention-days 2555 \
            --verify-integrity \
            --metadata-department "$DEPARTMENT" \
            --metadata-compliance "SOX,GDPR" \
            --metadata-classification "confidential" \
            --metadata-type "database"
        
        # Clean up temporary files
        rm -rf "$backup_dir"
    done
}

# Execute backup
backup_filesystems
backup_databases

echo "Finance department backup completed"
```

#### IT Infrastructure Backup

```bash
#!/bin/bash
# it-infrastructure-backup.sh - IT infrastructure backup script

JUSTSYNCIT_JAR="/opt/justsyncit/justsyncit.jar"
BACKUP_SERVER="backup.company.com"
DEPARTMENT="it"
USERNAME="it_backup"
PASSWORD="secure_it_password"

# Backup configuration files
backup_configurations() {
    echo "Backing up IT configurations..."
    
    backup_name="${DEPARTMENT}-configurations-$(date '+%Y-%m-%d-%H%M%S')"
    temp_dir="/tmp/it-config-backup-$$"
    mkdir -p "$temp_dir"
    
    # Collect configuration files
    cp -r /etc/nginx "$temp_dir/"
    cp -r /etc/apache2 "$temp_dir/"
    cp -r /etc/ssh "$temp_dir/"
    cp -r /etc/systemd "$temp_dir/"
    
    # Network configurations
    cp /etc/network/interfaces "$temp_dir/" 2>/dev/null || true
    cp -r /etc/netplan "$temp_dir/" 2>/dev/null || true
    
    java -jar "$JUSTSYNCIT_JAR" backup "$temp_dir" \
        --name "$backup_name" \
        --description "IT infrastructure configuration backup" \
        --remote-server "$BACKUP_SERVER" \
        --remote-username "$USERNAME" \
        --remote-password "$PASSWORD" \
        --transport tcp \
        --compression-level 6 \
        --retention-days 365 \
        --verify-integrity \
        --metadata-department "$DEPARTMENT" \
        --metadata-type "configuration"
    
    rm -rf "$temp_dir"
}

# Backup application servers
backup_applications() {
    echo "Backing up application servers..."
    
    servers=("web1.company.com" "web2.company.com" "app1.company.com" "app2.company.com")
    
    for server in "${servers[@]}"; do
        server_name=$(echo "$server" | cut -d. -f1)
        backup_name="${DEPARTMENT}-app-${server_name}-$(date '+%Y-%m-%d-%H%M%S')"
        
        echo "Backing up $server..."
        
        # Execute backup on remote server
        ssh "$server" "java -jar /opt/justsyncit/justsyncit.jar backup /opt/applications \
            --name '$backup_name' \
            --description 'Application backup from $server' \
            --remote-server '$BACKUP_SERVER' \
            --remote-username '$USERNAME' \
            --remote-password '$PASSWORD' \
            --transport tcp \
            --compression-level 6 \
            --retention-days 90 \
            --verify-integrity \
            --metadata-department '$DEPARTMENT' \
            --metadata-type 'application' \
            --metadata-server '$server'"
    done
}

# Execute backup
backup_configurations
backup_applications

echo "IT infrastructure backup completed"
```

### 3. Compliance and Retention Management

```bash
#!/bin/bash
# compliance-manager.sh - Compliance and retention management

JUSTSYNCIT_JAR="/opt/justsyncit/justsyncit.jar"
BACKUP_SERVER="backup.company.com"
ADMIN_USER="compliance_admin"
ADMIN_PASS="secure_compliance_password"

# Department retention policies
declare -A RETENTION_POLICIES=(
    ["finance"]="2555"    # 7 years for financial records
    ["hr"]="2190"         # 6 years for HR records
    ["legal"]="3650"      # 10 years for legal records
    ["it"]="365"          # 1 year for IT configurations
    ["marketing"]="1095"  # 3 years for marketing materials
)

# Apply retention policies
apply_retention_policies() {
    echo "Applying departmental retention policies..."
    
    for department in "${!RETENTION_POLICIES[@]}"; do
        retention_days="${RETENTION_POLICIES[$department]}"
        
        echo "Processing $department department (retention: $retention_days days)..."
        
        # List snapshots for this department
        snapshots=$(java -jar "$JUSTSYNCIT_JAR" snapshots list \
            --remote-server "$BACKUP_SERVER" \
            --remote-username "$ADMIN_USER" \
            --remote-password "$ADMIN_PASS" \
            --filter "department:$department" \
            --format json)
        
        # Process each snapshot
        echo "$snapshots" | jq -r '.snapshots[] | select(.age_days > '$retention_days') | .id' | while read -r snapshot_id; do
            echo "Deleting old snapshot $snapshot_id from $department department..."
            
            java -jar "$JUSTSYNCIT_JAR" snapshots delete "$snapshot_id" \
                --remote-server "$BACKUP_SERVER" \
                --remote-username "$ADMIN_USER" \
                --remote-password "$ADMIN_PASS" \
                --force
        done
    done
}

# Generate compliance report
generate_compliance_report() {
    echo "Generating compliance report..."
    
    report_file="/tmp/compliance-report-$(date '+%Y-%m-%d').html"
    
    cat > "$report_file" << EOF
<!DOCTYPE html>
<html>
<head>
    <title>JustSyncIt Compliance Report</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        table { border-collapse: collapse; width: 100%; }
        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
        th { background-color: #f2f2f2; }
        .compliant { color: green; }
        .non-compliant { color: red; }
    </style>
</head>
<body>
    <h1>JustSyncIt Compliance Report</h1>
    <p>Generated: $(date)</p>
    
    <h2>Departmental Backup Status</h2>
    <table>
        <tr>
            <th>Department</th>
            <th>Total Snapshots</th>
            <th>Compliant Snapshots</th>
            <th>Non-Compliant Snapshots</th>
            <th>Status</th>
        </tr>
EOF
    
    for department in "${!RETENTION_POLICIES[@]}"; do
        retention_days="${RETENTION_POLICIES[$department]}"
        
        # Get statistics for this department
        stats=$(java -jar "$JUSTSYNCIT_JAR" snapshots list \
            --remote-server "$BACKUP_SERVER" \
            --remote-username "$ADMIN_USER" \
            --remote-password "$ADMIN_PASS" \
            --filter "department:$department" \
            --statistics)
        
        # Parse statistics (simplified for example)
        total_snapshots=$(echo "$stats" | grep "Total snapshots:" | awk '{print $3}')
        compliant_snapshots=$(echo "$stats" | grep "Compliant snapshots:" | awk '{print $3}')
        non_compliant_snapshots=$((total_snapshots - compliant_snapshots))
        
        status_class="compliant"
        if [ "$non_compliant_snapshots" -gt 0 ]; then
            status_class="non-compliant"
        fi
        
        cat >> "$report_file" << EOF
        <tr>
            <td>$department</td>
            <td>$total_snapshots</td>
            <td>$compliant_snapshots</td>
            <td>$non_compliant_snapshots</td>
            <td class="$status_class">$([ "$non_compliant_snapshots" -gt 0 ] && echo "Non-Compliant" || echo "Compliant")</td>
        </tr>
EOF
    done
    
    cat >> "$report_file" << EOF
    </table>
    
    <h2>Storage Usage</h2>
    <pre>
$(df -h /data/justsyncit)
    </pre>
    
    <h2>Recent Backup Activity</h2>
    <pre>
$(java -jar "$JUSTSYNCIT_JAR" snapshots list \
    --remote-server "$BACKUP_SERVER" \
    --remote-username "$ADMIN_USER" \
    --remote-password "$ADMIN_PASS" \
    --sort-by-date \
    --limit 20)
    </pre>
</body>
</html>
EOF
    
    echo "Compliance report generated: $report_file"
    
    # Email report to compliance team
    if command -v mail; then
        mail -s "JustSyncIt Compliance Report - $(date '+%Y-%m-%d')" \
            -a "$report_file" \
            compliance@company.com < /dev/null
    fi
}

# Execute compliance management
apply_retention_policies
generate_compliance_report

echo "Compliance management completed"
```

### 4. Disaster Recovery Procedures

```bash
#!/bin/bash
# disaster-recovery.sh - Disaster recovery procedures

JUSTSYNCIT_JAR="/opt/justsyncit/justsyncit.jar"
BACKUP_SERVER="backup.company.com"
ADMIN_USER="disaster_recovery"
ADMIN_PASS="secure_dr_password"

# Function to restore critical systems
restore_critical_systems() {
    echo "Starting disaster recovery process..."
    
    # List available snapshots
    echo "Available snapshots for recovery:"
    java -jar "$JUSTSYNCIT_JAR" snapshots list \
        --remote-server "$BACKUP_SERVER" \
        --remote-username "$ADMIN_USER" \
        --remote-password "$ADMIN_PASS" \
        --sort-by-date \
        --limit 10
    
    # Restore finance systems (highest priority)
    echo "Restoring finance systems..."
    
    finance_snapshots=$(java -jar "$JUSTSYNCIT_JAR" snapshots list \
        --remote-server "$BACKUP_SERVER" \
        --remote-username "$ADMIN_USER" \
        --remote-password "$ADMIN_PASS" \
        --filter "department:finance,type:filesystem" \
        --sort-by-date \
        --limit 1)
    
    latest_finance_snapshot=$(echo "$finance_snapshots" | grep -E "^[a-f0-9]{64}" | head -1 | awk '{print $1}')
    
    if [ -n "$latest_finance_snapshot" ]; then
        echo "Restoring finance system from snapshot $latest_finance_snapshot..."
        
        java -jar "$JUSTSYNCIT_JAR" restore \
            --snapshot-id "$latest_finance_snapshot" \
            --target-path "/recovery/finance/" \
            --remote-server "$BACKUP_SERVER" \
            --remote-username "$ADMIN_USER" \
            --remote-password "$ADMIN_PASS" \
            --verify-integrity
    fi
    
    # Restore databases
    echo "Restoring databases..."
    
    db_snapshots=$(java -jar "$JUSTSYNCIT_JAR" snapshots list \
        --remote-server "$BACKUP_SERVER" \
        --remote-username "$ADMIN_USER" \
        --remote-password "$ADMIN_PASS" \
        --filter "department:finance,type:database" \
        --sort-by-date \
        --limit 1)
    
    latest_db_snapshot=$(echo "$db_snapshots" | grep -E "^[a-f0-9]{64}" | head -1 | awk '{print $1}')
    
    if [ -n "$latest_db_snapshot" ]; then
        echo "Restoring databases from snapshot $latest_db_snapshot..."
        
        java -jar "$JUSTSYNCIT_JAR" restore \
            --snapshot-id "$latest_db_snapshot" \
            --target-path "/recovery/databases/" \
            --remote-server "$BACKUP_SERVER" \
            --remote-username "$ADMIN_USER" \
            --remote-password "$ADMIN_PASS" \
            --verify-integrity
        
        # Import databases
        for sql_file in /recovery/databases/*.sql; do
            if [ -f "$sql_file" ]; then
                db_name=$(basename "$sql_file" .sql)
                echo "Importing database $db_name..."
                mysql -u root -p"$MYSQL_ROOT_PASSWORD" "$db_name" < "$sql_file"
            fi
        done
    fi
}

# Function to verify recovery
verify_recovery() {
    echo "Verifying disaster recovery..."
    
    # Check critical services
    services=("nginx" "mysql" "apache2")
    
    for service in "${services[@]}"; do
        if systemctl is-active --quiet "$service"; then
            echo "✓ $service is running"
        else
            echo "✗ $service is not running"
        fi
    done
    
    # Check application connectivity
    if curl -f http://localhost:80/health > /dev/null 2>&1; then
        echo "✓ Application is responding"
    else
        echo "✗ Application is not responding"
    fi
    
    # Check database connectivity
    if mysql -u root -p"$MYSQL_ROOT_PASSWORD" -e "SELECT 1;" > /dev/null 2>&1; then
        echo "✓ Database is accessible"
    else
        echo "✗ Database is not accessible"
    fi
}

# Execute disaster recovery
restore_critical_systems
verify_recovery

echo "Disaster recovery process completed"
```

### 5. Monitoring and Alerting

```bash
#!/bin/bash
# enterprise-monitoring.sh - Enterprise monitoring and alerting

JUSTSYNCIT_JAR="/opt/justsyncit/justsyncit.jar"
BACKUP_SERVER="backup.company.com"
ADMIN_USER="monitoring"
ADMIN_PASS="secure_monitoring_password"
ALERT_EMAIL="backup-alerts@company.com"

# Function to check backup health
check_backup_health() {
    echo "Checking backup health..."
    
    # Check cluster status
    cluster_status=$(java -jar "$JUSTSYNCIT_JAR" cluster status \
        --remote-server "$BACKUP_SERVER" \
        --remote-username "$ADMIN_USER" \
        --remote-password "$ADMIN_PASS")
    
    echo "$cluster_status"
    
    # Check for failed backups in last 24 hours
    failed_backups=$(java -jar "$JUSTSYNCIT_JAR" snapshots list \
        --remote-server "$BACKUP_SERVER" \
        --remote-username "$ADMIN_USER" \
        --remote-password "$ADMIN_PASS" \
        --filter "status:failed,age:1d" \
        --count)
    
    if [ "$failed_backups" -gt 0 ]; then
        echo "ALERT: $failed_backups failed backups in the last 24 hours"
        echo "$failed_backups failed backups detected in the last 24 hours" | \
            mail -s "JustSyncIt Backup Alert - Failed Backups" "$ALERT_EMAIL"
    fi
    
    # Check storage usage
    storage_usage=$(df /data/justsyncit | awk 'NR==2 {print $5}' | sed 's/%//')
    
    if [ "$storage_usage" -gt 85 ]; then
        echo "ALERT: Storage usage is ${storage_usage}%"
        echo "JustSyncIt storage usage is ${storage_usage}%, exceeding 85% threshold" | \
            mail -s "JustSyncIt Storage Alert - High Usage" "$ALERT_EMAIL"
    fi
}

# Function to generate performance metrics
generate_performance_metrics() {
    echo "Generating performance metrics..."
    
    metrics_file="/tmp/justsyncit-metrics-$(date '+%Y-%m-%d').json"
    
    # Get cluster metrics
    java -jar "$JUSTSYNCIT_JAR" cluster metrics \
        --remote-server "$BACKUP_SERVER" \
        --remote-username "$ADMIN_USER" \
        --remote-password "$ADMIN_PASS" \
        --format json > "$metrics_file"
    
    # Send metrics to monitoring system (Prometheus, etc.)
    if command -v curl; then
        curl -X POST http://monitoring.company.com/metrics \
            -H "Content-Type: application/json" \
            --data "@$metrics_file"
    fi
    
    echo "Performance metrics generated: $metrics_file"
}

# Execute monitoring
check_backup_health
generate_performance_metrics

echo "Enterprise monitoring completed"
```

## Best Practices

### 1. Security

- Implement role-based access control (RBAC)
- Use end-to-end encryption for sensitive data
- Regular security audits and penetration testing
- Multi-factor authentication for administrative access

### 2. Performance

- Optimize backup schedules to minimize network impact
- Use appropriate compression levels based on data types
- Implement load balancing for backup servers
- Monitor and tune database performance

### 3. Compliance

- Implement automated retention policy enforcement
- Maintain audit trails for all backup operations
- Regular compliance reporting and verification
- Data classification and handling procedures

### 4. Disaster Recovery

- Regular disaster recovery testing
- Off-site replication for critical data
- Documented recovery procedures
- Recovery time objective (RTO) and recovery point objective (RPO) definitions

## Conclusion

This enterprise backup solution provides:

- Scalable backup infrastructure with clustering
- Departmental backup policies and compliance management
- Comprehensive disaster recovery procedures
- Monitoring and alerting capabilities
- Security and compliance features

The solution can be customized to meet specific enterprise requirements and can scale to support growing data volumes and user needs.