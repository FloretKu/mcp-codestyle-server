# MCP Codestyle Server v2.1.0 进一步优化规划

**规划时间**: 2026-02-21  
**当前版本**: v2.0.0  
**目标版本**: v2.1.0  
**优化类型**: 代码简化 + 配置优化

---

## 📋 优化目标

进一步简化代码，利用 Lombok `@Data` 注解自动生成 getter/setter，移除冗余的手动 getter 方法，优化环境变量配置。

---

## 🔍 问题分析

### 1. 冗余的 Getter 方法

**问题**:
- `RepositoryConfig` 类已使用 `@Data` 注解
- Lombok 会自动生成所有字段的 getter/setter
- 手动编写的 getter 方法（81-114 行）完全冗余

**影响**:
- 代码冗余，增加维护成本
- 违反 DRY（Don't Repeat Yourself）原则
- 容易产生不一致（手动方法 vs 自动生成）

### 2. 环境变量配置过多

**问题**:
- 当前配置了 5 个环境变量
- 实际只需要 2 个核心变量：`CODESTYLE_CACHE_PATH` 和 `CODESTYLE_REMOTE_ENABLED`
- 其他配置（base-url, ak, sk, timeout）应该通过配置文件管理

**影响**:
- 环境变量过多，配置复杂
- 不符合 12-Factor App 原则（配置与代码分离）
- 增加部署和维护难度

---

## 🎯 优化方案

### 方案 1: 移除冗余 Getter 方法

#### 1.1 分析 Lombok @Data 生成的方法

`@Data` 注解会自动生成：
```java
// 自动生成的方法
public String getLocalPath() { return localPath; }
public RemoteConfig getRemote() { return remote; }

// RemoteConfig 内部也有 @Data
public boolean isEnabled() { return enabled; }
public String getBaseUrl() { return baseUrl; }
public String getAccessKey() { return accessKey; }
public String getSecretKey() { return secretKey; }
public int getTimeoutMs() { return timeoutMs; }
```

#### 1.2 需要保留的自定义方法

只保留有业务逻辑的方法：
```java
/**
 * 获取仓库目录路径（有业务逻辑：拼接路径）
 */
public String getRepositoryDir() {
    String basePath = localPath != null ? localPath : System.getProperty("java.io.tmpdir");
    return basePath + File.separator + "codestyle-cache";
}
```

#### 1.3 需要删除的冗余方法

以下方法完全冗余，应该删除：
```java
// 删除：Lombok 已生成 remote.isEnabled()
public boolean isRemoteEnabled() {
    return remote.enabled;
}

// 删除：Lombok 已生成 remote.getBaseUrl()
public String getRemoteBaseUrl() {
    return remote.baseUrl;
}

// 删除：Lombok 已生成 remote.getAccessKey()
public String getAccessKey() {
    return remote.accessKey;
}

// 删除：Lombok 已生成 remote.getSecretKey()
public String getSecretKey() {
    return remote.secretKey;
}

// 删除：Lombok 已生成 remote.getTimeoutMs()
public int getTimeoutMs() {
    return remote.timeoutMs;
}
```

#### 1.4 调用方代码修改

**修改前**:
```java
// TemplateService.java
public boolean isRemoteSearchEnabled() {
    return repositoryConfig.isRemoteEnabled();
}

// CodestyleClient.searchFromRemote()
String remoteBaseUrl = repositoryConfig.getRemoteBaseUrl();
String accessKey = repositoryConfig.getAccessKey();
String secretKey = repositoryConfig.getSecretKey();
int timeoutMs = repositoryConfig.getTimeoutMs();
```

**修改后**:
```java
// TemplateService.java
public boolean isRemoteSearchEnabled() {
    return repositoryConfig.getRemote().isEnabled();
}

// CodestyleClient.searchFromRemote()
String remoteBaseUrl = repositoryConfig.getRemote().getBaseUrl();
String accessKey = repositoryConfig.getRemote().getAccessKey();
String secretKey = repositoryConfig.getRemote().getSecretKey();
int timeoutMs = repositoryConfig.getRemote().getTimeoutMs();
```

---

### 方案 2: 简化环境变量配置

#### 2.1 当前配置（过于复杂）

```yaml
repository:
  local-path: ${CODESTYLE_CACHE_PATH:./mcp-cache}
  remote:
    enabled: ${CODESTYLE_REMOTE_ENABLED:false}
    base-url: ${CODESTYLE_BASE_URL:http://localhost:8000}
    access-key: ${CODESTYLE_AK:}
    secret-key: ${CODESTYLE_SK:}
    timeout-ms: ${CODESTYLE_TIMEOUT:10000}
```

