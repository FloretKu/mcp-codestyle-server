# Search Workflow

Find code templates by keyword. Executed locally with Lucene index for fast response.

## Command

### Windows

```bash
"<SKILL_DIR>\scripts\codestyle.bat" search "keyword"
```

### Linux/macOS

```bash
bash <SKILL_DIR>/scripts/codestyle search "keyword"
```

## Parameters

| Parameter | Required | Description |
|-----------|----------|-------------|
| keyword | Yes | Search keyword (e.g., "CRUD", "login", "export") |

## Search Strategy

| User Request | Suggested Keywords |
|--------------|-------------------|
| "create CRUD" | `CRUD` → `controller` → `service` |
| "create login" | `login` → `auth` → `user` |
| "generate export" | `export` → `excel` → `download` |
| "RuoYi template" | `ruoyi` → `continew` |

## Output Format

### Single Result

```
Found template group: {artifactId}

Directory tree:
└── {groupId}/{artifactId}/{version}/
    ├── src/main/java/controller/Controller.ftl
    └── ...
Template description:
{description}
```

### Multiple Results

```
Found {N} templates matching "{keyword}":

## 1. {groupId}/{artifactId}
Path: {groupId}/{artifactId}/{version}
Description: {description}
---
💡 Smart Decision: select most appropriate one or use exact "groupId/artifactId" format.
```

## Workflow

```
1. User requests template
2. Extract keyword from intent
3. Execute: codestyle search "keyword"
4. Parse results
5. If multiple results → Ask user to select
6. If single result → Proceed to Get Workflow
```

## Examples

**Example 1: Single Match**

```
User: "Find CRUD template"
You: [Execute] codestyle search "CRUD"
Result: Single match found with directory tree showing files
You: "Found CRUD template with multiple files. Which file do you need?"
User: "The Controller one"
You: [Execute] codestyle get "continew/CRUD/1.0.0/bankend/src/main/java/com/air/controller/Controller.ftl"
```

**Example 2: Multiple Matches**

```
User: "Search for user template"
You: [Execute] codestyle search "user"
Result: 3 templates found
You: "Found 3 templates:
     1. continew/user-crud - User CRUD operations
     2. continew/user-auth - User authentication
     3. ruoyi/user-manager - User management
     
     Which one would you like?"
User: "The first one"
You: [Execute] codestyle search "continew/user-crud"
Result: Shows directory tree with available files
You: "Which file do you need? (Controller, Service, Entity, etc.)"
User: "Controller"
You: [Execute] codestyle get "continew/user-crud/1.0.0/backend/src/main/java/controller/UserController.ftl"
```

## Fallback

If no local results found:

1. Suggest similar keywords
2. Or suggest using remote search (if configured)

```
No templates found for "xxx".

Suggestions:
- Try broader keywords: "CRUD", "controller"
- Check if template repository is initialized
```
