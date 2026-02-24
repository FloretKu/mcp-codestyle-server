# Template Format Specification

## Directory Structure

Standard template structure with **meta.json in version directory**:

```
<artifactId>/
└── <version>/
    ├── meta.json          # Template metadata (required)
    ├── README.md          # Template documentation (optional)
    └── <subdirectories>   # Template files (.ftl) in subdirectories
```

### Example

```
CRUD/
└── 1.0.0/
    ├── meta.json
    ├── README.md
    ├── backend/
    │   ├── sql/
    │   │   └── Menu.ftl
    │   └── src/
    │       └── main/
    │           └── java/
    │               ├── controller/Controller.ftl
    │               ├── service/Service.ftl
    │               └── mapper/Mapper.ftl
    └── frontend/
        └── src/
            ├── api/api.ftl
            ├── components/
            │   ├── AddModal.ftl
            │   └── DetailDrawer.ftl
            └── views/index.ftl
```

---

## meta.json Format

### Structure

```json
{
  "groupId": "string",       // Organization ID (required)
  "artifactId": "string",    // Template ID (required)
  "version": "string",       // Version number (required)
  "name": "string",          // Display name (optional)
  "description": "string",   // Description (optional)
  "files": [                 // File list (required)
    {
      "filePath": "string",        // Relative path from version directory
      "filename": "string",        // File name (must end with .ftl)
      "description": "string",     // File description
      "inputVariables": [...],     // Input variables
      "sha256": "string"           // File hash (optional)
    }
  ]
}
```

### Required Fields

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `groupId` | String | Organization or project identifier | `"continew"` |
| `artifactId` | String | Template identifier | `"CRUD"` |
| `version` | String | Semantic version | `"1.0.0"` |
| `files` | Array | List of template files | `[...]` |

### Optional Fields

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `name` | String | Display name | `"CRUD 代码生成模板"` |
| `description` | String | Brief description | `"用于快速生成完整的增删改查功能代码"` |

---

## File Object

Each file in the `files` array describes one template file:

```json
{
  "filePath": "/backend/src/main/java/controller",
  "filename": "Controller.ftl",
  "description": "控制层类模板 - 用于生成RESTful API Controller",
  "inputVariables": [...],
  "sha256": "待计算"
}
```

### Required Fields

| Field | Type | Description |
|-------|------|-------------|
| `filePath` | String | Relative path from version directory (starts with `/`) |
| `filename` | String | File name (must end with `.ftl`) |
| `inputVariables` | Array | List of input variables |

### Optional Fields

| Field | Type | Description |
|-------|------|-------------|
| `description` | String | What this template generates |
| `sha256` | String | File hash for integrity check |

---

## InputVariable Object

Each variable in `inputVariables` describes one template parameter:

```json
{
  "variableName": "packageName",
  "variableType": "String",
  "variableComment": "项目根包名",
  "example": "com.air.order"
}
```

### Required Fields

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `variableName` | String | Variable name (used in template) | `"packageName"` |
| `variableType` | String | Data type | `"String"`, `"Boolean"`, `"List<FieldConfig>"` |
| `variableComment` | String | Variable description | `"项目根包名"` |
| `example` | String/Any | Example value | `"com.air.order"` |

---

## Complete Example

```json
{
  "groupId": "continew",
  "artifactId": "CRUD",
  "version": "1.0.0",
  "name": "CRUD 代码生成模板",
  "description": "用于快速生成完整的增删改查功能代码",
  "files": [
    {
      "filePath": "/backend/src/main/java/controller",
      "filename": "Controller.ftl",
      "description": "控制层类模板 - 用于生成RESTful API Controller",
      "inputVariables": [
        {
          "variableName": "packageName",
          "variableType": "String",
          "variableComment": "项目根包名",
          "example": "com.air.order"
        },
        {
          "variableName": "className",
          "variableType": "String",
          "variableComment": "控制器类名",
          "example": "OrderController"
        },
        {
          "variableName": "businessName",
          "variableType": "String",
          "variableComment": "业务名称（中文）",
          "example": "订单"
        }
      ],
      "sha256": "待计算"
    },
    {
      "filePath": "/backend/src/main/java/service",
      "filename": "Service.ftl",
      "description": "Service接口模板 - 用于生成业务逻辑接口",
      "inputVariables": [
        {
          "variableName": "packageName",
          "variableType": "String",
          "variableComment": "项目根包名",
          "example": "com.air.order"
        },
        {
          "variableName": "className",
          "variableType": "String",
          "variableComment": "Service接口名",
          "example": "OrderService"
        }
      ],
      "sha256": "待计算"
    }
  ]
}
```

---

## Validation

Use `validate_template.py` to check format:

```bash
python validate_template.py <artifactId>/<version>
```

### Example

```bash
python validate_template.py CRUD/1.0.0
```

### Expected Output

```
Validating template: CRUD/1.0.0
------------------------------------------------------------
✓ meta.json structure valid (single-version)
✓ Template files exist (15 .ftl files found)
------------------------------------------------------------
✓ Template is valid
```

---

## Validation Checklist

Before uploading a template, ensure:

- [ ] Directory structure follows standard format
- [ ] `meta.json` exists in version directory
- [ ] `meta.json` has all required fields (`groupId`, `artifactId`, `version`, `files`)
- [ ] All files in `files` array have `filePath`, `filename`, and `inputVariables`
- [ ] All `inputVariables` have `variableName`, `variableType`, `variableComment`, and `example`
- [ ] At least one `.ftl` template file exists
- [ ] Template files match the paths declared in `meta.json`
- [ ] Template syntax is valid FreeMarker

---

## FreeMarker Template Syntax

Template files use FreeMarker syntax. See [template-syntax.md](template-syntax.md) for details.

### Common Directives

```freemarker
${variableName}              - Insert variable value
${variableName?cap_first}    - Capitalize first letter
${variableName?uncap_first}  - Lowercase first letter
<#if condition>...</#if>     - Conditional
<#list items as item>...</#list> - Iteration
```

---

## Path Format

Full path to a template file:

```
<artifactId>/<version>/<filePath>/<filename>
```

### Example

```
CRUD/1.0.0/backend/src/main/java/controller/Controller.ftl
```

Where:
- `artifactId`: `CRUD`
- `version`: `1.0.0`
- `filePath`: `/backend/src/main/java/controller`
- `filename`: `Controller.ftl`

