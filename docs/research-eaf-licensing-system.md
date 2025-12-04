# EAF Licensing System Research & Recommendations

## Executive Summary

This document provides comprehensive research and recommendations for implementing a licensing system for EAF (Enterprise Application Framework) and its products like DVMM. The system must support:

- **On-premise deployment** with offline/air-gapped capability
- **Core-based licensing** (CPU cores managed by products like DVMM)
- **Multi-tenancy** with per-tenant license allocation
- **Subscription model** with annual renewals
- **Integration with existing DPCM Service** for migration path

---

## 1. Current Context

### EAF Architecture
- **Framework-First Design**: EAF modules (`eaf-core`, `eaf-tenant`, `eaf-auth`, etc.) are reusable across products
- **Multi-Tenancy**: PostgreSQL RLS with `TenantContext` for isolation
- **IdP-Agnostic Auth**: Pluggable identity providers (Keycloak for MVP)
- **Products**: DVMM (VMware management), future products built on EAF

### Deployment Model
- Products packaged as **Docker images**
- Installed **on-premise** at customer sites
- Manages infrastructure resources (e.g., vSphere with 150 CPU cores)
- Requires **subscription licensing** based on managed resources

### Existing System
- **DPCM Service**: Legacy license server for DCA framework
- Migration path needed from DPCM to EAF licensing

---

## 2. Current DPCM Licensing Architecture

This section documents the existing DPCM licensing implementation to inform EAF design decisions.

### 2.1 System Overview

The current DPCM licensing consists of two components:

| Component | Stack | Role |
|-----------|-------|------|
| **DPCMService** | Node.js, Fastify, GraphQL, PostgreSQL | License server (cloud-hosted) |
| **DCA (DPCM X)** | Node.js, React, PostgreSQL | Product client (on-premise) |

### 2.2 Registration Flow (V2 - Current)

The V2 registration uses **client-side key generation** with challenge-response authentication:

```
┌─────────────────────────────────────────────────────────────────────┐
│                    V2 Registration Flow                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  DCA Client (On-Premise)                                             │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │ 1. Generate RSA 2048-bit keypair locally                      │  │
│  │ 2. Store privateKey in secureKeyStorage (encrypted)           │  │
│  │ 3. privateKey NEVER leaves the device                         │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                              │                                       │
│                              ▼                                       │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │ POST /api/v2/register/challenge                               │  │
│  │   Request:  { customerId, token }                             │  │
│  │   Response: { challengeId, challenge, dcaPublicKey }          │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                              │                                       │
│                              ▼                                       │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │ Client signs challenge with privateKey                        │  │
│  │ signedChallenge = sign(challenge, privateKey)                 │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                              │                                       │
│                              ▼                                       │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │ POST /api/v2/register                                         │  │
│  │   Request:  { customerId, challengeId, signedChallenge,       │  │
│  │              publicKeyPem, instanceId, appId, hostname }      │  │
│  │   Response: { certId, validation, message, signature }        │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                              │                                       │
│                              ▼                                       │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │ Client verifies server signature using dcaPublicKey           │  │
│  │ Stores certId + keyId reference for future communications     │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.3 Environment Data Reporting (License Validation)

After registration, clients periodically report environment data for license validation:

```
POST /api/v2/environment/submit
  Request: {
    customerId, instanceId, challengeId, signedChallenge,
    environmentData: {
      systems: [
        {
          typeModel: "9009-42G",
          serialNum: "ABC123",
          licensing: {
            configurableSysProcUnits: 48,
            isLinuxOnlySystem: false
          }
        }
      ]
    },
    timestamp, signature
  }
  Response: {
    received: true,
    licenseValidation: {
      isValid: true,
      isExceeded: false,
      gracePeriod: false,
      gracePeriodEnds: null
    }
  }
```

### 2.4 Core-Based License Model

Licenses are based on IBM Power system core types:

| Core Type | Description | Example Systems |
|-----------|-------------|-----------------|
| **S-Cores** | Small systems | Power5-185, Power6-520 |
| **M-Cores** | Medium systems | Power7-720, Power8-S812 |
| **L-Cores** | Large systems | Power7-770, Power9-E980 |
| **IFL-Cores** | Linux-only (Integrated Facility Link) | Power8+, Linux/VIOS only |

**Linux-Only Detection**:
```javascript
isLinuxOnlySystem = configurableSysProcUnitsLinuxVios === configurableSysProcUnits
```

### 2.5 Key Rotation Support

V2 includes built-in key rotation (90-day recommended cycle):

```
POST /api/v2/register/rotate
  Request: {
    customerId,
    oldPublicKey,      // Current key
    newPublicKey,      // New key to register
    signature,         // Signed with OLD key (proves ownership)
    rotationData
  }
  Response: { certId }  // New certificate ID
