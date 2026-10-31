# Emailclaw Deployment Guide (Docker & Shell)

<p align="center">
  <a href="https://emailclaw.email/">Official Website (emailclaw.email)</a> ·
  <a href="./README-zh.md">中文版</a>
</p>

This directory provides one-click deployment solutions for **Emailclaw**, including a native Linux automated installer and an industrial-grade containerized deployment based on **Docker Compose**.

---

## Directory Structure

```text
deploy/
├── Dockerfile                  # Container image definition (Ubuntu 26.04, bundled jpackage runtime, Chrome, CJK fonts, non-root user)
├── docker-compose.yml          # Docker Compose specification (volume persistence, memory & log rotation optimization)
├── .env.example                # Environment variables template
├── install.sh                  # Linux native one-click automated installer & launcher
└── templates/                  # Sample initial configurations
    ├── channels.json.example   # Email channel (IMAP/SMTP) configuration template
    └── providers.json.example  # LLM Provider configuration template
```

---

## Option 1: Docker Compose Deployment (Recommended)

Ideal for headless Linux servers, VPS, cloud instances, or NAS devices requiring a 24/7 background daemon service.

### 1. Key Production Features
* **Production Sandbox & Security**: Runs as an unprivileged user (`emailclaw:1000`) by default, following the principle of least privilege.
* **Built-in Official Headless Browser**: Pre-configured with official Google Chrome Stable and multilingual fonts (`fonts-noto-cjk`, emoji) to ensure Playwright web extraction and anti-bot evasions operate properly.
* **Graceful Shutdown & Process Reaping**: Uses `dumb-init` as PID 1 to capture `SIGTERM` signals, safely stopping long-polling IMAP channels and cron tasks without leaving orphaned or zombie processes.
* **Self-Contained Image Build**: The Dockerfile builds completely self-contained with zero external file dependencies in the build context.
* **Resource & Log Optimization**: Pre-allocates `shm_size: 2gb` to avoid Chromium crashes and sets log rotation limits (`max-size: 50m`, `max-file: 5`) to protect disk space.

### 2. Quickstart

1. **Prepare deployment directory and configuration**:
   * If working in a cloned repository:
     ```bash
     cd deploy
     cp .env.example .env
     ```
   * Or download standalone deployment files directly:
     ```bash
     mkdir -p emailclaw-docker && cd emailclaw-docker
     curl -O https://raw.githubusercontent.com/emailclaw/emailclaw/main/deploy/Dockerfile
     curl -O https://raw.githubusercontent.com/emailclaw/emailclaw/main/deploy/docker-compose.yml
     curl -O https://raw.githubusercontent.com/emailclaw/emailclaw/main/deploy/.env.example
     cp .env.example .env
     ```

2. **Build and launch the service**:
   ```bash
   docker compose up -d
   ```

3. **Inspect real-time service logs**:
   ```bash
   docker compose logs -f
   ```

### 3. Data Persistence & Configuration

All core configurations, SQLite database files, project workspaces, and logs are stored in the named volume `emailclaw_data` (mounted to `/data`, mapped to `EMAILCLAW_HOME`):

* **Method A: Copy configuration from desktop client (Easiest for beginners)**
  If you have already configured providers and email channels in the Emailclaw desktop app:
  ```bash
  # Copy local desktop config into the Docker volume
  docker compose cp ~/.emailclaw/.config/ emailclaw:/data/
  docker compose restart
  ```

* **Method B: Initialize using templates**
  For fresh server deployments, use the templates provided in `deploy/templates/`:
  ```bash
  # Create config directory and copy templates
  docker compose exec emailclaw mkdir -p /data/.config
  docker compose cp templates/channels.json.example emailclaw:/data/.config/channels.json
  docker compose cp templates/providers.json.example emailclaw:/data/.config/providers.json
  
  # Restart service after editing
  docker compose restart
  ```

* **Method C: Host Bind-Mount Directory**
  To directly edit configuration and browse data on the host filesystem, adjust `docker-compose.yml`:
  ```yaml
  volumes:
    - ./data:/data
  ```

### 4. Daily Management Commands

| Action | Command |
| :--- | :--- |
| Check service status | `docker compose ps` |
| View live logs | `docker compose logs -f emailclaw` |
| Restart daemon | `docker compose restart emailclaw` |
| Stop service | `docker compose down` |
| Upgrade to latest release | `docker compose pull && docker compose up -d` |

---

## Option 2: Linux Native One-Click Curl Installation (Bare Metal / No Docker)

Best for users running directly on personal Linux machines or physical servers without Docker:

```bash
# Desktop GUI Mode: Install and launch desktop interface immediately
curl -fsSL https://raw.githubusercontent.com/emailclaw/emailclaw/main/deploy/install.sh | bash

# Server Daemon Mode: Install and start as headless background service
curl -fsSL https://raw.githubusercontent.com/emailclaw/emailclaw/main/deploy/install.sh | bash -s -- --service

# Install Only Mode: Install without immediate launch
curl -fsSL https://raw.githubusercontent.com/emailclaw/emailclaw/main/deploy/install.sh | bash -s -- --no-run
```
