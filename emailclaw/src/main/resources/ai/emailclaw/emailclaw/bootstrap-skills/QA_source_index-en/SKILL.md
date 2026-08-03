---
name: QA_source_index
description: "Maps topics and keywords from user questions to Emailclaw official documentation paths and common source code entry points, reducing blind searching. Intended for the built-in QA Agent to quickly identify which files to read when answering questions about installation, configuration, skills, MCP, multi-agent, memory, CLI, etc."
metadata:
  builtin_skill_version: "1.2"
  emailclaw:
    emoji: "🗂️"
    requires: {}
---

# Documentation and Source Code Quick Reference

When answering questions about **installation, configuration, or behavioral principles**, first **classify by keyword**, then **open 1–2 paths most likely to contain the answer** from the table below, avoiding aimless directory traversal.

## Usage Steps

1. Extract the topic from the user's question (match against the left column or synonyms in the table below).
2. Resolve **`$EMAILCLAW_ROOT`**: use `which emailclaw` to get the executable path. If it is `…/emailclaw/bin/emailclaw`, the source root is three levels up (consistent with the **guidance** skill); otherwise, determine it from the user-provided installation path.
3. **Read documentation first**: `website/public/docs/<topic>.<language>.md` (use the same language as the user: `zh` / `en` / `ru`, etc.). If that is insufficient, read the **source entry points** listed in the table.

## Topic / Keywords → Preferred Documentation and Source Code

| Topic or Keywords (examples) | Preferred Documentation (`website/public/docs/`) | Common Source Entry Points (relative to `$EMAILCLAW_ROOT`) |
|---------------------|-----------------------------------|-----------------------------------|
| Installation, dependencies, getting started | `quickstart`, `intro` | `src/emailclaw/cli/`, `pyproject.toml` |
| Configuration, config.json, environment variables | `config` | `src/emailclaw/config/config.py`, `src/emailclaw/constant.py` |
| Skills, SKILL, skill_pool, built-in skills | `skills` | `src/emailclaw/agents/skills_manager.py`, `src/emailclaw/agents/skills/` |
| MCP, plugins | `mcp` | `src/emailclaw/app/routers/` (grep `mcp` as needed) |
| Multi-agent, workspace, agent, built-in QA | `multi-agent` | `src/emailclaw/app/routers/agents.py`, `src/emailclaw/app/migration.py`, `src/emailclaw/constant.py` (`BUILTIN_QA_AGENT_ID`, etc.) |
| Memory, MEMORY, memory_search | `memory` | `src/emailclaw/agents/memory/memory_manager.py`, `src/emailclaw/agents/tools/memory_search.py` |
| Console, frontend | `console` | `console/` |
| CLI, subcommands, init | `cli` | `src/emailclaw/cli/` (e.g., `init_cmd.py`) |
| Channels, sessions | `channels` | Search for `channels` keyword under `src/emailclaw` |
| Context, window | `context` | `config` docs + related logic in `src/emailclaw/agents/` |
| Models, API Key | `models` | `src/emailclaw/config/config.py` |
| Heartbeat, HEARTBEAT | `heartbeat` | Search for `heartbeat` / `HEARTBEAT` under `src/emailclaw` |
| Desktop client | `desktop` | `desktop/` (if present in the repository) |
| Security | `security` | Read `security.<lang>.md` first |
| Errors, FAQ | `faq` | Read `faq.<lang>.md` first, then examine source code as needed |
| Commands and slash commands | `commands` | CLI/command registration modules under `src/emailclaw` (search as needed) |

## Conventions

- Full documentation path: `$EMAILCLAW_ROOT/website/public/docs/<topic>.<language>.md` (fall back to `.en.md` if the corresponding language file does not exist).
- The **source entry points** in the table are starting points; use `read_file` or targeted `grep` to narrow down to specific symbols — do not read through an entire large directory listing at once.

## Notes

- This skill **does not replace** `read_file`: after identifying candidate paths, you should immediately read and verify the content.
- If a path does not exist locally (e.g., an installation tree without source code), use the **installed documentation package** or the root directory provided by the user, and clearly state which path you are relying on.