```

### 2.6 Secure Key Storage

Private keys are stored in encrypted local storage, never in application settings:

```javascript
// secureKeyStorage.js pattern
await secureKeyStorage.storePrivateKey(keyId, privateKeyPem)   // Encrypted storage
await secureKeyStorage.retrievePrivateKey(keyId)               // Decrypted retrieval
await secureKeyStorage.rotateKey(oldKeyId, newPrivateKeyPem)   // Atomic rotation
await secureKeyStorage.cleanupArchivedKeys()                   // Remove old keys
```

### 2.7 License Enforcement States

```
┌────────────┐     ┌────────────┐     ┌────────────┐     ┌────────────┐
│   VALID    │────▶│  EXCEEDED  │────▶│   GRACE    │────▶│  EXPIRED   │
│            │     │  (warning) │     │  (backup   │     │  (blocked) │
│            │     │            │     │   only)    │     │            │
└────────────┘     └────────────┘     └────────────┘     └────────────┘
      │                                     │                   │
      │ All commands   │ All commands      │ CMD_VM_BACKUP    │ No commands
      │ allowed        │ + warning         │ CMD_VM_BULK_     │ allowed
      │                │                   │ BACKUP only      │
```

### 2.8 Deprecated V1 Flow (Legacy)

The V1 flow is deprecated due to security concerns (server sent private keys):

```javascript
/**
 * @deprecated SECURITY VULNERABILITY - Receives private keys from server
 * Use backendInstanceRegistrationV2 instead
 */
