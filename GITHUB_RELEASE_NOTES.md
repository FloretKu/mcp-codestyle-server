# Codestyle Server v1.0.0

## 🎉 首个正式版本

Codestyle Server 是一个代码模板搜索和生成工具，支持本地和远程模板仓库。

---

## ✨ 核心功能

### 🔍 智能搜索
- 支持关键词搜索（CRUD, login, export）
- 支持路径搜索（continew/crud-plus）
- 支持模糊匹配和智能建议

### 📦 自动化管理
- **零配置**: 首次使用自动下载 JAR 和模板
- **零确认**: 全程无需用户干预
- **智能等待**: Git 克隆自动等待（最多 60 秒）
- **进度显示**: 实时显示克隆进度

### 🚀 快速生成
- 基于 FreeMarker 的模板引擎
- 支持 CRUD、登录、导出等常用模板
- 支持自定义模板扩展

### 🌐 多平台支持
- ✅ Windows 10+
- ✅ Linux (Ubuntu, CentOS, etc.)
- ✅ macOS

---

## 📥 安装

### 方式 1: 自动安装（推荐）

**Windows**:
```bash
# 直接运行，首次使用会自动初始化
C:\path\to\codestyle.bat search CRUD
```

**Linux/macOS**:
```bash
bash /path/to/codestyle search CRUD
```

首次使用会自动：
1. 下载 JAR 包（~36MB）
2. 克隆模板仓库（~10-30 秒）
3. 执行搜索命令

### 方式 2: 手动安装

1. 下载 `codestyle-server.jar`
2. 放置到: `~/.claude/skills/codestyle/scripts/`
3. 下载模板: https://github.com/itxaiohanglover/codestyle-repository/archive/refs/heads/main.zip
4. 解压到: `~/.codestyle/cache/codestyle-cache`

---

## 📖 使用指南

### 搜索模板

**Windows**:
```bash
"C:\Users\YourName\.claude\skills\codestyle\scripts\codestyle.bat" search "CRUD"
```

**Linux/macOS**:
```bash
bash ~/.claude/skills/codestyle/scripts/codestyle search "CRUD"
```

### 获取模板

```bash
codestyle get "continew/CRUD/1.0.0/bankend/src/main/java/com/air/controller/Controller.ftl"
```

### 常用搜索关键词

| 需求 | 搜索关键词 |
|------|-----------|
| CRUD 功能 | `CRUD`, `controller`, `service` |
| 登录模块 | `login`, `auth`, `user` |
| 数据导出 | `export`, `excel`, `download` |
| 数据库操作 | `entity`, `mapper`, `dao` |

---

## 🔧 系统要求

- **Java**: 17 或更高版本
- **Git**: 2.0+ (首次使用时需要)
- **网络**: 首次使用需要网络连接
- **磁盘空间**: ~100MB (JAR + 模板)

---

## 📝 更新日志

### v1.0.0 (2025-02-21)

#### 新功能
- ✅ 本地模板搜索和检索
- ✅ 自动下载 JAR 包
- ✅ 自动克隆模板仓库
- ✅ 智能等待机制（避免超时）
- ✅ 零用户确认（全自动初始化）
- ✅ 实时进度显示
- ✅ 跨平台支持

#### 优化
- ✅ 修复 Git 克隆超时问题
- ✅ 修复 `cmd.exe` 输出缓冲问题
- ✅ 简化用户交互流程（3 次确认 → 0 次）
- ✅ 改进错误提示和恢复选项
- ✅ 移除 PowerShell/jq/python 依赖

#### 技术细节
- **Git Clone**: 后台执行 + `.git` 目录轮询
- **超时保护**: 60 秒最大等待时间
- **进度显示**: 每 2 秒更新一次（1/30, 2/30...）
- **错误处理**: 克隆失败时提供手动下载方案

---

## 🐛 已知问题

### 网络相关
- 网络不稳定时 Git 克隆可能失败
- **解决方案**: 提供手动下载链接

### 防火墙
- 某些企业防火墙可能阻止 Git 克隆
- **解决方案**: 使用手动下载方式

---

## 🚀 快速开始

### 1. 下载 Skill 包

从 [Releases](https://github.com/itxaiohanglover/mcp-codestyle-server/releases) 下载最新版本。

### 2. 安装到 Claude

```bash
# 解压到 Claude skills 目录
cp -r codestyle ~/.claude/skills/
```

### 3. 首次使用

```bash
# Windows
"C:\Users\YourName\.claude\skills\codestyle\scripts\codestyle.bat" search "CRUD"

# Linux/macOS
bash ~/.claude/skills/codestyle/scripts/codestyle search "CRUD"
```

首次使用会自动初始化，等待 10-30 秒即可。

---

## 📚 文档

- [使用指南](https://github.com/itxaiohanglover/mcp-codestyle-server/blob/main/skill/codestyle/SKILL.md)
- [配置说明](https://github.com/itxaiohanglover/mcp-codestyle-server/blob/main/skill/codestyle/references/config.md)
- [模板语法](https://github.com/itxaiohanglover/mcp-codestyle-server/blob/main/skill/codestyle/references/template-syntax.md)
- [更新日志](https://github.com/itxaiohanglover/mcp-codestyle-server/blob/main/skill/CHANGELOG.md)

---

## 🤝 贡献

欢迎贡献代码、报告问题或提出建议！

- 🐛 [报告问题](https://github.com/itxaiohanglover/mcp-codestyle-server/issues)
- 💬 [参与讨论](https://github.com/itxaiohanglover/mcp-codestyle-server/discussions)
- 📧 邮件: support@codestyle.top

---

## 📄 许可证

MIT License - 详见 [LICENSE](https://github.com/itxaiohanglover/mcp-codestyle-server/blob/main/LICENSE)

---

## 🙏 致谢

感谢所有贡献者和用户的支持！

特别感谢:
- [@Kanttha](https://github.com/Kanttha)
- [@movclantian](https://github.com/movclantian)
- [@gccszs](https://github.com/gccszs)

---

## 📦 下载

### JAR 包
- [codestyle-server.jar](https://github.com/itxaiohanglover/mcp-codestyle-server/releases/download/v1.0.0/codestyle-server.jar) (36.9 MB)

### 完整 Skill 包
- [codestyle-skill-v1.0.0.zip](https://github.com/itxaiohanglover/mcp-codestyle-server/archive/refs/tags/v1.0.0.zip)

---

**发布日期**: 2025-02-21  
**版本**: 1.0.0  
**状态**: ✅ 稳定版

