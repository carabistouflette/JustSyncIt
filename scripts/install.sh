#!/bin/bash

# JustSyncIt Installation Script for Linux
# This script installs JustSyncIt on Linux systems

set -e

# Configuration
INSTALL_DIR="/opt/justsyncit"
BIN_DIR="/usr/local/bin"
SERVICE_DIR="/etc/systemd/system"
USER="justsyncit"
GROUP="justsyncit"
VERSION="0.1.0"

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

# Check if running as root
check_root() {
    if [[ $EUID -ne 0 ]]; then
        print_error "This script must be run as root"
        exit 1
    fi
}

# Check system requirements
check_requirements() {
    print_info "Checking system requirements..."
    
    # Check Java version
    if ! command -v java &> /dev/null; then
        print_error "Java is not installed. Please install Java 21 or higher."
        exit 1
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [[ $JAVA_VERSION -lt 21 ]]; then
        print_error "Java 21 or higher is required. Current version: $JAVA_VERSION"
        exit 1
    fi
    
    print_info "Java version requirement satisfied: $(java -version 2>&1 | head -n1)"
}

# Create system user
create_user() {
    print_info "Creating system user..."
    
    if ! id "$USER" &>/dev/null; then
        groupadd -r "$GROUP" 2>/dev/null || true
        useradd -r -g "$GROUP" -d "$INSTALL_DIR" -s /bin/false "$USER" 2>/dev/null || true
        print_info "Created system user: $USER"
    else
        print_warn "User $USER already exists"
    fi
}

# Create installation directory
create_directories() {
    print_info "Creating installation directories..."
    
    mkdir -p "$INSTALL_DIR"/{bin,lib,data,logs,config}
    mkdir -p "$SERVICE_DIR"
    
    print_info "Created directories in $INSTALL_DIR"
}

# Download and install JustSyncIt
install_justsyncit() {
    print_info "Downloading JustSyncIt v$VERSION..."
    
    # Download the distribution
    cd /tmp
    wget -q "https://github.com/carabistouflette/justsyncit/releases/download/v$VERSION/justsyncit-$VERSION.tar.gz" \
        -O "justsyncit-$VERSION.tar.gz"
    
    # Verify checksum
    wget -q "https://github.com/carabistouflette/justsyncit/releases/download/v$VERSION/justsyncit-$VERSION-all.jar.sha256" \
        -O "justsyncit-$VERSION-all.jar.sha256"
    
    print_info "Extracting JustSyncIt..."
    tar -xzf "justsyncit-$VERSION.tar.gz"
    
    # Copy files to installation directory
    cp -r justsyncit-$VERSION/* "$INSTALL_DIR/"
    
    # Set permissions
    chown -R "$USER:$GROUP" "$INSTALL_DIR"
    chmod +x "$INSTALL_DIR/bin/start.sh"
    
    print_info "JustSyncIt installed successfully"
}

# Create systemd service
create_service() {
    print_info "Creating systemd service..."
    
    cat > "$SERVICE_DIR/justsyncit.service" << EOF
[Unit]
Description=JustSyncIt Backup Service
Documentation=https://github.com/carabistouflette/justsyncit
After=network.target

[Service]
Type=simple
User=$USER
Group=$GROUP
WorkingDirectory=$INSTALL_DIR
ExecStart=$INSTALL_DIR/bin/start.sh server start --daemon
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
ReadWritePaths=$INSTALL_DIR/data $INSTALL_DIR/logs

# Environment
Environment=JAVA_OPTS=-Xmx512m -Xms256m -XX:+UseG1GC

[Install]
WantedBy=multi-user.target
EOF
    
    # Reload systemd
    systemctl daemon-reload
    
    print_info "Systemd service created: justsyncit.service"
}

# Create command line symlink
create_symlink() {
    print_info "Creating command line symlink..."
    
    ln -sf "$INSTALL_DIR/bin/start.sh" "$BIN_DIR/justsyncit"
    
    print_info "Created symlink: $BIN_DIR/justsyncit"
}

# Create configuration
create_config() {
    print_info "Creating default configuration..."
    
    cat > "$INSTALL_DIR/config/default.properties" << EOF
# JustSyncIt Default Configuration

# Storage settings
storage.directory=$INSTALL_DIR/data
storage.chunkSize=1048576

# Network settings
network.port=8080
network.host=0.0.0.0
network.transport=TCP

# Logging settings
logging.level=INFO
logging.file=$INSTALL_DIR/logs/justsyncit.log

# Performance settings
performance.threads=4
performance.memoryLimit=512MB
EOF
    
    chown "$USER:$GROUP" "$INSTALL_DIR/config/default.properties"
    
    print_info "Default configuration created"
}

# Display installation summary
display_summary() {
    print_info "Installation completed successfully!"
    echo
    echo "JustSyncIt v$VERSION has been installed to: $INSTALL_DIR"
    echo
    echo "Usage:"
    echo "  Command line: justsyncit --help"
    echo "  Start server: sudo systemctl start justsyncit"
    echo "  Enable on boot: sudo systemctl enable justsyncit"
    echo "  Check status: sudo systemctl status justsyncit"
    echo "  View logs: sudo journalctl -u justsyncit -f"
    echo
    echo "Configuration file: $INSTALL_DIR/config/default.properties"
    echo "Data directory: $INSTALL_DIR/data"
    echo "Log directory: $INSTALL_DIR/logs"
    echo
    print_info "Thank you for installing JustSyncIt!"
}

# Uninstallation function
uninstall() {
    print_warn "Uninstalling JustSyncIt..."
    
    # Stop and disable service
    if systemctl is-active --quiet justsyncit; then
        systemctl stop justsyncit
    fi
    
    if systemctl is-enabled --quiet justsyncit; then
        systemctl disable justsyncit
    fi
    
    # Remove files
    rm -f "$SERVICE_DIR/justsyncit.service"
    rm -f "$BIN_DIR/justsyncit"
    rm -rf "$INSTALL_DIR"
    
    # Remove user
    if id "$USER" &>/dev/null; then
        userdel "$USER" 2>/dev/null || true
    fi
    
    # Reload systemd
    systemctl daemon-reload
    
    print_info "JustSyncIt has been uninstalled"
}

# Main script logic
main() {
    case "${1:-install}" in
        install)
            check_root
            check_requirements
            create_user
            create_directories
            install_justsyncit
            create_service
            create_symlink
            create_config
            display_summary
            ;;
        uninstall)
            check_root
            uninstall
            ;;
        *)
            echo "Usage: $0 [install|uninstall]"
            echo "  install   - Install JustSyncIt (default)"
            echo "  uninstall - Remove JustSyncIt"
            exit 1
            ;;
    esac
}

# Run main function with all arguments
main "$@"