export const backendInstanceRegistration = async (token) => { ... }
```

**Key Differences V1 vs V2**:

| Aspect | V1 (Deprecated) | V2 (Current) |
|--------|-----------------|--------------|
| Key Generation | Server-side | Client-side |
| Private Key | Sent over network | Never leaves device |
| Authentication | Token-based | Challenge-response |
| Key Rotation | Manual | Built-in (90-day) |
| Storage | Settings (plaintext) | Encrypted secure storage |

---

## 3. Industry-Standard Solutions Analysis

### 3.1 Commercial Solutions

| Solution | Deployment | Pricing Model | Key Features |
|----------|------------|---------------|--------------|
| **[Keygen](https://keygen.sh/)** | Cloud + Self-hosted (CE/EE) | Free CE, Paid EE | Fair Source, REST API, offline support, machine fingerprinting |
| **[Cryptlex](https://cryptlex.com/)** | Cloud + Self-hosted | Tiered | Node-locked, floating, offline, customer portal |
| **[LicenseSpring](https://licensespring.com/)** | Cloud + Self-hosted | Per-activation | Hardware keys, air-gapped, metering |
| **[Cryptolens](https://cryptolens.io/)** | Cloud + On-premise server | Free tier + Paid | Open-source server option, .NET/Java SDKs |

**Recommendation**: **Keygen** stands out for:
- **Fair Source License** (becomes Apache 2.0 after 2 years)
- **Self-hosted Community Edition** (free for commercial use)
- **Enterprise Edition** for audit logs, environments, SSO
- **Comprehensive offline/air-gapped support**
- **REST API** compatible with Spring Boot

### 3.2 Build vs. Buy Analysis

| Approach | Pros | Cons |
|----------|------|------|
| **Build Custom** | Full control, EAF integration, no vendor lock-in | Development cost, security expertise needed, maintenance burden |
| **Adopt Keygen CE** | Production-ready, security-hardened, self-hosted | Less customization, dependency on external project |
| **Hybrid** | Core licensing from Keygen, EAF-specific extensions | Integration complexity |

**Recommendation**: **Hybrid Approach**
- Use Keygen CE as the license server backbone
- Build EAF-specific modules for multi-tenancy and product integration
- Create `eaf-licensing` module as abstraction layer

---

## 4. VMware Ecosystem Licensing Analysis

Since DVMM targets VMware vSphere environments, understanding how other VMware management software vendors handle licensing is critical for competitive positioning.

### 4.1 VMware's Own Licensing Changes (2024-2025)

Following Broadcom's acquisition (December 2023), VMware underwent significant licensing changes:

| Change | Impact |
|--------|--------|
| **Perpetual → Subscription** | All new deployments require subscription licenses |
| **Socket → Core-based** | Minimum 16 cores per CPU, 72-core minimum orders |
| **Product Consolidation** | 168 products reduced to 4 bundles (VCF, VVF, VVS, VSEP) |
| **Pricing** | ~$350/core/year (VCF), ~$135/core/year (VVF) |
| **Free Tier Eliminated** | vSphere Hypervisor (free) discontinued |

### 4.2 Third-Party VMware Management Software Licensing

| Vendor | Product Type | Primary Model | Alternative | Typical Pricing |
|--------|-------------|---------------|-------------|-----------------|
| **Veeam** | Backup/DR | Per-Socket (perpetual) | Per-Workload VUL (subscription) | VUL: flexible per instance |
| **Runecast** | Compliance/Analysis | Per-Socket | Subscription only | ~$500/year per socket |
| **SolarWinds VMAN** | Monitoring | Per-Socket (tiers) | Perpetual or subscription | Tiers: 8/64/192 sockets |
| **NAKIVO** | Backup/DR | Per-Socket (perpetual) | Per-Workload (subscription) | $229/socket or $2.45/workload/mo |
| **IBM Turbonomic** | Resource Optimization | Per-Socket/Per-VM | Transitioning to Per-Core | $11K-$49K+ enterprise |
| **ManageEngine OpManager** | Monitoring | Per-Device | - | From $245/10 devices |

### 4.3 Common Licensing Patterns

**Pattern 1: Per-Socket (Traditional)**
- Most common for VMware ecosystem tools
- Follows the "old" VMware licensing model customers are familiar with
- Simple to calculate: count physical CPU sockets on hosts
- Used by: Runecast, SolarWinds, Veeam (perpetual), NAKIVO (perpetual)

**Pattern 2: Per-Workload/VM (Modern)**
- Gaining popularity for flexibility and pay-for-what-you-use
- Better for environments with variable VM counts
- Typically subscription-based
- Used by: Veeam VUL, NAKIVO subscription, ManageEngine

**Pattern 3: Per-Core (Following VMware)**
- Aligns with VMware's new model but more complex
- Enables granular tenant allocation
- Turbonomic transitioning to this model
- Risk: customers already fatigued by core counting from VMware itself

### 4.4 Licensing Model Trade-offs for DVMM

| Model | Pros | Cons |
|-------|------|------|
| **Per-Core** | Aligns with VMware; granular tenant allocation | Complex; customers fatigued by core counting |
| **Per-Socket** | Simple; familiar to VMware admins | Less granular; doesn't scale with VM density |
| **Per-Managed VM** | Pay-for-what-you-use; tenant-friendly | Harder to predict revenue; requires VM tracking |
| **Per-Tenant** | Simple for multi-tenant SaaS | May not scale for large tenants |
| **Hybrid** | Customer choice; competitive flexibility | Implementation complexity |

### 4.5 Industry Trends (2024-2025)

1. **Subscription over Perpetual**: VMware discontinued perpetual entirely; Veeam phasing out socket licensing
2. **Core-based gaining traction**: Following VMware's lead, but with customer resistance
3. **Workload-based for flexibility**: Allows customers to pay for actual usage
4. **Tiered editions**: Most vendors offer Standard/Pro/Enterprise capability tiers
5. **Bundling**: VMware bundles everything (Aria, vSAN, etc.) into VCF/VVF

### 4.6 Recommendation for DVMM

Given DVMM's position as a VM provisioning/management tool for vSphere in the DACH market:

**Recommended Approach: Hybrid Model (like NAKIVO)**

| Option | Target Customer | Pricing Model |
|--------|-----------------|---------------|
| **Perpetual** | Traditional enterprises | Per-Socket (simple, familiar) |
| **Subscription** | Modern/cloud-native | Per-Managed VM or Per-Core |

**Rationale:**
- German enterprises often prefer perpetual licenses for budget predictability
- Per-socket is simpler than VMware's own licensing (competitive advantage)
- Per-VM subscription enables tenant-level billing for MSP scenarios
- Aligns with existing DPCM core-based model for migration continuity

---

## 5. License Models for EAF Products

### 5.1 Core-Based Licensing (Primary for DVMM)

```
License Metric: CPU Cores under management
Example: DVMM managing vSphere with 150 cores = 150-core license
```

**Implementation**:
```kotlin
data class CoreBasedLicense(
    val productId: ProductId,
    val customerId: CustomerId,
    val licensedCores: Int,          // e.g., 150
    val usedCores: Int,              // Reported by product
    val validFrom: Instant,
    val validUntil: Instant,         // Subscription end
    val graceEndDate: Instant?,      // Grace period after expiry
)
```

**Core Counting in Virtualization**:
- Count **physical cores** on managed hosts
- Use VMware API to retrieve `Host > Hardware > CPU > Cores per socket`
- Handle vMotion (fingerprint remains stable)
- Report usage periodically to license server

### 5.2 Subscription Model

| Term | Description |
|------|-------------|
| **Annual Subscription** | 1-year license with renewal |
| **Grace Period** | 15-30 days after expiry (industry standard) |
| **True-Up** | Periodic adjustment for increased usage |
| **Enforcement** | Soft limits → warnings → hard limits |

### 5.3 Multi-Tenant License Allocation

```
┌─────────────────────────────────────────────────────────┐
│                    License Pool                         │
│                   (1000 total cores)                    │
├─────────────────────────────────────────────────────────┤
│  Tenant A        │  Tenant B        │  Shared Pool      │
│  300 cores       │  200 cores       │  500 cores        │
│  (dedicated)     │  (dedicated)     │  (on-demand)      │
└─────────────────────────────────────────────────────────┘
```

**Allocation Strategies**:
1. **Dedicated Allocation**: Fixed cores per tenant
2. **Shared Pool**: First-come, first-served from global pool
3. **Hybrid**: Guaranteed minimum + burst from shared pool

---

## 6. Proposed Architecture

### 6.1 High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                        Customer Premise                          │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────────────┐  │
│  │   DVMM      │    │  eaf-       │    │   EAF License       │  │
│  │   Product   │───▶│  licensing  │───▶│   Server            │  │
│  │             │    │  (module)   │    │   (Self-hosted)     │  │
│  └─────────────┘    └─────────────┘    └──────────┬──────────┘  │
│                                                    │             │
│                                        ┌───────────▼───────────┐ │
│                                        │   PostgreSQL          │ │
│                                        │   (License Store)     │ │
│                                        └───────────────────────┘ │
│                                                                  │
└────────────────────────────────────┬─────────────────────────────┘
                                     │
                    ┌────────────────┼────────────────┐
                    │   (Optional)   │                │
                    ▼                │                │
    ┌───────────────────────┐       │      ┌─────────▼─────────┐
    │  ACCI Central         │       │      │  Offline License  │
    │  License Portal       │◀──────┘      │  File Exchange    │
    │  (Cloud-hosted)       │              │  (Air-gapped)     │
    └───────────────────────┘              └───────────────────┘
```

