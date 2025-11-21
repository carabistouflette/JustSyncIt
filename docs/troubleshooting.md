# JustSyncIt Troubleshooting Guide

## Table of Contents

- [Common Error Messages](#common-error-messages)
- [Network Connectivity Issues](#network-connectivity-issues)
- [Permission Problems](#permission-problems)
- [Performance Issues](#performance-issues)
- [Data Corruption Recovery](#data-corruption-recovery)
- [Storage Issues](#storage-issues)
- [Memory Issues](#memory-issues)
- [Installation Issues](#installation-issues)
- [Advanced Troubleshooting](#advanced-troubleshooting)

## Common Error Messages

### "Source directory does not exist"

**Description**: JustSyncIt cannot find the specified source directory.

**Causes**:
- Incorrect path specified
- Directory was moved or deleted
- Path contains typos
- Relative path issues

**Solutions**:
```bash
# Verify directory exists
ls -la /path/to/source

# Use absolute path
java -jar justsyncit.jar backup /home/user/documents

# Check current directory
pwd
find . -name "documents" -type d

# Use tab completion
java -jar justsyncit.jar backup ~/doc<TAB>
```

**Prevention**:
- Use absolute paths in scripts
- Verify paths before running backups
- Use `find` to locate directories

### "Insufficient permissions"

**Description**: JustSyncIt lacks required permissions to access files or directories.

**Causes**:
- Running as wrong user
- Incorrect file permissions
- SELinux/AppArmor restrictions
- Read-only filesystem

**Solutions**:
```bash
# Check file permissions
ls -la /path/to/source

# Fix permissions
chmod -R 755 /path/to/source

# Run as correct user
sudo -u backup-user java -jar justsyncit.jar backup /path/to/source

# Check SELinux context
ls -Z /path/to/source
chcon -R -t user_home_t /path/to/source
```

**Prevention**:
- Set up dedicated backup user
- Configure proper permissions
- Use ACLs for fine-grained control
- Test permissions before automated backups

### "Connection refused"

**Description**: Cannot connect to JustSyncIt server.

**Causes**:
- Server not running
- Wrong port or address
- Firewall blocking connection
- Server listening on different interface

**Solutions**:
```bash
# Check if server is running
java -jar justsyncit.jar server status

# Check server listening ports
netstat -tlnp | grep 8080
ss -tlnp | grep 8080

# Test connectivity
telnet backup-server 8080
nc -v backup-server 8080

# Check firewall
sudo ufw status
sudo iptables -L -n
```

**Prevention**:
- Use systemd service for auto-start
- Configure firewall rules
- Monitor server status
- Use proper network configuration

### "Out of memory"

**Description**: Java process has run out of memory.

**Causes**:
- Insufficient heap size
- Memory leak
- Large dataset with small heap
- Too many concurrent operations

**Solutions**:
```bash
# Increase heap size
export JAVA_OPTS="-Xmx4g"
java -jar justsyncit.jar backup /large/data

# Use memory-efficient mode
java -jar justsyncit.jar backup /data --memory-efficient

# Reduce chunk size
java -jar justsyncit.jar backup /data --chunk-size 262144

# Enable garbage collection tuning
export JAVA_OPTS="-Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

**Prevention**:
- Allocate adequate memory for data size
- Monitor memory usage
- Use appropriate chunk sizes
- Enable memory profiling

### "Storage full"

**Description**: Insufficient disk space for backup operations.

**Causes**:
- Backup storage full
- Temporary directory full
- Disk quota exceeded
- Filesystem corruption

**Solutions**:
```bash
# Check disk space
df -h
df -i  # Check inodes

# Clean up temporary files
rm -rf /tmp/justsyncit-*

# Run garbage collection
java -jar justsyncit.jar gc

# Move to larger storage
java -jar justsyncit.jar backup /data --storage /larger/disk
```

**Prevention**:
- Monitor disk usage
- Set up automated cleanup
- Use storage quotas
- Implement retention policies

### "Checksum verification failed"

**Description**: BLAKE3 hash verification failed during backup or restore.

**Causes**:
- Data corruption during transfer
- Hardware issues (RAM, disk)
- Network errors
- Software bugs

**Solutions**:
```bash
# Verify source data integrity
java -jar justsyncit.jar hash /path/to/source --recursive

# Test with different chunk size
java -jar justsyncit.jar backup /data --chunk-size 1048576

# Disable verification temporarily
java -jar justsyncit.jar backup /data --no-verify

# Run memory diagnostics
memtest86 4  # Test RAM
smartctl -t long /dev/sda  # Test disk
```

**Prevention**:
- Use ECC memory
- Monitor hardware health
- Use reliable storage
- Implement verification schedules

## Network Connectivity Issues

### Connection Timeouts

**Symptoms**:
- Operations hang and timeout
- "Connection timed out" errors
- Intermittent connection failures

**Diagnosis**:
```bash
# Test network connectivity
ping -c 10 backup-server
traceroute backup-server

# Check network latency
mtr backup-server

# Test port connectivity
telnet backup-server 8080
nc -v backup-server 8080 -w 5
```

**Solutions**:
```bash
# Increase timeout values
export JUSTSYNCIT_TIMEOUT=120000  # 2 minutes

# Use TCP instead of QUIC
java -jar justsyncit.jar backup /data --remote --server backup-server:8080 --transport TCP

# Enable retry logic
export JUSTSYNCIT_RETRY_ATTEMPTS=5
export JUSTSYNCIT_RETRY_DELAY=5000  # 5 seconds

# Use connection pooling
export JUSTSYNCIT_CONNECTION_POOL=true
```

### Packet Loss

**Symptoms**:
- Slow transfer speeds
- Frequent retransmissions
- "Connection lost" errors
- Corruption warnings

**Diagnosis**:
```bash
# Check packet loss
ping -c 100 -s 1500 backup-server
mtr --report-cycles 10 backup-server

# Monitor network quality
iperf3 -c backup-server -t 60
tcpdump -i eth0 -c 1000 host backup-server
```

**Solutions**:
```bash
# Switch to TCP (more reliable)
java -jar justsyncit.jar backup /data --remote --server backup-server:8080 --transport TCP

# Reduce parallel connections
java -jar justsyncit.jar backup /data --parallel-transfers 1

# Enable compression (reduces packet count)
java -jar justsyncit.jar backup /data --remote --server backup-server:8080 --compress

# Use smaller chunk size
java -jar justsyncit.jar backup /data --chunk-size 262144
```

### Bandwidth Limitations

**Symptoms**:
- Transfer speeds much lower than expected
- High CPU usage on network operations
- Long backup times

**Diagnosis**:
```bash
# Test available bandwidth
iperf3 -c speedtest.net
speedtest-cli

# Monitor network usage
iftop -i eth0
nethogs

# Check for QoS limits
tc qdisc show dev eth0
```

**Solutions**:
```bash
# Optimize for available bandwidth
java -jar justsyncit.jar backup /data --bandwidth-limit 10485760  # 10MB/s

# Use compression
java -jar justsyncit.jar backup /data --compression-level 6

# Schedule during off-peak hours
java -jar justsyncit.jar backup /data --schedule "02:00"

# Use QUIC protocol
java -jar justsyncit.jar backup /data --transport QUIC
```

### DNS Resolution Issues

**Symptoms**:
- "Unknown host" errors
- Slow connection establishment
- Intermittent resolution failures

**Diagnosis**:
```bash
# Test DNS resolution
nslookup backup-server
dig backup-server
host backup-server

# Check DNS servers
cat /etc/resolv.conf
systemd-resolve --status
```

**Solutions**:
```bash
# Use IP address directly
java -jar justsyncit.jar backup /data --remote --server 192.168.1.100:8080

# Configure alternative DNS
echo "nameserver 8.8.8.8" >> /etc/resolv.conf
echo "nameserver 1.1.1.1" >> /etc/resolv.conf

# Use local hosts file
echo "192.168.1.100 backup-server" >> /etc/hosts
```

## Permission Problems

### File Access Denied

**Symptoms**:
- "Permission denied" errors
- Cannot read certain files
- Backup skips files with permission errors

**Diagnosis**:
```bash
# Check file permissions
ls -la /path/to/problematic/file
getfacl /path/to/problematic/file

# Check user permissions
id
groups

# Check SELinux context
ls -Z /path/to/problematic/file
```

**Solutions**:
```bash
# Fix file permissions
chmod -R 755 /path/to/source
chown -R backup-user:backup-group /path/to/source

# Set ACLs
setfacl -R -m u:backup-user:rwx /path/to/source
setfacl -R -d -m u:backup-user:rwx /path/to/source

# Fix SELinux context
chcon -R -t user_home_t /path/to/source
setsebool -P httpd_can_network_connect 1
```

### Sudo Requirements

**Symptoms**:
- Operations fail without sudo
- "Operation not permitted" errors
- Cannot bind to privileged ports

**Diagnosis**:
```bash
# Check if sudo is needed
java -jar justsyncit.jar backup /data 2>&1 | grep -i permission

# Check port requirements
netstat -tlnp | grep :80

# Check user capabilities
capsh --print
```

**Solutions**:
```bash
# Run with sudo when needed
sudo java -jar justsyncit.jar backup /root/data

# Use non-privileged ports
java -jar justsyncit.jar server start --port 8080

# Grant capabilities
sudo setcap cap_net_bind_service+ep /usr/bin/java
```

### Directory Access Issues

**Symptoms**:
- Cannot traverse directories
- "Permission denied" on directory access
- Backup stops at certain directory levels

**Diagnosis**:
```bash
# Check directory permissions
ls -ld /path/to/directory
namei -l /path/to/directory

# Check mount options
mount | grep /path/to/directory
findmnt /path/to/directory
```

**Solutions**:
```bash
# Fix directory permissions
chmod 755 /path/to/directory
chmod +x /path/to/directory

# Fix ownership
chown backup-user:backup-group /path/to/directory

# Remount with proper options
sudo mount -o remount,exec /path/to/directory
```

## Performance Issues

### Slow Backup Speeds

**Symptoms**:
- Backup throughput <10 MB/s
- Long backup times for small datasets
- High CPU usage with low I/O

**Diagnosis**:
```bash
# Monitor system resources
top -p $(pgrep -f justsyncit)
iotop -o
iostat -x 1

# Profile backup operation
java -jar justsyncit.jar backup /data --verbose --profile

# Check disk performance
hdparm -Tt /dev/sda
fio --name=test --rw=read --bs=4k --size=1g
```

**Solutions**:
```bash
# Optimize chunk size
java -jar justsyncit.jar backup /data --chunk-size 1048576

# Increase thread count
java -jar justsyncit.jar backup /data --threads 8

# Enable parallel processing
export JUSTSYNCIT_PARALLEL_PROCESSING=true

# Optimize for storage type
java -jar justsyncit.jar backup /data --ssd-optimization
```

### High Memory Usage

**Symptoms**:
- Process uses >2GB RAM
- System becomes unresponsive
- Out of memory errors

**Diagnosis**:
```bash
# Monitor memory usage
free -h
vmstat 1
ps aux | grep justsyncit

# Profile memory usage
jmap -histo:live $(pgrep -f justsyncit)
jstat -gc $(pgrep -f justsyncit)
```

**Solutions**:
```bash
# Reduce heap size
export JAVA_OPTS="-Xmx2g"

# Enable memory-efficient mode
java -jar justsyncit.jar backup /data --memory-efficient

# Reduce chunk size
java -jar justsyncit.jar backup /data --chunk-size 262144

# Enable memory pooling
export JUSTSYNCIT_MEMORY_POOLING=true
```

### High CPU Usage

**Symptoms**:
- CPU usage >80% during backup
- System becomes slow
- High temperature

**Diagnosis**:
```bash
# Monitor CPU usage
top -p $(pgrep -f justsyncit)
htop
mpstat 1

# Profile CPU usage
java -jar justsyncit.jar backup /data --profile-cpu

# Check CPU frequency
cpufreq-info
lscpu
```

**Solutions**:
```bash
# Reduce thread count
java -jar justsyncit.jar backup /data --threads 2

# Disable CPU-intensive features
java -jar justsyncit.jar backup /data --no-compression
java -jar justsyncit.jar backup /data --no-verification

# Set CPU affinity
taskset -c 0-3 java -jar justsyncit.jar backup /data

# Optimize CPU governor
sudo cpufreq-set -g powersave
```

## Data Corruption Recovery

### Detecting Corruption

**Symptoms**:
- "Checksum verification failed" errors
- Files cannot be opened
- Restore operations fail
- Unexpected file sizes

**Diagnosis**:
```bash
# Verify snapshot integrity
java -jar justsyncit.jar snapshots verify snapshot-id --verbose

# Check individual files
java -jar justsyncit.jar hash /path/to/suspicious/file

# Compare with source
diff /path/to/source /path/to/backup
rsync -avc /path/to/source /path/to/backup
```

### Recovering from Corruption

#### File-Level Recovery

```bash
# Recover specific files from snapshot
java -jar justsyncit.jar snapshots recover snapshot-id \
  --files "/documents/important.txt,/photos/vacation.jpg" \
  --output /recovery/partial

# Recover with corruption tolerance
java -jar justsyncit.jar snapshots recover snapshot-id \
  --ignore-corruption \
  --max-tolerance 5 \
  --output /recovery/tolerant
```

#### Snapshot-Level Recovery

```bash
# Create new backup from remaining good data
java -jar justsyncit.jar backup /recovered/data \
  --name "Recovery-Backup" \
  --skip-corrupted

# Clone snapshot without corrupted chunks
java -jar justsyncit.jar snapshots clone snapshot-id \
  --skip-corrupted \
  --name "Clean-Clone"

# Rebuild from file index
java -jar justsyncit.jar snapshots rebuild snapshot-id \
  --from-index \
  --name "Rebuilt-Snapshot"
```

#### Storage-Level Recovery

```bash
# Check storage integrity
java -jar justsyncit.jar storage check --verbose

# Repair storage corruption
java -jar justsyncit.jar storage repair --auto

# Rebuild storage from scratch
java -jar justsyncit.jar storage rebuild --from-backup
```

### Preventing Future Corruption

#### Hardware Monitoring

```bash
# Monitor disk health
smartctl -a /dev/sda
smartctl -t long /dev/sda

# Monitor RAM errors
dmesg | grep -i memory
ecc-detect

# Monitor temperature
sensors
lm-sensors
```

#### Data Verification

```bash
# Regular integrity checks
java -jar justsyncit.jar snapshots verify --all --schedule daily

# Cross-verify backups
java -jar justsyncit.jar snapshots compare primary-backup secondary-backup

# Hash verification
find /data -type f -exec sha256sum {} \; > checksums.txt
sha256sum -c checksums.txt
```

## Storage Issues

### Disk Space Management

#### Running Out of Space

**Symptoms**:
- "Storage full" errors
- Operations fail unexpectedly
- System becomes unresponsive

**Diagnosis**:
```bash
# Check disk usage
df -h
du -sh /path/to/justsyncit/data

# Check inode usage
df -i
find /path/to/data | wc -l

# Check for large files
find /path/to/data -type f -size +1G -exec ls -lh {} \;
```

**Solutions**:
```bash
# Clean up temporary files
java -jar justsyncit.jar cleanup --temp-files

# Run garbage collection
java -jar justsyncit.jar gc --verbose

# Delete old snapshots
java -jar justsyncit.jar snapshots delete --older-than "30days" --force

# Move to larger storage
java -jar justsyncit.jar migrate --to /larger/storage
```

#### Inode Exhaustion

**Symptoms**:
- "No space left on device" despite free space
- Cannot create new files
- Strange file system errors

**Diagnosis**:
```bash
# Check inode usage
df -i
stat -f /path/to/filesystem

# Count files
find /path/to/data | wc -l

# Check for small files
find /path/to/data -size 0 -exec ls -la {} \;
```

**Solutions**:
```bash
# Clean up small files
find /path/to/data -size 0 -delete

# Use larger inode filesystem
mkfs.ext4 -N large /dev/sdb1
mount /dev/sdb1 /mnt/large-inode

# Consolidate small files
tar -cf archive.tar many-small-files/
rm many-small-files/
tar -xf archive.tar
```

### Filesystem Corruption

#### Detecting Corruption

**Symptoms**:
- "I/O error" messages
- Files disappear or become unreadable
- System crashes during I/O operations

**Diagnosis**:
```bash
# Check filesystem health
fsck -n /dev/sda1
e2fsck -n /dev/sda1
xfs_repair -n /dev/sda1

# Check system logs
dmesg | grep -i error
journalctl -p err -b

# Check SMART status
smartctl -a /dev/sda
smartctl -x /dev/sda
```

**Solutions**:
```bash
# Repair filesystem (read-only first)
fsck -n /dev/sda1
e2fsck -f /dev/sda1
xfs_repair -L /dev/sda1

# Remount with different options
mount -o remount,errors=remount-ro /dev/sda1 /mnt/data

# Backup to different device
java -jar justsyncit.jar backup /data --storage /mnt/backup-device
```

## Memory Issues

### Out of Memory Errors

#### Java Heap Issues

**Symptoms**:
- "OutOfMemoryError: Java heap space"
- Process terminates unexpectedly
- System becomes very slow

**Diagnosis**:
```bash
# Check Java heap usage
jstat -gc $(pgrep -f justsyncit)
jmap -heap $(pgrep -f justsyncit)

# Monitor system memory
free -h
vmstat 1
```

**Solutions**:
```bash
# Increase heap size
export JAVA_OPTS="-Xmx8g -Xms4g"

# Use 64-bit Java
java -d64 -jar justsyncit.jar backup /data

# Enable GC tuning
export JAVA_OPTS="-Xmx8g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Reduce memory pressure
java -jar justsyncit.jar backup /data --memory-efficient
```

#### Native Memory Issues

**Symptoms**:
- "OutOfMemoryError: Native memory"
- "Cannot allocate native memory"
- Crashes during I/O operations

**Diagnosis**:
```bash
# Check native memory usage
cat /proc/$(pgrep -f justsyncit)/status | grep VmRSS

# Check system limits
ulimit -a
cat /proc/sys/vm/overcommit_memory
```

**Solutions**:
```bash
# Increase native memory limit
ulimit -v unlimited

# Adjust system overcommit
echo 0 | sudo tee /proc/sys/vm/overcommit_memory

# Reduce native memory usage
java -jar justsyncit.jar backup /data --reduce-native-memory
```

### Memory Leaks

#### Detecting Memory Leaks

**Symptoms**:
- Memory usage increases over time
- Performance degrades during long operations
- Eventually runs out of memory

**Diagnosis**:
```bash
# Monitor memory over time
watch -n 5 'free -h'

# Generate heap dumps
jmap -dump:format=b,file=heap.hprof $(pgrep -f justsyncit)

# Profile memory allocation
java -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -Xloggc:gc.log \
  -jar justsyncit.jar backup /data
```

**Solutions**:
```bash
# Restart process periodically
java -jar justsyncit.jar backup /data --max-runtime 3600

# Use memory profiling
java -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/ \
  -jar justsyncit.jar backup /data

# Enable memory leak detection
-XX:+HeapDumpOnOutOfMemoryError -XX:OnOutOfMemoryError="kill"
```

## Installation Issues

### Java Version Problems

#### Incompatible Java Version

**Symptoms**:
- "UnsupportedClassVersionError"
- "Unsupported major.minor version"
- Application won't start

**Diagnosis**:
```bash
# Check Java version
java -version
javac -version

# Check required version
grep -i "java" /path/to/justsyncit/README.md
```

**Solutions**:
```bash
# Install Java 21
sudo apt install openjdk-21-jdk
sudo yum install java-21-openjdk

# Use Java 21 explicitly
/usr/lib/jvm/java-21/bin/java -jar justsyncit.jar backup /data

# Update alternatives
sudo update-alternatives --config java
```

#### Missing Java Components

**Symptoms**:
- "NoClassDefFoundError"
- "ClassNotFoundException"
- Missing native library errors

**Diagnosis**:
```bash
# Check Java installation
ls -la /usr/lib/jvm/java-21/
dpkg -L | grep openjdk

# Check for required components
java -verbose:class -jar justsyncit.jar backup /data 2>&1 | grep -i loaded
```

**Solutions**:
```bash
# Reinstall Java completely
sudo apt remove --purge openjdk-*
sudo apt install openjdk-21-jdk

# Install missing components
sudo apt install openjdk-21-jre-headless

# Use different Java distribution
sudo apt install oracle-java21-installer
```

### Library Dependencies

#### Missing Native Libraries

**Symptoms**:
- "UnsatisfiedLinkError"
- "Library not found"
- Native method failures

**Diagnosis**:
```bash
# Check library dependencies
ldd $(which java)
ldd /path/to/justsyncit.jar

# Check for missing libraries
find /usr/lib -name "*.so" | grep -i missing
```

**Solutions**:
```bash
# Install missing libraries
sudo apt install libc6-dev
sudo apt install libssl-dev

# Set library path
export LD_LIBRARY_PATH=/usr/local/lib:$LD_LIBRARY_PATH

# Use static linking if available
java -Djava.library.path=/usr/local/lib -jar justsyncit.jar backup /data
```

## Advanced Troubleshooting

### Debug Mode

#### Enabling Debug Logging

```bash
# Enable comprehensive debug logging
java -jar justsyncit.jar backup /data \
  --log-level DEBUG \
  --verbose \
  --debug-all

# Enable specific debug categories
java -jar justsyncit.jar backup /data \
  --debug-network \
  --debug-storage \
  --debug-hashing

# Output debug to file
java -jar justsyncit.jar backup /data \
  --log-level DEBUG \
  --log-file /tmp/justsyncit-debug.log
```

#### Performance Profiling

```bash
# Enable JVM profiling
java -XX:+FlightRecorder \
  -XX:StartFlightRecording=duration=60s,filename=profile.jfr \
  -jar justsyncit.jar backup /data

# Enable CPU profiling
java -XX:+PrintCompilation \
  -XX:+PrintInlining \
  -jar justsyncit.jar backup /data

# Enable memory profiling
java -XX:+PrintGCDetails \
  -XX:+PrintGCDateStamps \
  -Xloggc:gc.log \
  -jar justsyncit.jar backup /data
```

### System Diagnostics

#### Hardware Diagnostics

```bash
# CPU diagnostics
lscpu
cpuid
dmidecode -t processor

# Memory diagnostics
dmidecode -t memory
memtest86 4

# Storage diagnostics
hdparm -I /dev/sda
smartctl -a /dev/sda
badblocks -sv /dev/sda

# Network diagnostics
ethtool eth0
lspci | grep -i network
```

#### System Monitoring

```bash
# Real-time monitoring
watch -n 1 'free -h; echo "---"; iostat 1 1; echo "---"; top -n 1'

# Resource usage logging
vmstat 1 > vmstat.log &
iostat 1 > iostat.log &
top -b -d 1 > top.log &

# Performance counters
perf stat -e cycles,instructions,cache-misses \
  java -jar justsyncit.jar backup /data
```

### Getting Help

#### Collecting Diagnostic Information

```bash
# Create diagnostic bundle
java -jar justsyncit.jar diagnostics \
  --output /tmp/justsyncit-diagnostics.tar.gz

# Include system information
java -jar justsyncit.jar diagnostics \
  --include-system \
  --include-hardware \
  --include-network

# Generate support report
java -jar justsyncit.jar diagnostics \
  --support-report \
  --output support-request.txt
```

#### Community Support

```bash
# Check known issues
curl -s "https://api.github.com/repos/carabistouflette/justsyncit/issues?state=open" | \
  jq -r '.[].title'

# Search discussions
curl -s "https://api.github.com/repos/carabistouflette/justsyncit/discussions" | \
  jq -r '.[].title'

# Create bug report
cat > bug-report.md <<EOF'
## JustSyncIt Bug Report

### System Information
- OS: $(uname -a)
- Java: $(java -version)
- JustSyncIt: $(java -jar justsyncit.jar --version)

### Issue Description
[Describe the issue in detail]

### Steps to Reproduce
1. [Step 1]
2. [Step 2]
3. [Step 3]

### Expected Behavior
[What should happen]

### Actual Behavior
[What actually happened]

### Error Messages
[Paste any error messages]

### Additional Information
[Any other relevant information]
EOF
```

This comprehensive troubleshooting guide covers the most common issues you may encounter with JustSyncIt. For additional help, refer to the [User Guide](user-guide.md), [CLI Reference](cli-reference.md), or visit the [GitHub Issues](https://github.com/carabistouflette/justsyncit/issues) page.