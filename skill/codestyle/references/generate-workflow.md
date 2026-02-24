# Generate Workflow

Create Codestyle templates from various input sources via AI-powered generation.

## Overview

Generate workflow is a **mini DeepResearch** process:

```
识别输入类型 → 预处理（克隆/爬虫/读取） → 分析代码结构 → 选择模板类型 → 
逐个生成 → 去重检测 → 用户确认 → 上传存储
```

## Input Types

| 类型 | 示例 | 预处理 |
|------|------|--------|
| 代码片段 | `@RestController public class UserController {...}` | 无 |
| 当前文件 | "当前打开的这个文件" | 读取文件内容 |
| 本地路径 | `/path/to/Controller.java` | 读取文件 |
| GitHub URL | `https://github.com/xxx/demo` | 克隆仓库 |
| 网页 URL | `https://blog.xxx.com/post/123` | 爬取内容 |

## Workflow Steps

### Step 1: Identify Input Type

Detect the type of input and route accordingly:

```
User input
    │
    ├─ 代码片段（包含 class/interface/def 等）──→ Step 3
    │
    ├─ "当前文件" / "这个文件" ──→ 读取当前文件 ──→ Step 3
    │
    ├─ 本地路径（/path 或 C:\path）──→ 读取文件 ──→ Step 3
    │
    ├─ GitHub URL（github.com）──→ Step 2a: 克隆仓库
    │
    └─ 网页 URL（http/https）──→ Step 2b: 爬取内容
```

### Step 2a: Clone GitHub Repository

For GitHub URLs:

```bash
# Clone to temp directory
git clone --depth 1 <github-url> /tmp/codestyle-repo-xxx

# Ask user which files/directories to analyze
```

**Example:**
```
User: "https://github.com/spring-projects/spring-petclinic 这个仓库不错，帮我生成模板"

You: [克隆仓库]
仓库已克隆，发现以下主要代码目录：

1. src/main/java/org/springframework/samples/petclinic/controller/
2. src/main/java/org/springframework/samples/petclinic/service/
3. src/main/java/org/springframework/samples/petclinic/repository/

请选择要分析的目录（可多选，用逗号分隔）：
User: 1, 2

You: [分析选择的目录]
发现以下可生成模板的文件：

Controller 层 (4 files):
- OwnerController.java
- PetController.java
- VetController.java
- VisitController.java

Service 层 (2 files):
- ClinicService.java
- PetService.java

请选择要生成模板的文件（输入序号，或输入 "全部"）：
```

### Step 2b: Crawl Web Content

For web URLs:

```bash
# Fetch and extract content
curl -L <url> | html2text
```

**Example:**
```
User: "https://blog.example.com/best-practices-for-rest-api 这个文章不错"

You: [爬取网页内容]
文章内容已提取：

标题: Best Practices for REST API
内容: ...（代码示例）

发现以下代码示例：
1. REST Controller 示例
2. 响应包装类示例

是否基于这些代码生成模板？
User: 是的，生成第一个

You: [执行生成流程...]
```

### Step 3: Analyze Code Structure

Analyze the code to identify:
- Code patterns (CRUD, MVC, Repository, etc.)
- Extractable variables (entityName, packageName, etc.)
- Similar existing templates

**Example:**
```
You: 分析代码结构...

识别到代码模式：
- REST Controller（@RestController, @RequestMapping）
- CRUD 操作（GET, POST, PUT, DELETE）
- 依赖注入（@RequiredArgsConstructor）

提取到变量：
- packageName: org.springframework.samples.petclinic
- entityName: Owner
- serviceName: ClinicService

[执行 search "controller" 检索相似模板]
检索到 2 个相似模板：
1. continew/crud-plus - 已有类似结构
2. continew/user-crud - 用户模块 CRUD

选择：
- 复用现有模板（输入序号）
- 继续创建新模板（输入 "新"）
```

### Step 4: Select Template Types

For multiple files, ask which templates to generate:

**Example:**
```
You: 当前文件可生成以下模板类型：

1. Controller 模板 - REST 控制器
2. Service 模板 - 业务服务层
3. Repository 模板 - 数据访问层
4. Entity 模板 - 实体类

请选择要生成的模板类型（可多选，如 1,3）：
User: 1, 2

You: 将生成 2 个模板：
- Controller 模板
- Service 模板
```

### Step 5: Generate Templates

Generate each selected template:

**Windows:**
```bash
"<SKILL_DIR>\scripts\codestyle.bat" generate --source "code" --type "controller" --name "TemplateName"
```

**Linux/macOS:**
```bash
bash <SKILL_DIR>/scripts/codestyle generate --source "code" --type "controller" --name "TemplateName"
```

**For multiple templates:**
```
You: 正在生成模板...

✓ Controller 模板已生成
  变量: packageName, entityName, serviceName
  
✓ Service 模板已生成
  变量: packageName, entityName

模板预览：

=== Controller.java.ftl ===
package ${packageName}.controller;
...

=== Service.java.ftl ===
package ${packageName}.service;
...
```

### Step 6: Duplicate Check

Check for existing templates with **multiple detection levels**:

