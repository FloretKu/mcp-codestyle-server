#!/bin/bash
# Update codestyle-server.jar to latest version

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VERSION="2.1.0"
JAR_FILE="$SCRIPT_DIR/codestyle-server.jar"
DOWNLOAD_URL="https://github.com/itxaiohanglover/mcp-codestyle-server/releases/download/v${VERSION}/codestyle-server.jar"

echo "🔄 Codestyle Skill Updater"
echo ""

# Check current version
if [ -f "$JAR_FILE" ]; then
    CURRENT_SIZE=$(du -h "$JAR_FILE" | cut -f1)
    echo "📦 Current version: ${CURRENT_SIZE}"
else
    echo "⚠️  No existing installation found"
fi

echo "📦 Updating to v${VERSION}..."
echo "   URL: $DOWNLOAD_URL"
echo ""

# Backup old version
if [ -f "$JAR_FILE" ]; then
    mv "$JAR_FILE" "$JAR_FILE.backup"
    echo "✓ Backed up old version"
fi

# Download new version
if command -v curl &> /dev/null; then
    curl -L --progress-bar -o "$JAR_FILE" "$DOWNLOAD_URL" || {
        echo "❌ Download failed"
        # Restore backup
        if [ -f "$JAR_FILE.backup" ]; then
            mv "$JAR_FILE.backup" "$JAR_FILE"
            echo "✓ Restored backup"
        fi
        exit 1
    }
elif command -v wget &> /dev/null; then
    wget --show-progress -O "$JAR_FILE" "$DOWNLOAD_URL" || {
        echo "❌ Download failed"
        # Restore backup
        if [ -f "$JAR_FILE.backup" ]; then
            mv "$JAR_FILE.backup" "$JAR_FILE"
            echo "✓ Restored backup"
        fi
        exit 1
    }
else
    echo "❌ Error: curl or wget is required"
    # Restore backup
    if [ -f "$JAR_FILE.backup" ]; then
        mv "$JAR_FILE.backup" "$JAR_FILE"
        echo "✓ Restored backup"
    fi
    exit 1
fi

# Verify and cleanup
if [ -f "$JAR_FILE" ]; then
    rm -f "$JAR_FILE.backup"
    FILE_SIZE=$(du -h "$JAR_FILE" | cut -f1)
    echo ""
    echo "✓ Updated to v${VERSION} (${FILE_SIZE})"
    echo "✓ Update complete"
else
    echo "❌ Update failed"
    # Restore backup
    if [ -f "$JAR_FILE.backup" ]; then
        mv "$JAR_FILE.backup" "$JAR_FILE"
        echo "✓ Restored backup"
    fi
    exit 1
fi

