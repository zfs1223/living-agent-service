#!/bin/bash
# Living Agent Skills Installer
# Automatically installs dependencies for skills

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SKILLS_DIR="${LIVING_AGENT_SKILLS_PATH:-/app/skills}"
LOG_FILE="/app/logs/skills-install.log"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log() {
    local level=$1
    shift
    local message="$@"
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    echo -e "${timestamp} [${level}] ${message}" | tee -a "$LOG_FILE"
}

log_info() { log "INFO" "${BLUE}$@${NC}"; }
log_success() { log "SUCCESS" "${GREEN}$@${NC}"; }
log_warn() { log "WARN" "${YELLOW}$@${NC}"; }
log_error() { log "ERROR" "${RED}$@${NC}"; }

# Check if a command exists
has_command() {
    command -v "$1" &> /dev/null
}

# Install Python packages
install_python_packages() {
    log_info "Installing Python packages for document processing..."
    
    local packages=(
        "pypdf"
        "pdfplumber"
        "python-docx"
        "openpyxl"
        "python-pptx"
        "huggingface-hub"
        "requests"
        "beautifulsoup4"
        "lxml"
        "markdown"
        "pyyaml"
    )
    
    for pkg in "${packages[@]}"; do
        if pip3 show "$pkg" &> /dev/null; then
            log_info "Python package '$pkg' already installed"
        else
            log_info "Installing Python package: $pkg"
            pip3 install --no-cache-dir --break-system-packages "$pkg" && \
                log_success "Installed: $pkg" || \
                log_warn "Failed to install: $pkg (may not be critical)"
        fi
    done
}

# Install GitHub CLI
install_github_cli() {
    log_info "Checking GitHub CLI..."
    
    if has_command gh; then
        log_success "GitHub CLI already installed: $(gh --version | head -1)"
        return 0
    fi
    
    log_info "Installing GitHub CLI..."
    
    if has_command apt-get; then
        curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg | \
            dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg 2>/dev/null && \
            chmod go+r /usr/share/keyrings/githubcli-archive-keyring.gpg && \
            echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" | \
            tee /etc/apt/sources.list.d/github-cli.list > /dev/null && \
            apt-get update && \
            apt-get install -y gh && \
            log_success "GitHub CLI installed successfully"
    elif has_command apk; then
        apk add --no-cache github-cli && \
            log_success "GitHub CLI installed successfully"
    elif has_command brew; then
        brew install gh && \
            log_success "GitHub CLI installed successfully"
    else
        log_warn "Could not install GitHub CLI automatically. Install manually from: https://cli.github.com/"
    fi
}

# Install Docker CLI
install_docker_cli() {
    log_info "Checking Docker CLI..."
    
    if has_command docker; then
        log_success "Docker CLI already installed: $(docker --version)"
        return 0
    fi
    
    log_warn "Docker CLI not found. Docker operations will not be available."
    log_info "If running in a container, mount the Docker socket: -v /var/run/docker.sock:/var/run/docker.sock"
}

# Install Node.js tools
install_node_tools() {
    log_info "Checking Node.js tools..."
    
    if ! has_command node; then
        log_warn "Node.js not found. Some skills may not work."
        return 0
    fi
    
    log_success "Node.js installed: $(node --version)"
    
    # Check for npm packages
    local npm_packages=(
        "playwright"
        "puppeteer"
    )
    
    for pkg in "${npm_packages[@]}"; do
        if npm list -g "$pkg" &> /dev/null; then
            log_info "npm package '$pkg' already installed globally"
        else
            log_info "npm package '$pkg' not installed globally (may be installed locally)"
        fi
    done
}

# Install HuggingFace CLI
install_huggingface_cli() {
    log_info "Checking HuggingFace CLI..."
    
    if has_command huggingface-cli; then
        log_success "HuggingFace CLI already installed"
        return 0
    fi
    
    if pip3 show huggingface-hub &> /dev/null; then
        log_success "HuggingFace Hub installed, CLI should be available"
        return 0
    fi
    
    log_info "Installing HuggingFace Hub..."
    pip3 install --no-cache-dir --break-system-packages huggingface-hub && \
        log_success "HuggingFace CLI installed" || \
        log_warn "Failed to install HuggingFace CLI"
}

# Check and install uv (fast Python package installer)
install_uv() {
    log_info "Checking uv package manager..."
    
    if has_command uv; then
        log_success "uv already installed: $(uv --version)"
        return 0
    fi
    
    log_info "Installing uv..."
    curl -LsSf https://astral.sh/uv/install.sh | sh && \
        log_success "uv installed successfully" || \
        log_warn "Failed to install uv"
}

# Main installation function
main() {
    log_info "=========================================="
    log_info "Living Agent Skills Installer"
    log_info "=========================================="
    log_info "Skills directory: $SKILLS_DIR"
    log_info "Log file: $LOG_FILE"
    
    # Create log directory if it doesn't exist
    mkdir -p "$(dirname "$LOG_FILE")"
    
    # Install dependencies
    install_python_packages
    install_github_cli
    install_docker_cli
    install_node_tools
    install_huggingface_cli
    install_uv
    
    log_info "=========================================="
    log_success "Skills installation check completed!"
    log_info "=========================================="
    
    # Print summary
    echo ""
    log_info "Installed tools summary:"
    echo "  - Python: $(python3 --version 2>&1 || echo 'Not installed')"
    echo "  - pip: $(pip3 --version 2>&1 | cut -d' ' -f1-2 || echo 'Not installed')"
    echo "  - Git: $(git --version 2>&1 || echo 'Not installed')"
    echo "  - GitHub CLI: $(gh --version 2>&1 | head -1 || echo 'Not installed')"
    echo "  - Docker CLI: $(docker --version 2>&1 || echo 'Not installed')"
    echo "  - Node.js: $(node --version 2>&1 || echo 'Not installed')"
    echo "  - HuggingFace CLI: $(huggingface-cli version 2>&1 | head -1 || echo 'Not installed')"
}

# Run main function
main "$@"
