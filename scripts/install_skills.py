"""
Living Agent Skills Installer for Windows/Linux
Automatically installs dependencies for skills
"""

import os
import sys
import subprocess
import platform
import shutil
from pathlib import Path
from typing import List, Optional
import logging

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    handlers=[
        logging.StreamHandler(sys.stdout),
        logging.FileHandler('skills-install.log', mode='a')
    ]
)
logger = logging.getLogger(__name__)

class SkillsInstaller:
    def __init__(self):
        self.skills_dir = os.environ.get('LIVING_AGENT_SKILLS_PATH', '/app/skills')
        self.is_windows = platform.system() == 'Windows'
        self.is_linux = platform.system() == 'Linux'
        self.is_macos = platform.system() == 'Darwin'
        
    def has_command(self, cmd: str) -> bool:
        """Check if a command exists in PATH"""
        return shutil.which(cmd) is not None
    
    def run_command(self, cmd: List[str], check: bool = False) -> subprocess.CompletedProcess:
        """Run a shell command"""
        try:
            result = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                check=check
            )
            return result
        except subprocess.CalledProcessError as e:
            logger.error(f"Command failed: {' '.join(cmd)}")
            logger.error(f"Error: {e.stderr}")
            raise
    
    def install_python_packages(self):
        """Install Python packages for document processing"""
        logger.info("Installing Python packages for document processing...")
        
        packages = [
            "pypdf",
            "pdfplumber", 
            "python-docx",
            "openpyxl",
            "python-pptx",
            "huggingface-hub",
            "requests",
            "beautifulsoup4",
            "lxml",
            "markdown",
            "pyyaml"
        ]
        
        pip_cmd = [sys.executable, "-m", "pip", "install", "--no-cache-dir"]
        
        for pkg in packages:
            try:
                result = self.run_command([sys.executable, "-m", "pip", "show", pkg])
                if result.returncode == 0:
                    logger.info(f"Python package '{pkg}' already installed")
                else:
                    logger.info(f"Installing Python package: {pkg}")
                    self.run_command(pip_cmd + [pkg], check=True)
                    logger.info(f"Successfully installed: {pkg}")
            except Exception as e:
                logger.warning(f"Failed to install {pkg}: {e}")
    
    def install_github_cli(self):
        """Install GitHub CLI"""
        logger.info("Checking GitHub CLI...")
        
        if self.has_command("gh"):
            result = self.run_command(["gh", "--version"])
            logger.info(f"GitHub CLI already installed: {result.stdout.split()[0]}")
            return
        
        logger.info("Installing GitHub CLI...")
        
        if self.is_windows:
            if self.has_command("winget"):
                self.run_command(["winget", "install", "GitHub.cli", "--accept-source-agreements", "--accept-package-agreements"])
            elif self.has_command("choco"):
                self.run_command(["choco", "install", "gh", "-y"])
            else:
                logger.warning("Please install GitHub CLI manually from: https://cli.github.com/")
        elif self.is_linux:
            if self.has_command("apt-get"):
                self.run_command(["sudo", "apt-get", "update"], check=False)
                self.run_command(["sudo", "apt-get", "install", "-y", "gh"])
            elif self.has_command("apk"):
                self.run_command(["apk", "add", "--no-cache", "github-cli"])
            else:
                logger.warning("Please install GitHub CLI manually from: https://cli.github.com/")
        elif self.is_macos:
            if self.has_command("brew"):
                self.run_command(["brew", "install", "gh"])
            else:
                logger.warning("Please install GitHub CLI manually from: https://cli.github.com/")
    
    def install_docker_cli(self):
        """Check Docker CLI availability"""
        logger.info("Checking Docker CLI...")
        
        if self.has_command("docker"):
            result = self.run_command(["docker", "--version"])
            logger.info(f"Docker CLI installed: {result.stdout.strip()}")
        else:
            logger.warning("Docker CLI not found. Docker operations will not be available.")
            if self.is_windows:
                logger.info("Install Docker Desktop from: https://www.docker.com/products/docker-desktop")
            elif self.is_linux:
                logger.info("Install Docker from: https://docs.docker.com/engine/install/")
    
    def install_node_tools(self):
        """Check Node.js tools"""
        logger.info("Checking Node.js tools...")
        
        if self.has_command("node"):
            result = self.run_command(["node", "--version"])
            logger.info(f"Node.js installed: {result.stdout.strip()}")
        else:
            logger.warning("Node.js not found. Some skills may not work.")
            if self.is_windows:
                logger.info("Install Node.js from: https://nodejs.org/")
            elif self.is_linux:
                logger.info("Install Node.js: curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash - && sudo apt-get install -y nodejs")
    
    def install_huggingface_cli(self):
        """Install HuggingFace CLI"""
        logger.info("Checking HuggingFace CLI...")
        
        if self.has_command("huggingface-cli"):
            logger.info("HuggingFace CLI already installed")
            return
        
        try:
            self.run_command([sys.executable, "-m", "pip", "install", "--no-cache-dir", "huggingface-hub"], check=True)
            logger.info("HuggingFace CLI installed")
        except Exception as e:
            logger.warning(f"Failed to install HuggingFace CLI: {e}")
    
    def install_uv(self):
        """Install uv package manager"""
        logger.info("Checking uv package manager...")
        
        if self.has_command("uv"):
            result = self.run_command(["uv", "--version"])
            logger.info(f"uv already installed: {result.stdout.strip()}")
            return
        
        if self.is_windows:
            if self.has_command("powershell"):
                logger.info("Installing uv via PowerShell...")
                self.run_command([
                    "powershell", "-Command",
                    "irm https://astral.sh/uv/install.ps1 | iex"
                ])
        else:
            logger.info("Installing uv via curl...")
            self.run_command([
                "curl", "-LsSf", "https://astral.sh/uv/install.sh",
                "|", "sh"
            ], check=False)
    
    def print_summary(self):
        """Print installation summary"""
        logger.info("=" * 50)
        logger.info("Installed tools summary:")
        
        tools = [
            ("Python", ["python", "--version"]),
            ("pip", ["pip", "--version"]),
            ("Git", ["git", "--version"]),
            ("GitHub CLI", ["gh", "--version"]),
            ("Docker CLI", ["docker", "--version"]),
            ("Node.js", ["node", "--version"]),
            ("HuggingFace CLI", ["huggingface-cli", "version"]),
        ]
        
        for name, cmd in tools:
            if self.has_command(cmd[0]):
                try:
                    result = self.run_command(cmd)
                    version = result.stdout.strip().split('\n')[0]
                    logger.info(f"  - {name}: {version}")
                except:
                    logger.info(f"  - {name}: Installed")
            else:
                logger.info(f"  - {name}: Not installed")
        
        logger.info("=" * 50)
    
    def install_all(self):
        """Run all installations"""
        logger.info("=" * 50)
        logger.info("Living Agent Skills Installer")
        logger.info("=" * 50)
        logger.info(f"Skills directory: {self.skills_dir}")
        logger.info(f"Platform: {platform.system()} {platform.release()}")
        
        self.install_python_packages()
        self.install_github_cli()
        self.install_docker_cli()
        self.install_node_tools()
        self.install_huggingface_cli()
        self.install_uv()
        
        logger.info("=" * 50)
        logger.info("Skills installation check completed!")
        logger.info("=" * 50)
        
        self.print_summary()


def main():
    installer = SkillsInstaller()
    installer.install_all()


if __name__ == "__main__":
    main()
