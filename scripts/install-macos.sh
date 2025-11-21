#!/bin/bash

# JustSyncIt Installation Script for macOS
# This script installs JustSyncIt on macOS systems

set -e

# Configuration
INSTALL_DIR="/usr/local/lib/justsyncit"
BIN_DIR="/usr/local/bin"
LAUNCHD_DIR="$HOME/Library/LaunchAgents"
VERSION="0.1.0"
SERVICE_NAME="com.justsyncit.agent"

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

# Check if Homebrew is installed
check_homebrew() {
    if ! command -v brew &> /dev/null; then
        print_error "Homebrew is not installed. Please install Homebrew first:"
        echo "  /bin/bash -c \"\$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\""
        exit 1
    fi
}

# Check system requirements
check_requirements() {
    print_info "Checking system requirements..."
    
    # Check if running on macOS
    if [[ "$(uname)" != "Darwin" ]]; then
        print_error "This script is for macOS only"
        exit 1
    fi
    
    # Check Java version
    if ! command -v java &> /dev/null; then
        print_info "Java not found. Installing Java 21 via Homebrew..."
        brew install openjdk@21
        
        # Set up Java environment
        echo 'export PATH="/usr/local/opt/openjdk@21/bin:$PATH"' >> ~/.zshrc
        echo 'export JAVA_HOME="/usr/local/opt/openjdk@21"' >> ~/.zshrc
        
        # For bash users
        if [[ -f ~/.bash_profile ]]; then
            echo 'export PATH="/usr/local/opt/openjdk@21/bin:$PATH"' >> ~/.bash_profile
            echo 'export JAVA_HOME="/usr/local/opt/openjdk@21"' >> ~/.bash_profile
        fi
        
        export PATH="/usr/local/opt/openjdk@21/bin:$PATH"
        export JAVA_HOME="/usr/local/opt/openjdk@21"
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [[ $JAVA_VERSION -lt 21 ]]; then
        print_error "Java 21 or higher is required. Current version: $JAVA_VERSION"
        exit 1
    fi
    
    print_info "Java version requirement satisfied: $(java -version 2>&1 | head -n1)"
}

# Create installation directory
create_directories() {
    print_info "Creating installation directories..."
    
    # Use sudo for system directories
    sudo mkdir -p "$INSTALL_DIR"/{bin,lib,data,logs,config}
    sudo mkdir -p "$LAUNCHD_DIR"
    
    print_info "Created directories in $INSTALL_DIR"
}

