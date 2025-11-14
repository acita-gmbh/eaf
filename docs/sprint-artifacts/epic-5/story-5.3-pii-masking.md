# Story 5.3: PII Masking for GDPR Compliance

**Epic:** Epic 5 - Observability & Monitoring
**Status:** TODO
**Related Requirements:** FR005, FR006 (GDPR compliance), NFR002

---

## User Story

As a framework developer,
I want automatic PII masking in log messages,
So that sensitive data is not exposed in logs (GDPR compliance).

---

## Acceptance Criteria

1. ✅ PiiMaskingFilter.kt implements Logback filter
2. ✅ Regex patterns detect and mask: email addresses, phone numbers, credit card numbers
3. ✅ Masking format: email → e***@example.com, phone → ***-***-1234
4. ✅ Configurable PII patterns (extensible for custom data types)
5. ✅ Unit tests validate masking for all PII types
6. ✅ Integration test validates: log message with email → email masked in output
7. ✅ Performance impact <1ms per log entry
8. ✅ PII masking documented in docs/reference/security-logging.md

---

## Prerequisites

**Story 5.2** - Automatic Context Injection

---

## References

- PRD: FR005, FR006 (GDPR crypto-shredding, PII masking), NFR002
- Architecture: Section 13 (PII Masking)
- Tech Spec: Section 3 (FR005, FR006)
