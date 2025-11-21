#!/bin/bash

# JustSyncIt DEB Package Builder for Debian/Ubuntu
# This script creates a DEB package for JustSyncIt

set -e

# Configuration
VERSION="0.1.0"
PACKAGE_NAME="justsyncit"
MAINTAINER="JustSyncIt Team <team@justsyncit.com>"
DESCRIPTION="A modern, reliable backup solution built with Java 21+ featuring BLAKE3 hashing"
HOMEPAGE="https://github.com/carabistouflette/justsyncit"
LICENSE="MIT"
SECTION="utils"
PRIORITY="optional"
DEPENDS="openjdk-21-jre | default-jre (>= 2:21)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Helper functions
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check requirements
check_requirements() {
    print_info "Checking build requirements..."
    
    # Check for required tools
    for tool in dpkg-deb fakeroot; do
        if ! command -v $tool &> /dev/null; then
            print_error "$tool is not installed. Please install it with:"
            echo "  sudo apt-get install $tool"
            exit 1
        fi
    done
    
    print_info "Build requirements satisfied"
}

# Create package structure
create_package_structure() {
    print_info "Creating package structure..."
    
    # Clean previous builds
    rm -rf deb-build
    mkdir -p deb-build/DEBIAN
    mkdir -p deb-build/usr/local/lib/justsyncit/{bin,lib,data,logs,config}
    mkdir -p deb-build/usr/local/bin
    mkdir -p deb-build/etc/systemd/system
    mkdir -p deb-build/usr/share/doc/justsyncit
    mkdir -p deb-build/usr/share/man/man1
    
    print_info "Package structure created"
}

# Build JustSyncIt
build_justsyncit() {
    print_info "Building JustSyncIt..."
    
    # Build the application
    ./gradlew clean releaseBuild --no-daemon
    
    print_info "JustSyncIt built successfully"
}