### 6.2 EAF Module Structure

```
eaf/
├── eaf-licensing/                    # 🔐 LICENSING FRAMEWORK
│   └── src/main/kotlin/
│       └── de/acci/eaf/licensing/
│           ├── LicenseService.kt     # Interface for license operations
│           ├── LicenseValidator.kt   # Cryptographic validation
│           ├── LicenseFile.kt        # Offline license file handling
│           ├── MachineFingerprint.kt # Hardware binding
│           ├── UsageReporter.kt      # Usage metrics collection
│           ├── GracePeriod.kt        # Grace period handling
│           └── model/
│               ├── License.kt
│               ├── Entitlement.kt
│               ├── LicenseMetric.kt  # Cores, users, etc.
│               └── LicenseStatus.kt
│
├── eaf-licensing-server/             # 🖥️ LICENSE SERVER
│   └── src/main/kotlin/
│       └── de/acci/eaf/licensing/server/
│           ├── LicenseServerApplication.kt
│           ├── api/
│           │   ├── LicenseController.kt
│           │   ├── ActivationController.kt
│           │   └── UsageController.kt
│           ├── service/
│           │   ├── LicenseManagementService.kt
│           │   ├── ActivationService.kt
│           │   └── TenantLicenseService.kt  # Multi-tenant allocation
│           └── persistence/
│               ├── LicenseRepository.kt
│               └── UsageRecordRepository.kt
│
└── eaf-licensing-client/             # 📱 CLIENT SDK
    └── src/main/kotlin/
        └── de/acci/eaf/licensing/client/
            ├── LicenseClient.kt
            ├── OfflineLicenseValidator.kt
            └── HeartbeatService.kt
```

