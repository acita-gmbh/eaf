# Story 7.1: CLI Framework with Picocli

**Epic:** Epic 7 - Scaffolding CLI & Developer Tooling
**Status:** TODO
**Related Requirements:** FR002 (Code Generation with Bootstrap Fallbacks), NFR003 (Developer Experience)

---

## User Story

As a framework developer,
I want a CLI framework built with Picocli,
So that I have a modern, annotation-based foundation for scaffold commands.

---

## Acceptance Criteria

1. ✅ tools/eaf-cli module created with Picocli 4.7.7 dependency
2. ✅ EafCli.kt main class with @Command annotation
3. ✅ Subcommands structure: scaffold (parent), version, help
4. ✅ CLI executable via ./gradlew :tools:eaf-cli:run
5. ✅ Help output shows available commands
6. ✅ Version command displays EAF version
7. ✅ Unit tests validate CLI command parsing
8. ✅ CLI builds as standalone executable JAR

---

## Prerequisites

**Epic 6 complete**

---

## References

- PRD: FR002, NFR003
- Architecture: Section 7 (Developer Experience Stack - Picocli 4.7.7)
- Tech Spec: Section 2.4 (Picocli), Section 3 (FR002)
