# i18n Technical Specification

**Version:** 1.0
**Status:** Draft
**Author:** Claude
**Date:** 2025-12-03

## Executive Summary

This specification defines the internationalization (i18n) strategy for the DVMM application, covering both the Kotlin/Spring backend and React frontend. The primary language remains **English**, with a structured approach to enable future localization.

### Current State

| Layer | User-Facing Strings | i18n Status |
|-------|---------------------|-------------|
| Frontend (React) | ~150+ | None - all hardcoded |
| Backend API | ~60+ | None - all hardcoded |
| Domain Layer | ~15 | None - embedded in value objects |

### Proposed Solution

| Layer | Technology | Approach |
|-------|------------|----------|
| Frontend | react-i18next + zod-i18n-map | Key-based translations with namespaces |
| Backend | Spring MessageSource | Resource bundles with locale resolution |
| Integration | Error codes + frontend resolution | Backend returns codes, frontend displays messages |

---

## Table of Contents

1. [Design Decisions](#1-design-decisions)
2. [Frontend i18n Architecture](#2-frontend-i18n-architecture)
3. [Backend i18n Architecture](#3-backend-i18n-architecture)
4. [Error Handling Strategy](#4-error-handling-strategy)
5. [Implementation Plan](#5-implementation-plan)
6. [File Structure](#6-file-structure)
7. [Testing Strategy](#7-testing-strategy)
8. [Migration Guide](#8-migration-guide)

---

## 1. Design Decisions

### 1.1 Frontend Library Selection: react-i18next

**Decision:** Use `react-i18next` over `react-intl`

**Rationale:**
- Larger plugin ecosystem (language detection, lazy loading)
- Better TypeScript integration with typed keys
- More frequent updates and active maintenance
- Lower learning curve for the team
- Excellent React 19 compatibility

**Alternatives Considered:**
- `react-intl` - Good for ICU standards but requires manual language detection
- `Intlayer` - Newer, less proven ecosystem

### 1.2 Backend Approach: Error Codes + Frontend Resolution

**Decision:** Backend returns structured error codes; frontend resolves to localized messages

**Rationale:**
- Single source of truth for translations (frontend)
- No duplicate translation files
- Backend remains language-agnostic
- Simpler API contract
- Better caching (error structure doesn't change with locale)

**Alternative Considered:** Backend resolves messages via Accept-Language header
- Rejected: Duplicates translation effort, complicates caching

### 1.3 Translation Key Strategy: Namespaced Keys

**Decision:** Use hierarchical namespaces for translation keys

```
namespace:section.key
```

**Examples:**
```
common:actions.save
common:actions.cancel
requests:form.vmName.label
requests:form.vmName.placeholder
requests:errors.quotaExceeded
admin:queue.title
```

### 1.4 Primary Language Handling

**Decision:** English as default with fallback chain

```
User locale → English (en) → Key itself
```

---

## 2. Frontend i18n Architecture

### 2.1 Dependencies

Add to `dvmm/dvmm-web/package.json`:

```json
{
  "dependencies": {
    "i18next": "^25.1.4",
    "react-i18next": "^16.5.1",
    "i18next-browser-languagedetector": "^8.0.4",
    "i18next-http-backend": "^3.0.2",
    "zod-i18n-map": "^3.1.0"
  }
}
```

### 2.2 Configuration

Create `src/lib/i18n/config.ts`:

```typescript
import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import LanguageDetector from 'i18next-browser-languagedetector'
import HttpBackend from 'i18next-http-backend'
import { z } from 'zod'
import { zodI18nMap } from 'zod-i18n-map'

export const supportedLanguages = ['en', 'de'] as const
export type SupportedLanguage = (typeof supportedLanguages)[number]

export const defaultLanguage: SupportedLanguage = 'en'

export const namespaces = [
  'common',      // Shared UI: buttons, labels, status
  'requests',    // VM request forms, lists, details
  'admin',       // Admin dashboard, approval queue
  'errors',      // Error messages and codes
  'validation',  // Zod validation messages
] as const

export type Namespace = (typeof namespaces)[number]

i18n
  .use(HttpBackend)
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    fallbackLng: defaultLanguage,
    supportedLngs: supportedLanguages,

    ns: namespaces,
    defaultNS: 'common',

    backend: {
      loadPath: '/locales/{{lng}}/{{ns}}.json',
    },

    detection: {
      order: ['localStorage', 'navigator', 'htmlTag'],
      caches: ['localStorage'],
      lookupLocalStorage: 'dvmm-language',
    },

    interpolation: {
      escapeValue: false, // React already escapes
    },

    react: {
      useSuspense: true,
    },
  })
  .then(() => {
    // Configure Zod error map after i18n is initialized
    z.setErrorMap(zodI18nMap)
  })

export default i18n
```

### 2.3 Namespace Structure

#### `common.json` - Shared UI Elements

```json
{
  "appName": "DVMM",
  "actions": {
    "save": "Save",
    "cancel": "Cancel",
    "submit": "Submit",
    "delete": "Delete",
    "edit": "Edit",
    "retry": "Try Again",
    "goBack": "Go Back",
    "signIn": "Sign In",
    "signOut": "Sign Out",
    "loading": "Loading..."
  },
  "status": {
    "pending": "Pending",
    "approved": "Approved",
    "rejected": "Rejected",
    "cancelled": "Cancelled",
    "provisioning": "Provisioning",
    "ready": "Ready",
    "failed": "Failed"
  },
  "pagination": {
    "showing": "Showing {{from}}-{{to}} of {{total}}",
    "previous": "Previous",
    "next": "Next",
    "page": "Page {{current}} of {{total}}"
  },
  "time": {
    "ago": "{{time}} ago",
    "justNow": "Just now"
  }
}
```

#### `requests.json` - VM Request Module

```json
{
  "list": {
    "title": "My Requests",
    "empty": {
      "title": "No Requests",
      "description": "You haven't submitted any VM requests yet. Create your first request to get started.",
      "cta": "Request New VM"
    }
  },
  "form": {
    "title": "Request New VM",
    "subtitle": "Fill out the form to request a new virtual machine.",
    "vmName": {
      "label": "VM Name",
      "placeholder": "e.g. web-server-01",
      "description": "3-63 characters, lowercase letters, numbers, and hyphens"
    },
    "project": {
      "label": "Project",
      "placeholder": "Select project...",
      "quotaInfo": "{{available}} of {{total}} VMs available"
    },
    "justification": {
      "label": "Justification",
      "placeholder": "Describe the purpose of this VM...",
      "charCount": "{{current}}/{{min}} characters (min)"
    },
    "vmSize": {
      "label": "VM Size",
      "sizes": {
        "S": { "name": "Small", "specs": "2 vCPU, 4 GB RAM, 50 GB" },
        "M": { "name": "Medium", "specs": "4 vCPU, 8 GB RAM, 100 GB" },
        "L": { "name": "Large", "specs": "8 vCPU, 16 GB RAM, 200 GB" },
        "XL": { "name": "Extra Large", "specs": "16 vCPU, 32 GB RAM, 500 GB" }
      }
    },
    "submit": "Submit Request"
  },
  "detail": {
    "title": "Request Details",
    "timeline": {
      "title": "Timeline",
      "events": {
        "CREATED": "VM request was created",
        "APPROVED": "VM request was approved",
        "REJECTED": "VM request was rejected",
        "CANCELLED": "VM request was cancelled",
        "PROVISIONING_STARTED": "VM provisioning has begun",
        "VM_READY": "VM is ready for use"
      }
    }
  },
  "cancel": {
    "title": "Cancel Request",
    "confirm": "Are you sure you want to cancel the request for <strong>{{vmName}}</strong>? This action cannot be undone.",
    "reasonLabel": "Reason",
    "reasonOptional": "(optional)",
    "reasonPlaceholder": "Reason for cancellation...",
    "buttons": {
      "goBack": "Go Back",
      "cancel": "Cancel Request",
      "cancelling": "Cancelling..."
    }
  },
  "toast": {
    "submitSuccess": {
      "title": "Request submitted!",
      "description": "VM \"{{vmName}}\" has been submitted for approval."
    },
    "cancelSuccess": "Request cancelled successfully"
  }
}
```

#### `admin.json` - Admin Module

```json
{
  "queue": {
    "title": "Pending Requests",
    "subtitle": "Review and approve VM requests",
    "empty": {
      "title": "No Pending Requests",
      "description": "All caught up! There are no VM requests waiting for approval."
    },
    "table": {
      "requester": "Requester",
      "vmName": "VM Name",
      "project": "Project",
      "size": "Size",
      "age": "Age",
      "actions": "Actions"
    },
    "badges": {
      "waitingLong": "Waiting long"
    },
    "actions": {
      "approve": "Approve",
      "reject": "Reject"
    }
  },
  "projects": {
    "title": "Projects"
  }
}
```

#### `errors.json` - Error Messages

```json
{
  "network": {
    "title": "Connection Error",
    "description": "Please check your connection and try again."
  },
  "auth": {
    "title": "Authentication Error",
    "notAuthenticated": "Please sign in again.",
    "sessionExpired": "Your session has expired. Please sign in again."
  },
  "validation": {
    "title": "Validation Error",
    "description": "Please correct the highlighted fields."
  },
  "api": {
    "QUOTA_EXCEEDED": {
      "title": "Quota Exceeded",
      "description": "Project quota exceeded. Available: {{available}} VMs"
    },
    "NOT_FOUND": {
      "title": "Not Found",
      "description": "The requested resource was not found."
    },
    "FORBIDDEN": {
      "title": "Access Denied",
      "description": "You don't have permission to perform this action."
    },
    "INVALID_STATE": {
      "title": "Invalid Operation",
      "description": "Cannot perform this action on a request in {{state}} state."
    },
    "CONCURRENCY_CONFLICT": {
      "title": "Conflict",
      "description": "This resource was modified by another user. Please refresh and try again."
    },
    "INTERNAL_ERROR": {
      "title": "Server Error",
      "description": "An unexpected error occurred. Please try again later."
    }
  }
}
```

#### `validation.json` - Zod Validation Messages

```json
{
  "errors": {
    "invalid_type": "Expected {{expected}}, received {{received}}",
    "invalid_type_received_undefined": "Required",
    "invalid_literal": "Invalid literal value, expected {{expected}}",
    "unrecognized_keys": "Unrecognized key(s) in object: {{keys}}",
    "invalid_union": "Invalid input",
    "invalid_union_discriminator": "Invalid discriminator value. Expected {{options}}",
    "invalid_enum_value": "Invalid enum value. Expected {{options}}, received '{{received}}'",
    "invalid_arguments": "Invalid function arguments",
    "invalid_return_type": "Invalid function return type",
    "invalid_date": "Invalid date",
    "custom": "Invalid input",
    "invalid_intersection_types": "Intersection results could not be merged",
    "not_multiple_of": "Number must be a multiple of {{multipleOf}}",
    "not_finite": "Number must be finite",
    "too_small": {
      "array": {
        "exact": "Array must contain exactly {{minimum}} element(s)",
        "inclusive": "Array must contain at least {{minimum}} element(s)",
        "not_inclusive": "Array must contain more than {{minimum}} element(s)"
      },
      "string": {
        "exact": "Must be exactly {{minimum}} characters",
        "inclusive": "Minimum {{minimum}} characters required",
        "not_inclusive": "Must be more than {{minimum}} characters"
      },
      "number": {
        "exact": "Number must be exactly {{minimum}}",
        "inclusive": "Number must be at least {{minimum}}",
        "not_inclusive": "Number must be greater than {{minimum}}"
      }
    },
    "too_big": {
      "array": {
        "exact": "Array must contain exactly {{maximum}} element(s)",
        "inclusive": "Array must contain at most {{maximum}} element(s)",
        "not_inclusive": "Array must contain less than {{maximum}} element(s)"
      },
      "string": {
        "exact": "Must be exactly {{maximum}} characters",
        "inclusive": "Maximum {{maximum}} characters allowed",
        "not_inclusive": "Must be less than {{maximum}} characters"
      },
      "number": {
        "exact": "Number must be exactly {{maximum}}",
        "inclusive": "Number must be at most {{maximum}}",
        "not_inclusive": "Number must be less than {{maximum}}"
      }
    }
  },
  "custom": {
    "vmName": {
      "lowercase": "Only lowercase letters allowed",
      "invalidChars": "Only letters, numbers, and hyphens allowed",
      "startWithLetterOrNumber": "Must start with a letter or number",
      "endWithLetterOrNumber": "Must end with a letter or number",
      "noConsecutiveHyphens": "Cannot contain consecutive hyphens"
    },
    "project": {
      "required": "Project is required"
    },
    "vmSize": {
      "required": "Please select a VM size"
    }
  }
}
```

### 2.4 Component Integration Patterns

#### Hook Usage

```tsx
// src/components/requests/VmRequestForm.tsx
import { useTranslation } from 'react-i18next'

export function VmRequestForm() {
  const { t } = useTranslation('requests')

  return (
    <form>
      <FormLabel>
        {t('form.vmName.label')} <span className="text-destructive">*</span>
      </FormLabel>
      <Input placeholder={t('form.vmName.placeholder')} />
      <FormDescription>{t('form.vmName.description')}</FormDescription>
    </form>
  )
}
```

#### Multiple Namespaces

```tsx
// src/pages/RequestDetail.tsx
import { useTranslation } from 'react-i18next'

export function RequestDetail() {
  const { t } = useTranslation(['requests', 'common'])

  return (
    <div>
      <h1>{t('requests:detail.title')}</h1>
      <Badge>{t('common:status.pending')}</Badge>
    </div>
  )
}
```

#### Interpolation with Variables

```tsx
// src/components/requests/ProjectSelect.tsx
const { t } = useTranslation('requests')

// Template: "{{available}} of {{total}} VMs available"
<span>{t('form.project.quotaInfo', { available: 5, total: 10 })}</span>
```

#### HTML in Translations (Trans component)

```tsx
// src/components/requests/CancelConfirmDialog.tsx
import { Trans, useTranslation } from 'react-i18next'

const { t } = useTranslation('requests')

// Template: "Are you sure you want to cancel the request for <strong>{{vmName}}</strong>?"
<Trans
  i18nKey="requests:cancel.confirm"
  values={{ vmName }}
  components={{ strong: <strong /> }}
/>
```

### 2.5 Zod Integration

Update `src/lib/validations/vm-request.ts`:

```typescript
import { z } from 'zod'
import i18n from '@/lib/i18n/config'

export const vmNameSchema = z
  .string()
  .min(3)  // Uses validation:errors.too_small.string.inclusive
  .max(63) // Uses validation:errors.too_big.string.inclusive
  .superRefine((val, ctx) => {
    if (/[A-Z]/.test(val)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        params: { i18n: { key: 'custom.vmName.lowercase', ns: 'validation' } },
      })
    }
    if (/[^a-z0-9-]/.test(val)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        params: { i18n: { key: 'custom.vmName.invalidChars', ns: 'validation' } },
      })
    }
    if (val.startsWith('-')) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        params: { i18n: { key: 'custom.vmName.startWithLetterOrNumber', ns: 'validation' } },
      })
    }
    if (val.endsWith('-')) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        params: { i18n: { key: 'custom.vmName.endWithLetterOrNumber', ns: 'validation' } },
      })
    }
  })

export const projectIdSchema = z
  .string()
  .min(1, { message: i18n.t('validation:custom.project.required') })

export const justificationSchema = z
  .string()
  .min(10)
  .max(1000)

export const vmSizeSchema = z.enum(VM_SIZE_IDS, {
  errorMap: () => ({ message: i18n.t('validation:custom.vmSize.required') }),
})
```

### 2.6 Language Switcher Component

Create `src/components/layout/LanguageSwitcher.tsx`:

```tsx
import { useTranslation } from 'react-i18next'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { supportedLanguages, type SupportedLanguage } from '@/lib/i18n/config'

const languageNames: Record<SupportedLanguage, string> = {
  en: 'English',
  de: 'Deutsch',
}

export function LanguageSwitcher() {
  const { i18n } = useTranslation()

  return (
    <Select
      value={i18n.language}
      onValueChange={(lang) => i18n.changeLanguage(lang)}
    >
      <SelectTrigger className="w-32">
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {supportedLanguages.map((lang) => (
          <SelectItem key={lang} value={lang}>
            {languageNames[lang]}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
```

### 2.7 Date/Number Formatting

Create `src/lib/i18n/formatters.ts`:

```typescript
import { useTranslation } from 'react-i18next'

export function useFormatters() {
  const { i18n } = useTranslation()
  const locale = i18n.language

  return {
    formatDate: (date: Date, options?: Intl.DateTimeFormatOptions) =>
      new Intl.DateTimeFormat(locale, options).format(date),

    formatNumber: (num: number, options?: Intl.NumberFormatOptions) =>
      new Intl.NumberFormat(locale, options).format(num),

    formatRelativeTime: (date: Date) => {
      const rtf = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' })
      const diff = date.getTime() - Date.now()
      const diffDays = Math.round(diff / (1000 * 60 * 60 * 24))

      if (Math.abs(diffDays) < 1) {
        const diffHours = Math.round(diff / (1000 * 60 * 60))
        if (Math.abs(diffHours) < 1) {
          const diffMinutes = Math.round(diff / (1000 * 60))
          return rtf.format(diffMinutes, 'minute')
        }
        return rtf.format(diffHours, 'hour')
      }
      return rtf.format(diffDays, 'day')
    },
  }
}
```

---

## 3. Backend i18n Architecture

### 3.1 Design Philosophy

The backend does NOT resolve localized messages. Instead, it returns structured error responses with:
1. **Error type/code** - Machine-readable identifier
2. **Technical details** - Parameters for message interpolation
3. **Default message** - English fallback (for debugging/logging)

The frontend is responsible for resolving the error code to a localized message.

### 3.2 Error Response Structure

Update error DTOs to include error codes:

```kotlin
// dvmm-api/src/main/kotlin/de/acci/dvmm/api/vmrequest/ErrorResponses.kt

@JsonInclude(JsonInclude.Include.NON_NULL)
public data class ApiError(
    val code: String,           // e.g., "QUOTA_EXCEEDED"
    val message: String,        // English fallback: "Project quota exceeded"
    val details: Map<String, Any>? = null  // Interpolation params
)

public data class QuotaExceededResponse(
    override val code: String = "QUOTA_EXCEEDED",
    override val message: String,
    val available: Int,
    val requested: Int,
    override val details: Map<String, Any>? = null
) : ApiError(code, message, mapOf("available" to available))

public data class NotFoundResponse(
    override val code: String = "NOT_FOUND",
    override val message: String,
    val resourceType: String,
    val resourceId: String
) : ApiError(code, message, mapOf("resourceType" to resourceType, "resourceId" to resourceId))

public data class ForbiddenResponse(
    override val code: String = "FORBIDDEN",
    override val message: String,
    val reason: String? = null
) : ApiError(code, message)

public data class InvalidStateResponse(
    override val code: String = "INVALID_STATE",
    override val message: String,
    val currentState: String,
    val allowedStates: List<String>? = null
) : ApiError(code, message, mapOf("state" to currentState))

public data class ValidationErrorResponse(
    override val code: String = "VALIDATION_ERROR",
    override val message: String = "Validation failed",
    val errors: List<FieldError>
) : ApiError(code, message) {
    public data class FieldError(
        val field: String,
        val code: String,      // e.g., "TOO_SHORT", "INVALID_FORMAT"
        val message: String,   // English fallback
        val params: Map<String, Any>? = null  // e.g., { "min": 3, "max": 63 }
    )
}
```

### 3.3 Error Codes Enum (Shared Reference)

Create `eaf-core/src/main/kotlin/de/acci/eaf/core/error/ErrorCodes.kt`:

```kotlin
package de.acci.eaf.core.error

/**
 * Standard error codes for API responses.
 * Frontend uses these codes to resolve localized messages.
 */
public object ErrorCodes {
    // Validation errors
    public const val VALIDATION_ERROR: String = "VALIDATION_ERROR"
    public const val REQUIRED: String = "REQUIRED"
    public const val TOO_SHORT: String = "TOO_SHORT"
    public const val TOO_LONG: String = "TOO_LONG"
    public const val INVALID_FORMAT: String = "INVALID_FORMAT"

    // Business errors
    public const val QUOTA_EXCEEDED: String = "QUOTA_EXCEEDED"
    public const val INVALID_STATE: String = "INVALID_STATE"
    public const val NOT_FOUND: String = "NOT_FOUND"
    public const val FORBIDDEN: String = "FORBIDDEN"

    // System errors
    public const val CONCURRENCY_CONFLICT: String = "CONCURRENCY_CONFLICT"
    public const val INTERNAL_ERROR: String = "INTERNAL_ERROR"
}
```

### 3.4 Domain Validation Error Pattern

Update domain value objects to return structured errors:

```kotlin
// dvmm-domain/src/main/kotlin/de/acci/dvmm/domain/vmrequest/VmName.kt

public data class ValidationError(
    val code: String,
    val message: String,
    val params: Map<String, Any> = emptyMap()
)

@JvmInline
public value class VmName private constructor(public val value: String) {
    public companion object {
        private val VALID_PATTERN = "^[a-z0-9][a-z0-9-]*[a-z0-9]$".toRegex()
        private const val MIN_LENGTH = 3
        private const val MAX_LENGTH = 63

        public fun create(value: String): Either<ValidationError, VmName> {
            return when {
                value.length < MIN_LENGTH -> ValidationError(
                    code = "TOO_SHORT",
                    message = "VM name must be at least $MIN_LENGTH characters long",
                    params = mapOf("min" to MIN_LENGTH)
                ).left()

                value.length > MAX_LENGTH -> ValidationError(
                    code = "TOO_LONG",
                    message = "VM name must not exceed $MAX_LENGTH characters",
                    params = mapOf("max" to MAX_LENGTH)
                ).left()

                value.contains("--") -> ValidationError(
                    code = "INVALID_FORMAT",
                    message = "VM name cannot contain consecutive hyphens",
                    params = mapOf("rule" to "NO_CONSECUTIVE_HYPHENS")
                ).left()

                !VALID_PATTERN.matches(value) -> ValidationError(
                    code = "INVALID_FORMAT",
                    message = "VM name must contain only lowercase letters, numbers, and hyphens",
                    params = mapOf("rule" to "ALPHANUMERIC_HYPHEN")
                ).left()

                else -> VmName(value).right()
            }
        }
    }
}
```

### 3.5 Optional: Backend MessageSource for Logging/Email

If backend needs localized messages (e.g., email notifications), configure MessageSource:

```kotlin
// dvmm-app/src/main/kotlin/de/acci/dvmm/config/I18nConfig.kt

@Configuration
public class I18nConfig {

    @Bean
    public fun messageSource(): MessageSource {
        val source = ReloadableResourceBundleMessageSource()
        source.setBasenames(
            "classpath:messages/errors",
            "classpath:messages/notifications"
        )
        source.setDefaultEncoding("UTF-8")
        source.setFallbackToSystemLocale(false)
        source.setDefaultLocale(Locale.ENGLISH)
        return source
    }
}
```

Resource bundle structure:
```
dvmm-app/src/main/resources/
├── messages/
│   ├── errors.properties          # English (default)
│   ├── errors_de.properties       # German
│   ├── notifications.properties
│   └── notifications_de.properties
```

---

## 4. Error Handling Strategy

### 4.1 Frontend Error Resolution

Create `src/lib/api/error-handler.ts`:

```typescript
import i18n from '@/lib/i18n/config'
import { toast } from 'sonner'

interface ApiError {
  code: string
  message: string
  details?: Record<string, unknown>
}

interface FieldError {
  field: string
  code: string
  message: string
  params?: Record<string, unknown>
}

interface ValidationError extends ApiError {
  errors: FieldError[]
}

export function handleApiError(error: ApiError): void {
  const { code, details } = error

  // Try to get localized message, fallback to API message
  const title = i18n.t(`errors:api.${code}.title`, { defaultValue: error.message })
  const description = i18n.t(`errors:api.${code}.description`, {
    ...details,
    defaultValue: '',
  })

  toast.error(title, { description: description || undefined })
}

export function mapValidationErrors(
  errors: FieldError[]
): Record<string, { message: string }> {
  const result: Record<string, { message: string }> = {}

  for (const error of errors) {
    // Try to get localized message
    const message = i18n.t(`validation:errors.${error.code}`, {
      ...error.params,
      defaultValue: error.message,
    })
    result[error.field] = { message }
  }

  return result
}
```

### 4.2 React Query Integration

```typescript
// src/lib/api/use-api-mutation.ts
import { useMutation } from '@tanstack/react-query'
import { handleApiError } from './error-handler'

export function useApiMutation<TData, TVariables>(
  mutationFn: (vars: TVariables) => Promise<TData>,
  options?: {
    onSuccess?: (data: TData) => void
    showErrorToast?: boolean
  }
) {
  return useMutation({
    mutationFn,
    onError: (error: ApiError) => {
      if (options?.showErrorToast !== false) {
        handleApiError(error)
      }
    },
    onSuccess: options?.onSuccess,
  })
}
```

---

## 5. Implementation Plan

### Phase 1: Foundation (Story 1)

**Scope:** Set up i18n infrastructure without changing existing behavior

**Frontend Tasks:**
1. Install dependencies (`react-i18next`, `i18next`, `zod-i18n-map`)
2. Create i18n configuration (`src/lib/i18n/config.ts`)
3. Create locale file structure (`public/locales/en/*.json`)
4. Wrap app with `I18nextProvider`
5. Add Suspense boundary for async loading

**Backend Tasks:**
1. Create `ErrorCodes` constants in `eaf-core`
2. Update error response DTOs to include `code` field
3. Ensure all API responses include error codes

**Tests:**
- Verify i18n loads correctly
- Verify fallback to English works
- Verify error codes are present in API responses

### Phase 2: Common UI (Story 2)

**Scope:** Migrate shared components and common UI elements

**Components to Migrate:**
- `Header.tsx` - App name, navigation labels
- `StatusBadge.tsx` - Status labels
- `EmptyState.tsx` - Empty state messages
- `ErrorBoundary.tsx` - Error messages
- Buttons and common actions

**Pattern:**
1. Extract all hardcoded strings to `common.json`
2. Replace with `t('common:key')` calls
3. Update tests to include i18n provider

### Phase 3: Forms & Validation (Story 3)

**Scope:** Migrate form components and Zod validation

**Components to Migrate:**
- `VmRequestForm.tsx` - Labels, placeholders, descriptions
- `CancelConfirmDialog.tsx` - Dialog text
- `ProjectSelect.tsx` - Dropdown labels
- `VmSizeSelector.tsx` - Size names and descriptions

**Validation Migration:**
1. Configure `zod-i18n-map`
2. Update Zod schemas to use i18n params
3. Create `validation.json` namespace

### Phase 4: Pages & Features (Story 4)

**Scope:** Migrate all page-level components

**Pages to Migrate:**
- `Dashboard.tsx`
- `MyRequests.tsx`
- `NewRequest.tsx`
- `RequestDetail.tsx`
- `admin/PendingRequests.tsx`

### Phase 5: Toast & Errors (Story 5)

**Scope:** Migrate all toast notifications and error handling

**Tasks:**
1. Create `errors.json` namespace
2. Update all `toast.success/error` calls
3. Create error handling utilities
4. Map API error codes to localized messages

### Phase 6: Language Switcher (Story 6)

**Scope:** Add user-facing language selection

**Tasks:**
1. Create `LanguageSwitcher` component
2. Add to Header
3. Persist selection in localStorage
4. Add German translations (if needed)

---

## 6. File Structure

### Frontend

```
dvmm/dvmm-web/
├── public/
│   └── locales/
│       ├── en/
│       │   ├── common.json
│       │   ├── requests.json
│       │   ├── admin.json
│       │   ├── errors.json
│       │   └── validation.json
│       └── de/
│           ├── common.json
│           ├── requests.json
│           ├── admin.json
│           ├── errors.json
│           └── validation.json
├── src/
│   └── lib/
│       └── i18n/
│           ├── config.ts
│           ├── formatters.ts
│           └── types.ts
```

### Backend

```
eaf/eaf-core/
└── src/main/kotlin/de/acci/eaf/core/
    └── error/
        └── ErrorCodes.kt

dvmm/dvmm-api/
└── src/main/kotlin/de/acci/dvmm/api/
    └── common/
        └── ErrorResponses.kt

dvmm/dvmm-app/
└── src/main/resources/
    └── messages/
        ├── errors.properties
        ├── errors_de.properties
        ├── notifications.properties
        └── notifications_de.properties
```

---

## 7. Testing Strategy

### Frontend Unit Tests

```typescript
// src/components/requests/__tests__/VmRequestForm.test.tsx
import { render, screen } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import i18n from '@/lib/i18n/config'
import { VmRequestForm } from '../VmRequestForm'

const wrapper = ({ children }) => (
  <I18nextProvider i18n={i18n}>
    {children}
  </I18nextProvider>
)

describe('VmRequestForm', () => {
  it('displays translated labels', () => {
    render(<VmRequestForm />, { wrapper })
    expect(screen.getByText('VM Name')).toBeInTheDocument()
  })

  it('supports language switching', async () => {
    render(<VmRequestForm />, { wrapper })
    await i18n.changeLanguage('de')
    expect(screen.getByText('VM-Name')).toBeInTheDocument()
  })
})
```

### Backend Tests

```kotlin
// Verify error codes are present
@Test
fun `error response includes error code`() {
    val response = webTestClient.post()
        .uri("/api/vm-requests")
        .bodyValue(invalidRequest)
        .exchange()
        .expectStatus().isBadRequest
        .expectBody<ValidationErrorResponse>()
        .returnResult()
        .responseBody!!

    assertThat(response.code).isEqualTo("VALIDATION_ERROR")
    assertThat(response.errors.first().code).isNotBlank()
}
```

### E2E Tests

```typescript
// e2e/i18n.spec.ts
import { test, expect } from '@playwright/test'

test.describe('i18n', () => {
  test('switches language and persists preference', async ({ page }) => {
    await page.goto('/')

    // Switch to German
    await page.getByRole('combobox', { name: /language/i }).click()
    await page.getByRole('option', { name: 'Deutsch' }).click()

    // Verify translation
    await expect(page.getByText('Anfrage stellen')).toBeVisible()

    // Reload and verify persistence
    await page.reload()
    await expect(page.getByText('Anfrage stellen')).toBeVisible()
  })
})
```

---

## 8. Migration Guide

### Step-by-Step Component Migration

#### Before:
```tsx
export function MyComponent() {
  return (
    <div>
      <h1>Request New VM</h1>
      <p>Fill out the form to request a new virtual machine.</p>
      <Button>Submit</Button>
    </div>
  )
}
```

#### After:
```tsx
import { useTranslation } from 'react-i18next'

export function MyComponent() {
  const { t } = useTranslation(['requests', 'common'])

  return (
    <div>
      <h1>{t('requests:form.title')}</h1>
      <p>{t('requests:form.subtitle')}</p>
      <Button>{t('common:actions.submit')}</Button>
    </div>
  )
}
```

### Migration Checklist per Component

- [ ] Import `useTranslation` hook
- [ ] Identify all hardcoded strings
- [ ] Add translations to appropriate namespace
- [ ] Replace hardcoded strings with `t()` calls
- [ ] Handle interpolation for dynamic values
- [ ] Update tests to include i18n provider
- [ ] Verify component renders correctly
- [ ] Verify translations load without flash

---

## Appendix A: TypeScript Types for i18n

Create `src/lib/i18n/types.ts`:

```typescript
import type resources from './resources'

declare module 'i18next' {
  interface CustomTypeOptions {
    defaultNS: 'common'
    resources: typeof resources
  }
}
```

This enables TypeScript autocomplete for translation keys.

---

## Appendix B: Performance Considerations

### Lazy Loading Namespaces

```typescript
// Load namespaces on demand
const { t } = useTranslation('admin', { useSuspense: true })
```

### Bundle Size Impact

| Package | Size (gzipped) |
|---------|---------------|
| i18next | 15.1 kB |
| react-i18next | 7.1 kB |
| zod-i18n-map | 2.3 kB |
| **Total** | **~24.5 kB** |

### Caching Strategy

Translation files are cached by the browser. Consider cache headers:

```nginx
location /locales/ {
    add_header Cache-Control "public, max-age=604800";  # 1 week
}
```

---

## References

- [react-i18next Documentation](https://react.i18next.com/)
- [i18next Documentation](https://www.i18next.com/)
- [zod-i18n-map](https://github.com/aiji42/zod-i18n)
- [Spring Boot i18n Guide](https://www.baeldung.com/spring-boot-internationalization)
- [ICU Message Format](https://unicode-org.github.io/icu/userguide/format_parse/messages/)