### 6.3 Integration with EAF Multi-Tenancy

```kotlin
// LicenseContext integrates with existing TenantContext
public object LicenseContext {

    /** Returns the license for the current tenant */
    public suspend fun current(): License {
        val tenantId = TenantContext.current()
        return LicenseService.getLicenseForTenant(tenantId)
    }

    /** Checks if current tenant has entitlement for feature */
    public suspend fun hasEntitlement(feature: Feature): Boolean {
        return current().entitlements.contains(feature)
    }

    /** Reports usage metric for current tenant */
    public suspend fun reportUsage(metric: LicenseMetric, value: Long) {
        val tenantId = TenantContext.current()
        UsageReporter.record(tenantId, metric, value)
    }
}
```

---

## 7. License File Format

### 7.1 Signed License File (for Offline/Air-Gapped)

Based on industry standards (Keygen, SoftwareKey):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<license xmlns="urn:acci:eaf:licensing:v1" version="1.0">
    <id>lic_01HRCvErLiFUq9WTcSDuaLW2</id>
    <product>
        <id>dvmm</id>
        <version>2.0</version>
    </product>
    <customer>
        <id>cust_XYZ123</id>
        <name>Acme Corporation</name>
    </customer>
    <entitlements>
        <cores>150</cores>
        <tenants>5</tenants>
        <features>
            <feature>vm-provisioning</feature>
            <feature>approval-workflow</feature>
            <feature>audit-logging</feature>
        </features>
    </entitlements>
    <validity>
        <issued>2025-01-01T00:00:00Z</issued>
        <expires>2026-01-01T00:00:00Z</expires>
        <grace-days>30</grace-days>
    </validity>
    <machine-binding>
        <fingerprint-algorithm>SHA-256</fingerprint-algorithm>
        <fingerprint>a1b2c3d4e5f6...</fingerprint>
        <binding-mode>soft</binding-mode> <!-- soft | strict -->
    </machine-binding>
    <signature algorithm="Ed25519">
        -----BEGIN SIGNATURE-----
        MC0CFQCNb...base64-encoded-signature...
        -----END SIGNATURE-----
    </signature>
</license>
```

### 7.2 Cryptographic Approach

| Algorithm | Purpose |
|-----------|---------|
| **Ed25519** | License file signing (recommended for performance) |
| **RSA-2048** | Alternative for FIPS compliance |
| **AES-256-GCM** | Encrypting sensitive license data |
| **SHA-256** | Machine fingerprinting |

**Key Management**:
- Private key: Stored securely at ACCI (HSM recommended)
- Public key: Embedded in `eaf-licensing-client`
- Key rotation: Support multiple valid public keys

---

## 8. Machine Fingerprinting

### 8.1 Fingerprint Components

| Component | Weight | Notes |
|-----------|--------|-------|
| MAC Address | Medium | Can change on VM clone |
| CPU Info (CPUID) | High | Stable across reboots |
| VM UUID | High | Unique per VM instance |
| Hostname | Low | Easily changed |
| Disk Serial | Medium | Not accessible in VMs |

### 8.2 Virtualization Handling

```kotlin
object MachineFingerprint {

    fun generate(): String {
        val components = buildList {
            add(getCpuInfo())
            add(getMacAddress())

            if (isVirtualMachine()) {
                add(getVmUuid())  // Stable across vMotion
            } else {
                add(getDiskSerial())
                add(getMotherboardId())
            }
        }

        return components
            .joinToString("|")
            .sha256()
    }

