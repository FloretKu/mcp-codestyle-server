#!/bin/bash
# Codestyle Skill Installation Script
# Auto-downloads codestyle-server.jar on first use

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VERSION="2.1.0"
JAR_FILE="$SCRIPT_DIR/codestyle-server.jar"
DOWNLOAD_URL="https://github.com/itxaiohanglover/mcp-codestyle-server/releases/download/v${VERSION}/codestyle-server.jar"

echo "🚀 Codestyle Skill Installer v${VERSION}"
echo ""

# Check if JAR exists
if [ -f "$JAR_FILE" ]; then
    FILE_SIZE=$(du -h "$JAR_FILE" 2>/dev/null | cut -f1 || echo "unknown")
    echo "✓ codestyle-server.jar already exists (${FILE_SIZE})"
    echo "✓ Installation complete"
    exit 0
fi

echo "📦 Downloading codestyle-server v${VERSION}..."
echo "   URL: $DOWNLOAD_URL"
echo ""

# Download with progress
if command -v curl &> /dev/null; then
    curl -L --progress-bar -o "$JAR_FILE" "$DOWNLOAD_URL" || {
        echo "❌ Download failed"
        rm -f "$JAR_FILE"
        exit 1
    }
elif command -v wget &> /dev/null; then
    wget --show-progress -O "$JAR_FILE" "$DOWNLOAD_URL" || {
        echo "❌ Download failed"
        rm -f "$JAR_FILE"
        exit 1
    }
else
    echo "❌ Error: curl or wget is required"
    echo "   Please install curl or wget and try again"
    exit 1
fi

# Verify download
if [ -f "$JAR_FILE" ]; then
    FILE_SIZE=$(du -h "$JAR_FILE" | cut -f1)
    echo ""
    echo "✓ Downloaded successfully (${FILE_SIZE})"
    echo "✓ Installation complete"
    echo ""
    echo "💡 Usage:"
    echo "   $SCRIPT_DIR/codestyle search \"CRUD\""
    echo "   $SCRIPT_DIR/codestyle get \"path\""
else
    echo "❌ Download failed"
    exit 1
fi

