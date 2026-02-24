# Get Workflow

Retrieve full template file content by exact path. Used after search to get specific template details.

## Command

### Windows

```bash
"<SKILL_DIR>\scripts\codestyle.bat" get "template-path"
```

### Linux/macOS

```bash
bash <SKILL_DIR>/scripts/codestyle get "template-path"
```

## Parameters

| Parameter | Required | Description |
|-----------|----------|-------------|
| template-path | Yes | **Full path** to template file: `groupId/artifactId/version/filePath/filename` |

## Path Format

**IMPORTANT**: The path must include the **complete file path**, not just the version directory.

### Correct Format

```
groupId/artifactId/version/subdirectory/path/filename.ftl
```

### Examples

✅ **Correct** - Full path with filename:
```bash
codestyle get "continew/CRUD/1.0.0/bankend/src/main/java/com/air/controller/Controller.ftl"
codestyle get "continew/CRUD/1.0.0/frontend/src/api/api.ftl"
codestyle get "continew/CRUD/1.0.0/bankend/sql/Menu.ftl"
```

❌ **Incorrect** - Only version path (missing file path):
```bash
codestyle get "continew/CRUD/1.0.0"  # ❌ Missing file path and filename
```

## How to Get the Correct Path

### Method 1: From Search Results

After running `search`, the directory tree shows all available files:

```
Found template group: CRUD

Directory tree:
└── continew/CRUD/1.0.0/
    ├── bankend/
    │   ├── src/main/java/com/air/controller/
    │   │   └── Controller.ftl
    │   ├── src/main/java/com/air/service/
    │   │   └── Service.ftl
    │   └── sql/
    │       └── Menu.ftl
    └── frontend/
        └── src/api/
            └── api.ftl
```

**Pick the file you want** and construct the full path:
- Base: `continew/CRUD/1.0.0`
- File: `bankend/src/main/java/com/air/controller/Controller.ftl`
- **Full path**: `continew/CRUD/1.0.0/bankend/src/main/java/com/air/controller/Controller.ftl`

### Method 2: From User Request

If user says "get the Controller template", you need to:

1. Know the template location from previous search
2. Construct the full path including the file
3. Execute get command

## Output Format

```
Template: {filename}
Path: {full-path}

Variables:
- {variableName}: {description} (example: {example}) [{type}]
- ...

Template Content:
{template-content}
```

## Workflow

```
1. User requests specific template file
2. Construct full path: groupId/artifactId/version/filePath/filename
3. Execute: codestyle get "full-path"
4. Parse and display template content
5. User can now use the template
```

## Complete Example

**Step 1: Search**

```
User: "Find CRUD template"
AI: [Execute] codestyle search "CRUD"

Result:
Found template group: CRUD

Directory tree:
└── continew/CRUD/1.0.0/
    ├── bankend/
    │   └── src/main/java/com/air/controller/
    │       └── Controller.ftl
    └── ...
```

**Step 2: Get Specific File**

```
User: "Get the Controller template"
AI: [Execute] codestyle get "continew/CRUD/1.0.0/bankend/src/main/java/com/air/controller/Controller.ftl"

Result:
Template: Controller.ftl
Path: continew/CRUD/1.0.0/bankend/src/main/java/com/air/controller/Controller.ftl

Variables:
- packageName: 项目根包名 (example: com.air.order) [String]
- className: 控制器类名 (example: OrderController) [String]
- businessName: 业务名称（中文） (example: 订单) [String]
...

Template Content:
package ${packageName}.${subPackageName};
...
```

## AI Decision Logic

### When User Says "Get CRUD Template"

**❌ Wrong Approach:**
```bash
codestyle get "continew/CRUD/1.0.0"  # Missing file path!
```

**✅ Correct Approach:**

1. **Ask for clarification** if multiple files exist:
   ```
   "The CRUD template has multiple files:
   1. Controller.ftl - Controller layer
   2. Service.ftl - Service layer
   3. Entity.ftl - Entity class
   
   Which one would you like?"
   ```

2. **Or list all files** if user wants to see all:
   ```
   "I'll get all CRUD template files for you..."
   [Execute multiple get commands]
   ```

### When User Says "Get the Controller"

If you already know the template location from previous search:

```bash
codestyle get "continew/CRUD/1.0.0/bankend/src/main/java/com/air/controller/Controller.ftl"
```

## Common Mistakes

### Mistake 1: Using Only Version Path

```bash
# ❌ Wrong
codestyle get "continew/CRUD/1.0.0"

# ✅ Correct
codestyle get "continew/CRUD/1.0.0/bankend/src/main/java/com/air/controller/Controller.ftl"
```

### Mistake 2: Missing File Extension

```bash
# ❌ Wrong
codestyle get "continew/CRUD/1.0.0/bankend/src/main/java/com/air/controller/Controller"

# ✅ Correct
codestyle get "continew/CRUD/1.0.0/bankend/src/main/java/com/air/controller/Controller.ftl"
```

### Mistake 3: Wrong Path Separator

```bash
# ❌ Wrong (mixing separators)
codestyle get "continew\CRUD/1.0.0\bankend/src/main/java/com/air/controller/Controller.ftl"

# ✅ Correct (use forward slash consistently)
codestyle get "continew/CRUD/1.0.0/bankend/src/main/java/com/air/controller/Controller.ftl"
```

## Fallback

If path is incorrect:

```
Error: Template not found at path "xxx"

Suggestions:
1. Check the path format: groupId/artifactId/version/filePath/filename.ftl
2. Run search first to see available files
3. Copy the exact path from search results
```

## Integration with Search

**Best Practice**: Always use search first, then get:

```
Step 1: Search
  codestyle search "CRUD"
  → Get directory tree with all files

Step 2: Get specific file
  codestyle get "continew/CRUD/1.0.0/[file-path-from-tree]"
  → Get full template content
```

This ensures you always have the correct, complete path.