    private fun isVirtualMachine(): Boolean {
        // Detect VMware, Hyper-V, KVM, etc.
    }
}
```

### 8.3 Soft vs. Strict Binding

| Mode | Behavior | Use Case |
|------|----------|----------|
| **Soft** | Warning on fingerprint change, allow with re-activation | Production (handles hardware changes) |
| **Strict** | Block on fingerprint change, require manual intervention | High-security environments |

---

## 9. Offline & Air-Gapped Support

### 9.1 Activation Workflow

```
┌─────────────────────────────────────────────────────────────────┐
│                    ONLINE ACTIVATION                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Product ──▶ Generate Request ──▶ License Server ──▶ Activate   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                   OFFLINE ACTIVATION                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. Product generates activation request file (.req)             │
│  2. Admin transfers to internet-connected machine                │
│  3. Upload to ACCI License Portal                                │
│  4. Download signed license file (.lic)                          │
│  5. Transfer license file to air-gapped product                  │
│  6. Product validates signature and activates                    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 9.2 Periodic Validation

| Mode | Validation Interval | Grace After Failure |
|------|---------------------|---------------------|
| Online | Daily heartbeat | 7 days |
| Offline | Manual sync every 90 days | 30 days |
| Air-gapped | Annual license file renewal | 30 days |

---

## 10. Multi-Tenant License Management

### 10.1 License Hierarchy

```
Organization License (1000 cores)
├── Tenant A License (300 cores - dedicated)
│   ├── Project 1 (100 cores allocated)
│   └── Project 2 (200 cores allocated)
├── Tenant B License (200 cores - dedicated)
│   └── Project 1 (200 cores allocated)
└── Shared Pool (500 cores)
    ├── Tenant C (using 150 cores)
    └── Tenant D (using 100 cores)
```

### 10.2 Tenant License Service

```kotlin
interface TenantLicenseService {

    /** Get license allocation for a tenant */
    suspend fun getTenantAllocation(tenantId: TenantId): TenantLicenseAllocation

    /** Check if tenant can provision additional cores */
    suspend fun canProvision(tenantId: TenantId, requestedCores: Int): Boolean

    /** Reserve cores for a new VM request */
    suspend fun reserveCores(tenantId: TenantId, vmRequestId: VmRequestId, cores: Int): Result<Reservation, LicenseError>

    /** Release cores when VM is deprovisioned */
    suspend fun releaseCores(tenantId: TenantId, vmRequestId: VmRequestId): Result<Unit, LicenseError>

    /** Get current usage across all tenants */
    suspend fun getUsageSummary(): UsageSummary
}

data class TenantLicenseAllocation(
    val tenantId: TenantId,
    val dedicatedCores: Int,          // Guaranteed allocation
    val sharedPoolAccess: Boolean,    // Can use shared pool?
    val currentUsage: Int,            // Cores in use
    val availableCores: Int,          // Can provision now
)
```

### 10.3 Integration with DVMM Quota System

The license system integrates with DVMM's existing quota enforcement:

```kotlin
// In VmRequestCommandHandler
class CreateVmRequestHandler(
    private val licenseService: TenantLicenseService,
    // ... other dependencies
) {
    suspend fun handle(command: CreateVmRequest): Result<VmRequestId, DomainError> {
        val tenantId = TenantContext.current()

        // Step 1: Check license allows this
        val canProvision = licenseService.canProvision(tenantId, command.cpuCores)
        if (!canProvision) {
            return Result.failure(LicenseExceededError(
                message = "License limit reached. Contact administrator for additional cores."
            ))
        }

        // Step 2: Reserve cores (optimistic locking)
        val reservation = licenseService.reserveCores(
            tenantId = tenantId,
            vmRequestId = command.requestId,
            cores = command.cpuCores
        )

        // Step 3: Proceed with VM request creation
        // ...
    }
}
```

---

## 11. Grace Period & Enforcement

### 11.1 License States

```
┌────────────┐     ┌────────────┐     ┌────────────┐     ┌────────────┐
│   ACTIVE   │────▶│  WARNING   │────▶│   GRACE    │────▶│  EXPIRED   │
│            │     │ (7d before │     │  (15-30d)  │     │            │
│            │     │   expiry)  │     │            │     │            │
└────────────┘     └────────────┘     └────────────┘     └────────────┘
      │                                     │                   │
      │           Features: Full            │ Features: Full    │ Features: Limited
      │           Warnings: None            │ Warnings: Banner  │ New provisions: Blocked
      │           Provisions: Yes           │ Provisions: Yes   │ Existing VMs: Running
```