# Download and install JustSyncIt
install_justsyncit() {
    print_info "Downloading JustSyncIt v$VERSION..."
    
    # Download the distribution
    cd /tmp
    curl -L -o "justsyncit-$VERSION.tar.gz" \
        "https://github.com/carabistouflette/justsyncit/releases/download/v$VERSION/justsyncit-$VERSION.tar.gz"
    
    # Download checksum
    curl -L -o "justsyncit-$VERSION-all.jar.sha256" \
        "https://github.com/carabistouflette/justsyncit/releases/download/v$VERSION/justsyncit-$VERSION-all.jar.sha256"
    
    print_info "Extracting JustSyncIt..."
    tar -xzf "justsyncit-$VERSION.tar.gz"
    
    # Copy files to installation directory
    sudo cp -r justsyncit-$VERSION/* "$INSTALL_DIR/"
    
    # Set permissions
    sudo chown -R "$(whoami):staff" "$INSTALL_DIR"
    sudo chmod +x "$INSTALL_DIR/bin/start.sh"
    
    print_info "JustSyncIt installed successfully"
}

# Create LaunchAgent for user service
create_launch_agent() {
    print_info "Creating LaunchAgent for user service..."
    
    cat > "$LAUNCHD_DIR/$SERVICE_NAME.plist" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>$SERVICE_NAME</string>
    <key>ProgramArguments</key>
    <array>
        <string>$INSTALL_DIR/bin/start.sh</string>
        <string>server</string>
        <string>start</string>
        <string>--daemon</string>
    </array>
    <key>WorkingDirectory</key>
    <string>$INSTALL_DIR</string>
    <key>RunAtLoad</key>
    <false/>
    <key>KeepAlive</key>
    <false/>
    <key>StandardOutPath</key>
    <string>$INSTALL_DIR/logs/justsyncit.log</string>
    <key>StandardErrorPath</key>
    <string>$INSTALL_DIR/logs/justsyncit.err</string>
    <key>EnvironmentVariables</key>
    <dict>
        <key>JAVA_OPTS</key>
        <string>-Xmx512m -Xms256m -XX:+UseG1GC</string>
        <key>JAVA_HOME</key>
        <string>$JAVA_HOME</string>
    </dict>
</dict>
</plist>
EOF
    
    # Load the LaunchAgent
    launchctl load "$LAUNCHD_DIR/$SERVICE_NAME.plist"
    
    print_info "LaunchAgent created: $SERVICE_NAME.plist"
}

# Create command line symlink
create_symlink() {
    print_info "Creating command line symlink..."
    
    # Remove existing symlink if it exists
    if [[ -L "$BIN_DIR/justsyncit" ]]; then
        rm "$BIN_DIR/justsyncit"
    fi
    
    ln -s "$INSTALL_DIR/bin/start.sh" "$BIN_DIR/justsyncit"
    
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
network.host=127.0.0.1
network.transport=TCP

# Logging settings
logging.level=INFO
logging.file=$INSTALL_DIR/logs/justsyncit.log

# Performance settings
performance.threads=4
performance.memoryLimit=512MB
EOF
    
    print_info "Default configuration created"
}

# Create macOS app bundle (optional)
create_app_bundle() {
    print_info "Creating macOS app bundle..."
    
    APP_DIR="/Applications/JustSyncIt.app"
    CONTENTS_DIR="$APP_DIR/Contents"
    MACOS_DIR="$CONTENTS_DIR/MacOS"
    RESOURCES_DIR="$CONTENTS_DIR/Resources"
    
    # Create app bundle structure
    sudo mkdir -p "$MACOS_DIR" "$RESOURCES_DIR"
    
    # Create Info.plist
    cat > "$CONTENTS_DIR/Info.plist" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDisplayName</key>
    <string>JustSyncIt</string>
    <key>CFBundleExecutable</key>
    <string>justsyncit</string>
    <key>CFBundleIconFile</key>
    <string>justsyncit.icns</string>
    <key>CFBundleIdentifier</key>
    <string>com.justsyncit.app</string>
    <key>CFBundleName</key>
    <string>JustSyncIt</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>$VERSION</string>
    <key>CFBundleVersion</key>
    <string>$VERSION</string>
    <key>LSMinimumSystemVersion</key>
    <string>10.15</string>
    <key>NSHighResolutionCapable</key>
    <true/>
</dict>
</plist>
EOF
    
    # Create executable script
    cat > "$MACOS_DIR/justsyncit" << EOF
#!/bin/bash
# JustSyncIt macOS app launcher

# Get the directory where this script is located
SCRIPT_DIR="\$(cd "\$(dirname "\${BASH_SOURCE[0]}")" && pwd)"
INSTALL_DIR="$INSTALL_DIR"

# Open Terminal and run JustSyncIt
osascript -e 'tell application "Terminal" to do script "'"$INSTALL_DIR"'/bin/start.sh --help"'
EOF
    
    # Make executable
    sudo chmod +x "$MACOS_DIR/justsyncit"
    
    # Set ownership
    sudo chown -R "$(whoami):staff" "$APP_DIR"
    
    print_info "macOS app bundle created: $APP_DIR"
}

# Display installation summary
display_summary() {
    print_info "Installation completed successfully!"
    echo
    echo "JustSyncIt v$VERSION has been installed to: $INSTALL_DIR"
    echo
    echo "Usage:"
    echo "  Command line: justsyncit --help"
    echo "  Start service: launchctl start $SERVICE_NAME"
    echo "  Stop service: launchctl stop $SERVICE_NAME"
    echo "  Check status: launchctl list | grep $SERVICE_NAME"
    echo "  View logs: tail -f $INSTALL_DIR/logs/justsyncit.log"
    echo
    echo "Configuration file: $INSTALL_DIR/config/default.properties"
    echo "Data directory: $INSTALL_DIR/data"
    echo "Log directory: $INSTALL_DIR/logs"
    echo
    if [[ -d "/Applications/JustSyncIt.app" ]]; then
        echo "App bundle: /Applications/JustSyncIt.app"
        echo
    fi
    print_info "Thank you for installing JustSyncIt!"
}

# Uninstallation function
uninstall() {
    print_warn "Uninstalling JustSyncIt..."
    
    # Stop and unload LaunchAgent
    launchctl list | grep "$SERVICE_NAME" > /dev/null 2>&1
    if [[ $? -eq 0 ]]; then
        launchctl stop "$SERVICE_NAME" 2>/dev/null || true
        launchctl unload "$LAUNCHD_DIR/$SERVICE_NAME.plist" 2>/dev/null || true
    fi
    
    # Remove files
    sudo rm -f "$LAUNCHD_DIR/$SERVICE_NAME.plist"
    sudo rm -f "$BIN_DIR/justsyncit"
    sudo rm -rf "$INSTALL_DIR"
    sudo rm -rf "/Applications/JustSyncIt.app"
    
    print_info "JustSyncIt has been uninstalled"
}

# Main script logic
main() {
    case "${1:-install}" in
        install)
            check_homebrew
            check_requirements
            create_directories
            install_justsyncit
            create_launch_agent
            create_symlink
            create_config
            create_app_bundle
            display_summary
            ;;
        uninstall)
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