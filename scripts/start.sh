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
LIB_DIR="$SCRIPT_DIR/../lib"

# Find the JAR file
JAR_FILE=$(find "$LIB_DIR" -name "justsyncit-*-all.jar" | head -n 1)

if [ -z "$JAR_FILE" ]; then
    echo "Error: JustSyncIt JAR file not found in $LIB_DIR"
    exit 1
fi

echo "Starting JustSyncIt..."
echo "JVM Options: $JVM_OPTS"
echo "JAR File: $JAR_FILE"

# Run the application
exec java $JVM_OPTS -jar "$JAR_FILE" "$@"
