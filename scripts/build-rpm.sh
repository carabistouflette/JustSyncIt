#!/bin/bash

# JustSyncIt RPM Package Builder for RHEL/CentOS/Fedora
# This script creates an RPM package for JustSyncIt

set -e

# Configuration
VERSION="0.1.0"
PACKAGE_NAME="justsyncit"
MAINTAINER="JustSyncIt Team <team@justsyncit.com>"
DESCRIPTION="A modern, reliable backup solution built with Java 21+ featuring BLAKE3 hashing"
HOMEPAGE="https://github.com/carabistouflette/justsyncit"
LICENSE="GPL-3.0"
GROUP="Applications/System"
REQUIRES="java >= 1:21"

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
    for tool in rpmbuild; do
        if ! command -v $tool &> /dev/null; then
            print_error "$tool is not installed. Please install it with:"
            echo "  sudo yum install rpm-build"
            echo "  # or"
            echo "  sudo dnf install rpm-build"
            exit 1
        fi
    done
    
    print_info "Build requirements satisfied"
}

# Create RPM build structure
create_rpm_structure() {
    print_info "Creating RPM build structure..."
    
    # Clean previous builds
    rm -rf rpm-build
    mkdir -p rpm-build/{BUILD,RPMS,SOURCES,SPECS,SRPMS}
    
    print_info "RPM build structure created"
}

# Build JustSyncIt
build_justsyncit() {
    print_info "Building JustSyncIt..."
    
    # Build application
    ./gradlew clean releaseBuild --no-daemon
    
    print_info "JustSyncIt built successfully"
}

