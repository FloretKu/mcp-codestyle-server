# Template Syntax & Variables

## Command Output Formats

### search Response

**Single result:**
```
Found template group: {artifactId}

Directory tree:
└── {groupId}/{artifactId}/{version}/
    ├── src/main/java/controller/Controller.ftl
    └── ...
Template description:
{description}
```

**Multiple results:**
```
Found {N} templates matching "{keyword}":

## 1. {groupId}/{artifactId}
Path: {groupId}/{artifactId}/{version}
Description: {description}
---
💡 Smart Decision: select most appropriate one or use exact "groupId/artifactId" format.
```

### get Response

```
#Filename: {path}
#Variables:
variableName: description (example: exampleValue) [type]
#Content:
{template content}
```

## Variable Handling

### Format

```
variableName: description (example: exampleValue) [type]
```

### Common Variables

| Variable | Format | Example |
|----------|--------|---------|
| `entityName` | PascalCase | `User`, `OrderItem` |
| `entityNameLower` | camelCase | `user`, `orderItem` |
| `packageName` | dot-separated | `com.example.user` |
| `tableName` | snake_case | `sys_user` |
| `moduleName` | lowercase | `user`, `order` |
| `author` | string | `John` |
| `date` | yyyy-MM-dd | `2025-01-29` |

### Inference Rules

| User Input | Inferred Variable |
|------------|-------------------|
| "User entity" | `entityName: User` |
| "com.example package" | `packageName: com.example` |
| "sys_user table" | `tableName: sys_user` |

### Principles

1. Same variable uses same value across all template files
2. Uncertain variables must be confirmed with user
3. Reference example value format

### FreeMarker Syntax

- Interpolation: `${variableName}`
- Conditional: `<#if condition>...</#if>`
- Loop: `<#list items as item>...</#list>`

## Path Extraction

From directory tree, concatenate path ignoring `├──`, `└──`, `│` symbols.

Full path: `groupId/artifactId/version/filePath/filename.ftl`