### 11.2 Enforcement Levels

| Level | Trigger | Behavior |
|-------|---------|----------|
| **Info** | 30 days before expiry | Dashboard banner |
| **Warning** | 7 days before expiry | Email to admins, persistent banner |
| **Grace** | Expiry date passed | Block new provisions, existing VMs run |
| **Expired** | Grace period ended | Block all operations, emergency contact |

---

## 12. Migration from DPCM Service

### 12.1 Migration Strategy

1. **Phase 1: Parallel Operation**
   - Deploy EAF License Server alongside DPCM
   - Import existing licenses to new format
   - Products check both systems

2. **Phase 2: Gradual Migration**
   - New products use EAF licensing only
   - Existing DPCM products migrate on upgrade
   - Provide migration tools for license conversion

3. **Phase 3: DPCM Sunset**
   - All products migrated to EAF licensing
   - DPCM Service deprecated
   - Historical data archived

### 12.2 DPCM V1→V2 Transition (Already Completed)

The DPCM product already successfully migrated from V1 to V2 registration:

| Aspect | Migration Impact |
|--------|-----------------|
| **Existing V1 Instances** | Continue working until re-registration |
| **New Registrations** | Always use V2 flow |
| **Key Storage** | V2 uses local encryption, no dependency on server master key |
| **Backward Compatibility** | Server supports both V1 and V2 endpoints during transition |

**Lessons for EAF Migration:**
- Maintain backward compatibility during transition period
- Don't force immediate re-registration - let natural upgrade cycle handle it
- V2 endpoints can coexist with V1 indefinitely (DPCM still has both)

### 12.3 License Format Migration

```kotlin
object DpcmMigrator {

    fun migrateToEaf(dpcmLicense: DpcmLicense): EafLicense {
        return EafLicense(
            id = LicenseId.generate(),
            legacyId = dpcmLicense.id,  // For traceability
            product = mapProduct(dpcmLicense.productCode),
            entitlements = mapEntitlements(dpcmLicense),
            validity = ValidityPeriod(
                issued = Instant.now(),
                expires = dpcmLicense.expirationDate,
            ),
            // ... map other fields
        )
    }
}
```

---

## 13. Security Considerations

### 13.1 Threat Model

| Threat | Mitigation |
|--------|------------|
| License file tampering | Ed25519/RSA signature verification |
| Clock manipulation | Server-signed timestamps, NTP validation |
| VM cloning | UUID-based fingerprinting, clone detection |
| Reverse engineering | Continuous remote validation, runtime integrity checks, code signing (obfuscation as defense-in-depth only) |
| Man-in-the-middle | TLS 1.3, certificate pinning |

### 13.2 Key Security Practices

1. **Private Key Protection**: Store in HSM or secure vault
2. **Key Rotation**: Support multiple valid keys, rotate annually
3. **Audit Logging**: Log all license operations for compliance
4. **Rate Limiting**: Prevent brute-force activation attempts
5. **Secure Storage**: Encrypt license data at rest

### 13.3 V2 Security Architecture (Learned from DPCM)

The DPCM V2 registration flow introduced critical security improvements over V1:

| Aspect | V1 (Deprecated) | V2 (Current) |
|--------|-----------------|--------------|
| **Key Generation** | Server-side | Client-side (RSA 2048-bit) |
| **Private Key Transmission** | Sent over network | Never leaves device |
| **Authentication** | Token-based | Challenge-response |
| **Key Storage** | Encrypted with master key from server | Encrypted locally with device master key |
| **Key Rotation** | Manual | Automatic 90-day cycle |

**V2 Security Benefits:**
1. **Zero Private Key Exposure**: Private key generated locally, only public key transmitted
2. **Challenge-Response Proof**: Server verifies client owns private key without seeing it
3. **Forward Secrecy**: Compromised network traffic cannot decrypt historical sessions
4. **Local Key Encryption**: Private keys encrypted at rest using device-specific master key

**EAF Recommendation:** Adopt the V2 model as baseline security for new licensing system

---

## 14. Implementation Roadmap

### Phase 1: Core Framework (MVP)
- [ ] `eaf-licensing` module with interfaces
- [ ] License file format and validation
- [ ] Machine fingerprinting (VM-aware)
- [ ] Basic activation flow

