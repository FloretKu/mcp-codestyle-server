# MCP Codestyle Server 优化升级详细规划

**版本**: v2.0.0  
**规划时间**: 2026-02-21  
**当前版本**: v1.0.2

---

## 目录

1. [问题分析](#问题分析)
2. [优化目标](#优化目标)
3. [详细方案](#详细方案)
4. [实施计划](#实施计划)
5. [代码质量保证](#代码质量保证)
6. [验收标准](#验收标准)

---

## 问题分析

### 1. 远程检索未启用

**问题**: 
- `CodestyleClient.java` 已实现 `searchFromRemote()` 方法（使用 Open API 签名认证）
- `CodestyleService.java` 第 45 行仍调用旧方法 `fetchRemoteMetaConfig()`
- 配置文件中的 `access-key` 和 `secret-key` 未被使用

**影响**:
- 新的签名认证功能无法使用
- 安全性较低
- 配置项无效

### 2. 配置冗余

**问题**:
- 新版配置: `repository.remote.enabled`, `repository.remote.base-url`
- 旧版配置: `repository.remote-search-enabled`, `repository.remote-path`
- RepositoryConfig 中大量兼容方法

**影响**:
- 用户困惑，不知道用哪个
- 代码维护成本高
- 配置文件冗长

### 3. 数据模型混乱

**问题**:
- `RemoteSearchResult`: 新模型，包含 MCP 字段
- `RemoteMetaConfig`: 旧模型，传统结构
- 需要转换方法 `toRemoteMetaConfig()`

**影响**:
- 代码复杂
- 容易出错
- 理解困难

### 4. 缺少验证机制

**问题**:
- 配置错误时无法及时发现
- 错误提示不友好
- 启动后才发现问题

**影响**:
- 调试困难
- 用户体验差

---

## 优化目标

### 设计原则

1. **简单优先**: 移除所有旧版兼容代码，不考虑向后兼容
2. **统一标准**: 只保留新版 Open API 签名认证
3. **快速失败**: 配置错误立即报错，不要等到运行时
4. **清晰明确**: 错误提示友好，指导用户如何修复

### 核心目标

- ✅ 统一使用 Open API 签名认证
- ✅ 移除所有旧版配置和代码
- ✅ 简化数据模型（RemoteSearchResult 即可满足需求）
- ✅ 完善配置验证和错误处理

---

## 详细方案

### 方案 1: 统一远程检索接口

#### 1.1 修改 CodestyleService

**文件**: `service/CodestyleService.java`

**当前代码** (第 45 行):
```java
List<RemoteMetaConfig> remoteResults = templateService.fetchRemoteMetaConfig(templateKeyword);
```

**修改为**:
```java
List<RemoteSearchResult> remoteResults = templateService.searchFromRemote(templateKeyword);

if (remoteResults.isEmpty()) {
    return searchLocalFallback(templateKeyword, true);
}

if (remoteResults.size() == 1) {
    RemoteSearchResult result = remoteResults.get(0);
    // 直接使用 result 下载模板
    templateService.downloadTemplate(result);
    
    List<MetaInfo> metaInfos = templateService.searchLocalRepository(
        result.getGroupId(), result.getArtifactId());
    
    if (metaInfos.isEmpty()) {
        return "本地仓库模板文件不完整";
    }
    
    TreeNode treeNode = PromptUtils.buildTree(metaInfos);
    String treeStr = PromptUtils.buildTreeStr(treeNode, "").trim();
    return promptService.buildSearchResult(
        result.getArtifactId(), treeStr, result.getDescription());
}

// 多个结果
return promptService.buildRemoteMultiResultResponse(templateKeyword, remoteResults);
```

#### 1.2 修改 TemplateService

**文件**: `service/TemplateService.java`

**删除方法**:
- `fetchRemoteMetaConfig()` - 完全删除
- `buildRemoteMultiResultResponse(String, List<RemoteMetaConfig>)` - 完全删除

**新增方法**:
```java
/**
 * 下载远程模板
 * 
 * @param result 远程检索结果
 * @return 是否成功
 */
public boolean downloadTemplate(RemoteSearchResult result) {
    String localRepoPath = repositoryConfig.getRepositoryDir();
    String remoteBaseUrl = repositoryConfig.getRemoteBaseUrl();
    
    return CodestyleClient.downloadTemplate(localRepoPath, remoteBaseUrl, result);
}
```

### 方案 2: 简化数据模型

#### 2.1 RemoteSearchResult 定义

**文件**: `util/CodestyleClient.java`

**设计思路**: RemoteSearchResult 应该包含 MCP 所需的所有信息，无需转换

```java
public static class RemoteSearchResult {
    // 基础信息
    private String id;
    private String title;
    private String description;
    
    // MCP 必要字段（用于定位和下载）
    private String groupId;
    private String artifactId;
    private String version;
    
    // 检索相关
    private Double score;
    private String highlight;
    
    // 文件信息（用于下载和验证）
    private List<FileInfo> files;
    
    public static class FileInfo {
        private String filePath;
        private String filename;
        private String fileType;
        private String sha256;
    }
    
    // 生成下载 URL
    public String getDownloadUrl(String baseUrl) {
        return String.format("%s/api/template/download?groupId=%s&artifactId=%s&version=%s",
            baseUrl, groupId, artifactId, version);
    }
}
```

**说明**:
- 不需要 `toRemoteMetaConfig()` 方法
- 不需要 `RemoteMetaConfig` 类（可以删除）
- 直接使用 `RemoteSearchResult` 进行所有操作


#### 2.2 新增下载方法

**文件**: `util/CodestyleClient.java`

```java
/**
 * 下载模板（使用 RemoteSearchResult）
 */
public static boolean downloadTemplate(String localRepoPath, String remoteBaseUrl, 
                                       RemoteSearchResult result) {
    try {
        String downloadUrl = result.getDownloadUrl(remoteBaseUrl);
        
        HttpResponse response = HttpRequest.get(downloadUrl)
            .timeout(60000)
            .execute();
            
        if (!response.isOk()) {
            return false;
        }
        
        File zipFile = FileUtil.createTempFile("template-", ".zip", true);
        IoUtil.copy(response.bodyStream(), FileUtil.getOutputStream(zipFile));
        
        String targetPath = localRepoPath + File.separator + 
                           result.getGroupId() + File.separator + 
                           result.getArtifactId();
        
        ZipUtil.unzip(zipFile, new File(targetPath));
        FileUtil.del(zipFile);
        
        return true;
    } catch (Exception e) {
        return false;
    }
}
```

### 方案 3: 移除旧版配置

#### 3.1 清理 RepositoryConfig

**文件**: `config/RepositoryConfig.java`

**删除内容**:
```java
// 删除这些字段
private String remotePath;
private boolean remoteSearchEnabled;
private int remoteSearchTimeoutMs;
private String apiKey;

// 删除所有兼容方法
public String getRemotePath() { ... }
public boolean isRemoteSearchEnabled() { ... }
public int getRemoteSearchTimeoutMs() { ... }
public String getApiKey() { ... }
public String getAccessKey() { ... }  // 删除兼容逻辑
```

**保留内容**:
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
    
    // 简化的 getter 方法
    public boolean isRemoteEnabled() {
        return remote.enabled;
    }
    
    public String getRemoteBaseUrl() {
        return remote.baseUrl;
    }
    
    public String getAccessKey() {
        return remote.accessKey;
    }
    
    public String getSecretKey() {
        return remote.secretKey;
    }
    
    public int getTimeoutMs() {
        return remote.timeoutMs;
    }
    
    public String getRepositoryDir() {
        if (repositoryDir == null || repositoryDir.isEmpty()) {
            return getLocalPath() + File.separator + "codestyle-cache";
        }
        return repositoryDir;
    }
}
```

#### 3.2 更新 application.yml

**文件**: `src/main/resources/application.yml`

```yaml
spring:
  application:
    name: mcp-codestyle-server
  main:
    web-application-type: none
    banner-mode: off
  ai:
    mcp:
      server:
        name: mcp-codestyle-server
        version: 2.0.0
        type: SYNC
        stdio: true
        annotation-scanner:
          enabled: true

logging:
  level:
    root: OFF

repository:
  # 本地缓存路径
  local-path: ${CODESTYLE_CACHE_PATH:E:/kaiyuan/codestyle/mcp-cache}
  
  # 远程检索配置
  remote:
    enabled: ${CODESTYLE_REMOTE_ENABLED:false}
    base-url: ${CODESTYLE_BASE_URL:http://localhost:8000}
    access-key: ${CODESTYLE_AK:}
    secret-key: ${CODESTYLE_SK:}
    timeout-ms: ${CODESTYLE_TIMEOUT:10000}
```

### 方案 4: 配置验证

#### 4.1 创建配置验证器

**新增文件**: `config/RepositoryConfigValidator.java`

```java
package top.codestyle.mcp.config;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 仓库配置验证器
 * 在应用启动时验证配置的有效性
 *
 * @author CodeStyle Team
 * @since 2.0.0
 */
@Component
@RequiredArgsConstructor
public class RepositoryConfigValidator implements ApplicationRunner {

    private final RepositoryConfig config;

    @Override
    public void run(ApplicationArguments args) {
        validateLocalPath();
        
        if (config.isRemoteEnabled()) {
            validateRemoteConfig();
        }
    }

    private void validateLocalPath() {
        String localPath = config.getLocalPath();
        if (StrUtil.isBlank(localPath)) {
            throw new IllegalStateException(
                "本地缓存路径未配置\n" +
                "请设置 repository.local-path 或环境变量 CODESTYLE_CACHE_PATH"
            );
        }
    }

    private void validateRemoteConfig() {
        String baseUrl = config.getRemoteBaseUrl();
        String accessKey = config.getAccessKey();
        String secretKey = config.getSecretKey();

        if (StrUtil.isBlank(baseUrl)) {
            throw new IllegalStateException(
                "远程检索已启用，但未配置 base-url\n" +
                "请设置 repository.remote.base-url 或环境变量 CODESTYLE_BASE_URL"
            );
        }

        if (StrUtil.isBlank(accessKey)) {
            throw new IllegalStateException(
                "远程检索已启用，但未配置 access-key\n" +
                "请在 CodeStyle 管理后台创建 Open API 应用获取 Access Key\n" +
                "然后设置 repository.remote.access-key 或环境变量 CODESTYLE_AK"
            );
        }

        if (StrUtil.isBlank(secretKey)) {
            throw new IllegalStateException(
                "远程检索已启用，但未配置 secret-key\n" +
                "请在 CodeStyle 管理后台创建 Open API 应用获取 Secret Key\n" +
                "然后设置 repository.remote.secret-key 或环境变量 CODESTYLE_SK"
            );
        }

        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            throw new IllegalStateException(
                "base-url 格式错误，必须以 http:// 或 https:// 开头\n" +
                "当前值: " + baseUrl
            );
        }
    }
}
```

### 方案 5: 错误处理优化

#### 5.1 创建统一异常类

**新增文件**: `exception/RemoteSearchException.java`

```java
package top.codestyle.mcp.exception;

/**
 * 远程检索异常
 *
 * @author CodeStyle Team
 * @since 2.0.0
 */
public class RemoteSearchException extends RuntimeException {

    private final ErrorCode errorCode;

    public RemoteSearchException(ErrorCode errorCode, String message) {
        super(String.format("[%s] %s", errorCode.name(), message));
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public enum ErrorCode {
        SIGNATURE_FAILED,
        ACCESS_DENIED,
        TIMEOUT,
        SERVER_ERROR,
        NETWORK_ERROR,
        CONFIG_ERROR
    }
}
```

#### 5.2 优化错误处理

**文件**: `util/CodestyleClient.java`

**修改 searchFromRemote() 方法**:
```java
public static List<RemoteSearchResult> searchFromRemote(...) {
    if (StrUtil.isBlank(accessKey) || StrUtil.isBlank(secretKey)) {
        throw new RemoteSearchException(
            ErrorCode.CONFIG_ERROR,
            "Access Key 或 Secret Key 未配置"
        );
    }

    try {
        // ... 构建请求
        
        HttpResponse response = request.execute();
        int status = response.getStatus();

        if (status == 401) {
            throw new RemoteSearchException(
                ErrorCode.SIGNATURE_FAILED,
                "签名验证失败，请检查 Access Key 和 Secret Key 是否正确"
            );
        }
        
        if (status == 403) {
            throw new RemoteSearchException(
                ErrorCode.ACCESS_DENIED,
                "访问被拒绝，请检查应用是否已启用且未过期"
            );
        }
        
        if (status >= 500) {
            throw new RemoteSearchException(
                ErrorCode.SERVER_ERROR,
                "服务器错误: HTTP " + status
            );
        }
        
        if (status != 200) {
            throw new RemoteSearchException(
                ErrorCode.SERVER_ERROR,
                "请求失败: HTTP " + status
            );
        }

        // ... 解析响应
        
    } catch (RemoteSearchException e) {
        throw e;
    } catch (java.net.SocketTimeoutException e) {
        throw new RemoteSearchException(
            ErrorCode.TIMEOUT,
            "请求超时，请检查网络连接或增加超时时间"
        );
    } catch (Exception e) {
        throw new RemoteSearchException(
            ErrorCode.NETWORK_ERROR,
            "网络错误: " + e.getMessage()
        );
    }
}
```

---

## 实施计划

### 第 1 周: 核心重构

| 任务 | 工作量 | 说明 |
|------|--------|------|
| 清理 RepositoryConfig | 2h | 删除旧配置字段和方法 |
| 修改 CodestyleService | 3h | 使用新的 searchFromRemote() |
| 修改 TemplateService | 3h | 删除旧方法，添加新方法 |
| 简化 RemoteSearchResult | 2h | 移除不必要的字段和方法 |
| 单元测试 | 4h | 测试核心功能 |

### 第 2 周: 验证和优化

| 任务 | 工作量 | 说明 |
|------|--------|------|
| 配置验证器 | 2h | 实现 RepositoryConfigValidator |
| 异常处理 | 3h | 实现 RemoteSearchException |
| 集成测试 | 4h | 端到端测试 |
| 文档更新 | 3h | 更新 README 和配置文档 |
| 代码审查 | 2h | 代码质量检查 |

### 第 3 周: 发布准备

| 任务 | 工作量 | 说明 |
|------|--------|------|
| 性能测试 | 2h | 测试响应时间 |
| 安全测试 | 2h | 测试签名认证 |
| 打包发布 | 2h | 构建 v2.0.0 |
| 部署验证 | 2h | 生产环境验证 |

**总工作量**: 约 40 小时（5 个工作日）


---

## 代码质量保证

### Git 提交规范

#### Commit Message 格式

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Type 类型**:
- `feat`: 新功能
- `fix`: Bug 修复
- `refactor`: 重构（不改变功能）
- `perf`: 性能优化
- `style`: 代码格式调整
- `test`: 测试相关
- `docs`: 文档更新
- `chore`: 构建/工具相关
- `breaking`: 破坏性变更

**示例**:
```
feat(remote): 统一使用 Open API 签名认证

- 移除旧版 fetchRemoteMetaConfig() 方法
- 使用新版 searchFromRemote() 方法
- 简化 RemoteSearchResult 数据模型

BREAKING CHANGE: 移除所有旧版配置支持
```

#### 分支管理

```
main (v1.0.2)
  ↓
develop
  ↓
feature/v2-upgrade
  ├── refactor(config): 移除旧版配置
  ├── feat(remote): 实现新版远程检索
  ├── feat(validation): 添加配置验证器
  ├── feat(exception): 优化错误处理
  └── docs: 更新文档
  ↓
develop (合并后)
  ↓
release/v2.0.0
  ├── chore: 更新版本号到 2.0.0
  └── docs: 更新 CHANGELOG
  ↓
main (v2.0.0)
```

### 代码审查清单

#### 功能审查
- [ ] 远程检索使用 Open API 签名认证
- [ ] 本地检索正常工作
- [ ] 配置验证正常工作
- [ ] 错误处理完善
- [ ] 所有旧版代码已移除

#### 代码质量
- [ ] 无编译警告
- [ ] 无 SonarLint 警告
- [ ] 代码格式符合规范（使用 spotless）
- [ ] 注释完整清晰
- [ ] 无重复代码

#### 测试覆盖
- [ ] 单元测试通过
- [ ] 集成测试通过
- [ ] 测试覆盖率 > 80%
- [ ] 边界条件测试完整

#### 文档完整性
- [ ] README 更新
- [ ] CHANGELOG 更新
- [ ] API 文档更新
- [ ] 配置文档更新

### 自动化检查

#### Maven 配置

**pom.xml 添加插件**:

```xml
<build>
    <plugins>
        <!-- 代码格式检查 -->
        <plugin>
            <groupId>com.diffplug.spotless</groupId>
            <artifactId>spotless-maven-plugin</artifactId>
            <version>2.40.0</version>
            <configuration>
                <java>
                    <googleJavaFormat>
                        <version>1.17.0</version>
                        <style>AOSP</style>
                    </googleJavaFormat>
                    <removeUnusedImports/>
                </java>
            </configuration>
        </plugin>
        
        <!-- 测试覆盖率 -->
        <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
            <version>0.8.11</version>
            <executions>
                <execution>
                    <goals>
                        <goal>prepare-agent</goal>
                    </goals>
                </execution>
                <execution>
                    <id>report</id>
                    <phase>test</phase>
                    <goals>
                        <goal>report</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

#### Pre-commit Hook

**创建文件**: `.git/hooks/pre-commit`

```bash
#!/bin/bash

echo "Running pre-commit checks..."

# 1. 代码格式检查
echo "Checking code format..."
mvn spotless:check
if [ $? -ne 0 ]; then
    echo "Code format check failed. Run 'mvn spotless:apply' to fix."
    exit 1
fi

# 2. 编译检查
echo "Compiling..."
mvn clean compile -DskipTests
if [ $? -ne 0 ]; then
    echo "Compilation failed."
    exit 1
fi

# 3. 单元测试
echo "Running unit tests..."
mvn test
if [ $? -ne 0 ]; then
    echo "Unit tests failed."
    exit 1
fi

echo "All pre-commit checks passed!"
```

#### CI/CD Pipeline

**创建文件**: `.github/workflows/ci.yml`

```yaml
name: CI

on:
  push:
    branches: [ develop, main ]
  pull_request:
    branches: [ develop, main ]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Cache Maven packages
      uses: actions/cache@v3
      with:
        path: ~/.m2
        key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
    
    - name: Build
      run: mvn clean package -DskipTests
    
    - name: Test
      run: mvn test
    
    - name: Check coverage
      run: mvn jacoco:report
    
    - name: Upload coverage
      uses: codecov/codecov-action@v3
```

---

## 验收标准

### 功能验收

- [ ] 远程检索使用 Open API 签名认证
- [ ] 配置错误时启动失败并提示清晰错误信息
- [ ] 远程检索失败自动降级到本地检索
- [ ] 所有旧版配置和代码已移除
- [ ] 下载模板功能正常

### 性能验收

- [ ] 远程检索响应时间 < 3 秒
- [ ] 本地检索响应时间 < 500ms
- [ ] 内存占用 < 200MB
- [ ] 启动时间 < 5 秒

### 代码质量

- [ ] 无编译警告
- [ ] 无 SonarLint 警告
- [ ] 测试覆盖率 > 80%
- [ ] 代码复杂度合理（圈复杂度 < 10）
- [ ] 无重复代码

### 文档完整性

- [ ] README 准确完整
- [ ] CHANGELOG 详细清晰
- [ ] API 文档更新
- [ ] 配置文档更新
- [ ] 迁移指南完整

### 安全性

- [ ] 签名认证正常工作
- [ ] 敏感信息不在日志中输出
- [ ] 配置文件不包含明文密钥
- [ ] 依赖无已知漏洞

---

## 风险评估

### 破坏性变更

| 变更 | 影响 | 缓解措施 |
|------|------|---------|
| 移除旧版配置 | 现有用户无法直接升级 | 发布 v2.0.0 作为大版本升级 |
| 移除旧版方法 | 依赖旧版的代码无法编译 | 明确标注为破坏性变更 |
| 修改数据模型 | API 不兼容 | 提供详细的升级文档 |

### 技术风险

| 风险 | 概率 | 影响 | 应对措施 |
|------|------|------|---------|
| 远程服务不可用 | 中 | 高 | 自动降级到本地检索 |
| 签名验证失败 | 低 | 高 | 详细的错误提示和文档 |
| 性能下降 | 低 | 中 | 性能测试和优化 |
| 配置错误 | 中 | 中 | 配置验证器 |

---

## 后续规划

### v2.1.0（1 个月后）

- [ ] 优化远程检索性能
- [ ] 添加本地缓存机制
- [ ] 支持批量下载模板
- [ ] 添加监控指标

### v2.2.0（2 个月后）

- [ ] 支持模板版本管理
- [ ] 支持模板热更新
- [ ] 添加健康检查接口
- [ ] 支持多语言提示词

### v3.0.0（6 个月后）

- [ ] 支持多个远程仓库
- [ ] 支持向量检索（语义搜索）
- [ ] 支持模板推荐
- [ ] 支持分布式部署

---

## 附录

### 配置示例

#### 开发环境

```yaml
repository:
  local-path: ./mcp-cache
  remote:
    enabled: false
```

#### 生产环境

```yaml
repository:
  local-path: /data/codestyle/mcp-cache
  remote:
    enabled: true
    base-url: https://api.codestyle.top
    access-key: ${CODESTYLE_AK}
    secret-key: ${CODESTYLE_SK}
    timeout-ms: 10000
```

### 常见问题

**Q: 如何获取 Access Key 和 Secret Key？**

A: 
1. 登录 CodeStyle 管理后台
2. 进入【能力开放】→【应用管理】
3. 创建新应用，获取 AK/SK

**Q: 签名验证失败怎么办？**

A: 
1. 检查 AK/SK 是否正确
2. 检查应用是否已启用
3. 检查应用是否已过期
4. 查看详细错误日志

**Q: 远程检索超时怎么办？**

A: 
1. 检查网络连接
2. 增加 timeout-ms 配置
3. 检查远程服务是否正常

---

**规划者**: AI Assistant  
**审核者**: 待定  
**批准者**: 待定  
**最后更新**: 2026-02-21

