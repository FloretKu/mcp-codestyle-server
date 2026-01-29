---
name: codestyle
description: This skill should be used when the user asks to "generate code template", "create CRUD", "generate scaffold", "code generator", "generate controller", "generate service", "generate entity", "use template to generate code", or needs code template retrieval and generation. Provides code template search and content retrieval via jar-based CLI.
---

# Codestyle

Code template retrieval and generation via jar-based CLI tool.

## Skill Location

Jar file: `scripts/codestyle-server.jar` (relative to this SKILL.md)

Installation paths:
- Personal: `~/.claude/skills/codestyle/scripts/codestyle-server.jar`
- Project: `.claude/skills/codestyle/scripts/codestyle-server.jar`
- Plugin: `<plugin-path>/skills/codestyle/scripts/codestyle-server.jar`

## Workflow

1. **Initialize project config (REQUIRED)**: You MUST create `.codestyle/cfg.json` in the current working directory (project root) before executing any search or get command:
   ```bash
   mkdir -p .codestyle && cat > .codestyle/cfg.json << 'EOF'
   {
     "repository": {
       "repository-dir": "cache",
       "remote-path": "https://api.codestyle.top",
       "remote-search-enabled": false,
       "remote-search-timeout-ms": 5000,
       "api-key": ""
     }
   }
   EOF
   ```
2. Locate skill directory (where this SKILL.md is located)
3. Execute `search "keyword"` based on user requirements
4. Parse returned directory tree, select matching template path
5. Execute `get "full-path"` to retrieve template content
6. List variables, request user confirmation, generate code

## Commands

Replace `<SKILL_DIR>` with actual skill installation path.

**Search templates:**
```bash
java -jar <SKILL_DIR>/scripts/codestyle-server.jar search "keyword"
```

**Get template content:**
```bash
java -jar <SKILL_DIR>/scripts/codestyle-server.jar get "path"
```

## Search Keyword Formats

| Format | Description | Example |
|--------|-------------|---------|
| Keyword | Full-text search | `CRUD`, `login`, `export` |
| groupId/artifactId | Exact match | `continew/crud-plus` |
| Mixed | Combined search | `user CRUD` |

## Result Types

**Single result:** Returns directory tree directly. Select path and execute `get`.

**Multiple results:** Returns matching list. Ask user to choose or use `groupId/artifactId` for exact search.

**No result:** Not found locally. Suggest synonyms or enable remote search (`remote-search-enabled: true`).

## Path Format

Template path: `groupId/artifactId/version/filePath/filename.ftl`

Example: `continew/crud-plus/1.0.0/src/main/java/controller/Controller.ftl`

## Configuration

Config loading order (later overrides former):
1. `jar-directory/cfg.json` (global)
2. `current-working-directory/.codestyle/cfg.json` (project)

## Reference Files

| File | Purpose |
|------|---------|
| [references/config.md](references/config.md) | Configuration options |
| [references/template-syntax.md](references/template-syntax.md) | Output format parsing & variable handling |
