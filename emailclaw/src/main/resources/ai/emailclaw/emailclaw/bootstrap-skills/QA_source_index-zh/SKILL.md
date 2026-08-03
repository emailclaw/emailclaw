---
name: QA_source_index
description: "将用户问题中的主题、关键词映射到 Emailclaw 官方文档路径与常见源码入口，减少盲目搜索。适用于内置 QA Agent 在回答安装、配置、技能、MCP、多智能体、记忆、CLI 等问题时快速选定要读的文件。"
metadata:
  builtin_skill_version: "1.2"
  emailclaw:
    emoji: "🗂️"
    requires: {}
---

# 文档与源码速查

回答 **安装、配置、行为原理** 类问题时，先 **按关键词归类**，再按下表 **打开 1～2 个最可能命中的路径** 阅读，避免长时间无目的遍历。

## 使用步骤

1. 从用户问题中提取主题（对照下表左列或同类词）。
2. 解析 **`$EMAILCLAW_ROOT`**：以 `which emailclaw` 得到可执行路径，若为 `…/emailclaw/bin/emailclaw` 则源码根为其上三级目录（与 **guidance** skill 一致）；否则结合用户给出的安装路径判断。
3. **先读文档** `website/public/docs/<专题>.<语言>.md`（语言取与用户一致：`zh` / `en` / `ru` 等），仍不足再读表中 **源码入口**。

## 主题 / 关键词 → 优先文档与源码

| 主题或关键词（示例） | 优先文档（`website/public/docs/`） | 常见源码入口（相对 `$EMAILCLAW_ROOT`） |
|---------------------|-----------------------------------|-----------------------------------|
| 安装、依赖、首次使用 | `quickstart`、`intro` | `src/emailclaw/cli/`、`pyproject.toml` |
| 配置、config.json、环境变量 | `config` | `src/emailclaw/config/config.py`、`src/emailclaw/constant.py` |
| 技能、SKILL、skill_pool、内置技能 | `skills` | `src/emailclaw/agents/skills_manager.py`、`src/emailclaw/agents/skills/` |
| MCP、插件 | `mcp` | `src/emailclaw/app/routers/`（按需 grep `mcp`） |
| 多智能体、工作区、agent、内置 QA | `multi-agent` | `src/emailclaw/app/routers/agents.py`、`src/emailclaw/app/migration.py`、`src/emailclaw/constant.py`（`BUILTIN_QA_AGENT_ID` 等） |
| 记忆、MEMORY、memory_search | `memory` | `src/emailclaw/agents/memory/memory_manager.py`、`src/emailclaw/agents/tools/memory_search.py` |
| 控制台、前端 | `console` | `console/` |
| 命令行、子命令、init | `cli` | `src/emailclaw/cli/`（如 `init_cmd.py`） |
| 频道、会话 | `channels` | 在 `src/emailclaw` 下按 `channels` 关键词检索 |
| 上下文、窗口 | `context` | `config` 文档 + `src/emailclaw/agents/` 相关逻辑 |
| 模型、API Key | `models` | `src/emailclaw/config/config.py` |
| 心跳、HEARTBEAT | `heartbeat` | 在 `src/emailclaw` 下检索 `heartbeat` / `HEARTBEAT` |
| 桌面客户端 | `desktop` | `desktop/`（若仓库中存在） |
| 安全 | `security` | 先读 `security.<lang>.md` |
| 报错、常见问题 | `faq` | 先 `faq.<lang>.md`，再针对性看源码 |
| 命令与斜杠指令 | `commands` | `src/emailclaw` 下与 CLI/命令注册相关的模块（按需检索） |

## 约定

- 文档完整路径：`$EMAILCLAW_ROOT/website/public/docs/<专题>.<语言>.md`（无对应语言时用 `.en.md` 兜底）。
- 表中 **源码入口** 为起点；应用 `read_file` 或局部 `grep` 缩小到具体符号，不要一次性通读大目录 listing。

## 注意

- 本 skill **不替代** `read_file`：锁定候选路径后应立即读取并核对。
- 若某路径在本地不存在（例如未带源码的安装树），以 **已安装的文档包** 或用户提供的根目录为准，并明确告知依据路径。
