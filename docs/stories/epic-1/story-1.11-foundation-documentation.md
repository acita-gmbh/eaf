# Story 1.11: Foundation Documentation and Project README

**Epic:** Epic 1 - Foundation & Project Infrastructure
**Status:** TODO
**Story Points:** TBD
**Related Requirements:** FR015 (Onboarding & Learning)

---

## User Story

As a framework developer,
I want comprehensive foundation documentation,
So that the project structure and setup process are clearly explained.

---

## Acceptance Criteria

1. ✅ README.md updated with: project overview, prerequisites, quick start (./scripts/init-dev.sh), architecture overview, contribution guidelines
2. ✅ docs/getting-started/00-prerequisites.md documents required tools (JDK 21, Docker, Git)
3. ✅ CONTRIBUTING.md with development workflow and quality standards
4. ✅ LICENSE file (Apache 2.0 or Axians choice)
5. ✅ .editorconfig for consistent code formatting across IDEs
6. ✅ All documentation tested by following instructions on clean system
7. ✅ Documentation links verified and working

---

## Prerequisites

**Story 1.10** - Git Hooks for Quality Gates

---

## Technical Notes

### README.md Structure

```markdown
# EAF v1.0 - Enterprise Application Framework

Production-ready event-sourced framework combining Hexagonal Architecture,
CQRS/Event Sourcing (Axon Framework), and Spring Modulith.

## Quick Start

```bash
# One-command setup
./scripts/init-dev.sh

# Start application
./gradlew bootRun
```

## Prerequisites

- JDK 21 LTS
- Docker Desktop 4.x+
- Git 2.x+

## Architecture

- **Core:** Kotlin 2.2.21 + Spring Boot 3.5.7
- **CQRS/ES:** Axon Framework 4.12.1
- **Event Store:** PostgreSQL 16.10
- **Identity:** Keycloak 26.4.2 OIDC
- **Testing:** 7-layer defense (Static → Unit → Integration → Property → Fuzz → Concurrency → Mutation)

## Project Structure

```
eaf-v1/
├── framework/      # Core framework modules
├── products/       # Product implementations
├── shared/         # Shared libraries
├── apps/           # Application deployments
└── tools/          # Developer tools (CLI)
```

## Development Workflow

1. Create feature branch: `git checkout -b feature/my-feature`
2. Make changes (TDD: write tests first!)
3. Run tests: `./gradlew test`
4. Commit: Git hooks run automatically
5. Push: `git push` (pre-push hooks validate)
6. Create PR: CI/CD validates

## Documentation

- [Getting Started](docs/getting-started/)
- [Architecture Decisions](docs/architecture.md)
- [PRD](docs/PRD.md)
- [Tech Spec](docs/tech-spec.md)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md)

## License

[Apache 2.0](LICENSE)
```

### CONTRIBUTING.md Structure

```markdown
# Contributing to EAF v1.0

## Development Setup

1. Run `./scripts/init-dev.sh` (one-command setup)
2. Verify setup: `./gradlew build`

## Development Workflow

### Test-Driven Development (Mandatory)

EAF follows **Constitutional TDD** (Red-Green-Refactor):

1. **RED:** Write failing test first
2. **GREEN:** Write minimal code to pass test
3. **REFACTOR:** Improve code while keeping tests green

Git hooks enforce TDD compliance. Code without tests will be rejected.

### Quality Gates

**Pre-commit (<5s):**
- ktlint formatting check

**Pre-push (<30s):**
- Detekt static analysis
- Fast unit tests

**CI/CD (<15min):**
- Full build
- Integration tests
- Architecture tests (Konsist)

### Code Standards

- **Kotlin Style:** ktlint (official Kotlin style)
- **Coverage:** 85%+ line coverage (Kover)
- **Mutation Score:** 60-70% (Pitest)
- **Architecture:** Konsist validates boundaries

## Pull Request Process

1. Fork repository
2. Create feature branch
3. Write tests first (TDD)
4. Implement feature
5. Ensure all quality gates pass
6. Create PR with description
7. Address review feedback
8. Merge after approval

## Questions?

- Slack: #eaf-development
- Email: eaf-team@axians.com
```

### .editorconfig

```ini
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true

[*.{kt,kts}]
indent_style = space
indent_size = 4
max_line_length = 120

[*.{yml,yaml}]
indent_style = space
indent_size = 2

[*.md]
trim_trailing_whitespace = false
```

---

## Implementation Checklist

- [ ] Update README.md with structure above
- [ ] Create docs/getting-started/00-prerequisites.md
- [ ] Create CONTRIBUTING.md with development workflow
- [ ] Add LICENSE file (Apache 2.0)
- [ ] Create .editorconfig for IDE consistency
- [ ] Add project overview badges (build status, coverage)
- [ ] Test documentation on clean system
- [ ] Verify all links work
- [ ] Commit: "Add comprehensive foundation documentation"

---

## Test Evidence

- [ ] README.md quick start works on clean system
- [ ] Prerequisites documented accurately
- [ ] CONTRIBUTING.md workflow tested
- [ ] All documentation links working
- [ ] .editorconfig applied in IDEs
- [ ] LICENSE file present

---

## Definition of Done

- [ ] All acceptance criteria met
- [ ] Documentation tested on clean system
- [ ] All links verified
- [ ] README.md comprehensive
- [ ] CONTRIBUTING.md clear
- [ ] .editorconfig applied
- [ ] Story marked as DONE in workflow status

---

## Related Stories

**Previous Story:** Story 1.10 - Git Hooks for Quality Gates
**Next Epic:** Epic 2 - Walking Skeleton CQRS/Event Sourcing Core

---

## References

- PRD: FR015 (Comprehensive Onboarding and Learning)
- Architecture: Section 19 (Development Environment)
- Tech Spec: Section 3 (FR015 Implementation)
