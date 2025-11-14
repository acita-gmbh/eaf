# Story 7.11: CLI Installation and Distribution

**Epic:** Epic 7 - Scaffolding CLI & Developer Tooling
**Status:** TODO
**Related Requirements:** FR002, NFR003

---

## User Story

As a framework developer,
I want CLI installable as global command,
So that developers can use `eaf` command from anywhere.

---

## Acceptance Criteria

1. ✅ Installation script: scripts/install-cli.sh (symlinks to PATH)
2. ✅ CLI executable: ./gradlew :tools:eaf-cli:installDist creates bin/eaf
3. ✅ Shadow JAR build for standalone distribution
4. ✅ `eaf` command works from any directory
5. ✅ CLI version check: `eaf version` displays current version
6. ✅ Uninstall script: scripts/uninstall-cli.sh
7. ✅ Installation documented in docs/getting-started/01-install-cli.md
8. ✅ Multi-platform support (Linux, macOS, Windows via gradlew.bat)

---

## Prerequisites

**Story 7.10**

---

## References

- PRD: FR002, NFR003
- Tech Spec: Section 3 (FR002)
