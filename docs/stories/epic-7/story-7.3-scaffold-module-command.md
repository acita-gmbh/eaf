# Story 7.3: scaffold module Command

**Epic:** Epic 7 - Scaffolding CLI & Developer Tooling
**Status:** TODO
**Related Requirements:** FR002

---

## User Story

As a framework developer,
I want `eaf scaffold module <name>` command to generate Spring Modulith modules,
So that I can create new framework or product modules with correct structure.

---

## Acceptance Criteria

1. ✅ ScaffoldModuleCommand.kt implements module scaffolding
2. ✅ Command parameters: module name, parent directory (framework/products)
3. ✅ Generates: build.gradle.kts, src/main/kotlin structure, module-info.java (if needed)
4. ✅ Module template includes convention plugin application
5. ✅ Generated module compiles successfully
6. ✅ Spring Modulith recognizes generated module
7. ✅ Integration test validates: scaffold module → compiles → Konsist passes
8. ✅ Command documented with examples

---

## Prerequisites

**Story 7.2**

---

## References

- PRD: FR002
- Tech Spec: Section 3 (FR002 - scaffold module)
