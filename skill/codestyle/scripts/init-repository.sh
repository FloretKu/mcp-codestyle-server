#!/bin/bash
# Codestyle Repository Initialization Script
# Auto-clones codestyle-repository if local cache is empty

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_URL="https://github.com/itxaiohanglover/codestyle-repository.git"

# 简化路径处理 - 直接使用默认路径
CACHE_DIR="$HOME/.codestyle/cache/codestyle-cache"

echo ""
echo "🔍 Checking local repository..."
echo "   Path: $CACHE_DIR"
echo ""

# Check if cache directory exists
if [ ! -d "$CACHE_DIR" ]; then
    echo "📁 Creating cache directory..."
    mkdir -p "$CACHE_DIR"
    IS_EMPTY=1
else
    # Check if cache directory is empty (excluding lucene-index)
    IS_EMPTY=1
    for item in "$CACHE_DIR"/*; do
        if [ -e "$item" ] && [ "$(basename "$item")" != "lucene-index" ]; then
            IS_EMPTY=0
            break
        fi
    done
fi

if [ "$IS_EMPTY" -eq 0 ]; then
    echo "✓ Local repository already initialized"
    exit 0
fi

echo "📦 Local repository is empty"
echo "🚀 Cloning codestyle-repository..."
echo "   URL: $REPO_URL"
echo ""

# Check if git is available
if ! command -v git &> /dev/null; then
    echo "❌ Error: Git is not installed"
    echo ""
    echo "Please install Git:"
    echo "   macOS: brew install git"
    echo "   Ubuntu/Debian: sudo apt install git"
    echo "   CentOS/RHEL: sudo yum install git"
    echo ""
    echo "Alternative: Download templates manually from:"
    echo "   https://github.com/itxaiohanglover/codestyle-repository/archive/refs/heads/main.zip"
    echo ""
    echo "Extract to: $CACHE_DIR"
    exit 1
fi

# Clone repository to temp directory
TEMP_DIR=$(mktemp -d)
echo "Cloning to temporary directory..."
echo "   Temp: $TEMP_DIR"
git clone --depth 1 "$REPO_URL" "$TEMP_DIR"
CLONE_ERROR=$?
if [ $CLONE_ERROR -ne 0 ]; then
    echo "❌ Clone failed (Error code: $CLONE_ERROR)"
    echo ""
    echo "Please check:"
    echo "   1. Internet connection"
    echo "   2. Repository URL: $REPO_URL"
    echo "   3. Git configuration"
    echo "   4. Try manually: git clone $REPO_URL"
    rm -rf "$TEMP_DIR"
    exit 1
fi

# Verify clone success
if [ ! -d "$TEMP_DIR/.git" ]; then
    echo "❌ Clone incomplete (no .git directory found)"
    rm -rf "$TEMP_DIR"
    exit 1
fi

echo "✓ Clone successful"

# Copy templates to cache directory
echo ""
echo "📋 Copying templates to cache..."
echo "   From: $TEMP_DIR"
echo "   To:   $CACHE_DIR"

# Ensure cache directory exists
mkdir -p "$CACHE_DIR"

# Copy files
cp -r "$TEMP_DIR"/* "$CACHE_DIR/" 2>/dev/null
COPY_ERROR=$?
if [ $COPY_ERROR -ne 0 ]; then
    echo "❌ Copy failed (Error code: $COPY_ERROR)"
    echo ""
    echo "Diagnostics:"
    echo "   Source directory contents:"
    ls -la "$TEMP_DIR" 2>/dev/null
    echo ""
    echo "   Target directory: $CACHE_DIR"
    rm -rf "$TEMP_DIR"
    exit 1
fi

echo "✓ Copy successful"

# Clean up
rm -rf "$TEMP_DIR"

# Remove .git directory from cache
rm -rf "$CACHE_DIR/.git" 2>/dev/null || true

# Verify templates were copied
echo ""
echo "📊 Verifying templates..."
TEMPLATE_COUNT=0
for dir in "$CACHE_DIR"/*; do
    if [ -d "$dir" ] && [ "$(basename "$dir")" != "lucene-index" ]; then
        TEMPLATE_COUNT=$((TEMPLATE_COUNT + 1))
        echo "   ✓ $(basename "$dir")"
    fi
done

if [ $TEMPLATE_COUNT -eq 0 ]; then
    echo ""
    echo "❌ No templates found after copy"
    echo "   Repository may be empty or copy failed"
    echo ""
    echo "Manual fix:"
    echo "   1. Download: https://github.com/itxaiohanglover/codestyle-repository/archive/refs/heads/main.zip"
    echo "   2. Extract to: $CACHE_DIR"
    exit 1
fi

echo ""
echo "✓ Repository initialized successfully"
echo "   Found $TEMPLATE_COUNT template group(s)"
echo ""

exit 0
