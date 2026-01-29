# Codestyle Server MCP【码蜂】

基于 Spring AI 的 MCP 服务器，为 IDE 和 AI 代理提供代码模板搜索和检索工具。

## 核心特性

- **MCP 工具**：`codestyleSearch` + `getTemplateByPath`，支持 Cherry Studio、Cursor 等 STDIO 客户端
- **Lucene 全文检索**：中文分词（SmartChineseAnalyzer），离线可用
- **双模式检索**：本地 Lucene（默认） / 远程 API
- **增量更新**：SHA256 比对，按需下载
- **多版本共存**：`groupId/artifactId/version/` Maven 风格目录

## 技术栈

- Java 17, Maven 3.9+
- Spring Boot 3.4.3, Spring AI MCP Server 1.1.0
- Apache Lucene 9.12.3, Hutool 5.8.42

## 快速开始

### 构建

```bash
git clone https://github.com/itxaiohanglover/mcp-codestyle-server.git
cd mcp-codestyle-server
./mvnw clean package -DskipTests
```

### MCP 客户端配置

```json
{
  "mcpServers": {
    "codestyle": {
      "command": "java",
      "args": [
        "-Dspring.ai.mcp.server.stdio=true",
        "-Dspring.main.web-application-type=none",
        "-Dlogging.pattern.console=",
        "-Dfile.encoding=UTF-8",
        "-jar",
        "/path/to/codestyle-server.jar"
      ]
    }
  }
}
```

## MCP 工具

### codestyleSearch

搜索模板，返回目录树 + 描述。

```
输入: templateKeyword (如: CRUD, continew/CRUD)
输出:
  找到模板组: CRUD
  目录树:
  └── continew/CRUD/1.0.0/
      └── src/main/java/.../Controller.ftl
  模板组介绍: ...
```

### getTemplateByPath

获取模板内容，返回变量说明 + 代码。

```
输入: templatePath (如: continew/CRUD/1.0.0/.../Controller.ftl)
输出:
  #文件名：...
  #文件变量：
  - className: 类名（示例：UserController）[String]
  #文件内容：
  package ${packageName};
  ...
```

## 模板仓库结构

```
codestyle-cache/
├── lucene-index/           # Lucene 索引
└── {groupId}/{artifactId}/
    ├── meta.json           # 元数据（多版本）
    └── {version}/          # 模板文件
```

### meta.json 格式

```json
{
  "groupId": "continew",
  "artifactId": "CRUD",
  "configs": [{
    "version": "1.0.0",
    "files": [{
      "filePath": "/src/main/java/controller",
      "filename": "Controller.ftl",
      "sha256": "...",
      "inputVariables": [...]
    }]
  }]
}
```

## [Codestyle Skill 集成](codestyle-skill/README.md)

### 安装

```bash
cp -r codestyle ~/.claude/skills/     # 个人级别
cp -r codestyle .claude/skills/       # 项目级别
```

### Skill 结构

```
codestyle/
├── SKILL.md
├── scripts/
│   ├── codestyle-server.jar
│   └── cfg.json
└── references/
    ├── config.md
    └── template-syntax.md
```

## 配置

### 配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `repository.repository-dir` | `./codestyle-cache` | 缓存目录 |
| `repository.remote-path` | - | 远程 API 地址 |
| `repository.remote-search-enabled` | `false` | 启用远程检索 |
| `repository.remote-search-timeout-ms` | `5000` | 远程超时(ms) |
| `repository.api-key` | - | API 密钥（可选） |

### 操作命令
```bash
java -jar codestyle-server.jar search "CRUD"
java -jar codestyle-server.jar get "continew/CRUD/1.0.0/src/.../Controller.ftl"
```

### 配置覆盖优先级

```
JVM参数 > 环境变量 > .codestyle/cfg.json > scripts/cfg.json > application.yml
```

### 项目级 cfg.json 示例

在项目根目录创建 `.codestyle/cfg.json`，仅配置需要覆盖的字段：

```json
{
  "repository": {
    "local-path": "cache",
    "remote-path": "https://api.codestyle.top",
    "repository-dir": "cache",
    "remote-search-enabled": true,
    "remote-search-timeout-ms": 5000,
    "api-key": ""
  }
}

```

### 触发短语

- "生成 CRUD 代码"
- "用模板生成 controller"
- "帮我生成 User 实体的增删改查"

## 常见问题

| 问题 | 解决方案 |
|------|----------|
| 清理缓存 | 删除 `codestyle-cache/` 目录 |
| 索引损坏 | 删除 `lucene-index/` 目录，重启 |
| 远程不可用 | 自动降级到本地检索 |
| SHA256 失败 | 删除对应模板目录，重新下载 |

## 许可证

[MIT License](LICENSE)

## 作者

- artboy (itxaiohanglover)
- Kanttha
- movclantian
- cris_tofu (gccszs)

## 更新日志

### v1.0.2 (2026-01-29)
- Claude Code Skill 集成
- CLI 模式（search/get 命令）
- cfg.json 外部配置（双层级覆盖）
- 远程多结果响应
- 远程超时配置

### v1.0.1 (2025-12-18)
- Spring Boot 3.4.12
- 新版 MCP 注解
- 搜索功能重构

### v1.0.0 (2025-12-03)
- Lucene 本地索引
- 远程配置与模板下载
- SHA256 增量更新

### v0.1.0 (2025-09-29)
- 项目初始化
- MCP 工具基础实现
- 远程模板下载与缓存