# Create source tarball
create_source_tarball() {
    print_info "Creating source tarball..."
    
    # Create source directory
    local source_dir="rpm-build/SOURCES/${PACKAGE_NAME}-${VERSION}"
    mkdir -p "$source_dir"
    
    # Copy application files
    cp -r build/distributions/justsyncit-$VERSION/* "$source_dir/"
    
    # Copy additional files
    cp README.md "$source_dir/"
    cp CHANGELOG.md "$source_dir/"
    cp LICENSE "$source_dir/"
    
    # Create tarball
    cd rpm-build/SOURCES
    tar -czf "${PACKAGE_NAME}-${VERSION}.tar.gz" "${PACKAGE_NAME}-${VERSION}"
    cd - > /dev/null
    
    print_info "Source tarball created"
}

# Create spec file
create_spec_file() {
    print_info "Creating RPM spec file..."
    
    cat > rpm-build/SPECS/${PACKAGE_NAME}.spec << EOF
Name:           ${PACKAGE_NAME}
Version:        ${VERSION}
Release:        1%{?dist}
Summary:        ${DESCRIPTION}

License:        ${LICENSE}
URL:            ${HOMEPAGE}
Source0:        %{name}-%{version}.tar.gz

Group:          ${GROUP}
Requires:       ${REQUIRES}
BuildArch:      noarch

%description
JustSyncIt is a comprehensive backup solution designed to provide reliable and 
efficient data synchronization and backup capabilities. Built with modern Java 
practices and a focus on code quality, maintainability, and security.

Features:
* BLAKE3-based content-addressable storage
* Efficient file chunking with deduplication
* Network transfer capabilities with TCP and QUIC protocols
* Server mode for centralized backup storage
* Snapshot management with point-in-time recovery
* Integrity verification using BLAKE3 cryptographic hashes
* Performance optimizations with SIMD acceleration
* Multi-threaded processing for maximum throughput

%prep
%autosetup

%build
# No build step needed - we're using pre-built JAR

%install
rm -rf %{buildroot}

# Create directories
mkdir -p %{buildroot}%{_prefix}/lib/%{name}/{bin,lib,data,logs,config}
mkdir -p %{buildroot}%{_bindir}
mkdir -p %{buildroot}%{_unitdir}
mkdir -p %{buildroot}%{_sysconfdir}/%{name}
mkdir -p %{buildroot}%{_localstatedir}/lib/%{name}
mkdir -p %{buildroot}%{_localstatedir}/log/%{name}
mkdir -p %{buildroot}%{_datadir}/doc/%{name}
mkdir -p %{buildroot}%{_mandir}/man1

# Copy application files
cp -r * %{buildroot}%{_prefix}/lib/%{name}/

# Create symlink
ln -sf ../lib/%{name}/bin/start.sh %{buildroot}%{_bindir}/%{name}

# Copy documentation
cp README.md CHANGELOG.md LICENSE %{buildroot}%{_datadir}/doc/%{name}/

# Create man page
cat > %{buildroot}%{_mandir}/man1/%{name}.1 << 'EOMAN'
.TH JUSTSYNCIT 1 "$(date +%Y-%m-%d)" "JustSyncIt %{version}" "User Commands"
.SH NAME
justsyncit \\- A modern, reliable backup solution
.SH SYNOPSIS
.B justsyncit
[\\fIoptions\\fR] [\\fIcommand\\fR] [\\fIarguments\\fR]
.SH DESCRIPTION
JustSyncIt is a comprehensive backup solution designed to provide reliable and efficient data synchronization and backup capabilities. Built with modern Java practices and a focus on code quality, maintainability, and security.
.SH OPTIONS
.TP
\\fB\\-\\-help\\fR
Display help information
.TP
\\fB\\-\\-version\\fR
Display version information
.TP
\\fB\\-\\-verbose\\fR
Enable verbose logging
.TP
\\fB\\-\\-quiet\\fR
Enable quiet logging
.SH COMMANDS
.TP
\\fBbackup\\fR \\fIsource\\fR
Create a backup of specified source directory
.TP
\\fBrestore\\fR \\fIsnapshot-id\\fR \\fItarget\\fR
Restore a snapshot to specified target directory
.TP
\\fBsnapshots\\fR \\fIsubcommand\\fR
Manage snapshots (list, info, verify, delete)
.TP
\\fBserver\\fR \\fIsubcommand\\fR
Manage backup server (start, stop, status)
.TP
\\fBhash\\fR \\fIfile\\fR
Generate BLAKE3 hash for a file
.TP
\\fBverify\\fR \\fIfile\\fR \\fIhash\\fR
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
JustSyncIt documentation is available at: \\fIhttps://github.com/carabistouflette/justsyncit\\fR
.SH AUTHOR
JustSyncIt Team
.SH LICENSE
This software is released under the GNU General Public License v3.0.
EOM

# Compress man page
gzip -9 %{buildroot}%{_mandir}/man1/%{name}.1

# Create systemd service
cat > %{buildroot}%{_unitdir}/%{name}.service << 'EOSERVICE'
[Unit]
Description=JustSyncIt Backup Service
Documentation=https://github.com/carabistouflette/justsyncit
After=network.target

[Service]
Type=simple
User=justsyncit
Group=justsyncit
WorkingDirectory=%{_prefix}/lib/%{name}
ExecStart=%{_prefix}/lib/%{name}/bin/start.sh server start --daemon
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
ReadWritePaths=%{_localstatedir}/lib/%{name} %{_localstatedir}/log/%{name}

# Environment
Environment=JAVA_OPTS=-Xmx512m -Xms256m -XX:+UseG1GC

[Install]
WantedBy=multi-user.target
EOSERVICE

%files
%{_prefix}/lib/%{name}
%{_bindir}/%{name}
%{_unitdir}/%{name}.service
%{_datadir}/doc/%{name}
%{_mandir}/man1/%{name}.1.gz
%config(noreplace) %{_sysconfdir}/%{name}/default.properties
%dir %attr(0755,justsyncit,justsyncit) %{_localstatedir}/lib/%{name}
%dir %attr(0755,justsyncit,justsyncit) %{_localstatedir}/log/%{name}

%post
# Create system user if it doesn't exist
if ! id justsyncit &>/dev/null; then
    useradd -r -s /bin/false -d %{_localstatedir}/lib/%{name} justsyncit 2>/dev/null || :
fi

# Create configuration if it doesn't exist
if [ ! -f %{_sysconfdir}/%{name}/default.properties ]; then
    cat > %{_sysconfdir}/%{name}/default.properties << 'EOCONF'
# JustSyncIt Default Configuration

# Storage settings
storage.directory=%{_localstatedir}/lib/%{name}
storage.chunkSize=1048576

# Network settings
network.port=8080
network.host=0.0.0.0
network.transport=TCP

# Logging settings
logging.level=INFO
logging.file=%{_localstatedir}/log/%{name}/%{name}.log

# Performance settings
performance.threads=4
performance.memoryLimit=512MB
EOCONF
fi

# Set ownership
chown -R justsyncit:justsyncit %{_localstatedir}/lib/%{name} %{_localstatedir}/log/%{name} %{_sysconfdir}/%{name}

# Reload systemd and enable service
systemctl daemon-reload
systemctl enable %{name}

echo "JustSyncIt has been installed successfully!"
echo "Start the service with: sudo systemctl start %{name}"
echo "Check status with: sudo systemctl status %{name}"
echo "View logs with: sudo journalctl -u %{name} -f"

%preun
if [ \$1 -eq 0 ]; then
    # Package is being removed
    systemctl --no-reload disable %{name} || :
    systemctl stop %{name} || :
fi

%postun
if [ \$1 -eq 1 ]; then
    # Package is being upgraded
    systemctl daemon-reload
    systemctl try-restart %{name} || :
fi

%changelog
* $(date +'%a %b %d %Y') JustSyncIt Team <team@justsyncit.com> - ${VERSION}-1
- Initial RPM release of JustSyncIt ${VERSION}
- Modern backup solution with BLAKE3 hashing
- Content-addressable storage with deduplication
- Network transfer capabilities with TCP and QUIC
- Server mode for centralized backup storage
- Snapshot management with point-in-time recovery
EOF
    
    print_info "RPM spec file created"
}

# Build RPM package
build_rpm() {
    print_info "Building RPM package..."
    
    # Build the RPM
    rpmbuild -ba \
        --define "_topdir $(pwd)/rpm-build" \
        rpm-build/SPECS/${PACKAGE_NAME}.spec
    
    # Find the built RPM
    local rpm_file=$(find rpm-build/RPMS -name "${PACKAGE_NAME}-${VERSION}-*.noarch.rpm" | head -n1)
    
    if [[ -n "$rpm_file" ]]; then
        cp "$rpm_file" ./
        print_info "RPM package created: $(basename $rpm_file)"
        
        # Show package info
        rpm -qip "$(basename $rpm_file)"
    else
        print_error "RPM package not found"
        exit 1
    fi
}

# Main function
main() {
    print_info "Building JustSyncIt RPM package..."
    echo
    
    check_requirements
    create_rpm_structure
    build_justsyncit
    create_source_tarball
    create_spec_file
    build_rpm
    
    echo
    print_info "RPM package build completed successfully!"
}

# Run main function
main "$@"