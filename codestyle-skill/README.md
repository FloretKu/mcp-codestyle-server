# Codestyle Skill

代码模板检索与生成的 Claude Code Skill。

## 安装

### 个人级别（所有项目可用）

```bash
cp -r codestyle ~/.claude/skills/
```

### 项目级别（仅当前项目可用）

```bash
cp -r codestyle .claude/skills/
```

## 目录结构

```
codestyle/
├── SKILL.md              # 核心指令（YAML frontmatter + 工作流）
├── scripts/
│   ├── codestyle-server.jar   # 构建MCP打包产物
│   └── cfg.json               # 全局默认配置
└── references/
    ├── config.md              # 配置项文档
    └── template-syntax.md     # 输出格式与变量规则
```

## 使用

启动 Claude Code 后，Skill 自动加载。直接说：

```
帮我生成 User 实体的 CRUD 代码
```

或使用斜杠命令：

```
/codestyle CRUD
```

## 触发短语

- "生成 CRUD 代码"
- "用模板生成 controller"
- "帮我生成 User 实体的增删改查"

## 配置

| 位置 | 优先级 | 作用域 |
|------|--------|--------|
| `scripts/cfg.json` | 低 | 全局默认 |
| `.codestyle/cfg.json` | 高 | 项目覆盖 |

项目配置示例（`.codestyle/cfg.json`）：

```json
{
  "repository": {
    "local-path": "~/.codestyle/cache",
    "remote-path": "https://api.codestyle.top",
    "repository-dir": "~/.codestyle/cache",
    "remote-search-enabled": true,
    "remote-search-timeout-ms": 5000,
    "api-key": ""
  }
}

```

## 前置要求

- JDK 17+

## 参考

- [Claude Code Skills 官方文档](https://code.claude.com/docs/en/skills)
- [Skills 深度解析](https://mikhail.io/2025/10/claude-code-skills/)
- [Anthropic Skills 仓库](https://github.com/anthropics/skills)
