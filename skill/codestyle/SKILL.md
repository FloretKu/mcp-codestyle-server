---
name: codestyle
description: Code template search and generation tool. Auto-initializes on first use with minimal user interaction.
---

# Codestyle

Code template retrieval and generation via jar-based CLI tool.

## IMPORTANT: Command Execution

### Windows

**Method 1: Direct execution (Recommended for Claude)**
```bash
"<SKILL_DIR>\scripts\codestyle.bat" search "keyword"
```

**Method 2: With cmd.exe (For manual use)**
```bash
cmd.exe /c "<SKILL_DIR>\scripts\codestyle.bat search \"keyword\""
```

**Example:**
```bash
# Claude should use this format
"C:\Users\artboy\.claude\skills\codestyle\scripts\codestyle.bat" search "CRUD"

# Or this format
C:\Users\artboy\.claude\skills\codestyle\scripts\codestyle.bat search CRUD
```

### Linux/macOS
```bash
bash <SKILL_DIR>/scripts/codestyle search "keyword"
```

### Important Notes

- **For Claude/AI**: Use direct path without `cmd.exe /c` to avoid output buffering
- **For manual use**: Both methods work, but direct execution is faster
- **Quotes**: Use quotes around paths with spaces, keywords usually don't need quotes

## Workflow

### First-Time Use (Automatic)

When user first runs a command, the script will:
1. Detect no `.initialized` marker
2. Show: `[Codestyle] 首次使用，正在初始化...`
3. Auto-download JAR (if needed)
4. Auto-clone repository (silent)
5. Create `.initialized` marker
6. Execute the original command

**No user confirmation needed** - fully automatic.

### Subsequent Use

Direct execution, no initialization overhead.

## Commands

### Search templates

**Windows (for Claude):**
```bash
"<SKILL_DIR>\scripts\codestyle.bat" search "keyword"
```

**Linux/macOS:**
```bash
bash <SKILL_DIR>/scripts/codestyle search "keyword"
```

**Examples:**
```bash
# Search CRUD
"C:\Users\artboy\.claude\skills\codestyle\scripts\codestyle.bat" search "CRUD"

# Search login  
"C:\Users\artboy\.claude\skills\codestyle\scripts\codestyle.bat" search "login"

# Search by group
"C:\Users\artboy\.claude\skills\codestyle\scripts\codestyle.bat" search "continew/crud-plus"
```

### Get template

**Windows (for Claude):**
```bash
"<SKILL_DIR>\scripts\codestyle.bat" get "path"
```

**Linux/macOS:**
```bash
bash <SKILL_DIR>/scripts/codestyle get "path"
```

**Example:**
```bash
"C:\Users\artboy\.claude\skills\codestyle\scripts\codestyle.bat" get "continew/crud-plus/1.0.0/src/main/java/controller/Controller.ftl"
```

## Search Strategy

| User Request | Search Keywords |
|--------------|----------------|
| "create CRUD" | `CRUD` → `controller` → `continew/crud-plus` |
| "create login" | `login` → `auth` → `user` |
| "generate export" | `export` → `excel` → `download` |

JAR provides intelligent suggestions if no match found.

## Configuration

Default config (`scripts/cfg.json`):
```json
{
  "repository": {
    "local-path": "~/.codestyle/cache",
    "remote": {
      "enabled": false
    }
  }
}
```

## Troubleshooting

### Command not found or timeout

**Problem**: Command times out or shows no output when using `cmd.exe /c`

**Solution**: Use direct path execution instead:
```bash
# Instead of this (may timeout in Claude)
cmd.exe /c "C:\Users\artboy\.claude\skills\codestyle\scripts\codestyle.bat search \"CRUD\""

# Use this (works in Claude)
"C:\Users\artboy\.claude\skills\codestyle\scripts\codestyle.bat" search "CRUD"
```

**Reason**: `cmd.exe /c` causes output buffering in bash environments, leading to timeouts.

### Repository clone fails

**Manual fix:**
1. Download: https://github.com/itxaiohanglover/codestyle-repository/archive/refs/heads/main.zip
2. Extract to: `~/.codestyle/cache/codestyle-cache`
3. Re-run command

### Java not found

Install Java 17+:
- Windows: https://adoptium.net/
- macOS: `brew install openjdk@17`
- Linux: `sudo apt install openjdk-17-jdk`

### Git not found

Install Git:
- Windows: https://git-scm.com/download/win
- macOS: `brew install git`
- Linux: `sudo apt install git`

## Reference Files

| File | Purpose |
|------|---------|
| [references/config.md](references/config.md) | Configuration options |
| [references/template-syntax.md](references/template-syntax.md) | Template syntax |
| [references/search-guide.md](references/search-guide.md) | Search strategies |

## Requirements

- JDK 17+
- Git 2.0+ (for first-time setup)
- Internet connection (for first-time setup)
