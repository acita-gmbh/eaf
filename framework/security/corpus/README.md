# Jazzer Fuzzing Corpus

This directory contains corpus files for Jazzer fuzz testing of JWT security components. The corpus serves as seed inputs and regression test cases for coverage-guided fuzzing.

## Directory Structure

```
corpus/
├── jwt-format/           # JWT 3-part structure parsing seeds
├── token-extractor/      # Authorization header extraction seeds
└── role-normalization/   # Role claim structure seeds
```

## Purpose

**Seed Files**: Bootstrap fuzzing with known-good and known-bad inputs
**Auto-Generated Files**: Jazzer discovers new test cases during fuzzing and persists them here
**Regression Prevention**: Previously-discovered edge cases are automatically retested

## Corpus Management

### Adding New Seeds

When adding new corpus seeds manually:

1. **Choose appropriate directory** based on fuzzer target
2. **Use descriptive names** (e.g., `04-unicode-attack.txt`, `05-sql-injection.txt`)
3. **Keep files small** (<1KB recommended for fast fuzzing)
4. **One test case per file** for granular regression tracking

Example:
```bash
# Add new JWT format edge case
echo "eyJhbGci..malformed..Oi" > jwt-format/06-base64-corruption.txt
```

### Corpus Growth Monitoring

Jazzer automatically adds discovered inputs to the corpus. Monitor growth:

```bash
# Check corpus size
du -sh framework/security/corpus/

# Count corpus files per target
find framework/security/corpus -type f | wc -l

# List large corpus files (>100KB)
find framework/security/corpus -type f -size +100k -ls
```

### Corpus Cleanup

**When to clean**:
- Corpus exceeds 100MB total size
- Individual files exceed 1MB (inefficient for fuzzing)
- Corpus not accessed in 90+ days

**How to clean**:
```bash
# Remove large files (retain small, high-value seeds)
find framework/security/corpus -type f -size +1M -delete

# Remove old auto-generated files (keep manual seeds)
find framework/security/corpus -type f -name '*-auto-*' -mtime +90 -delete
```

**CRITICAL**: Never delete seed files (files present in git history). Only clean auto-generated corpus.

### Corpus Reset

If fuzzing discovers false positives or corpus quality degrades:

```bash
# Backup current corpus
cp -r framework/security/corpus framework/security/corpus.backup

# Reset to git-tracked seeds only
git checkout framework/security/corpus/

# Re-run fuzzers to rebuild corpus
./gradlew :framework:security:fuzzTest -PnightlyBuild
```

## Fuzzer Targets

### jwt-format/ (JwtFormatFuzzer.kt)
**Target**: JWT 3-part structure parsing (`header.payload.signature`)
**Runtime**: 5 minutes
**Strategies**: Valid structure, part count, Base64 encoding, delimiters, empty parts, edge cases

**Seed Files**:
- `valid-jwt.txt`: Standard RS256 JWT (3 parts, valid Base64)
- `malformed-parts.txt`: Missing signature part (only 2 parts)
- `empty-signature.txt`: Empty third part (`header.payload.`)

### token-extractor/ (TokenExtractorFuzzer.kt)
**Target**: Authorization header parsing (`Bearer <token>`)
**Runtime**: 5 minutes
**Strategies**: Case sensitivity, whitespace, missing scheme, wrong scheme, injection patterns, Unicode

**Seed Files**:
- `valid-bearer.txt`: Standard `Bearer <token>` format
- `lowercase-bearer.txt`: `bearer` (lowercase scheme)
- `extra-whitespace.txt`: `Bearer  <token>` (double space)

### role-normalization/ (RoleNormalizationFuzzer.kt)
**Target**: Keycloak role claim parsing (`realm_access`, `resource_access`)
**Runtime**: 5 minutes (2 tests × 2m30s)
**Strategies**: Null claims, malformed structures, injection patterns, Unicode attacks

**Seed Files**:
- `realm-roles.txt`: `{"realm_access":{"roles":["WIDGET_ADMIN","USER"]}}`
- `resource-roles.txt`: `{"resource_access":{"eaf-api":{"roles":["WIDGET_VIEWER"]}}}`
- `empty-roles.txt`: `{"realm_access":{"roles":[]},"resource_access":{}}`

## Execution

### Local Testing (Individual Fuzzer)
```bash
# Run specific fuzzer (5 minutes)
JAZZER_FUZZ=1 ./gradlew :framework:security:fuzzTest \
  --tests "*JwtFormatFuzzer*" \
  -PnightlyBuild

# Run all security fuzzers (15 minutes)
JAZZER_FUZZ=1 ./gradlew :framework:security:fuzzTest -PnightlyBuild
```

### CI/CD Execution
Fuzz tests run automatically in the nightly pipeline:
```yaml
# .github/workflows/nightly.yml
- name: Nightly Comprehensive Test Suite (V2)
  run: ./gradlew :framework:security:nightlyTest -PnightlyBuild
  timeout-minutes: 480
```

## Troubleshooting

### Fuzzer Crashes
If a fuzzer finds a crash:
1. Check `framework/security/build/test-results/fuzzTest/` for crash reports
2. Review the input that caused the crash (saved automatically)
3. Create a bug report with crash input
4. Fix the vulnerability in production code
5. Add crash input to corpus as regression test

### Corpus Corruption
If corpus files become corrupted:
1. Delete corrupted files
2. Re-run fuzzer to regenerate corpus
3. Jazzer will rebuild from seeds

### Performance Issues
If fuzzing is too slow:
1. Check for very large corpus files (>1MB)
2. Prune old or redundant test cases
3. Consider reducing maxDuration if 5min is excessive

## References

- [Story 3.12: Security Fuzz Testing](../../docs/sprint-artifacts/epic-3/story-3.12-security-fuzz-testing.md)
- [Architecture: Section 11 - 7-Layer Testing](../../docs/architecture.md#11-testing-strategy)
- [Jazzer Documentation](https://github.com/CodeIntelligenceTesting/jazzer)
- [Google OSS-Fuzz](https://github.com/google/oss-fuzz)
