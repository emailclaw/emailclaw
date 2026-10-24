#!/usr/bin/env bash
# ==============================================================================
# Emailclaw - Portable One-Click Installer for Linux
# Repository: https://github.com/emailclaw/emailclaw
# ==============================================================================

set -euo pipefail

# Configuration
REPO="${EMAILCLAW_REPO:-emailclaw/emailclaw}"
VERSION="${EMAILCLAW_VERSION:-latest}"
INSTALL_DIR="${EMAILCLAW_INSTALL_DIR:-$HOME/.local/emailclaw}"
BIN_DIR="$HOME/.local/bin"
DESKTOP_DIR="$HOME/.local/share/applications"

# Determine download URL
if [ "$VERSION" = "latest" ]; then
    DOWNLOAD_URL="https://github.com/${REPO}/releases/latest/download/emailclaw-linux-latest.tar.gz"
else
    DOWNLOAD_URL="https://github.com/${REPO}/releases/download/${VERSION}/emailclaw-linux-${VERSION}.tar.gz"
fi

# Visual styling
if [ -t 1 ]; then
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    BLUE='\033[0;34m'
    YELLOW='\033[1;33m'
    BOLD='\033[1m'
    NC='\033[0m'
else
    RED=''
    GREEN=''
    BLUE=''
    YELLOW=''
    BOLD=''
    NC=''
fi

info() {
    printf "${BLUE}==>${NC} ${BOLD}%s${NC}\n" "$1"
}

success() {
    printf "${GREEN}==>${NC} ${BOLD}%s${NC}\n" "$1"
}

warn() {
    printf "${YELLOW}WARNING:${NC} %s\n" "$1"
}

error() {
    printf "${RED}ERROR:${NC} %s\n" "$1" >&2
    exit 1
}

# 1. System verification
OS="$(uname -s)"
ARCH="$(uname -m)"

if [ "$OS" != "Linux" ]; then
    error "Emailclaw Linux installer only supports Linux. Detected OS: $OS"
fi

if [ "$ARCH" != "x86_64" ] && [ "$ARCH" != "amd64" ]; then
    warn "The pre-built release package is compiled for x86_64/amd64. Detected architecture: $ARCH"
fi

# 2. Check downloader
DOWNLOADER=""
if command -v curl >/dev/null 2>&1; then
    DOWNLOADER="curl"
elif command -v wget >/dev/null 2>&1; then
    DOWNLOADER="wget"
else
    error "Neither 'curl' nor 'wget' was found. Please install curl or wget first."
fi

# Check tar
if ! command -v tar >/dev/null 2>&1; then
    error "'tar' command not found. Please install tar first."
fi

# Parse script flags
NO_RUN=false
RUN_ARGS=()

for arg in "$@"; do
    case "$arg" in
        --no-run)
            NO_RUN=true
            ;;
        --help|-h)
            echo "Emailclaw One-Click Installer"
            echo "Usage: [curl ... |] bash [options]"
            echo ""
            echo "Options:"
            echo "  --no-run        Install without starting Emailclaw immediately"
            echo "  --service, -s   Launch directly in background service mode"
            echo "  --help, -h      Show this help message"
            echo "  Any other arguments will be passed directly to the Emailclaw launcher."
            exit 0
            ;;
        *)
            RUN_ARGS+=("$arg")
            ;;
    esac
done

# 3. Download package to temporary directory
TMP_DIR="$(mktemp -d -t emailclaw-install-XXXXXX)"
trap 'rm -rf "$TMP_DIR"' EXIT

ARCHIVE_FILE="$TMP_DIR/emailclaw.tar.gz"

info "Downloading Emailclaw ($VERSION) from GitHub Releases..."
if [ "$DOWNLOADER" = "curl" ]; then
    if ! curl -fL --progress-bar "$DOWNLOAD_URL" -o "$ARCHIVE_FILE"; then
        error "Failed to download Emailclaw package from $DOWNLOAD_URL"
    fi
