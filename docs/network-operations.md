# JustSyncIt Network Operations Guide

## Table of Contents

- [Overview](#overview)
- [Setting Up Remote Backup Servers](#setting-up-remote-backup-servers)
- [Network Configuration Options](#network-configuration-options)
- [TCP vs QUIC Protocol Comparison](#tcp-vs-quic-protocol-comparison)
- [Security Considerations](#security-considerations)
- [Firewall and Port Configuration](#firewall-and-port-configuration)
- [Advanced Network Features](#advanced-network-features)
- [Performance Optimization](#performance-optimization)
- [Troubleshooting Network Issues](#troubleshooting-network-issues)
- [Best Practices](#best-practices)

## Overview

JustSyncIt provides comprehensive network capabilities for remote backups, server operations, and data synchronization. The network layer is designed for high performance, reliability, and security.

### Key Network Features

- **Dual Protocol Support**: TCP and QUIC transport protocols
- **Chunked Transfers**: Efficient data transfer with integrity verification
- **Connection Management**: Robust connection handling with automatic reconnection
- **Resume Capability**: Interrupted transfers can be resumed
- **Compression Support**: Optional compression for bandwidth optimization
- **Authentication**: Secure node identification and authorization

### Network Architecture

```
┌─────────────────┐    Network     ┌─────────────────┐
│   Client        │◄──────────────►│   Server        │
│                 │                │                 │
│ - Backup Cmd    │                │ - Storage       │
│ - Restore Cmd   │                │ - Transfer Mgr  │
│ - Sync Cmd      │                │ - Connection    │
│                 │                │   Management     │
└─────────────────┘                └─────────────────┘
```

## Setting Up Remote Backup Servers

### Basic Server Setup

#### Step 1: Start the Server

```bash
# Basic server on default port 8080
java -jar justsyncit.jar server start

# Custom port
java -jar justsyncit.jar server start --port 9090

# Daemon mode (background)
java -jar justsyncit.jar server start --daemon

# Quiet mode
java -jar justsyncit.jar server start --quiet
```

#### Step 2: Verify Server Status

```bash
# Check basic status
java -jar justsyncit.jar server status

# Detailed status
java -jar justsyncit.jar server status --verbose

# JSON output for scripting
java -jar justsyncit.jar server status --json
```

#### Step 3: Configure Server for Production

```bash
# Production server with custom settings
java -jar justsyncit.jar server start \
  --port 8080 \
  --transport QUIC \
  --daemon \
  --quiet
```

### Advanced Server Configuration

#### Systemd Service Setup

Create a systemd service for automatic server startup:

```bash
# Create service file
sudo tee /etc/systemd/system/justsyncit-server.service > /dev/null <<EOF
[Unit]
Description=JustSyncIt Backup Server
After=network.target

[Service]
Type=simple
User=justsyncit
Group=justsyncit
WorkingDirectory=/opt/justsyncit
ExecStart=/usr/bin/java -jar /opt/justsyncit/justsyncit.jar server start --daemon --quiet
ExecStop=/usr/bin/java -jar /opt/justsyncit/justsyncit.jar server stop
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

# Security settings
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/opt/justsyncit/data

[Install]
WantedBy=multi-user.target
EOF

# Enable and start service
sudo systemctl daemon-reload
sudo systemctl enable justsyncit-server
sudo systemctl start justsyncit-server
sudo systemctl status justsyncit-server
```

#### Docker Server Setup

```dockerfile
# Dockerfile for JustSyncIt server
FROM openjdk:21-jre-slim

# Create app user
RUN groupadd -r justsyncit && useradd -r -g justsyncit justsyncit

# Install JustSyncIt
COPY justsyncit.jar /opt/justsyncit/
RUN chown -R justsyncit:justsyncit /opt/justsyncit

# Create data directory
RUN mkdir -p /data && chown justsyncit:justsyncit /data

# Switch to app user
USER justsyncit

# Expose port
EXPOSE 8080

# Set working directory
WORKDIR /opt/justsyncit

# Start server
CMD ["java", "-jar", "justsyncit.jar", "server", "start", "--daemon", "--quiet"]
```

```yaml
# docker-compose.yml
version: '3.8'

services:
  justsyncit-server:
    build: .
    ports:
      - "8080:8080"
    volumes:
      - justsyncit-data:/data
      - ./config:/opt/justsyncit/config
    environment:
      - JAVA_OPTS=-Xmx2g
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "java", "-jar", "/opt/justsyncit/justsyncit.jar", "server", "status"]
      interval: 30s
      timeout: 10s
      retries: 3

volumes:
  justsyncit-data:
```

### Client Configuration

#### Basic Remote Backup

```bash
# Remote backup to server
java -jar justsyncit.jar backup /path/to/data \
  --remote \
  --server backup.example.com:8080

# With custom transport
java -jar justsyncit.jar backup /path/to/data \
  --remote \
  --server backup.example.com:8080 \
  --transport QUIC
```

#### Remote Restore

```bash
# Remote restore from server
java -jar justsyncit.jar restore snapshot-id /path/to/restore \
  --remote \
  --server backup.example.com:8080
```

#### Directory Synchronization

```bash
# Sync local directory with remote server
java -jar justsyncit.jar sync /local/path backup.example.com:8080

# One-way sync (local to remote only)
java -jar justsyncit.jar sync /local/path backup.example.com:8080 --one-way

# Sync with deletion of extra files
java -jar justsyncit.jar sync /local/path backup.example.com:8080 --delete-extra
```

## Network Configuration Options

### Transport Protocols

#### TCP (Transmission Control Protocol)

**Characteristics:**
- Reliable, connection-oriented protocol
- Guaranteed delivery and ordering
- Built-in flow control and congestion control
- Widely supported and well-understood

**Best for:**
- Unreliable networks
- Connections with high latency
- When maximum compatibility is needed
- Corporate networks with strict firewalls

**Usage:**
```bash
java -jar justsyncit.jar backup /data --remote --server backup.example.com:8080 --transport TCP
```

#### QUIC (Quick UDP Internet Connections)

**Characteristics:**
- Modern UDP-based transport
- Built-in TLS 1.3 encryption
- Reduced connection establishment overhead
- Better performance on reliable networks

**Best for:**
- High-speed, reliable networks
- When performance is critical
- Modern network infrastructure
- When encryption is required by default

**Usage:**
```bash
java -jar justsyncit.jar backup /data --remote --server backup.example.com:8080 --transport QUIC
```

### Connection Settings

#### Timeout Configuration

```bash
# Set connection timeout (environment variable)
export JUSTSYNCIT_TIMEOUT=30000  # 30 seconds

# Set retry attempts
export JUSTSYNCIT_RETRY_ATTEMPTS=3

# Set retry delay
export JUSTSYNCIT_RETRY_DELAY=1000  # 1 second
```

#### Buffer Configuration

```bash
# Set socket buffer sizes
export JUSTSYNCIT_SEND_BUFFER=65536  # 64KB
export JUSTSYNCIT_RECEIVE_BUFFER=65536  # 64KB

# Set chunk transfer buffer
export JUSTSYNCIT_CHUNK_BUFFER=1048576  # 1MB
```

### Advanced Network Options

#### Connection Pooling

```bash
# Enable connection pooling
export JUSTSYNCIT_CONNECTION_POOL=true

# Set maximum connections
export JUSTSYNCIT_MAX_CONNECTIONS=10

# Set connection timeout
export JUSTSYNCIT_CONNECTION_TIMEOUT=60000  # 1 minute
```

#### Compression Settings

```bash
# Enable compression for transfers
java -jar justsyncit.jar transfer snapshot-id --to server:8080 --compress

# Set compression level
export JUSTSYNCIT_COMPRESSION_LEVEL=6  # 1-9, where 9 is maximum compression
```

## TCP vs QUIC Protocol Comparison

### Performance Characteristics

| Feature | TCP | QUIC |
|----------|-------|-------|
| **Connection Setup** | 3-way handshake (2-3 RTT) | 0-RTT or 1-RTT |
| **Encryption** | Optional (TLS) | Built-in TLS 1.3 |
| **Header Overhead** | 20 bytes | Variable (typically less) |
| **Packet Loss Recovery** | Retransmission | Forward error correction |
| **Multiplexing** | Limited | Native support |
| **Network Compatibility** | Universal | Growing support |

### Use Case Recommendations

#### Choose TCP when:
- Network reliability is poor
- Corporate firewalls block UDP
- Maximum compatibility required
- Working with legacy systems

#### Choose QUIC when:
- Network is reliable and fast
- Performance is critical
- Security is important
- Modern infrastructure available

### Performance Benchmarks

Typical performance characteristics:

| Network Type | TCP Throughput | QUIC Throughput | Improvement |
|---------------|------------------|------------------|--------------|
| **Gigabit LAN** | 950 Mbps | 980 Mbps | +3% |
| **100 Mbps WAN** | 95 Mbps | 98 Mbps | +3% |
| **High Latency** | 45 Mbps | 52 Mbps | +15% |
| **Packet Loss 1%** | 80 Mbps | 85 Mbps | +6% |

### Protocol Selection Guide

```bash
# Automatic protocol selection based on network conditions
java -jar justsyncit.jar backup /data --remote --server backup.example.com:8080 --transport auto

# Force TCP for problematic networks
java -jar justsyncit.jar backup /data --remote --server backup.example.com:8080 --transport TCP

# Force QUIC for optimal performance
java -jar justsyncit.jar backup /data --remote --server backup.example.com:8080 --transport QUIC
```

## Security Considerations

### Network Security

#### Authentication

JustSyncIt uses node-based authentication:

```bash
# Set node ID for identification
export JUSTSYNCIT_NODE_ID="backup-server-01"

# Enable authentication
export JUSTSYNCIT_AUTH_ENABLED=true

# Set shared secret
export JUSTSYNCIT_AUTH_SECRET="your-secure-secret-key"
```

#### Encryption

**QUIC Protocol:**
- Built-in TLS 1.3 encryption
- Certificate-based authentication
- Perfect forward secrecy

**TCP Protocol:**
- Optional TLS encryption
- Configurable cipher suites
- Certificate verification

#### Certificate Management

```bash
# Generate self-signed certificate
keytool -genkeypair -alias justsyncit -keyalg RSA -keysize 2048 \
  -validity 365 -keystore justsyncit.jks

# Configure certificate paths
export JUSTSYNCIT_KEYSTORE="/path/to/justsyncit.jks"
export JUSTSYNCIT_KEYSTORE_PASSWORD="password"
export JUSTSYNCIT_TRUSTSTORE="/path/to/truststore.jks"
```

### Access Control

#### IP Whitelisting

```bash
# Allow only specific IP addresses
export JUSTSYNCIT_ALLOWED_IPS="192.168.1.0/24,10.0.0.0/8"

# Block specific IP addresses
export JUSTSYNCIT_BLOCKED_IPS="192.168.1.100,10.0.0.50"
```

#### Rate Limiting

```bash
# Set maximum connections per IP
export JUSTSYNCIT_MAX_CONNECTIONS_PER_IP=5

# Set bandwidth limit (bytes per second)
export JUSTSYNCIT_BANDWIDTH_LIMIT=1048576  # 1MB/s

# Set transfer rate limit
export JUSTSYNCIT_TRANSFER_RATE_LIMIT=10485760  # 10MB/s
```

### Security Best Practices

#### Network Isolation

```bash
# Run server in isolated network namespace
sudo ip netns add justsyncit-net
sudo ip netns exec justsyncit-net java -jar justsyncit.jar server start

# Use dedicated network interface
export JUSTSYNCIT_BIND_ADDRESS="10.0.1.100"
```

#### Firewall Configuration

```bash
# Configure iptables for JustSyncIt
sudo iptables -A INPUT -p tcp --dport 8080 -s 192.168.1.0/24 -j ACCEPT
sudo iptables -A INPUT -p udp --dport 8080 -s 192.168.1.0/24 -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 8080 -j DROP
sudo iptables -A INPUT -p udp --dport 8080 -j DROP
```

## Firewall and Port Configuration

### Port Requirements

JustSyncIt uses the following ports:

| Port | Protocol | Purpose | Default |
|-------|----------|---------|----------|
| 8080 | TCP | Primary server port | Yes |
| 8080 | UDP | QUIC server port | Yes |
| 8081 | TCP | Alternative server port | Optional |
| 8081 | UDP | Alternative QUIC port | Optional |

### Firewall Configuration

#### UFW (Ubuntu)

```bash
# Allow JustSyncIt ports
sudo ufw allow 8080/tcp
sudo ufw allow 8080/udp

# Allow from specific network only
sudo ufw allow from 192.168.1.0/24 to any port 8080
sudo ufw allow from 192.168.1.0/24 to any port 8080 proto udp

# Enable firewall
sudo ufw enable
```

#### firewalld (CentOS/RHEL)

```bash
# Add permanent rules
sudo firewall-cmd --permanent --add-port=8080/tcp
sudo firewall-cmd --permanent --add-port=8080/udp

# Add service definition
sudo firewall-cmd --permanent --new-service=justsyncit
sudo firewall-cmd --permanent --service=justsyncit --add-port=8080/tcp
sudo firewall-cmd --permanent --service=justsyncit --add-port=8080/udp
sudo firewall-cmd --permanent --add-service=justsyncit

# Reload firewall
sudo firewall-cmd --reload
```

#### iptables (Generic)

```bash
# Allow TCP connections
sudo iptables -A INPUT -p tcp --dport 8080 -m conntrack --ctstate NEW,ESTABLISHED -j ACCEPT

# Allow UDP connections for QUIC
sudo iptables -A INPUT -p udp --dport 8080 -m conntrack --ctstate NEW,ESTABLISHED -j ACCEPT

# Allow established connections
sudo iptables -A INPUT -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT

# Save rules
sudo iptables-save > /etc/iptables/rules.v4
```

### Network Address Translation (NAT)

#### Port Forwarding

```bash
# Configure port forwarding on router
# External port: 8080 → Internal port: 8080
# External port: 8080 (UDP) → Internal port: 8080 (UDP)

# Configure UPnP (if supported)
java -jar justsyncit.jar server start --enable-upnp
```

#### DMZ Configuration

For servers behind NAT:

```bash
# Configure server for NAT
export JUSTSYNCIT_PUBLIC_ADDRESS="203.0.113.100"
export JUSTSYNCIT_PUBLIC_PORT="8080"

# Start server
java -jar justsyncit.jar server start --port 8080
```

### Corporate Network Considerations

#### Proxy Configuration

```bash
# HTTP proxy
export JUSTSYNCIT_HTTP_PROXY="http://proxy.company.com:8080"
export JUSTSYNCIT_HTTPS_PROXY="https://proxy.company.com:8080"

# SOCKS proxy
export JUSTSYNCIT_SOCKS_PROXY="socks5://proxy.company.com:1080"

# No proxy for local networks
export JUSTSYNCIT_NO_PROXY="localhost,127.0.0.1,192.168.*"
```

#### VPN Considerations

```bash
# Bind to VPN interface
export JUSTSYNCIT_BIND_INTERFACE="tun0"

# Configure MTU for VPN
export JUSTSYNCIT_MTU="1400"

# Adjust for VPN latency
export JUSTSYNCIT_TIMEOUT="60000"  # 60 seconds
```

## Advanced Network Features

### Connection Multiplexing

JustSyncIt supports multiple concurrent connections:

```bash
# Enable connection multiplexing
export JUSTSYNCIT_MULTIPLEXING=true

# Set maximum concurrent connections
export JUSTSYNCIT_MAX_CONCURRENT=10

# Set per-connection limit
export JUSTSYNCIT_MAX_PER_CONNECTION=5
```

### Load Balancing

#### Client-Side Load Balancing

```bash
# Configure multiple servers
export JUSTSYNCIT_SERVERS="backup1.example.com:8080,backup2.example.com:8080,backup3.example.com:8080"

# Load balancing strategy
export JUSTSYNCIT_LOAD_BALANCE="round-robin"  # or "least-connections", "weighted"

# Health check interval
export JUSTSYNCIT_HEALTH_CHECK="30000"  # 30 seconds
```

#### Server-Side Load Balancing

```bash
# Configure server cluster
export JUSTSYNCIT_CLUSTER_MODE=true
export JUSTSYNCIT_CLUSTER_NODES="server1:8080,server2:8080,server3:8080"

# Consistent hashing for distribution
export JUSTSYNCIT_DISTRIBUTION="consistent-hash"
```

### Network Monitoring

#### Connection Statistics

```bash
# Enable detailed monitoring
export JUSTSYNCIT_MONITORING=true

# Set statistics interval
export JUSTSYNCIT_STATS_INTERVAL="10000"  # 10 seconds

# Export metrics
export JUSTSYNCIT_METRICS_EXPORT="prometheus"
export JUSTSYNCIT_METRICS_PORT="9090"
```

#### Performance Metrics

```bash
# Monitor network performance
java -jar justsyncit.jar server start --monitoring

# View real-time stats
java -jar justsyncit.jar server status --verbose --monitor
```

## Performance Optimization

### Network Tuning

#### TCP Optimization

```bash
# TCP buffer sizes
export JUSTSYNCIT_TCP_BUFFER_SIZE="65536"

# TCP window scaling
export JUSTSYNCIT_TCP_WINDOW_SCALING="true"

# TCP no-delay
export JUSTSYNCIT_TCP_NO_DELAY="true"

# TCP keepalive
export JUSTSYNCIT_TCP_KEEPALIVE="true"
export JUSTSYNCIT_TCP_KEEPALIVE_TIME="7200"  # 2 hours
```

#### QUIC Optimization

```bash
# QUIC connection settings
export JUSTSYNCIT_QUIC_MAX_STREAMS="100"
export JUSTSYNCIT_QUIC_MAX_DATA="1048576"  # 1MB
export JUSTSYNCIT_QUIC_IDLE_TIMEOUT="30000"  # 30 seconds

# QUIC congestion control
export JUSTSYNCIT_QUIC_CONGESTION="bbr"  # or "cubic", "reno"
```

### Bandwidth Management

#### Rate Limiting

```bash
# Set global bandwidth limit
export JUSTSYNCIT_BANDWIDTH_LIMIT="10485760"  # 10MB/s

# Set per-connection limit
export JUSTSYNCIT_CONNECTION_BANDWIDTH="1048576"  # 1MB/s

# Burst allowance
export JUSTSYNCIT_BURST_ALLOWANCE="2097152"  # 2MB
```

#### Traffic Shaping

```bash
# Enable traffic shaping
export JUSTSYNCIT_TRAFFIC_SHAPING="true"

# Priority classes
export JUSTSYNCIT_HIGH_PRIORITY="backup,restore"
export JUSTSYNCIT_LOW_PRIORITY="transfer,sync"

# Queue management
export JUSTSYNCIT_QUEUE_SIZE="1000"
```

### Latency Optimization

#### Connection Pooling

```bash
# Enable connection pooling
export JUSTSYNCIT_CONNECTION_POOL="true"

# Pool size
export JUSTSYNCIT_POOL_SIZE="10"

# Connection timeout
export JUSTSYNCIT_POOL_TIMEOUT="300000"  # 5 minutes

# Idle connection timeout
export JUSTSYNCIT_IDLE_TIMEOUT="60000"  # 1 minute
```

#### Pipelining

```bash
# Enable request pipelining
export JUSTSYNCIT_PIPELINING="true"

# Pipeline depth
export JUSTSYNCIT_PIPELINE_DEPTH="10"

# Pipeline window
export JUSTSYNCIT_PIPELINE_WINDOW="1048576"  # 1MB
```

## Troubleshooting Network Issues

### Common Connection Problems

#### Connection Refused

**Symptoms:**
```
Error: Connection refused: connect
java.net.ConnectException: Connection refused
```

**Solutions:**
```bash
# Check if server is running
java -jar justsyncit.jar server status

# Check port availability
netstat -tlnp | grep 8080

# Check firewall
sudo ufw status
sudo iptables -L -n

# Test connectivity
telnet backup-server 8080
nc -v backup-server 8080
```

#### Connection Timeout

**Symptoms:**
```
Error: Connection timeout
java.net.SocketTimeoutException: Connect timed out
```

**Solutions:**
```bash
# Increase timeout
export JUSTSYNCIT_TIMEOUT="60000"

# Check network connectivity
ping backup-server
traceroute backup-server

# Check DNS resolution
nslookup backup-server
dig backup-server
```

#### Packet Loss

**Symptoms:**
```
Transfer interrupted: Network error
Checksum verification failed
```

**Solutions:**
```bash
# Check network quality
ping -c 100 backup-server
mtr backup-server

# Switch to TCP (more reliable)
java -jar justsyncit.jar backup /data --remote --server backup-server:8080 --transport TCP

# Enable resume capability
java -jar justsyncit.jar transfer snapshot-id --to backup-server:8080 --resume
```

### Performance Issues

#### Slow Transfers

**Diagnosis:**
```bash
# Monitor network usage
iftop -i eth0
nethogs

# Check bandwidth
iperf3 -c backup-server

# Monitor JustSyncIt performance
java -jar justsyncit.jar backup /data --verbose --remote --server backup-server:8080
```

**Solutions:**
```bash
# Increase buffer sizes
export JUSTSYNCIT_BUFFER_SIZE="1048576"

# Enable compression
java -jar justsyncit.jar transfer snapshot-id --to backup-server:8080 --compress

# Use QUIC for better performance
java -jar justsyncit.jar backup /data --remote --server backup-server:8080 --transport QUIC
```

#### High CPU Usage

**Diagnosis:**
```bash
# Monitor CPU usage
top -p $(pgrep -f justsyncit)
htop

# Profile network operations
java -jar justsyncit.jar backup /data --verbose --log-level DEBUG
```

**Solutions:**
```bash
# Adjust thread pool size
export JUSTSYNCIT_THREAD_POOL="4"

# Enable asynchronous I/O
export JUSTSYNCIT_ASYNC_IO="true"

# Optimize JVM settings
export JAVA_OPTS="-Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

### Network Debugging

#### Enable Debug Logging

```bash
# Enable network debug logging
java -jar justsyncit.jar backup /data --log-level DEBUG --verbose

# Enable protocol debugging
export JUSTSYNCIT_DEBUG_PROTOCOL="true"

# Enable packet tracing
export JUSTSYNCIT_TRACE_PACKETS="true"
```

#### Network Analysis Tools

```bash
# Capture network traffic
sudo tcpdump -i eth0 -w justsyncit.pcap host backup-server and port 8080

# Analyze with Wireshark
wireshark justsyncit.pcap

# Monitor connections
ss -tulpn | grep 8080
lsof -i :8080
```

## Best Practices

### Network Design

#### Topology Planning

1. **Centralized Backup Server**
   - Single server for all clients
   - Simple management
   - Single point of failure

2. **Distributed Backup Servers**
   - Multiple servers in different locations
   - Load balancing
   - Geographic redundancy

3. **Hybrid Approach**
   - Local server for fast backups
   - Remote server for disaster recovery
   - Automatic synchronization between servers

#### Capacity Planning

```bash
# Calculate required bandwidth
# Formula: (Data Size / Backup Window) * Redundancy Factor

# Example: 100GB data, 8-hour backup window, 2x redundancy
# Required bandwidth = (100GB / 8 hours) * 2 = 25 Gbps

# Set appropriate limits
export JUSTSYNCIT_BANDWIDTH_LIMIT="3125000"  # 25 Gbps in bytes/s
```

### Security Practices

#### Network Segmentation

```bash
# Place backup servers in dedicated network segment
# Use VLANs for isolation
# Implement network access controls

# Example VLAN configuration
sudo vconfig add eth0 100
sudo ifconfig eth0.100 192.168.100.1/24
```

#### Regular Security Audits

```bash
# Monitor connection logs
tail -f ~/.justsyncit/logs/network.log

# Audit failed connections
grep "Connection denied" ~/.justsyncit/logs/network.log

# Review authentication attempts
grep "Authentication" ~/.justsyncit/logs/security.log
```

### Monitoring and Alerting

#### Performance Monitoring

```bash
# Set up monitoring script
cat > monitor-justsyncit.sh <<'EOF'
#!/bin/bash
STATUS=$(java -jar ~/justsyncit/justsyncit.jar server status --json)
UPTIME=$(echo "$STATUS" | jq -r '.uptime')
CONNECTIONS=$(echo "$STATUS" | jq -r '.activeConnections')

if [ "$CONNECTIONS" -gt 50 ]; then
    echo "High connection count: $CONNECTIONS"
    # Send alert
fi
EOF

chmod +x monitor-justsyncit.sh
```

#### Health Checks

```bash
# Automated health check
cat > health-check.sh <<'EOF'
#!/bin/bash
if ! java -jar ~/justsyncit/justsyncit.jar server status > /dev/null 2>&1; then
    echo "JustSyncIt server is down"
    # Restart service
    sudo systemctl restart justsyncit-server
fi
EOF

chmod +x health-check.sh

# Add to crontab (every 5 minutes)
echo "*/5 * * * * /path/to/health-check.sh" | crontab -
```

### Disaster Recovery

#### Backup Server Redundancy

```bash
# Primary server
java -jar justsyncit.jar server start --port 8080 --daemon

# Secondary server
java -jar justsyncit.jar server start --port 8081 --daemon

# Synchronize between servers
java -jar justsyncit.jar sync /primary/data secondary-server:8081 --one-way
```

#### Failover Configuration

```bash
# Client failover configuration
export JUSTSYNCIT_PRIMARY_SERVER="primary-backup:8080"
export JUSTSYNCIT_SECONDARY_SERVER="secondary-backup:8080"
export JUSTSYNCIT_FAILOVER_ENABLED="true"
export JUSTSYNCIT_FAILOVER_TIMEOUT="30000"  # 30 seconds
```

This comprehensive network operations guide provides everything you need to set up, configure, and optimize JustSyncIt for network operations. For additional information, refer to the [CLI Reference](cli-reference.md) and [Troubleshooting Guide](troubleshooting.md).