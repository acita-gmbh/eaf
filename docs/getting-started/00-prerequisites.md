# Prerequisites

This guide helps you set up your development environment for the Enterprise Application Framework (EAF) v1.0.

---

## Required Tools

### 1. JDK 21 LTS

EAF requires Java Development Kit 21 (Long Term Support).

**macOS:**

```bash
# Using Homebrew
brew install openjdk@21

# Add to PATH (add to ~/.zshrc or ~/.bash_profile)
export PATH="/usr/local/opt/openjdk@21/bin:$PATH"
```

**Linux (Ubuntu/Debian):**

```bash
# Using apt
sudo apt update
sudo apt install openjdk-21-jdk

# Verify installation
java -version
```

**Linux (Red Hat/Fedora):**

```bash
# Using dnf
sudo dnf install java-21-openjdk-devel

# Verify installation
java -version
```

**Windows:**

1. Download JDK 21 from [Adoptium](https://adoptium.net/) or [Oracle](https://www.oracle.com/java/technologies/downloads/#java21)
2. Run the installer
3. Add `JAVA_HOME` environment variable pointing to JDK installation directory
4. Add `%JAVA_HOME%\bin` to your `PATH`
5. Verify: `java -version` in PowerShell or CMD

**Verification:**

```bash
java -version
# Should show: openjdk version "21.x.x"
```

---

### 2. Docker Desktop

EAF uses Docker for local development infrastructure (PostgreSQL, Keycloak, Redis, Prometheus, Grafana).

**macOS:**

1. Download [Docker Desktop for Mac](https://www.docker.com/products/docker-desktop)
2. Install and start Docker Desktop
3. Ensure Docker is running (Docker icon in menu bar)

**Linux:**

```bash
# Install Docker Engine
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Add your user to docker group
sudo usermod -aG docker $USER

# Install Docker Compose
sudo apt install docker-compose-plugin  # Ubuntu/Debian
# OR
sudo dnf install docker-compose-plugin  # Fedora/Red Hat

# Start Docker service
sudo systemctl enable docker
sudo systemctl start docker
```

**Windows:**

1. Download [Docker Desktop for Windows](https://www.docker.com/products/docker-desktop)
2. Install and start Docker Desktop
3. Enable WSL 2 backend if prompted
4. Ensure Docker is running (Docker icon in system tray)

**Verification:**

```bash
docker --version
# Should show: Docker version 24.x.x or higher

docker-compose --version
# Should show: Docker Compose version v2.x.x or higher
```

---

### 3. Git

Version control for EAF development.

**macOS:**

```bash
# Using Homebrew
brew install git

# OR use Xcode Command Line Tools
xcode-select --install
```

**Linux:**

```bash
# Ubuntu/Debian
sudo apt install git

# Fedora/Red Hat
sudo dnf install git
```

**Windows:**

1. Download [Git for Windows](https://git-scm.com/download/win)
2. Run installer (use default settings)
3. Git Bash will be available for Unix-like commands

**Verification:**

```bash
git --version
# Should show: git version 2.x.x or higher
```

---

## Recommended Tools

### IDE: IntelliJ IDEA

IntelliJ IDEA is the recommended IDE for Kotlin development.

- **Download:** [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/download/) (free)
- **Plugins:** Kotlin plugin (bundled), Spring Boot plugin

**Configuration:**

1. Import project as Gradle project
2. Enable annotation processing: `Settings → Build → Compiler → Annotation Processors`
3. Set JDK 21 as Project SDK: `File → Project Structure → Project → SDK`

---

## Optional Tools

### Gradle (Optional)

Gradle wrapper is included in the repository (`./gradlew`), so manual installation is not required.

If you prefer a global Gradle installation:

```bash
# macOS
brew install gradle

# Linux (SDKMAN)
sdk install gradle 9.1.0
```

---

## System Requirements

### Minimum Hardware

- **CPU:** 4 cores (8 cores recommended)
- **RAM:** 8 GB (16 GB recommended)
- **Disk Space:** 10 GB free space

### Supported Operating Systems

- **macOS:** 12.0 (Monterey) or later (Intel and Apple Silicon)
- **Linux:** Ubuntu 20.04+, Fedora 35+, Debian 11+, or equivalent
- **Windows:** Windows 10/11 with WSL 2 (for Docker)

---

## Multi-Architecture Support

EAF supports the following processor architectures:

- **amd64 (x86_64):** Standard Intel/AMD processors
- **arm64 (aarch64):** Apple Silicon (M1/M2/M3), ARM servers
- **ppc64le (optional):** IBM POWER architecture (custom Keycloak build required)

---

## Next Steps

Once you have all prerequisites installed:

1. **Clone the repository:**
   ```bash
   git clone <eaf-repository-url>
   cd eaf-v1
   ```

2. **Initialize development environment:**
   ```bash
   ./scripts/init-dev.sh
   ```

   This script will:
   - Start Docker Compose services (PostgreSQL, Keycloak, Redis, Prometheus, Grafana)
   - Install Git hooks for quality gates
   - Download project dependencies
   - Verify environment setup

3. **Verify setup:**
   ```bash
   ./gradlew build
   ```

4. **Continue to Getting Started Guide** (coming soon)

---

## Troubleshooting

### Docker Issues on macOS

If Docker containers fail to start:

1. Increase Docker resources: Docker Desktop → Preferences → Resources
   - **CPUs:** 4 or more
   - **Memory:** 8 GB or more
2. Restart Docker Desktop

### Docker Issues on Linux

If you get permission errors:

```bash
sudo usermod -aG docker $USER
# Log out and log back in
```

### JDK Version Conflicts

If multiple JDK versions are installed:

```bash
# macOS (using jEnv)
brew install jenv
jenv add /usr/local/opt/openjdk@21
jenv global 21

# Linux (using update-alternatives)
sudo update-alternatives --config java
```

### Git Hooks Not Running

If pre-commit hooks don't execute:

```bash
# Ensure hooks are executable
chmod +x .git-hooks/*

# Reinstall hooks
./scripts/install-git-hooks.sh
```

---

## Support

For questions or issues:

- **Slack:** #eaf-development
- **Email:** eaf-team@axians.com
- **Documentation:** [EAF Documentation](../README.md)
