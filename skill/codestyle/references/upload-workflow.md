# Upload Workflow

Submit manually prepared template files to the server.

## When to Use

- User has prepared template files (meta.json + template.ftl)
- User says: "上传模板", "submit template", "提交模板"

## Prerequisites

Before upload, ensure template format is valid:

1. Template follows standard directory structure
2. `meta.json` exists in version directory (e.g., `CRUD/1.0.0/meta.json`)
3. At least one `.ftl` template file exists
4. All inputVariables have required fields

### Standard Directory Structure

```
<artifactId>/
└── <version>/
    ├── meta.json          # Required
    ├── backend/
    │   └── *.ftl          # Template files
    └── frontend/
        └── *.ftl
```

## Step-by-Step Process

### Step 1: Validate Template Format

Run validation script on the **version directory**:

```bash
python "<SKILL_DIR>/scripts/validate_template.py" <artifactId>/<version>
```

**Example:**
```bash
python validate_template.py CRUD/1.0.0
```

**Valid output:**
```
Validating template: CRUD/1.0.0
------------------------------------------------------------
✓ meta.json structure valid (single-version)
✓ Template files exist (15 .ftl files found)
------------------------------------------------------------
✓ Template is valid
```

**Invalid output:**
```
Validating template: CRUD/1.0.0
------------------------------------------------------------
✗ meta.json validation failed:
  - Missing required field: version
✗ Template files validation failed:
  - No .ftl template files found in version directory
------------------------------------------------------------
✗ Validation failed with 2 error(s)
```

### Step 2: Confirm Upload

Ask user to confirm:

```
You: 模板格式验证通过，确认上传？

📁 模板信息：
- 名称: UserController
- 版本: 1.0.0
- 文件: Controller.java.ftl
- 变量: packageName, entityName

确认上传？(是/否)
```

### Step 3: Upload

**Windows:**
```bash
"<SKILL_DIR>\scripts\codestyle.bat" upload --path <artifactId>/<version>
```

**Linux/macOS:**
```bash
bash <SKILL_DIR>/scripts/codestyle upload --path <artifactId>/<version>
```

**Example:**
```bash
codestyle.bat upload --path CRUD/1.0.0
```

**Success response:**
```
✓ Template uploaded successfully
- ID: continew/CRUD/1.0.0
- Search: codestyle search "CRUD"
```

### Step 4: Verify

Suggest user to verify:

```
You: ✓ 上传成功！你可以通过以下方式验证：
1. 搜索模板: search "UserController"
2. 获取详情: get "user/user-controller/1.0.0/..."
```

## Example Dialogue

```
User: 我准备好了一个模板，在 CRUD/1.0.0 目录

You: [执行验证]
python scripts/validate_template.py CRUD/1.0.0

✓ 模板格式验证通过

模板信息：
- 组织: continew
- 模板: CRUD
- 版本: 1.0.0
- 文件: 15 个 .ftl 文件

确认上传？

User: 是的，上传

You: [执行 upload 命令]
✓ 上传成功！

模板ID: continew/CRUD/1.0.0
可通过 search "CRUD" 检索使用
```

## Error Handling

### Validation Failed

```
You: [执行验证]
✗ 模板格式验证失败：
  - meta.json: 缺少 version 字段
  - 未找到 .ftl 模板文件

请修复后再上传。参考 template-format.md 了解正确格式。
```

### Upload Failed

```
You: [执行 upload 命令]
Error: 远程服务不可用 / 认证失败

You: 上传失败，可能原因：
1. 远程服务暂时不可用
2. access-key/secret-key 配置错误
3. 网络连接问题

请检查配置后重试，或联系管理员。
```

## Notes

- Upload requires remote API (authentication needed)
- Always validate before upload
- Keep local copy of templates for backup