### Phase 2: License Server
- [ ] `eaf-licensing-server` deployment
- [ ] REST API for license operations
- [ ] PostgreSQL persistence
- [ ] Admin UI for license management

### Phase 3: Multi-Tenancy
- [ ] Per-tenant license allocation
- [ ] Usage metering and reporting
- [ ] Integration with `eaf-tenant`
- [ ] Quota system integration

### Phase 4: Offline Support
- [ ] Offline activation workflow
- [ ] License file generation portal
- [ ] Grace period handling
- [ ] Air-gapped deployment guide

### Phase 5: Migration
- [ ] DPCM import tools
- [ ] Parallel operation support
- [ ] Migration documentation
- [ ] DPCM sunset plan

---

## 15. References & Sources

### Commercial Solutions
- [Keygen - Software Licensing API](https://keygen.sh/) - Fair Source, self-hosted option
- [Keygen Self-Hosting Documentation](https://keygen.sh/docs/self-hosting/)
- [Keygen Air-Gapped Activation Example](https://github.com/keygen-sh/air-gapped-activation-example)
- [Cryptlex - Software Licensing](https://cryptlex.com/)
- [LicenseSpring - Licensing Solutions](https://licensespring.com/)
- [Cryptolens - Open Source Option](https://cryptolens.io/)

### Technical Standards
- [RFC 8725 - JWT Best Current Practices](https://tools.ietf.org/html/rfc8725)
- [10Duke - License Token Handling](https://docs.enterprise.10duke.com/developer-guide/consuming-licenses/handle-and-store-jwts/)

### Licensing Models
- [10Duke - Software Licensing Models](https://www.10duke.com/learn/software-licensing/software-licensing-models/)
- [Revenera - License Types](https://www.revenera.com/blog/software-monetization/common-software-license-terms/)
- [Microsoft Core-Based Licensing](https://www.microsoft.com/licensing/guidance/Core-based-licensing-models)

### Multi-Tenancy
- [SLASCONE - Multi-Tenant Licensing](https://slascone.com/multi-tenant-licensing/)
- [Red Hat - Multi-Tenancy Approaches](https://developers.redhat.com/articles/2022/05/09/approaches-implementing-multi-tenancy-saas-applications)

### Virtualization & Fingerprinting
- [Thales - VM Fingerprinting](https://docs.sentinel.thalesgroup.com/softwareandservices/rms/RMSDocumentation/Vendor/Content/DevGuide/Chapter%2015_VMs/Fingerprinting%20in%20Single%20Host%20VM%20Environments.htm)
- [VMware Core Counting](https://knowledge.broadcom.com/external/article/313548/counting-cores-for-vmware-cloud-foundati.html)
- [LimeLM - VM Licensing](https://wyday.com/limelm/help/vm-hypervisor-licensing/)

### Offline Licensing
- [Keygen - Offline Licensing Model](https://keygen.sh/docs/choosing-a-licensing-model/offline-licenses/)
- [Spring Boot Offline Licensing](https://medium.com/@shahharsh172/implementing-secure-offline-licensing-for-spring-boot-applications-in-dark-room-environments-18d0f68c4578)
- [PACE Anti-Piracy - Air-Gapped Environments](https://paceap.com/software-licensing-for-air-gapped-environments/)

### Grace Periods
- [HP Anyware - License Grace Period](https://anyware.hp.com/web-help/pcoip_license_server/windows/25.03/overview/about-pcoip-session-licensing/)
- [Citrix - License Technical Overview](https://docs.citrix.com/en-us/licensing/current-release/license-server/licensing-technical-overview.html)

---

## 16. Decision Summary

| Decision | Recommendation | Rationale |
|----------|----------------|-----------|
| **Build vs. Buy** | Hybrid (Keygen CE + EAF extensions) | Balance of control and proven security |
| **License Model** | Core-based subscription | Matches DVMM use case (managed infrastructure) |
| **Offline Support** | Signed license files with Ed25519 | Industry standard, performant |
| **Multi-Tenancy** | Dedicated + shared pool allocation | Flexible for different customer needs |
| **Grace Period** | 30 days | Industry standard, customer-friendly |
| **Fingerprint** | VM-aware (UUID + MAC + CPU) | Handles vMotion and VM cloning |

---

*Document Version: 1.0*
*Created: 2025-12-03*
*Author: Claude (Research Assistant)*
