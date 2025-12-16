#!/bin/bash
# Network Optimization Script for JustSyncIt
# Run with sudo to apply changes

echo "Applying Network Optimizations..."

# 1. Increase TCP Buffer Sizes (OS Level)
# Allow larger window sizes for high-bandwidth WAN connections
sysctl -w net.core.rmem_max=16777216
sysctl -w net.core.wmem_max=16777216
sysctl -w net.ipv4.tcp_rmem="4096 87380 16777216"
sysctl -w net.ipv4.tcp_wmem="4096 65536 16777216"

# 2. Enable TCP Window Scaling
sysctl -w net.ipv4.tcp_window_scaling=1

# 3. Enable BBR Congestion Control
# Check if BBR is available
if grep -q bbr /proc/sys/net/ipv4/tcp_available_congestion_control; then
    sysctl -w net.core.default_qdisc=fq
    sysctl -w net.ipv4.tcp_congestion_control=bbr
    echo "BBR congestion control enabled."
else
    echo "BBR not available, falling back to cubic."
    sysctl -w net.ipv4.tcp_congestion_control=cubic
fi

# 4. Connection Tracking and Backlog
sysctl -w net.core.netdev_max_backlog=5000
sysctl -w net.ipv4.tcp_max_syn_backlog=4096
# TIME_WAIT reuse (be careful with this in NAT environments)
sysctl -w net.ipv4.tcp_tw_reuse=1

# 5. Keepalive Settings
sysctl -w net.ipv4.tcp_keepalive_time=60
sysctl -w net.ipv4.tcp_keepalive_intvl=10
sysctl -w net.ipv4.tcp_keepalive_probes=6

echo "Network optimizations applied successfully."
