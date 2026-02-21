<div align="center">
  <img src="../logo.png" alt="Codestyle Logo" width="150"/>
  
  # Codestyle Skill
  
  代码模板检索与生成的 Claude Code Skill，支持自动安装和配置。
  
  [![License](https://img.shields.io/badge/license-MIT-blue.svg)](../LICENSE)
  [![Version](https://img.shields.io/badge/version-1.0.0-green.svg)](CHANGELOG.md)
</div>

---

## ✨ 特性

- 🚀 **零配置**: 首次使用自动下载 JAR 和模板仓库
- 🔍 **智能搜索**: 支持关键词、中文、路径搜索
- 📦 **自动更新**: 单命令更新 JAR 和模板
- 🌍 **跨平台**: 完整支持 Windows、Linux、macOS
- 🎯 **开箱即用**: 安装即用，无需配置

## 🚀 快速开始

### 安装

**方式 1: 通过 Git（推荐）**

```bash
# 1. Clone 仓库
git clone https://github.com/itxaiohanglover/mcp-codestyle-server.git

# 2. 安装到 Claude Skills
cp -r mcp-codestyle-server/skill/codestyle ~/.claude/skills/

# 3. 重启 Claude Code
```

**方式 2: 手动下载**

1. 下载 [最新版本](https://github.com/itxaiohanglover/mcp-codestyle-server/releases/latest)
2. 解压到 `~/.claude/skills/codestyle`
3. 重启 Claude Code

**方式 3: npx工具一键安装 (推荐)**

输入如下命令，按照CLI界面指引操作直接完成安装！
```bash
  npx skills add itxaiohanglover/mcp-codestyle-server
```

### 首次使用

启动 Claude Code 后，直接说：

```
帮我生成 User 实体的 CRUD 代码
```

系统会自动：
1. 下载 codestyle-server.jar（~37 MB）
2. 克隆模板仓库
3. 构建搜索索引
4. 返回搜索结果

**总耗时**: 首次约 30-60 秒，后续 < 1 秒

## 💡 使用示例

### 在 Claude Code 中使用

```
# 生成 CRUD 代码
帮我生成 User 实体的 CRUD 代码

# 生成登录模块
创建一个登录模块

# 生成数据导出功能
生成数据导出功能

# 使用斜杠命令
/codestyle CRUD
```

### 手动命令行使用

**搜索模板**:
```bash
# Linux/macOS
cd ~/.claude/skills/codestyle/scripts
bash codestyle search "CRUD"

# Windows
cd %USERPROFILE%\.claude\skills\codestyle\scripts
codestyle.bat search "CRUD"
```

**获取模板内容**:
```bash
# Linux/macOS
bash codestyle get "continew/CRUD/1.0.0/bankend/src/main/java/com/air/controller/Controller.ftl"

# Windows
codestyle.bat get "continew/CRUD/1.0.0/bankend/src/main/java/com/air/controller/Controller.ftl"
```

**更新到最新版本**:
```bash
# Linux/macOS
bash update.sh

# Windows
update.bat
```

## 📁 目录结构

```
codestyle/
├── SKILL.md                    # Skill 定义（YAML frontmatter + 工作流）
├── scripts/
│   ├── codestyle               # CLI 包装脚本（Linux/macOS）
│   ├── codestyle.bat           # CLI 包装脚本（Windows）
│   ├── install.sh              # JAR 安装脚本（Linux/macOS）
│   ├── install.bat             # JAR 安装脚本（Windows）
│   ├── update.sh               # 更新脚本（Linux/macOS）
│   ├── update.bat              # 更新脚本（Windows）
│   ├── init-repository.sh      # 仓库初始化（Linux/macOS）
│   ├── init-repository.bat     # 仓库初始化（Windows）
│   ├── cfg.json                # 默认配置
│   ├── .gitignore              # Git 忽略规则
│   └── codestyle-server.jar    # JAR 文件（自动下载）
└── references/
    ├── config.md               # 配置文档
    └── template-syntax.md      # 模板语法文档
```

## 🔧 配置

### 默认配置（推荐）

首次使用时，Skill 会使用默认配置，模板存储在 `~/.codestyle/cache`，无需额外配置。

### 自定义配置（可选）

如需自定义，可以修改 `~/.claude/skills/codestyle/scripts/cfg.json`。

详细配置说明请参考：[配置文档](codestyle/references/config.md)

## ⚙️ 前置要求

### 必需
- **JDK 17+** - 运行 JAR 文件
  - 检查: `java -version`
  - 下载: https://adoptium.net/
- **Git** - 克隆模板仓库
  - Windows: https://git-scm.com/download/win
  - macOS: `brew install git`
  - Linux: `sudo apt install git` 或 `sudo yum install git`
- **网络连接** - 首次使用需要下载

### 可选
- **curl 或 wget** - 下载 JAR 文件
  - Windows 10+ 内置 curl
  - PowerShell 可作为备选

## 🐛 故障排除

### 问题 1: JAR 下载失败

**现象**: `❌ Download failed`

**解决方案**:
1. 检查网络连接
2. 手动下载 JAR:
   ```bash
   cd ~/.claude/skills/codestyle/scripts
   curl -L https://github.com/itxaiohanglover/mcp-codestyle-server/releases/download/v2.1.0/codestyle-server.jar -o codestyle-server.jar
   ```

### 问题 2: Git 克隆失败

**现象**: `❌ Clone failed`

**解决方案**:
1. 检查 Git 安装: `git --version`
2. 检查网络连接: `ping github.com`
3. 手动下载模板:
   ```bash
   cd ~/.codestyle/cache
   git clone https://github.com/itxaiohanglover/codestyle-repository.git codestyle-cache
   ```

### 问题 3: 搜索不到模板

**现象**: `未找到匹配的模板`

**解决方案**:
1. 检查模板仓库是否初始化:
   ```bash
   ls ~/.codestyle/cache/codestyle-cache
   ```
2. 手动初始化:
   ```bash
   cd ~/.claude/skills/codestyle/scripts
   bash init-repository.sh  # Linux/macOS
   init-repository.bat      # Windows
   ```

### 问题 4: 权限错误

**Windows**: 以管理员身份运行 PowerShell

**Linux/macOS**:
```bash
chmod +x ~/.claude/skills/codestyle/scripts/*
```

### 问题 5: Java 版本过低

**现象**: `UnsupportedClassVersionError`

**解决方案**: 升级到 JDK 17+
```bash
# 检查版本
java -version

# 下载 JDK 17+
# https://adoptium.net/
```

## 🔄 更新

### 自动更新检查

Skill 会在使用时检查新版本（可选功能）。

### 手动更新

**更新 JAR 文件**:
```bash
cd ~/.claude/skills/codestyle/scripts
bash update.sh  # Linux/macOS
update.bat      # Windows
```

**更新 Skill 本身**:
```bash
cd ~/mcp-codestyle-server
git pull
cp -r skill/codestyle ~/.claude/skills/
```

## 📚 文档

- [配置文档](codestyle/references/config.md) - 详细配置说明
- [模板语法](codestyle/references/template-syntax.md) - 模板格式和变量规则
- [更新日志](CHANGELOG.md) - 版本更新日志
- [GitHub 仓库](https://github.com/itxaiohanglover/mcp-codestyle-server)

## 🤝 贡献

欢迎贡献代码、报告问题或提出建议！

1. Fork 仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 📄 许可证

[MIT License](../LICENSE)

## 👥 作者

- **artboy** (itxaiohanglover) - 项目负责人
- **Kanttha** - 核心开发
- **movclantian** - 核心开发
- **cris_tofu** (gccszs) - 贡献者

## 🔗 相关链接

- [GitHub 仓库](https://github.com/itxaiohanglover/mcp-codestyle-server)
- [问题反馈](https://github.com/itxaiohanglover/mcp-codestyle-server/issues)
- [讨论区](https://github.com/itxaiohanglover/mcp-codestyle-server/discussions)
- [最新版本](https://github.com/itxaiohanglover/mcp-codestyle-server/releases/latest)