#### Detection Levels

| 级别 | 检测条件 | 说明 |
|------|----------|------|
| **精确匹配** | 模板名称完全相同 | `OwnerController` == `OwnerController` |
| **功能相似** | 模式类型相同 | 都是 `CRUD Controller` |
| **代码相似** | 模板结构相似度 > 80% | 变量、结构相似 |
| **模板组冲突** | 模板组有部分重叠 | 新 CRUD 组 vs 已有 CRUD 组 |

#### Detection Flow

```
执行检测
    │
    ├─ 精确匹配 ──→ 提供覆盖/升级/重命名/删除选项
    │
    ├─ 功能相似 ──→ 提供合并/不合并/替换选项
    │
    ├─ 代码相似 ──→ 展示差异，提供合并/保留选项
    │
    └─ 模板组冲突 ──→ 展示冲突项，提供逐项处理选项
```

#### Scenario 1: Exact Match (同名)

```
You: [执行 search 检测重复]

⚠️ 检测到同名模板：
- user/owner-controller/1.0.0 - OwnerController

选项：
1. 覆盖 - 替换现有模板
2. 升级版本 - 保存为 v1.1.0
3. 重命名 - 修改新模板名称
4. 删除旧模板 - 删除已有模板，保存新模板
5. 取消 - 不保存此模板

请选择：
```

#### Scenario 2: Similar Function (功能相似)

```
You: [执行 search 检测相似模板]

⚠️ 检测到功能相似的模板：

已有模板：user/crud-controller/1.0.0
- 名称：CrudController
- 功能：CRUD Controller（增删改查）
- 变量：packageName, entityName

新模板：OwnerController  
- 功能：CRUD Controller（增删改查）
- 变量：packageName, entityName, serviceName

功能相似度：85%

选项：
1. 合并 - 将两个模板合并为一个更完善的版本
2. 不合并 - 保留两个独立的模板
3. 替换 - 用新模板替换已有模板
4. 查看差异 - 对比两个模板的具体差异

请选择：
```

#### Scenario 3: Template Group Conflict (模板组冲突)

```
You: [执行 search 检测模板组冲突]

⚠️ 检测到模板组部分重叠：

新模板组 (Owner CRUD)：
├── Controller.java.ftl
└── Service.java.ftl

已有模板组 (Pet CRUD)：
├── Controller.java.ftl  ← 功能相似
├── Service.java.ftl     ← 功能相似
└── Repository.java.ftl

冲突项：
- Controller：功能相似度 90%
- Service：功能相似度 85%

处理选项：
1. 逐项处理 - 对每个冲突项单独选择
2. 全部合并 - 将新模板合并到已有组
3. 全部新建 - 创建独立的模板组
4. 查看差异 - 对比具体差异

请选择：
```

#### Merge Operation (合并操作)

```
User: 选择"合并"

You: 合并模式 - 选择要保留的内容：

已有模板功能：
✓ 基础 CRUD（GET/POST/PUT/DELETE）
✓ 分页查询
✗ 批量删除

新模板功能：
✓ 基础 CRUD（GET/POST/PUT/DELETE）
✗ 分页查询
✓ 批量删除
✓ 导出功能

合并后功能：
✓ 基础 CRUD
✓ 分页查询（来自已有模板）
✓ 批量删除（来自新模板）
✓ 导出功能（来自新模板）

确认合并？
User: 是

You: ✓ 合并完成，新版本 v1.1.0 已保存
包含功能：基础CRUD + 分页 + 批量删除 + 导出
```

#### Processing Options Summary

| 选项 | 说明 | 适用场景 |
|------|------|---------|
| **覆盖** | 替换现有模板 | 修复 bug、优化代码 |
| **升级版本** | 保存为新版本 | 功能增强 |
| **重命名** | 修改新模板名称 | 不同用途 |
| **删除旧模板** | 删除已有，保存新模板 | 完全替代 |
| **合并** | 合并新旧模板功能 | 功能互补 |
| **不合并** | 保留两个独立模板 | 不同场景使用 |
| **替换** | 用新模板替换已有 | 新模板更完善 |
| **取消** | 不保存新模板 | 放弃创建 |

### Step 7: User Confirmation

Confirm before upload:

```
You: 模板确认完成，即将上传：

📦 模板组: Owner CRUD
├── Controller.java.ftl (新模板)
└── Service.java.ftl (新模板)

变量：
- packageName: org.springframework.samples.petclinic
- entityName: Owner
- serviceName: ClinicService

是否上传？(是/否/修改)
User: 是

You: [执行 upload]
✓ 上传成功！
- user/owner-controller/1.0.0
- user/owner-service/1.0.0

可通过 search "Owner" 检索使用
```

## Example Dialogues

### Example 1: Single Code Snippet (无重复)

```
User: 把这段代码做成模板：
```java
@RestController
@RequestMapping("/api/user")
public class UserController {
    @GetMapping("/{id}")
    public User getById(@PathVariable Long id) { ... }
}
```

You: 识别到 REST Controller 代码片段

分析代码结构...
- 模式: REST Controller + CRUD
- 变量: packageName, entityName

[检索相似模板]
未找到相似模板

请命名：
User: SimpleCrudController

You: [生成模板]
模板已生成：

Controller.java.ftl:
package ${packageName}.controller;
...

✓ 未检测到重复

是否保存？
User: 保存

You: ✓ 已保存
```

