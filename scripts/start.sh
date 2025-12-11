#!/bin/bash
# JustSyncIt Startup Script

# Set default JVM options
JVM_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC"

# Override with environment variable if set
if [ -n "$JUSTSYNCIT_JVM_OPTS" ]; then
    JVM_OPTS="$JUSTSYNCIT_JVM_OPTS"
fi

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Find the JAR file - check multiple locations for robustness
JAR_FILE=""

# 1. Standard layout (../lib)
if [ -d "$SCRIPT_DIR/../lib" ]; then
    JAR_FILE=$(find "$SCRIPT_DIR/../lib" -name "justsyncit-*-all.jar" | head -n 1)
fi

# 2. Local/Flat layout (./lib)
if [ -z "$JAR_FILE" ] && [ -d "$SCRIPT_DIR/lib" ]; then
    JAR_FILE=$(find "$SCRIPT_DIR/lib" -name "justsyncit-*-all.jar" | head -n 1)
fi

# 3. Same directory (.)
if [ -z "$JAR_FILE" ]; then
    JAR_FILE=$(find "$SCRIPT_DIR" -maxdepth 1 -name "justsyncit-*-all.jar" | head -n 1)
fi

if [ -z "$JAR_FILE" ]; then
    echo "Error: JustSyncIt JAR file (justsyncit-*-all.jar) not found in:"
    echo "  - $SCRIPT_DIR/../lib"
    echo "  - $SCRIPT_DIR/lib"
    echo "  - $SCRIPT_DIR"
    exit 1
fi

echo "Starting JustSyncIt..."
echo "JVM Options: $JVM_OPTS"
echo "JAR File: $JAR_FILE"

# Run the application
exec java $JVM_OPTS -jar "$JAR_FILE" "$@"