**问题**:
- 5 个环境变量，配置复杂
- base-url, ak, sk 应该在配置文件中管理
- 环境变量应该只用于环境相关配置

#### 2.2 优化后配置（简洁明了）

```yaml
repository:
  # 本地缓存路径（可通过环境变量覆盖）
  local-path: ${CODESTYLE_CACHE_PATH:./mcp-cache}
  
  # 远程检索配置
  remote:
    # 是否启用远程检索（可通过环境变量覆盖）
    enabled: ${CODESTYLE_REMOTE_ENABLED:false}
    
    # 以下配置建议在配置文件中直接设置，不使用环境变量
    base-url: http://localhost:8000
    access-key: ""
    secret-key: ""
    timeout-ms: 10000
```

#### 2.3 配置管理策略

**环境变量（2 个）**:
- `CODESTYLE_CACHE_PATH` - 缓存路径（不同环境可能不同）
- `CODESTYLE_REMOTE_ENABLED` - 是否启用远程（开发/生产环境切换）

**配置文件管理**:
- `base-url` - 在 application.yml 或 application-prod.yml 中配置
- `access-key` - 在 application.yml 中配置（或使用 Spring Cloud Config）
- `secret-key` - 在 application.yml 中配置（或使用 Spring Cloud Config）
- `timeout-ms` - 在 application.yml 中配置

**最佳实践**:
```bash
# 开发环境
export CODESTYLE_CACHE_PATH=/tmp/mcp-cache
export CODESTYLE_REMOTE_ENABLED=false

# 生产环境
export CODESTYLE_CACHE_PATH=/data/codestyle/mcp-cache
export CODESTYLE_REMOTE_ENABLED=true

# application-prod.yml
repository:
  remote:
    base-url: https://api.codestyle.top
    access-key: your-production-ak
    secret-key: your-production-sk
```

---

## 📝 实施步骤

### 第 1 步: 修改 RepositoryConfig.java

**删除冗余方法**:
```java
// 删除 81-114 行的所有手动 getter 方法
// 只保留 getRepositoryDir() 方法（有业务逻辑）
```

**最终代码**:
```java
@Data
@Configuration
@ConfigurationProperties(prefix = "repository")
public class RepositoryConfig {

    private String localPath;
    private RemoteConfig remote = new RemoteConfig();

    @Data
    public static class RemoteConfig {
        private boolean enabled = false;
        private String baseUrl;
        private String accessKey;
        private String secretKey;
        private int timeoutMs = 10000;
    }

    /**
     * 获取仓库目录路径
     */
    public String getRepositoryDir() {
        String basePath = localPath != null ? localPath : System.getProperty("java.io.tmpdir");
        return basePath + File.separator + "codestyle-cache";
    }

    @Bean
    public Path repositoryDirectory() {
        // ... 保持不变
    }
}
```

**代码行数**: 从 120 行减少到 45 行（-62%）

---

### 第 2 步: 修改调用方代码

#### 2.1 TemplateService.java

**修改**:
```java
// 修改 isRemoteSearchEnabled() 方法
public boolean isRemoteSearchEnabled() {
    return repositoryConfig.getRemote().isEnabled();
}

// 修改 searchFromRemote() 方法
public List<CodestyleClient.RemoteSearchResult> searchFromRemote(String templateKeyword) {
    RepositoryConfig.RemoteConfig remote = repositoryConfig.getRemote();
    return CodestyleClient.searchFromRemote(
        remote.getBaseUrl(), 
        templateKeyword, 
        remote.getAccessKey(), 
        remote.getSecretKey(), 
        remote.getTimeoutMs()
    );
}

// 修改 downloadTemplate() 方法
public boolean downloadTemplate(CodestyleClient.RemoteSearchResult result) {
    String localRepoPath = repositoryConfig.getRepositoryDir();
    String remoteBaseUrl = repositoryConfig.getRemote().getBaseUrl();
    return CodestyleClient.downloadTemplate(localRepoPath, remoteBaseUrl, result);
}
```

#### 2.2 RepositoryConfigValidator.java

**修改**:
```java
private void validateRemoteConfig() {
    RepositoryConfig.RemoteConfig remote = config.getRemote();
    String baseUrl = remote.getBaseUrl();
    String accessKey = remote.getAccessKey();
    String secretKey = remote.getSecretKey();
    
    // ... 其余验证逻辑保持不变
}
```