else
    if ! wget -q --show-progress -O "$ARCHIVE_FILE" "$DOWNLOAD_URL"; then
        error "Failed to download Emailclaw package from $DOWNLOAD_URL"
    fi
fi

# 4. Extract package
info "Installing to $INSTALL_DIR..."
mkdir -p "$HOME/.local"
tar -xzf "$ARCHIVE_FILE" -C "$HOME/.local/" --no-same-owner

# Verify binary
EXECUTABLE="$INSTALL_DIR/bin/emailclaw"
if [ ! -x "$EXECUTABLE" ]; then
    if [ -f "$EXECUTABLE" ]; then
        chmod +x "$EXECUTABLE"
    else
        error "Installation failed: $EXECUTABLE not found after extraction."
    fi
fi

# 5. Create symlink in ~/.local/bin
mkdir -p "$BIN_DIR"
ln -sf "$EXECUTABLE" "$BIN_DIR/emailclaw"

# 6. Create Desktop Shortcut if applications directory exists
if [ -d "$DESKTOP_DIR" ] || [ -n "${XDG_DATA_HOME:-}" ]; then
    mkdir -p "$DESKTOP_DIR"
    ICON_PATH="$INSTALL_DIR/lib/emailclaw.png"
    cat > "$DESKTOP_DIR/emailclaw.desktop" << EOF
[Desktop Entry]
Name=Emailclaw
Comment=Personal AI Email Assistant
Exec=$BIN_DIR/emailclaw
Icon=$ICON_PATH
Terminal=false
Type=Application
Categories=Utility;Office;Network;
StartupWMClass=ai.emailclaw.emailclaw.Launcher
EOF
    chmod +x "$DESKTOP_DIR/emailclaw.desktop" 2>/dev/null || true
    if command -v update-desktop-database >/dev/null 2>&1; then
        update-desktop-database "$DESKTOP_DIR" 2>/dev/null || true
    fi
fi

success "Emailclaw successfully installed to $INSTALL_DIR!"
echo ""
echo "Executable: $EXECUTABLE"
echo "Symlink:    $BIN_DIR/emailclaw"
echo ""

# Check if ~/.local/bin is in PATH
case ":$PATH:" in
    *":$BIN_DIR:"*) ;;
    *)
        warn "$BIN_DIR is not currently in your PATH."
        echo "   To run 'emailclaw' directly from any terminal, add this to your ~/.bashrc or ~/.zshrc:"
        echo "   export PATH=\"\$HOME/.local/bin:\$PATH\""
        echo ""
        ;;
esac

# 7. Launch application if not --no-run
if [ "$NO_RUN" = true ]; then
    info "Installation complete (--no-run specified). You can start Emailclaw later with:"
    echo "  $BIN_DIR/emailclaw           # Desktop GUI mode"
    echo "  $BIN_DIR/emailclaw --service # Headless background service mode"
    exit 0
fi

# Check if running in a headless environment without DISPLAY/WAYLAND_DISPLAY
HAS_DISPLAY=true
if [ -z "${DISPLAY:-}" ] && [ -z "${WAYLAND_DISPLAY:-}" ]; then
    HAS_DISPLAY=false
fi

# Check if user explicitly asked for service mode
IS_SERVICE=false
for a in "${RUN_ARGS[@]}"; do
    if [ "$a" = "--service" ] || [ "$a" = "-s" ]; then
        IS_SERVICE=true
        break
    fi
done

if [ "$HAS_DISPLAY" = false ] && [ "$IS_SERVICE" = false ]; then
    warn "No graphical display detected (DISPLAY or WAYLAND_DISPLAY not set)."
    echo "If you are running on a server or remote terminal, run Emailclaw in background service mode:"
    echo "  $BIN_DIR/emailclaw --service"
    echo ""
    echo "Or start it now in service mode by running:"
    echo "  curl -fsSL https://raw.githubusercontent.com/${REPO}/main/deploy/install.sh | bash -s -- --service"
    exit 0
fi

info "Starting Emailclaw..."
exec "$EXECUTABLE" "${RUN_ARGS[@]}"
