# Story 1.1: Initialize Repository and Root Build System

**Epic:** Epic 1 - Foundation & Project Infrastructure
**Status:** TODO
**Story Points:** TBD
**Related Requirements:** FR001 (Development Environment Setup)

---

## User Story

As a framework developer,
I want to initialize the EAF repository with Gradle 9.1.0 and Kotlin DSL configuration,
So that I have a working build system foundation for multi-module development.

---

## Acceptance Criteria

1. ✅ Git repository initialized with main branch
2. ✅ Gradle wrapper 9.1.0 configured (gradlew, gradlew.bat)
3. ✅ Root build.gradle.kts with Kotlin plugin and basic configuration
4. ✅ settings.gradle.kts with project name "eaf-v1"
5. ✅ .gitignore configured for Gradle, IDE, and build artifacts
6. ✅ ./gradlew build executes successfully (even with no modules yet)
7. ✅ README.md with project overview and setup instructions

---

## Prerequisites

**None** (first story)

---

## Technical Notes

### Prototype Reuse Strategy (ADR-009)

Per architecture.md Section 3, this story should implement the prototype cloning approach:

```bash
# Clone validated prototype structure:
git clone <prototype-repo> eaf-v1
cd eaf-v1

# Clean prototype implementations (keep structure):
rm -rf framework/*/src/     # Remove prototype code
rm -rf products/*/src/      # Remove prototype products
# Keep: Build config, module structure, Docker setup, CI/CD pipelines

# Verify structure compiles:
./gradlew build
```

**Critical:** Do NOT use Spring Initializr or JHipster starters. EAF reuses validated prototype structure to save 4-6 weeks setup time.

---

## Implementation Checklist

- [ ] Clone prototype repository
- [ ] Clean prototype implementations (keep structure)
- [ ] Verify Gradle wrapper 9.1.0
- [ ] Update project name to "eaf-v1" in settings.gradle.kts
- [ ] Update README.md with EAF v1.0 overview
- [ ] Configure .gitignore
- [ ] Run `./gradlew build` - verify success
- [ ] Commit: "Initial repository setup with prototype structure"

---

## Test Evidence

- [ ] `./gradlew build` executes without errors
- [ ] Git repository shows clean history with main branch
- [ ] README.md contains project overview and prerequisites
- [ ] .gitignore excludes build/, .gradle/, .idea/, *.iml

---

## Definition of Done

- [ ] All acceptance criteria met
- [ ] Code compiles (`./gradlew build` succeeds)
- [ ] README.md tested by following on clean system
- [ ] Git history clean (no prototype commits)
- [ ] Story marked as DONE in workflow status

---

## Related Stories

**Next Story:** Story 1.2 - Create Multi-Module Structure

---

## References

- PRD: FR001 (Development Environment Setup and Infrastructure)
- Architecture: Section 3 (Project Initialization)
- Architecture: ADR-009 (Prototype Structure Reuse)
- Tech Spec: Section 3 (FR001 Implementation)