### Example 2: Exact Match (同名处理)

```
User: 把这个 Controller 做成模板：...

You: 请命名：
User: CrudController

You: [生成模板]
模板已生成...

⚠️ 检测到同名模板：
- continew/crud-controller/1.0.0 - CrudController

选项：
1. 覆盖 - 替换现有模板
2. 升级版本 - 保存为 v1.1.0
3. 重命名 - 修改新模板名称
4. 删除旧模板 - 删除已有模板
5. 取消

User: 2

You: ✓ 已保存为 continew/crud-controller/1.1.0
```

### Example 3: Similar Function (合并)

```
User: 帮我生成一个 CRUD 模板：...

You: [生成模板]
模板已生成：CrudController

⚠️ 检测到功能相似的模板：

已有：continew/simple-crud/1.0.0
- 功能：基础 CRUD（增删改查）
- 变量：packageName, entityName

新模板：
- 功能：CRUD + 分页 + 导出
- 变量：packageName, entityName, enablePage

功能相似度：85%

选项：
1. 合并 - 合并两个模板的功能
2. 不合并 - 保留两个独立模板
3. 替换 - 用新模板替换已有
4. 查看差异

User: 1

You: 合并模式 - 选择要保留的内容：

已有模板功能：
✓ 基础 CRUD
✗ 分页查询
✗ 导出功能

新模板功能：
✓ 基础 CRUD
✓ 分页查询
✓ 导出功能

合并后功能：
✓ 基础 CRUD
✓ 分页查询（来自新模板）
✓ 导出功能（来自新模板）

确认合并？
User: 是

You: ✓ 合并完成，已保存为 continew/simple-crud/1.1.0
```

### Example 4: Template Group Conflict (逐项处理)

```
User: https://github.com/xxx/demo 帮我生成模板

You: [克隆仓库，分析代码]
发现可生成模板组：
- Controller
- Service
- Repository

[检测模板组冲突]

⚠️ 检测到模板组部分重叠：

新模板组：
├── Controller (CRUD + 分页)
└── Service (CRUD)

已有模板组 continew/crud-plus：
├── Controller (基础 CRUD) ← 相似度 90%
├── Service (CRUD) ← 相似度 85%
└── Repository (JPA)

处理选项：
1. 逐项处理
2. 全部合并
3. 全部新建
4. 查看差异

User: 1

You: 逐项处理：

=== Controller ===
已有：基础 CRUD
新模板：CRUD + 分页
选择：[合并/不合并/替换/取消]
User: 合并

You: ✓ Controller 已合并

=== Service ===
已有：基础 CRUD Service
新模板：CRUD Service（相同功能）
选择：[合并/不合并/替换/取消]
User: 不合并

You: ✓ Service 保留两个独立版本

=== 最终结果 ===
✓ Controller：已合并到 continew/crud-plus/1.1.0
✓ Service：新建为 xxx/crud-service/1.0.0
```

### Example 5: Current File → Multiple Templates

```
User: 当前打开的这个文件，帮我生成模板

You: [读取当前文件]
检测到文件: UserService.java

分析代码结构...
- 模式: Service 层
- 包含: 业务逻辑、事务管理

当前目录还有相关文件：
- UserController.java
- UserRepository.java

是否一并分析？
User: 是

You: [分析完成]
可生成 3 个模板

[检测冲突]
⚠️ 检测到 1 个冲突：
- UserController 与 continew/user-controller 功能相似 (80%)

选择处理方式：
User: 不合并

You: ✓ 模板组已保存：
- xxx/user-service/1.0.0 (新建)
- xxx/user-controller/1.0.0 (新建)
- xxx/user-repository/1.0.0 (新建)
```

## Command Reference

### Generate Command

```bash
# 基础用法
codestyle generate --source "code" --name "TemplateName"

# 指定类型
codestyle generate --source "code" --type "controller" --name "TemplateName"

# 批量生成
codestyle generate --source "code" --types "controller,service" --name "TemplateName"
```

### Upload Command

```bash
# 单个模板
codestyle upload --template "content" --meta "meta.json"

# 模板组
codestyle upload --group --path ./templates/
```

## Error Handling

| 错误 | 处理 |
|------|------|
| Git 克隆失败 | 提示手动下载或检查网络 |
| 网页爬取失败 | 提示用户提供代码片段 |
| 代码解析失败 | 提示检查代码格式 |
| API 不可用 | 建议使用 Web 平台 |

## Notes

- Generate requires remote API (AI-powered)
- **Multiple templates** can be generated from one source
- **Four-level duplicate detection**: exact match, similar function, code similar, group conflict
- **Merge operation** available for templates with similar functions
- **Per-item processing** for template group conflicts
- For complex analysis (full repo), suggest Web platform
- Reference [search-workflow.md](search-workflow.md) for search commands