# Copy files to package
copy_files() {
    print_info "Copying files to package..."
    
    # Copy application files
    cp -r build/distributions/justsyncit-$VERSION/* deb-build/usr/local/lib/justsyncit/
    
    # Create symlink
    ln -sf ../lib/justsyncit/bin/start.sh deb-build/usr/local/bin/justsyncit
    
    # Copy documentation
    cp README.md deb-build/usr/share/doc/justsyncit/
    cp CHANGELOG.md deb-build/usr/share/doc/justsyncit/
    cp LICENSE deb-build/usr/share/doc/justsyncit/
    
    # Create man page
    cat > deb-build/usr/share/man/man1/justsyncit.1 << EOF
.TH JUSTSYNCIT 1 "$(date +%Y-%m-%d)" "JustSyncIt $VERSION" "User Commands"
.SH NAME
justsyncit \- A modern, reliable backup solution
.SH SYNOPSIS
.B justsyncit
[\fIoptions\fR] [\fIcommand\fR] [\fIarguments\fR]
.SH DESCRIPTION
JustSyncIt is a comprehensive backup solution designed to provide reliable and efficient data synchronization and backup capabilities. Built with modern Java practices and a focus on code quality, maintainability, and security.
.SH OPTIONS
.TP
\fB\-\-help\fR
Display help information
.TP
\fB\-\-version\fR
Display version information
.TP
\fB\-\-verbose\fR
Enable verbose logging
.TP
\fB\-\-quiet\fR
Enable quiet logging
.SH COMMANDS
.TP
\fBbackup\fR \fIsource\fR
Create a backup of the specified source directory
.TP
\fBrestore\fR \fIsnapshot-id\fR \fItarget\fR
Restore a snapshot to the specified target directory
.TP
\fBsnapshots\fR \fIsubcommand\fR
Manage snapshots (list, info, verify, delete)
.TP
\fBserver\fR \fIsubcommand\fR
Manage backup server (start, stop, status)
.TP
\fBhash\fR \fIfile\fR
Generate BLAKE3 hash for a file
.TP
\fBverify\fR \fIfile\fR \fIhash\fR
Verify file integrity using BLAKE3 hash
.SH EXAMPLES
Create a backup:
.PP
.RS 4n
justsyncit backup /home/user/documents
.RE
.PP
List snapshots:
.PP
.RS 4n
justsyncit snapshots list
.RE
.PP
Restore a snapshot:
.PP
.RS 4n
justsyncit restore abc123 /home/user/restore
.RE
.SH FILES
.TP
.I /etc/justsyncit/default.properties
Default configuration file
.TP
.I /var/lib/justsyncit/
Default data directory
.TP
.I /var/log/justsyncit/
Default log directory
.SH SEE ALSO
JustSyncIt documentation is available at: \fIhttps://github.com/carabistouflette/justsyncit\fR
.SH AUTHOR
JustSyncIt Team
.SH LICENSE
This software is released under the MIT License.
EOF
    
    # Compress man page
    gzip -9 deb-build/usr/share/man/man1/justsyncit.1
    
    print_info "Files copied to package"
}

# Create control file
create_control_file() {
    print_info "Creating control file..."
    
    cat > deb-build/DEBIAN/control << EOF
Package: $PACKAGE_NAME
Version: $VERSION
Section: $SECTION
Priority: $PRIORITY
Architecture: all
Depends: $DEPENDS
Maintainer: $MAINTAINER
Description: $DESCRIPTION
 JustSyncIt provides:
  * BLAKE3-based content-addressable storage
  * Efficient file chunking with deduplication
  * Network transfer capabilities with TCP and QUIC protocols
  * Server mode for centralized backup storage
  * Snapshot management with point-in-time recovery
  * Integrity verification using BLAKE3 cryptographic hashes
  * Performance optimizations with SIMD acceleration
  * Multi-threaded processing for maximum throughput
 .
 Homepage: $HOMEPAGE
License: $LICENSE
EOF
    
    print_info "Control file created"
}

# Create conffiles
create_conffiles() {
    print_info "Creating conffiles..."
    
    cat > deb-build/DEBIAN/conffiles << EOF
/etc/justsyncit/default.properties
EOF
    
    print_info "Conffiles created"
}

# Create systemd service
create_systemd_service() {
    print_info "Creating systemd service..."
    
    cat > deb-build/etc/systemd/system/justsyncit.service << EOF
[Unit]
Description=JustSyncIt Backup Service
Documentation=https://github.com/carabistouflette/justsyncit
After=network.target

[Service]
Type=simple
User=justsyncit
Group=justsyncit
WorkingDirectory=/usr/local/lib/justsyncit
ExecStart=/usr/local/lib/justsyncit/bin/start.sh server start --daemon
ExecReload=/bin/kill -HUP \$MAINPID
Restart=on-failure
RestartSec=5
StandardOutput=journal
StandardError=journal
SyslogIdentifier=justsyncit

# Security settings
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/var/lib/justsyncit /var/log/justsyncit

# Environment
Environment=JAVA_OPTS=-Xmx512m -Xms256m -XX:+UseG1GC

[Install]
WantedBy=multi-user.target
EOF
    
    print_info "Systemd service created"
}

# Create postinst script
create_postinst() {
    print_info "Creating postinst script..."
    
    cat > deb-build/DEBIAN/postinst << 'EOF'
#!/bin/bash
set -e

# Create system user if it doesn't exist
if ! id justsyncit &>/dev/null; then
    adduser --system --group --home /var/lib/justsyncit --shell /bin/false justsyncit
fi

# Create data and log directories
mkdir -p /var/lib/justsyncit /var/log/justsyncit /etc/justsyncit
chown -R justsyncit:justsyncit /var/lib/justsyncit /var/log/justsyncit /etc/justsyncit

# Create default configuration if it doesn't exist
if [ ! -f /etc/justsyncit/default.properties ]; then
    cat > /etc/justsyncit/default.properties << 'EOCONF'
# JustSyncIt Default Configuration

# Storage settings
storage.directory=/var/lib/justsyncit
storage.chunkSize=1048576

# Network settings
network.port=8080
network.host=0.0.0.0
network.transport=TCP

# Logging settings
logging.level=INFO
logging.file=/var/log/justsyncit/justsyncit.log

# Performance settings
performance.threads=4
performance.memoryLimit=512MB
EOCONF
    chown justsyncit:justsyncit /etc/justsyncit/default.properties
fi

# Reload systemd
systemctl daemon-reload

# Enable service (but don't start it)
systemctl enable justsyncit

echo "JustSyncIt has been installed successfully!"
echo "Start the service with: sudo systemctl start justsyncit"
echo "Check status with: sudo systemctl status justsyncit"
echo "View logs with: sudo journalctl -u justsyncit -f"
EOF
    
    chmod +x deb-build/DEBIAN/postinst
    
    print_info "Postinst script created"
}

# Create prerm script
create_prerm() {
    print_info "Creating prerm script..."
    
    cat > deb-build/DEBIAN/prerm << 'EOF'
#!/bin/bash
set -e

# Stop and disable service if it's running
if systemctl is-active --quiet justsyncit; then
    systemctl stop justsyncit
fi

if systemctl is-enabled --quiet justsyncit; then
    systemctl disable justsyncit
fi
EOF
    
    chmod +x deb-build/DEBIAN/prerm
    
    print_info "Prerm script created"
}

# Calculate installed size
calculate_size() {
    print_info "Calculating package size..."
    
    local size=$(du -s deb-build/usr deb-build/etc | awk '{sum += $1} END {print sum}')
    echo "Installed-Size: $size" >> deb-build/DEBIAN/control
    
    print_info "Package size calculated: $size KB"
}

# Build the package
build_package() {
    print_info "Building DEB package..."
    
    # Set permissions
    find deb-build -type d -exec chmod 755 {} \;
    find deb-build -type f -exec chmod 644 {} \;
    chmod +x deb-build/usr/local/lib/justsyncit/bin/start.sh
    chmod +x deb-build/usr/local/bin/justsyncit
    chmod +x deb-build/DEBIAN/postinst
    chmod +x deb-build/DEBIAN/prerm
    
    # Build the package
    local package_name="${PACKAGE_NAME}_${VERSION}_all.deb"
    dpkg-deb --build deb-build "$package_name"
    
    print_info "DEB package created: $package_name"
    
    # Show package info
    dpkg --info "$package_name"
}

# Main function
main() {
    print_info "Building JustSyncIt DEB package..."
    echo
    
    check_requirements
    create_package_structure
    build_justsyncit
    copy_files
    create_control_file
    create_conffiles
    create_systemd_service
    create_postinst
    create_prerm
    calculate_size
    build_package
    
    echo
    print_info "DEB package build completed successfully!"
}

# Run main function
main "$@"