# Codestyle Server MCP【码蜂】

基于 Spring AI 的 MCP 服务器，为 IDE 和 AI 代理提供代码模板搜索和检索工具。

## 🚀 核心特性

- **MCP 工具**：`codestyleSearch` + `getTemplateByPath`，支持 Cherry Studio、Cursor 等 STDIO 客户端
- **Lucene 全文检索**：中文分词（SmartChineseAnalyzer），离线可用
- **双模式检索**：本地 Lucene（默认） / 远程 Open API（签名认证）
- **配置验证**：启动时自动验证配置，快速失败
- **增量更新**：SHA256 比对，按需下载
- **多版本共存**：`groupId/artifactId/version/` Maven 风格目录

## 📦 技术栈

- Java 17, Maven 3.9+
- Spring Boot 3.4.3, Spring AI MCP Server 1.1.0
- Apache Lucene 9.12.3, Hutool 5.8.42

## 🎯 快速开始

### 构建

```bash
git clone https://github.com/itxaiohanglover/mcp-codestyle-server.git
cd mcp-codestyle-server
mvn clean package -DskipTests
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

## 🔧 配置说明

### 配置文件 (application.yml)

```yaml
repository:
  # 本地缓存路径（可通过环境变量 CODESTYLE_CACHE_PATH 覆盖）
  local-path: ${CODESTYLE_CACHE_PATH:./mcp-cache}
  
  # 远程检索配置
  remote:
    # 是否启用远程检索（可通过环境变量 CODESTYLE_REMOTE_ENABLED 覆盖）
    enabled: ${CODESTYLE_REMOTE_ENABLED:false}
    
    # 远程服务地址（建议在配置文件中设置，不同环境使用不同的 application-{profile}.yml）
    base-url: http://localhost:8000
    
    # Access Key 和 Secret Key（建议在配置文件中设置，或使用配置中心）
    access-key: ""
    secret-key: ""
    
    # 超时时间（毫秒）
    timeout-ms: 10000
```

### 环境变量

| 变量名 | 说明 | 默认值 | 使用场景 |
|--------|------|--------|----------|
| `CODESTYLE_CACHE_PATH` | 本地缓存路径 | `./mcp-cache` | 不同环境使用不同的缓存目录 |
| `CODESTYLE_REMOTE_ENABLED` | 是否启用远程检索 | `false` | 开发环境关闭，生产环境开启 |

**注意**: 其他配置（base-url, access-key, secret-key, timeout-ms）建议在 `application.yml` 中直接配置，或使用 Spring Profile 管理不同环境的配置。

### 配置管理最佳实践

#### 开发环境

```bash
# 环境变量
export CODESTYLE_CACHE_PATH=/tmp/mcp-cache
export CODESTYLE_REMOTE_ENABLED=false

# application.yml（使用默认配置即可）
```

#### 生产环境

```bash
# 环境变量
export CODESTYLE_CACHE_PATH=/data/codestyle/mcp-cache
export CODESTYLE_REMOTE_ENABLED=true
```

```yaml
# application-prod.yml
repository:
  remote:
    base-url: https://api.codestyle.top
    access-key: your-production-ak
    secret-key: your-production-sk
    timeout-ms: 10000
```

```bash
# 启动命令
java -jar codestyle-server.jar --spring.profiles.active=prod
```

#### 使用配置中心（推荐）

如果使用 Spring Cloud Config 或 Nacos：

```yaml
# bootstrap.yml
spring:
  cloud:
    config:
      uri: http://config-server:8888
      name: mcp-codestyle-server
      profile: ${SPRING_PROFILES_ACTIVE:dev}
```

配置中心统一管理 `access-key` 和 `secret-key`，避免敏感信息泄露。

### 获取 Access Key 和 Secret Key

1. 登录 CodeStyle 管理后台
2. 进入【能力开放】→【应用管理】
3. 创建新应用，获取 AK/SK
4. 在 `application.yml` 或配置中心中配置

## 🛠️ MCP 工具

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

## 📁 模板仓库结构

```
mcp-cache/
├── lucene-index/           # Lucene 索引
└── {groupId}/{artifactId}/
    ├── meta.json           # 元数据（多版本）
    └── {version}/          # 模板文件
        └── README.md       # 模板描述（缓存）
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

## ❓ 常见问题

### 启动报错

**Q: "本地缓存路径未配置"**

A: 请设置 `repository.local-path` 或环境变量 `CODESTYLE_CACHE_PATH`

**Q: "远程检索已启用，但未配置 access-key"**

A: 请在 CodeStyle 管理后台创建 Open API 应用获取 AK/SK，然后在 `application.yml` 中配置

### 运行时错误

**Q: 签名验证失败**

A: 请检查 AK/SK 是否正确，应用是否已启用且未过期

**Q: 请求超时**

A: 请检查网络连接，或增加 `timeout-ms` 配置

**Q: 远程检索失败**

A: 系统会自动降级到本地 Lucene 检索

### 维护操作

| 操作 | 方法 |
|------|------|
| 清理缓存 | 删除 `mcp-cache/` 目录 |
| 重建索引 | 删除 `lucene-index/` 目录，重启服务 |
| 强制更新模板 | 删除对应的 `{groupId}/{artifactId}` 目录 |

## 📝 更新日志

查看 [CHANGELOG.md](CHANGELOG.md) 了解详细的版本更新记录。

### v2.1.0 (2026-02-21) ✨

**代码简化**：
- 移除 `RepositoryConfig` 中的冗余 getter 方法，利用 Lombok `@Data` 自动生成
- 代码行数减少 70 行（-22%）

**配置优化**：
- 简化环境变量配置，从 6 个减少到 2 个
- 只保留 `CODESTYLE_CACHE_PATH` 和 `CODESTYLE_REMOTE_ENABLED`
- 其他配置建议在 `application.yml` 中管理

**最佳实践**：
- 新增配置管理最佳实践文档
- 推荐使用 Spring Profile 管理不同环境配置
- 推荐使用配置中心管理敏感信息

### v2.0.0 (2026-02-21) 🎉

**破坏性变更**：
- 移除所有旧版配置支持，不再兼容 v1.x
- 统一使用 Open API 签名认证（ContiNew 标准）

**新功能**：
- 新增配置验证器，启动时自动验证配置
- 优化错误处理，提供清晰的错误提示
- 简化配置结构，移除冗余配置项

**升级指南**：查看 [CHANGELOG.md](CHANGELOG.md#migration-guide-v1x--v20)

### v1.0.2 (2026-02-17)

- 支持本地 Lucene 全文检索
- 支持远程模板下载（使用 API Key）
- 支持模板智能缓存

## 📄 许可证

[MIT License](LICENSE)

## 👥 作者

- artboy (itxaiohanglover)
- Kanttha
- movclantian
- cris_tofu (gccszs)

## 🔗 相关链接

- [详细升级规划](docs/UPGRADE_PLAN_V2_DETAILED.md)
- [升级总结](docs/UPGRADE_SUMMARY.md)
- [Codestyle Skill 集成](codestyle-skill/README.md)