---

### 第 3 步: 简化 application.yml

**修改前**:
```yaml
repository:
  local-path: ${CODESTYLE_CACHE_PATH:./mcp-cache}
  remote:
    enabled: ${CODESTYLE_REMOTE_ENABLED:false}
    base-url: ${CODESTYLE_BASE_URL:http://localhost:8000}
    access-key: ${CODESTYLE_AK:}
    secret-key: ${CODESTYLE_SK:}
    timeout-ms: ${CODESTYLE_TIMEOUT:10000}
```

**修改后**:
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

---

### 第 4 步: 更新 README.md

#### 4.1 更新环境变量说明

**修改前**:
```markdown
### 环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `CODESTYLE_CACHE_PATH` | 本地缓存路径 | `./mcp-cache` |
| `CODESTYLE_REMOTE_ENABLED` | 是否启用远程检索 | `false` |
| `CODESTYLE_BASE_URL` | 远程服务地址 | `http://localhost:8000` |
| `CODESTYLE_AK` | Access Key | - |
| `CODESTYLE_SK` | Secret Key | - |
| `CODESTYLE_TIMEOUT` | 超时时间（毫秒） | `10000` |
```

**修改后**:
```markdown
### 环境变量

| 变量名 | 说明 | 默认值 | 使用场景 |
|--------|------|--------|----------|
| `CODESTYLE_CACHE_PATH` | 本地缓存路径 | `./mcp-cache` | 不同环境使用不同的缓存目录 |
| `CODESTYLE_REMOTE_ENABLED` | 是否启用远程检索 | `false` | 开发环境关闭，生产环境开启 |

**注意**: 其他配置（base-url, access-key, secret-key, timeout-ms）建议在 `application.yml` 中直接配置，或使用 Spring Profile 管理不同环境的配置。
```

#### 4.2 新增配置管理最佳实践

```markdown
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
```

---

### 第 5 步: 更新 CHANGELOG.md

```markdown
## [2.1.0] - 2026-02-21

### 🔧 Improvements

- **代码简化**: 移除 `RepositoryConfig` 中的冗余 getter 方法，利用 Lombok `@Data` 自动生成
- **配置优化**: 简化环境变量配置，只保留 2 个核心变量（`CODESTYLE_CACHE_PATH`, `CODESTYLE_REMOTE_ENABLED`）
- **最佳实践**: 新增配置管理最佳实践文档，推荐使用 Spring Profile 管理不同环境配置

### 📝 Changes

- `RepositoryConfig.java`: 删除 5 个冗余 getter 方法，代码行数减少 62%
- `TemplateService.java`: 调用方式改为 `repositoryConfig.getRemote().getXxx()`
- `RepositoryConfigValidator.java`: 调用方式改为 `config.getRemote().getXxx()`
- `application.yml`: 移除 3 个环境变量占位符（`CODESTYLE_BASE_URL`, `CODESTYLE_AK`, `CODESTYLE_SK`）
- `README.md`: 更新环境变量说明，新增配置管理最佳实践

### ⚠️ Breaking Changes

**调用方式变更**:
```java
// 旧版（v2.0.0）
repositoryConfig.isRemoteEnabled()
repositoryConfig.getRemoteBaseUrl()
repositoryConfig.getAccessKey()

// 新版（v2.1.0）
repositoryConfig.getRemote().isEnabled()
repositoryConfig.getRemote().getBaseUrl()
repositoryConfig.getRemote().getAccessKey()
```

