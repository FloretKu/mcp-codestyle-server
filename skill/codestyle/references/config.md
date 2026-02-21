# Configuration

## v2.1.0 Configuration (Recommended)

### Minimal Config (Local Mode)

Default configuration, auto-created on first use:

```json
{
  "repository": {
    "local-path": "./.codestyle/cache"
  }
}
```

### Remote Mode

Enable remote search with Open API authentication:

```json
{
  "repository": {
    "local-path": "./.codestyle/cache",
    "remote": {
      "enabled": true,
      "base-url": "https://api.codestyle.top",
      "access-key": "your-ak",
      "secret-key": "your-sk",
      "timeout-ms": 10000
    }
  }
}
```

## Config File Locations

| Priority | Location | Scope |
|----------|----------|-------|
| Low | `<skill-dir>/scripts/cfg.json` | Global default |
| High | `<project-root>/.codestyle/cfg.json` | Project override |

Project config overrides global config fields with the same name.

## Field Descriptions

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `repository.local-path` | string | `./.codestyle/cache` | Local cache directory |
| `repository.remote.enabled` | boolean | `false` | Enable remote search |
| `repository.remote.base-url` | string | - | Remote API URL |
| `repository.remote.access-key` | string | - | Access Key (AK) |
| `repository.remote.secret-key` | string | - | Secret Key (SK) |
| `repository.remote.timeout-ms` | integer | `10000` | Request timeout (ms) |

## Path Resolution

- `~` expands to user home directory
- Relative paths resolve based on config file location
- Absolute paths used directly

## Project Config Example

Create `.codestyle/cfg.json` in project root. **Only configure fields to override**:

```json
{
  "repository": {
    "local-path": "./.codestyle/cache",
    "remote": {
      "enabled": true,
      "base-url": "https://api.codestyle.top",
      "access-key": "your-ak",
      "secret-key": "your-sk"
    }
  }
}
```

## Get Access Key & Secret Key

1. Login to CodeStyle admin panel
2. Navigate to: **Open API** → **Application Management**
3. Create new application
4. Copy **Access Key (AK)** and **Secret Key (SK)**
5. Configure in `.codestyle/cfg.json`

## Migration from v1.0

### Old Format (Deprecated)

```json
{
  "repository": {
    "local-path": "cache",
    "remote-path": "https://api.codestyle.top",
    "repository-dir": "cache",
    "remote-search-enabled": true,
    "remote-search-timeout-ms": 5000,
    "api-key": "xxx"
  }
}
```

### New Format (v2.1.0)

```json
{
  "repository": {
    "local-path": "./.codestyle/cache",
    "remote": {
      "enabled": true,
      "base-url": "https://api.codestyle.top",
      "access-key": "xxx",
      "secret-key": "xxx",
      "timeout-ms": 10000
    }
  }
}
```

### Key Changes

1. **Nested structure**: `remote` is now a nested object
2. **Renamed fields**:
   - `remote-path` → `remote.base-url`
   - `remote-search-enabled` → `remote.enabled`
   - `remote-search-timeout-ms` → `remote.timeout-ms`
3. **Authentication upgrade**:
   - `api-key` → `remote.access-key` + `remote.secret-key`
   - Now uses Open API signature authentication (MD5 + timestamp + nonce)
4. **Removed fields**:
   - `repository-dir` (merged into `local-path`)

## Troubleshooting

### Configuration Not Found

**Error**: "Configuration file not found"

**Solution**: The config is auto-created on first use. If you see this error, manually create `.codestyle/cfg.json` in project root.

### Remote Search Failed

**Error**: "签名验证失败" (Signature verification failed)

**Solution**: Check if `access-key` and `secret-key` are correct. Ensure the application is enabled in CodeStyle admin.

### Permission Denied

**Error**: "无法创建仓库目录" (Cannot create repository directory)

**Solution**: Check if `local-path` directory has write permissions.

