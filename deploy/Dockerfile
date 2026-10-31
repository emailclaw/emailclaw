# ==============================================================================
# Emailclaw - Industrial Grade Container Image
# Multi-stage, secure non-root, Playwright/Chrome-enabled headless runtime
# ==============================================================================

FROM ubuntu:26.04 AS runtime

ARG EMAILCLAW_VERSION=latest
ARG EMAILCLAW_REPO=emailclaw/emailclaw
ARG TARGETARCH=amd64

LABEL org.opencontainers.image.title="Emailclaw" \
      org.opencontainers.image.description="Personal AI Email Assistant - Headless Daemon" \
      org.opencontainers.image.url="https://emailclaw.email" \
      org.opencontainers.image.source="https://github.com/emailclaw/emailclaw" \
      org.opencontainers.image.documentation="https://emailclaw.email" \
      org.opencontainers.image.licenses="MIT"

ENV DEBIAN_FRONTEND=noninteractive \
    EMAILCLAW_HOME=/data \
    CHROME_OR_EDGE_PATH=/usr/bin/google-chrome-stable \
    EMAILCLAW_BROWSER_HEADLESS=true \
    TZ=UTC \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8

# 1. Install system utilities, dumb-init, multilingual fonts, and browser dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    wget \
    dumb-init \
    tzdata \
    locales \
    fonts-liberation \
    fonts-noto-cjk \
    fonts-noto-color-emoji \
    libnss3 \
    libatk-bridge2.0-0 \
    libgtk-3-0 \
    libgbm1 \
    libasound2t64 \
    && rm -rf /var/lib/apt/lists/*

# 2. Install official Google Chrome Stable for Playwright web fetching
RUN wget -q https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb \
    && apt-get update \
    && apt-get install -y --no-install-recommends ./google-chrome-stable_current_amd64.deb \
    && rm -f google-chrome-stable_current_amd64.deb \
    && rm -rf /var/lib/apt/lists/*

# 3. Create non-root application user and system directories
# Ubuntu noble base image pre-creates an 'ubuntu' user with UID/GID 1000; safely clean it up first.
RUN if id "ubuntu" >/dev/null 2>&1; then userdel -r ubuntu || true; fi \
    && if getent group ubuntu >/dev/null 2>&1; then groupdel ubuntu || true; fi \
    && (getent group emailclaw >/dev/null 2>&1 || groupadd -g 1000 emailclaw) \
    && (id -u emailclaw >/dev/null 2>&1 || useradd -u 1000 -g 1000 -m -s /bin/bash emailclaw) \
    && mkdir -p /opt/emailclaw /data \
    && chown -R emailclaw:emailclaw /opt/emailclaw /data

# 4. Fetch and unpack Emailclaw portable release
WORKDIR /opt
RUN set -eux; \
    if [ "$EMAILCLAW_VERSION" = "latest" ]; then \
        DOWNLOAD_URL="https://github.com/${EMAILCLAW_REPO}/releases/latest/download/emailclaw-linux-latest.tar.gz"; \
    else \
        DOWNLOAD_URL="https://github.com/${EMAILCLAW_REPO}/releases/download/${EMAILCLAW_VERSION}/emailclaw-linux-${EMAILCLAW_VERSION}.tar.gz"; \
    fi; \
    echo "Downloading Emailclaw release package from ${DOWNLOAD_URL}..."; \
    curl -fsSL "${DOWNLOAD_URL}" -o /tmp/emailclaw.tar.gz; \
    tar -xzf /tmp/emailclaw.tar.gz -C /opt/; \
    rm -f /tmp/emailclaw.tar.gz; \
    chown -R emailclaw:emailclaw /opt/emailclaw; \
    chmod +x /opt/emailclaw/bin/emailclaw; \
    ln -sf /opt/emailclaw/bin/emailclaw /usr/local/bin/emailclaw

# 5. Generate self-contained entrypoint script (zero local file dependencies)
RUN printf '%s\n' \
'#!/usr/bin/env bash' \
'set -euo pipefail' \
'DATA_DIR="${EMAILCLAW_HOME:-/data}"' \
'mkdir -p "$DATA_DIR" "$DATA_DIR/.config" "$DATA_DIR/logs" "$DATA_DIR/.database" "$DATA_DIR/projects"' \
'if [ -n "${JAVA_OPTS:-}" ]; then' \
'    export JDK_JAVA_OPTIONS="${JAVA_OPTS} ${JDK_JAVA_OPTIONS:-}"' \
'fi' \
'echo "================================================================================"' \
'echo " Starting Emailclaw Daemon (Headless Service Mode)"' \
'echo " Official Website:       https://emailclaw.email"' \
'echo " Base Image:             Ubuntu 26.04 LTS"' \
'echo " Repository:             https://github.com/emailclaw/emailclaw"' \
'echo " Data Directory:         $DATA_DIR"' \
'echo " Timezone:               ${TZ:-UTC} ($(date +%Z))"' \
'echo " Chrome Executable:      ${CHROME_OR_EDGE_PATH:-/usr/bin/google-chrome-stable}"' \
'echo " Headless Browser:       ${EMAILCLAW_BROWSER_HEADLESS:-true}"' \
'echo "================================================================================"' \
'CONFIG_DIR="$DATA_DIR/.config"' \
'if [ ! -f "$CONFIG_DIR/channels.json" ]; then' \
'    echo "[NOTICE] No channels.json found in $CONFIG_DIR."' \
'    echo "         To activate Emailclaw IMAP/SMTP mailboxes, configure:"' \
'    echo "         $CONFIG_DIR/channels.json"' \
'fi' \
'if [ ! -f "$CONFIG_DIR/providers.json" ]; then' \
'    echo "[NOTICE] No providers.json found in $CONFIG_DIR."' \
'    echo "         To connect to LLM Providers (OpenAI, DeepSeek, DashScope, Ollama), configure:"' \
'    echo "         $CONFIG_DIR/providers.json"' \
'fi' \
'exec /opt/emailclaw/bin/emailclaw "$@"' \
> /usr/local/bin/entrypoint.sh \
&& chmod +x /usr/local/bin/entrypoint.sh

# 6. Volumes, Working directory, User, and Entrypoint
VOLUME ["/data"]
WORKDIR /data

USER emailclaw

STOPSIGNAL SIGTERM
ENTRYPOINT ["/usr/bin/dumb-init", "--", "/usr/local/bin/entrypoint.sh"]
CMD ["--service"]