**环境变量变更**:
- 移除 `CODESTYLE_BASE_URL`（改为在配置文件中设置）
- 移除 `CODESTYLE_AK`（改为在配置文件中设置）
- 移除 `CODESTYLE_SK`（改为在配置文件中设置）
- 移除 `CODESTYLE_TIMEOUT`（改为在配置文件中设置）
```

---

## 📊 优化效果

### 代码简化

| 文件 | 优化前 | 优化后 | 减少 |
|------|--------|--------|------|
| RepositoryConfig.java | 120 行 | 45 行 | -75 行 (-62%) |
| TemplateService.java | 120 行 | 125 行 | +5 行 (+4%) |
| RepositoryConfigValidator.java | 75 行 | 75 行 | 0 行 |
| **总计** | **315 行** | **245 行** | **-70 行 (-22%)** |

### 配置简化

| 配置项 | 优化前 | 优化后 | 说明 |
|--------|--------|--------|------|
| 环境变量 | 6 个 | 2 个 | 减少 4 个 |
| 配置文件行数 | 12 行 | 15 行 | 增加注释 |
| 配置复杂度 | 高 | 低 | 更符合最佳实践 |

---

## ✅ 验收标准

### 功能验收

- [ ] 编译通过，无错误和警告
- [ ] 所有调用方代码已更新
- [ ] 配置验证器正常工作
- [ ] 远程检索功能正常

### 代码质量

- [ ] 无冗余代码
- [ ] 符合 DRY 原则
- [ ] 充分利用 Lombok 特性
- [ ] 代码行数减少 20% 以上

### 配置管理

- [ ] 环境变量减少到 2 个
- [ ] 配置文件结构清晰
- [ ] 支持 Spring Profile
- [ ] 文档完善

---

## 🚀 实施时间估算

| 步骤 | 工作量 | 说明 |
|------|--------|------|
| 修改 RepositoryConfig | 0.5h | 删除冗余方法 |
| 修改调用方代码 | 1h | 更新 3 个文件 |
| 简化 application.yml | 0.5h | 移除环境变量占位符 |
| 更新 README.md | 1h | 更新文档和最佳实践 |
| 更新 CHANGELOG.md | 0.5h | 记录变更 |
| 编译测试 | 0.5h | 验证功能 |
| **总计** | **4h** | **半个工作日** |

---

## 🎯 预期收益

### 短期收益

1. **代码更简洁**: 减少 70 行代码，提升可读性
2. **配置更简单**: 只需配置 2 个环境变量
3. **维护更容易**: 利用 Lombok 自动生成，减少手动维护

### 长期收益

1. **符合最佳实践**: 配置与代码分离，环境变量最小化
2. **易于扩展**: 新增配置项只需在 RemoteConfig 中添加字段
3. **降低出错率**: 减少手动代码，减少人为错误

---

## ⚠️ 风险评估

### 破坏性变更

| 变更 | 影响 | 缓解措施 |
|------|------|---------|
| 调用方式变更 | 需要更新所有调用代码 | 编译时会报错，容易发现 |
| 环境变量移除 | 现有部署脚本需要更新 | 提供迁移指南 |

### 技术风险

| 风险 | 概率 | 影响 | 应对措施 |
|------|------|------|---------|
| Lombok 版本兼容 | 低 | 低 | 已使用 Lombok 1.18.36 |
| 调用方遗漏 | 中 | 中 | 编译检查 + 代码审查 |
| 配置错误 | 低 | 中 | 配置验证器会检查 |

---

## 📝 迁移指南

### 代码迁移

如果你的代码中使用了以下方法，需要更新：

```java
// 旧版（需要更新）
repositoryConfig.isRemoteEnabled()
repositoryConfig.getRemoteBaseUrl()
repositoryConfig.getAccessKey()
repositoryConfig.getSecretKey()
repositoryConfig.getTimeoutMs()

// 新版（推荐）
RepositoryConfig.RemoteConfig remote = repositoryConfig.getRemote();
remote.isEnabled()
remote.getBaseUrl()
remote.getAccessKey()
remote.getSecretKey()
remote.getTimeoutMs()
```

### 配置迁移

**环境变量**:
```bash
# 移除这些环境变量
unset CODESTYLE_BASE_URL
unset CODESTYLE_AK
unset CODESTYLE_SK
unset CODESTYLE_TIMEOUT

# 只保留这两个
export CODESTYLE_CACHE_PATH=/data/codestyle/mcp-cache
export CODESTYLE_REMOTE_ENABLED=true
```

**配置文件**:
```yaml
# application-prod.yml（新增）
repository:
  remote:
    base-url: https://api.codestyle.top
    access-key: your-production-ak
    secret-key: your-production-sk
    timeout-ms: 10000
```

---

## 🎉 总结

这次优化进一步简化了代码和配置：

1. **代码简化**: 利用 Lombok 特性，减少 70 行冗余代码
2. **配置优化**: 环境变量从 6 个减少到 2 个
3. **最佳实践**: 符合 12-Factor App 和 Spring Boot 最佳实践
4. **易于维护**: 代码更简洁，配置更清晰

这是一次小而美的优化，预计 4 小时即可完成，收益明显。

---

**规划者**: AI Assistant  
**审核者**: 待定  
**批准者**: 待定  
**最后更新**: 2026-02-21

