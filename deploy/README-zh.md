# Emailclaw Deployment Guide (Docker & Shell)

本目录提供 **Emailclaw** 的一键部署解决方案，包含原生 Linux 自动化安装脚本与基于 **Docker Compose** 的工业级容器化部署方案。

---

## 目录结构

```text
deploy/
├── Dockerfile                  # 容器镜像定义（基于通用 Ubuntu 26.04，利用 jpackage 内置运行时，无需外部 JDK，包含官方 Chrome、中文字体与安全非 root 用户）
├── docker-compose.yml          # Docker Compose 编排文件（包含卷持久化、内存与日志优化）
├── .env.example                # 容器环境变量模板
├── install.sh                  # Linux 原生一键自动化安装与启动脚本
└── templates/                  # 初始配置模板
    ├── channels.json.example   # 邮件通道（IMAP/SMTP）配置示例
    └── providers.json.example  # 模型 Provider 配置示例
```

---

## 方案一：Docker Compose 一键部署（推荐）

适合无头 Linux 服务器、轻量级 VPS、云主机或 NAS 环境长期稳定运行后台服务。

### 1. 特性优势
* **生产级沙箱与安全**：默认以非 root 用户（`emailclaw:1000`）运行，遵循最小权限原则。
* **内置官方无头浏览器**：预置 Google Chrome Stable 与多国/中文字体支持（Noto Sans CJK），保证 Playwright 动态网页抓取与反爬规避正常工作。
* **优雅停机与进程回收**：使用 `dumb-init` 作为 1 号进程，正确接管 `SIGTERM` 信号以平滑关闭信道连接与定时任务，防止僵尸进程堆积。
* **共享内存与日志调优**：预设 `shm_size: 2gb` 防止 Chromium 崩溃，内置日志切分限制（`max-size: 50m`, `max-file: 5`）。

### 2. 快速启动

1. **进入部署目录，下载配置文件，复制环境变量文件**：
   ```bash
   mkdir -p emailclaw-docker && cd emailclaw-docker
   curl -O https://raw.githubusercontent.com/emailclaw/emailclaw/main/deploy/Dockerfile
   curl -O https://raw.githubusercontent.com/emailclaw/emailclaw/main/deploy/docker-compose.yml
   curl -O https://raw.githubusercontent.com/emailclaw/emailclaw/main/deploy/.env.example
   cp .env.example .env
   ```

2. **一键构建并启动服务**：
   ```bash
   docker compose up -d
   ```

3. **查看实时运行日志**：
   ```bash
   docker compose logs -f
   ```

### 3. 数据持久化与配置

容器内的主要数据与配置存储在命名卷 `emailclaw_data`（映射到容器内 `/data`，对应 `EMAILCLAW_HOME`）：

* **方式 A：从桌面端复制配置（推荐新手）**
  若您已经在桌面版 Emailclaw 配置好 Provider 和 Email 邮箱：
  ```bash
  # 将本地桌面配置复制进 Docker 卷
  docker compose cp ~/.emailclaw/.config/ emailclaw:/data/
  docker compose restart
  ```

* **方式 B：使用模板文件初始化配置**
  首次部署时，您也可以参考 `deploy/templates/` 目录下的示例：
  ```bash
  # 创建配置目录并复制模板
  docker compose exec emailclaw mkdir -p /data/.config
  docker compose cp templates/channels.json.example emailclaw:/data/.config/channels.json
  docker compose cp templates/providers.json.example emailclaw:/data/.config/providers.json
  
  # 修改配置后重启
  docker compose restart
  ```

* **方式 C：使用宿主机挂载目录**
  如需在宿主机直接编辑文件，可在 `docker-compose.yml` 中将卷映射切换为宿主机目录：
  ```yaml
  volumes:
    - ./data:/data
  ```

### 4. 日常运维命令

| 操作 | 命令 |
| :--- | :--- |
| 查看运行状态 | `docker compose ps` |
| 实时日志排查 | `docker compose logs -f emailclaw` |
| 重启服务 | `docker compose restart emailclaw` |
| 停止服务 | `docker compose down` |
| 升级至最新版本 | `docker compose pull && docker compose up -d` |

---

## 方案二：Linux 原生 Curl 一键安装（无 Docker）

适合直接在物理机或个人 Linux 机器上运行：

```bash
# 桌面端：安装后直接拉起图形界面
curl -fsSL https://raw.githubusercontent.com/emailclaw/emailclaw/main/deploy/install.sh | bash

# 服务器端：安装并作为后台服务（--service）运行
curl -fsSL https://raw.githubusercontent.com/emailclaw/emailclaw/main/deploy/install.sh | bash -s -- --service

# 仅安装，不立即运行
curl -fsSL https://raw.githubusercontent.com/emailclaw/emailclaw/main/deploy/install.sh | bash -s -- --no-run
```
