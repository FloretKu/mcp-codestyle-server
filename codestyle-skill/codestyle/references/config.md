# Configuration

## Config File Locations

| Priority | Location | Scope |
|----------|----------|-------|
| Low | `jar-directory/cfg.json` | Global default |
| High | `project-directory/.codestyle/cfg.json` | Project override |

Project config overrides global config fields with the same name.

## Config Options

```json
{
  "repository": {
    "local-path": "~/.codestyle/cache",
    "repository-dir": "~/.codestyle/cache",
    "remote-path": "https://api.codestyle.top",
    "remote-search-enabled": false,
    "remote-search-timeout-ms": 5000,
    "api-key": ""
  }
}
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `local-path` | string | `~/.codestyle/cache` | Local cache directory |
| `repository-dir` | string | same as local-path | Template repository directory |
| `remote-path` | string | `https://api.codestyle.top` | Remote API URL |
| `remote-search-enabled` | boolean | `false` | Enable remote search |
| `remote-search-timeout-ms` | integer | `5000` | Remote request timeout (ms) |
| `api-key` | string | `""` | Remote API key (optional) |

## Path Resolution

- `~` expands to user home directory
- Relative paths resolve based on config file location
- Absolute paths used directly

## Project Config Example

Create `.codestyle/cfg.json` in project root. **Only configure fields to override**, other fields use global defaults:

```json
{
  "repository": {
    "repository-dir": "./.codestyle/cache",
    "remote-search-enabled": true
  }
}
```

This config:
- `repository-dir` overrides to project directory
- `remote-search-enabled` overrides to true
- Other fields (`remote-path`, `api-key`, etc.) use global config values
