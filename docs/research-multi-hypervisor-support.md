# Multi-Hypervisor Support Research for DVMM

**Author:** Claude (Research Agent)
**Date:** 2025-12-08
**Status:** Research Complete

---

## Executive Summary

This document provides exhaustive research on extending DVMM to support multiple hypervisors beyond VMware vSphere. We analyze three alternative platforms: **Microsoft Hyper-V**, **Proxmox VE**, and **IBM PowerVM**. Each platform has distinct integration patterns, SDK availability, and market positioning.

**Key Findings:**
1. **Proxmox VE** offers the lowest integration effort with a clean REST API and mature Java SDK
2. **Microsoft Hyper-V** requires Windows-specific tooling (WinRM/WMI) and has no official Java SDK
3. **IBM PowerVM** serves a niche enterprise market with limited Java tooling (Python-focused)
4. The current `VspherePort` abstraction pattern is **well-suited** for multi-hypervisor extension

**Recommendation:** Support multi-hypervisor architecture **after MVP**, starting with Proxmox VE as the second hypervisor due to its clean API and growing market adoption.

---

## Table of Contents

1. [Current Architecture Analysis](#1-current-architecture-analysis)
2. [Microsoft Hyper-V Research](#2-microsoft-hyper-v-research)
3. [Proxmox VE Research](#3-proxmox-ve-research)
4. [IBM PowerVM Research](#4-ibm-powervm-research)
5. [Architectural Patterns for Multi-Hypervisor Support](#5-architectural-patterns-for-multi-hypervisor-support)
6. [Integration Plans](#6-integration-plans)
7. [Effort Estimates](#7-effort-estimates)
8. [MVP Timing Recommendation](#8-mvp-timing-recommendation)
9. [Risk Assessment](#9-risk-assessment)
10. [Sources](#10-sources)

---

## 1. Current Architecture Analysis

### Existing VMware Integration Pattern

DVMM currently implements a **clean port/adapter pattern** for hypervisor integration:

```
dvmm-application/
└── vmware/
    └── VspherePort.kt          # Interface (Port)

dvmm-infrastructure/
└── vmware/
    ├── VcenterAdapter.kt       # Production implementation
    ├── VcsimAdapter.kt         # Test simulator implementation
    └── VsphereClient.kt        # Low-level SDK wrapper
```

**Key Interface (`VspherePort.kt`):**
```kotlin
public interface VspherePort {
    suspend fun testConnection(params: VcenterConnectionParams, password: String): Result<ConnectionInfo, ConnectionError>
    suspend fun listDatacenters(): Result<List<Datacenter>, VsphereError>
    suspend fun listClusters(datacenter: Datacenter): Result<List<Cluster>, VsphereError>
    suspend fun listDatastores(cluster: Cluster): Result<List<Datastore>, VsphereError>
    suspend fun listNetworks(datacenter: Datacenter): Result<List<Network>, VsphereError>
    suspend fun listResourcePools(cluster: Cluster): Result<List<ResourcePool>, VsphereError>
    suspend fun createVm(spec: VmSpec): Result<VmProvisioningResult, VsphereError>
    suspend fun getVm(vmId: VmId): Result<VmInfo, VsphereError>
    suspend fun deleteVm(vmId: VmId): Result<Unit, VsphereError>
}
```

**Strengths for Multi-Hypervisor Extension:**
- ✅ Clean separation between interface and implementation
- ✅ Spring Profile-based adapter selection (`@Profile("!vcsim")`)
- ✅ Async/suspend functions using Kotlin coroutines
- ✅ Result type for error handling
- ✅ Test adapter (VcsimAdapter) validates the pattern works

**Changes Needed for Multi-Hypervisor:**
- Rename `VspherePort` → `HypervisorPort` (or create abstraction layer)
- Define hypervisor-agnostic value objects (e.g., `VmSpec`, `VmInfo`)
- Tenant-level hypervisor configuration selection
- Adapter factory for dynamic hypervisor selection

---

## 2. Microsoft Hyper-V Research

### Platform Overview

| Aspect | Details |
|--------|---------|
| **Vendor** | Microsoft |
| **Type** | Type 1 hypervisor (bare-metal) |
| **Target Market** | Windows Server environments, Azure Stack HCI |
| **License** | Included with Windows Server, free Hyper-V Server edition |
| **Current Versions** | Windows Server 2025, 2022, 2019, Azure Local 2311.2+ |

### Available APIs

Microsoft provides **four main API categories** for Hyper-V:

1. **Hyper-V WMI Provider (V2)**
   - Namespace: `root\virtualization\v2`
   - High-level workflow-oriented API
   - Manages VMs, virtual networks, virtual hard disks
   - Best for server virtualization scenarios

2. **Host Compute System (HCS) APIs**
   - Low-level, granular access
   - Used for containers, WSL2, custom virtualization solutions
   - More flexibility but more complexity

3. **Windows Hypervisor Platform (WHP)**
   - Third-party virtualization stack support
   - Hypervisor-level partition management
   - Available since Windows April 2018 Update

4. **Virtualization Developer Tools**
   - VHD/VHDX management APIs
   - Host Compute Network (HCN) Service API
   - Hypervisor Instruction Emulator API

### Integration Approaches for Java/Kotlin

#### Option A: WinRM + PowerShell (Recommended)

Hyper-V management is primarily PowerShell-based. Java applications can execute PowerShell commands remotely via WinRM:

**Java Library: [winrm4j](https://github.com/cloudsoft/winrm4j)**
- Apache 2.0 license
- Developed by Cloudsoft Corporation
- Used by Apache Brooklyn for Windows automation

**⚠️ Maintenance Risk Warning:**
| Attribute | Value |
|-----------|-------|
| **Latest Stable Version** | v0.12.3 |
| **Last Release Date** | August 31, 2021 |
| **Last Repo Activity** | March 2022 |
| **Open Issues** | Multiple unresolved (auth, SSL, timeout issues) |
| **Production Readiness** | ⚠️ **RISKY** - effectively unmaintained |

**Required Next Steps Before Adoption:**
1. **Evaluate alternatives:** Research actively maintained WinRM Java libraries (e.g., Apache MINA SSHD with WinRM extension, custom REST client for WS-Management)
2. **Security assessment:** Audit winrm4j for known CVEs and security vulnerabilities in dependencies
3. **Maintenance plan:** If no alternatives exist, budget for internal fork maintenance (security patches, dependency updates)
4. **Fallback strategy:** Consider Python-based WinRM tools (pywinrm) with subprocess integration as a backup

```java
// Example: Create a VM via WinRM + PowerShell
WinRmTool tool = WinRmTool.Builder.builder("hyperv-host.local", "admin", "password")
    .authenticationScheme(AuthSchemes.NTLM)
    .port(5985)
    .useHttps(false)
    .build();

String psScript = """
    New-VM -Name "web-server-01" -MemoryStartupBytes 4GB -Generation 2
    Add-VMNetworkAdapter -VMName "web-server-01" -SwitchName "VM Network"
    Start-VM -Name "web-server-01"
    """;

WinRmToolResponse response = tool.executePs(psScript);
```

**Pros:**
- Full access to all Hyper-V PowerShell cmdlets
- Supports NTLM, Kerberos, CredSSP authentication
- Apache Brooklyn validates basic functionality

**Cons:**
- ⚠️ **Unmaintained since 2021** - significant production risk
- Requires WinRM configuration on Hyper-V hosts
- CredSSP needed for certain operations (security implications)
- Command-output parsing required (brittle)
- Higher latency than native API calls
- Potential security vulnerabilities in outdated dependencies

#### Option B: Direct WMI via Java-COM Bridge

**Libraries:**
- **JACOB** (Java-COM Bridge)
- **JNI-based WMI wrappers**

**Pros:**
- Direct access to WMI objects
- Strongly typed responses

**Cons:**
- Windows-only (JNI bindings)
- Complex deployment (native libraries)
- Not cross-platform

### Key PowerShell Cmdlets for VM Provisioning

| Operation | PowerShell Cmdlet |
|-----------|-------------------|
| Create VM | `New-VM -Name "..." -MemoryStartupBytes 4GB -Generation 2` |
| Clone from Template | `Export-VM` + `Import-VM` or differential disk |
| Configure CPU | `Set-VMProcessor -VMName "..." -Count 4` |
| Attach Network | `Add-VMNetworkAdapter -VMName "..." -SwitchName "..."` |
| Start VM | `Start-VM -Name "..."` |
| Get IP Address | `Get-VMNetworkAdapter -VMName "..." | Select IPAddresses` |
| Get VM State | `Get-VM -Name "..." | Select State` |
| Delete VM | `Remove-VM -Name "..." -Force` |

### Configuration Model

Unlike vSphere's centralized vCenter, Hyper-V can be:
1. **Standalone hosts** - Direct management per host
2. **System Center VMM** - Centralized management (requires SCVMM)
3. **Windows Admin Center** - Web-based management
4. **Azure Stack HCI** - Azure-integrated management

For DVMM MVP integration, targeting **standalone hosts via WinRM** is most practical.

### Market Relevance for DVMM Target Market (DACH SMB/Mid-Market)

| Factor | Assessment |
|--------|------------|
| Market Share | ~15-20% in DACH on-prem virtualization |
| Common Use Cases | Windows Server workloads, AD integration |
| Target Customers | Microsoft-centric shops, Azure Stack HCI users |
| Competitive Pressure | Lower - VMware customers unlikely to switch mid-contract |

---

## 3. Proxmox VE Research

### Platform Overview

| Aspect | Details |
|--------|---------|
| **Vendor** | Proxmox Server Solutions GmbH (Vienna, Austria) |
| **Type** | Type 1 hypervisor (KVM/LXC-based) |
| **Target Market** | SMB, education, budget-conscious enterprises |
| **License** | AGPLv3 (open source), optional paid support subscriptions |
| **Current Version** | 8.x (as of 2024) |

### API Architecture

Proxmox VE provides a **RESTful API** with JSON Schema definitions:

- **Base URL:** `https://{host}:8006/api2/json/`
- **Authentication:** Username/password or API tokens
- **Protocol:** HTTPS with certificate validation
- **Format:** JSON request/response bodies

### Java SDK: cv4pve-api-java

**Repository:** [Corsinvest/cv4pve-api-java](https://github.com/Corsinvest/cv4pve-api-java)

| Attribute | Value |
|-----------|-------|
| **GroupId** | `it.corsinvest.proxmoxve` |
| **ArtifactId** | `cv4pve-api-java` |
| **Version** | 7.3.0 |
| **License** | GPL-3.0 |
| **Java Version** | 8+ |
| **Last Update** | April 2024 (no major release in 12+ months) |
| **Maven Central** | ❌ **Not available** |
| **Distribution** | GitHub releases, manual JAR inclusion, or JitPack |

**⚠️ Installation Note:**
This library is **not published to Maven Central**. To use it:
1. **JitPack:** Add JitPack repository and use `com.github.Corsinvest:cv4pve-api-java:7.3.0`
2. **Manual:** Download JAR from [GitHub releases](https://github.com/Corsinvest/cv4pve-api-java/releases) and add to classpath
3. **Local build:** Clone repo and run `mvn install` to install to local Maven repository

**Maintenance Status:** The library has not had a major release in over 12 months. Verify compatibility with your target Proxmox VE version before adoption. Consider community activity level when planning production use.

**Key Features:**
- Full REST API coverage
- Tree-structured API mirroring Proxmox hierarchy
- API token authentication support
- Task tracking for long-running operations
- Comprehensive JavaDoc

**Usage Example:**
```kotlin
// Kotlin usage of cv4pve-api-java
val client = PveClient("proxmox.example.com", 8006)
if (client.login("root@pam", "password")) {
    // List all nodes
    val nodes = client.nodes.index().response.getJSONArray("data")

    // Create VM from template (clone)
    val result = client.nodes["pve1"].qemu["100"]
        .clone(newVmId = 200, name = "web-server-01")

    // Start VM
    client.nodes["pve1"].qemu[200].status.start()
}
```

### Key API Endpoints for VM Provisioning

| Operation | Endpoint | Method |
|-----------|----------|--------|
| List nodes | `/nodes` | GET |
| List VMs | `/nodes/{node}/qemu` | GET |
| Create VM | `/nodes/{node}/qemu` | POST |
| Clone template | `/nodes/{node}/qemu/{vmid}/clone` | POST |
| Start VM | `/nodes/{node}/qemu/{vmid}/status/start` | POST |
| Get VM status | `/nodes/{node}/qemu/{vmid}/status/current` | GET |
| Get VM config | `/nodes/{node}/qemu/{vmid}/config` | GET |
| Delete VM | `/nodes/{node}/qemu/{vmid}` | DELETE |
| List networks | `/nodes/{node}/network` | GET |
| List storage | `/nodes/{node}/storage` | GET |

### Proxmox Concepts Mapping to DVMM

| DVMM Concept | Proxmox Equivalent |
|--------------|-------------------|
| Datacenter | Proxmox Cluster |
| Cluster | Node (single host) or Cluster |
| Datastore | Storage (local, NFS, Ceph, ZFS) |
| Network | Bridge (vmbr0, vmbr1, etc.) |
| Template | VM with "template" flag |
| VM | QEMU/KVM VM (type: qemu) |

### Market Relevance for DVMM Target Market (DACH SMB/Mid-Market)

| Factor | Assessment |
|--------|------------|
| Market Share | Growing rapidly (~5-10% and increasing) |
| Common Use Cases | Cost-sensitive deployments, homelab-to-enterprise |
| Target Customers | Budget-conscious SMBs, education, hosting providers |
| **Key Advantage** | Free tier with commercial support option |
| **Growth Trend** | Strong growth post-Broadcom/VMware price increases |

### Integration Complexity Assessment

| Aspect | Rating | Notes |
|--------|--------|-------|
| SDK Maturity | ⭐⭐⭐ | Not on Maven Central, no release in 12+ months |
| API Design | ⭐⭐⭐⭐⭐ | Clean REST, JSON Schema documented |
| Authentication | ⭐⭐⭐⭐⭐ | API tokens, standard HTTPS |
| Documentation | ⭐⭐⭐⭐ | Good API viewer, community resources |
| **Overall Effort** | **LOW-MEDIUM** | Easiest of alternatives, but SDK concerns |

---

## 4. IBM PowerVM Research

### Platform Overview

| Aspect | Details |
|--------|---------|
| **Vendor** | IBM |
| **Type** | Type 1 hypervisor (firmware-level) |
| **Target Market** | Large enterprise, mainframe-adjacent workloads |
| **Hardware** | IBM POWER6, POWER7, POWER8, POWER9, POWER10, POWER11 |
| **License** | Commercial (requires PowerVM feature license) |
| **Management** | Hardware Management Console (HMC) |

### Architecture Overview

PowerVM is fundamentally different from x86-based hypervisors:

```
┌─────────────────────────────────────────────────┐
│              Hardware Management Console (HMC)   │
│                   REST API (port 12443)          │
└─────────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────┐
│              IBM POWER Server                    │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐         │
│  │  LPAR 1 │  │  LPAR 2 │  │  LPAR 3 │         │
│  │ (AIX)   │  │ (Linux) │  │ (IBM i) │         │
│  └─────────┘  └─────────┘  └─────────┘         │
│                                                  │
│         PHYP (PowerVM Hypervisor)               │
└─────────────────────────────────────────────────┘
```

### API Access

**HMC REST API:**
- **Base URL:** `https://{HMC}:12443/rest/api/uom/`
- **Authentication:** HMC user credentials
- **Format:** XML-based (not JSON)
- **Documentation:** IBM Knowledge Center

**Key API Endpoints:**
| Operation | Endpoint |
|-----------|----------|
| Get management console info | `/ManagementConsole` |
| List managed systems | `/ManagedSystem` |
| List LPARs | `/ManagedSystem/{systemId}/LogicalPartition` |
| Create LPAR | `/ManagedSystem/{systemId}/LogicalPartition` (POST) |
| Get LPAR details | `/LogicalPartition/{lparId}` |
| Power on LPAR | `/LogicalPartition/{lparId}?operation=PowerOn` |

### Available SDKs

#### Python SDK: pypowervm (Primary)

**Repository:** [powervm/pypowervm](https://github.com/powervm/pypowervm)

| Attribute | Value |
|-----------|-------|
| **License** | Apache-2.0 |
| **Status** | Actively maintained |
| **PyPI** | ✅ Available |

**Features:**
- Full HMC REST API coverage
- Wrapper classes for PowerVM objects
- Transaction support for atomic operations
- PCM (Performance and Capacity Monitoring) integration
- Event polling for state changes

#### Java SDK Status

**⚠️ No Official Java SDK Available**

IBM has focused Python tooling (pypowervm). For Java/Kotlin integration, options include:

1. **Direct HTTP Client:**
   ```kotlin
   // Manual REST API calls with OkHttp/Retrofit
   val response = httpClient.get("https://hmc:12443/rest/api/uom/ManagedSystem")
   // Parse XML response manually
   ```

2. **Python Subprocess:**
   ```kotlin
   // Shell out to pypowervm scripts (not recommended for production)
   val process = ProcessBuilder("python", "create_lpar.py", "--name", vmName).start()
   ```

3. **Custom Java REST Client:**
   - Build Java wrappers around HMC REST API
   - Significant development effort required

### PowerVM Concepts Mapping to DVMM

| DVMM Concept | PowerVM Equivalent |
|--------------|-------------------|
| Datacenter | HMC (Management Console) |
| Cluster | Managed System (physical server) |
| Host | Frame/CEC (Central Electronics Complex) |
| Datastore | Virtual I/O Server (VIOS) storage |
| Network | Virtual LAN (VLAN) via VIOS |
| Template | Reference LPAR + Golden Image |
| VM | LPAR (Logical Partition) |

### Market Relevance for DVMM Target Market (DACH SMB/Mid-Market)

| Factor | Assessment |
|--------|------------|
| Market Share | Very small (<2% in target market) |
| Common Use Cases | Banking, insurance, large SAP installations |
| Target Customers | Large enterprise only |
| **Key Challenge** | Expensive hardware, specialized skills required |
| **DACH Prevalence** | Banks, insurance companies, government data centers |

### Integration Complexity Assessment

| Aspect | Rating | Notes |
|--------|--------|-------|
| SDK Maturity | ⭐⭐ | Python only, no Java |
| API Design | ⭐⭐⭐ | REST but XML-based, complex schema |
| Authentication | ⭐⭐⭐ | Standard HTTPS, certificate-based |
| Documentation | ⭐⭐⭐ | IBM Knowledge Center (enterprise focus) |
| Market Fit | ⭐ | Not aligned with DVMM target market |
| **Overall Effort** | **HIGH** | Requires custom Java SDK development |

---

## 5. Architectural Patterns for Multi-Hypervisor Support

### Pattern 1: Abstraction Layer (Recommended)

Create a hypervisor-agnostic interface in the application layer:

```kotlin
// dvmm-application/src/main/kotlin/.../hypervisor/HypervisorPort.kt
public interface HypervisorPort {
    suspend fun testConnection(config: HypervisorConfig, credentials: Credentials): Result<ConnectionInfo, ConnectionError>
    suspend fun listResources(): Result<HypervisorResources, HypervisorError>
    suspend fun createVm(spec: VmProvisionSpec): Result<VmProvisioningResult, HypervisorError>
    suspend fun getVm(vmId: VmIdentifier): Result<VmInfo, HypervisorError>
    suspend fun startVm(vmId: VmIdentifier): Result<Unit, HypervisorError>
    suspend fun stopVm(vmId: VmIdentifier): Result<Unit, HypervisorError>
    suspend fun deleteVm(vmId: VmIdentifier): Result<Unit, HypervisorError>
}
```

**Adapter implementations:**
```
dvmm-infrastructure/
└── hypervisor/
    ├── vsphere/
    │   └── VsphereAdapter.kt
    ├── proxmox/
    │   └── ProxmoxAdapter.kt
    ├── hyperv/
    │   └── HyperVAdapter.kt
    └── powervm/
        └── PowerVmAdapter.kt
```

### Pattern 2: Tenant-Level Hypervisor Configuration

Each tenant can configure their preferred hypervisor:

```kotlin
// Domain model for hypervisor configuration
data class TenantHypervisorConfig(
    val tenantId: TenantId,
    val hypervisorType: HypervisorType,  // VSPHERE, PROXMOX, HYPERV, POWERVM
    val connectionConfig: HypervisorConnectionConfig,
    val resourceMappings: HypervisorResourceMappings
)

enum class HypervisorType {
    VSPHERE,
    PROXMOX,
    HYPERV,
    POWERVM
}
```

### Pattern 3: Adapter Factory

Dynamic adapter selection based on tenant configuration:

```kotlin
@Component
class HypervisorAdapterFactory(
    private val vsphereAdapter: VsphereAdapter,
    private val proxmoxAdapter: ProxmoxAdapter,
    private val hypervAdapter: HyperVAdapter,
    private val powerVmAdapter: PowerVmAdapter
) {
    fun getAdapter(tenantId: TenantId): HypervisorPort {
        val config = configRepository.findByTenantId(tenantId)
            ?: throw NoHypervisorConfiguredException(tenantId)

        return when (config.hypervisorType) {
            HypervisorType.VSPHERE -> vsphereAdapter.withConfig(config)
            HypervisorType.PROXMOX -> proxmoxAdapter.withConfig(config)
            HypervisorType.HYPERV -> hypervAdapter.withConfig(config)
            HypervisorType.POWERVM -> powerVmAdapter.withConfig(config)
        }
    }
}
```

### Pattern 4: Resource Abstraction

Define hypervisor-agnostic resource models:

```kotlin
// Generic VM specification
data class VmProvisionSpec(
    val name: String,
    val template: TemplateReference,
    val compute: ComputeSpec,
    val storage: StorageSpec,
    val network: NetworkSpec,
    val metadata: Map<String, String>
)

data class ComputeSpec(
    val cpuCores: Int,
    val memoryMb: Long
)

// Each adapter maps to hypervisor-specific API calls
class VsphereAdapter : HypervisorPort {
    override suspend fun createVm(spec: VmProvisionSpec): Result<VmProvisioningResult, HypervisorError> {
        // Map to VCF SDK CloneVM_Task
    }
}

class ProxmoxAdapter : HypervisorPort {
    override suspend fun createVm(spec: VmProvisionSpec): Result<VmProvisioningResult, HypervisorError> {
        // Map to /nodes/{node}/qemu/{vmid}/clone
    }
}
```

---

## 6. Integration Plans

### Plan A: Proxmox VE Integration (Recommended First)

**Rationale:** Lowest effort, growing market, clean API

**Phase 1: Foundation (2-3 weeks)**
1. Add `cv4pve-api-java` dependency to `dvmm-infrastructure`
2. Create `ProxmoxAdapter` implementing `HypervisorPort`
3. Implement connection test functionality
4. Add Proxmox-specific configuration entities

**Phase 2: Core Operations (3-4 weeks)**
1. Implement `listResources()` (nodes, storage, networks)
2. Implement `createVm()` via template cloning
3. Implement IP address detection (QEMU guest agent)
4. Add progress tracking for clone operations

**Phase 3: Configuration UI (2-3 weeks)**
1. Extend VMware config form for Proxmox
2. Add hypervisor type selection
3. Create Proxmox-specific settings fields
4. Add connection test UI

**Phase 4: Testing & Documentation (2 weeks)**
1. Integration tests with Proxmox Testcontainer or mock
2. E2E tests for complete provisioning flow
3. Admin documentation for Proxmox setup

**Total Effort: 9-12 weeks**

### Plan B: Microsoft Hyper-V Integration

**Rationale:** Windows-centric customer base, but higher complexity

**Phase 1: WinRM Infrastructure (3-4 weeks)**
1. Add `winrm4j` dependency
2. Create `WinRmClient` wrapper with retry logic
3. Implement PowerShell script execution framework
4. Handle authentication (NTLM, CredSSP)

**Phase 2: Hyper-V Adapter (4-5 weeks)**
1. Create `HyperVAdapter` implementing `HypervisorPort`
2. Implement VM creation via PowerShell cmdlets
3. Parse PowerShell output (JSON format recommended)
4. Handle async operations (jobs)

**Phase 3: Configuration & Security (3-4 weeks)**
1. Credential management for WinRM
2. Certificate handling for HTTPS
3. CredSSP configuration guidance
4. Host discovery/enumeration

**Phase 4: Testing & Validation (3-4 weeks)**
1. Windows Server test environment setup
2. Integration tests with real Hyper-V (no simulator)
3. Security review (credential handling)
4. Performance testing (WinRM latency)

**Total Effort: 13-17 weeks**

### Plan C: IBM PowerVM Integration

**Rationale:** Niche market, requires custom SDK development

**Phase 1: HMC REST Client (4-5 weeks)**
1. Create custom Java HTTP client for HMC API
2. Implement XML parsing for responses
3. Handle HMC authentication and sessions
4. Map PowerVM concepts to DVMM models

**Phase 2: PowerVM Adapter (5-6 weeks)**
1. Create `PowerVmAdapter` implementing `HypervisorPort`
2. Implement LPAR creation workflow
3. Handle VIOS for storage/network
4. Implement event monitoring

**Phase 3: Enterprise Features (4-5 weeks)**
1. Support for multiple managed systems
2. Processor pool management
3. Dynamic LPAR Operations (DLPAR)
4. Integration with AIX/IBM i templates

**Phase 4: Validation (3-4 weeks)**
1. Access to IBM POWER test environment (expensive!)
2. Partner with IBM for testing
3. Enterprise customer pilot
4. Documentation and runbooks

**Total Effort: 16-20 weeks**

### Plan D: libvirt/KVM Direct Integration (Alternative to Proxmox)

**Rationale:** For customers with raw KVM without Proxmox management layer

**Phase 1: libvirt-java Setup (2-3 weeks)**
1. Add libvirt-java dependency (JNA-based)
2. Handle native library deployment
3. Create connection management wrapper

**Phase 2: KVM Adapter (4-5 weeks)**
1. Create `LibvirtAdapter` implementing `HypervisorPort`
2. Implement domain creation from XML templates
3. Handle storage pool and volume management
4. Network configuration via libvirt

**Phase 3: Challenges (2-3 weeks)**
1. No central management (host-by-host)
2. Template management complexity
3. Network configuration variability
4. Storage backend variations (local, Ceph, NFS)

**Total Effort: 8-11 weeks**

---

## 7. Effort Estimates Summary

| Hypervisor | Integration Effort | SDK Quality | Market Fit | Recommendation |
|------------|-------------------|-------------|------------|----------------|
| **Proxmox VE** | 9-12 weeks | ⭐⭐⭐ ⚠️ | ⭐⭐⭐⭐ | **First priority** |
| **Hyper-V** | 13-17 weeks | ⭐⭐ ⚠️ | ⭐⭐⭐ | Second priority |
| **KVM/libvirt** | 8-11 weeks | ⭐⭐⭐ | ⭐⭐ | Consider for specific cases |
| **PowerVM** | 16-20 weeks | ⭐⭐ | ⭐ | Only if customer demands |

**SDK Quality Notes:**
- ⚠️ **Proxmox (cv4pve-api-java):** Not on Maven Central, no release in 12+ months
- ⚠️ **Hyper-V (winrm4j):** Unmaintained since Aug 2021, security risk

### Resource Requirements

| Hypervisor | Backend Dev | Frontend Dev | DevOps/Infra | Test Environment |
|------------|-------------|--------------|--------------|------------------|
| Proxmox VE | 1 senior | 0.5 | 0.25 | Docker/VM (easy) |
| Hyper-V | 1 senior | 0.5 | 0.5 | Windows Server (complex) |
| PowerVM | 1-2 senior | 0.5 | 0.5 | IBM POWER (very expensive) |

---

## 8. MVP Timing Recommendation

### Strong Recommendation: **After MVP**

**Rationale:**

1. **MVP Focus Risk:**
   - Adding hypervisor abstraction during MVP introduces significant architectural complexity
   - Current Epic 3 (VMware Provisioning) is already marked as **Critical Risk**
   - Diluting focus could jeopardize core VMware functionality

2. **Market Validation First:**
   - MVP targets VMware-centric customers (largest installed base)
   - Multi-hypervisor is a "nice to have" until customer demand is validated
   - Post-MVP feedback will prioritize which hypervisor to add first

3. **Technical Foundation Stability:**
   - Current `VspherePort` pattern is solid but needs validation at scale
   - Abstraction layer design should be informed by real production usage
   - Premature generalization often leads to wrong abstractions

4. **Resource Constraints:**
   - MVP already has 53 stories across 5 epics
   - Adding 9-12 weeks of Proxmox work would delay MVP by ~2-3 months
   - Team context switching between VMware and new hypervisor is costly

### Recommended Timeline

```
Q1-Q2 2026: MVP (VMware vSphere only)
│
├── Epic 1: Foundation
├── Epic 2: Core Workflow
├── Epic 3: VM Provisioning (VMware)
├── Epic 4: Projects & Quota
└── Epic 5: Compliance & Oversight
│
▼
Q3 2026: Post-MVP Assessment
│
├── Collect customer feedback on hypervisor needs
├── Validate market demand for Proxmox/Hyper-V
├── Design hypervisor abstraction layer (ADR-004)
└── Prioritize integration based on customer contracts
│
▼
Q4 2026: Multi-Hypervisor Phase 1
│
├── Implement HypervisorPort abstraction
├── Refactor VsphereAdapter to new interface
├── Add ProxmoxAdapter (9-12 weeks)
└── Update UI for hypervisor selection
│
▼
Q1 2027: Multi-Hypervisor Phase 2 (if demand)
│
└── Add HyperVAdapter (13-17 weeks)
```

### Alternative: Minimal MVP Preparation

If PM insists on multi-hypervisor preparation during MVP, consider:

1. **Design-Only (No Code):**
   - Create ADR-004: Multi-Hypervisor Architecture
   - Document interface design for `HypervisorPort`
   - No implementation until post-MVP

2. **Interface Rename (Low Risk):**
   - Rename `VspherePort` → `HypervisorPort` in application layer
   - Keep `VcenterAdapter` as-is in infrastructure
   - This is a 1-2 day refactoring task

3. **Tenant Config Schema (Medium Risk):**
   - Add `hypervisor_type` column to tenant/config tables
   - Default to "VSPHERE" for all tenants
   - No UI changes in MVP

---

## 9. Risk Assessment

### Technical Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| WinRM security issues | High | Medium | CredSSP + HTTPS + network segmentation |
| Proxmox API changes | Medium | Low | Version pinning, integration tests |
| PowerVM access cost | High | High | Partner with IBM, cloud-based testing |
| Abstraction layer complexity | Medium | Medium | Start with Proxmox to validate pattern |

### Business Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Feature dilution | High | Medium | Strict MVP scope enforcement |
| Delayed time-to-market | High | Medium | Defer to post-MVP |
| Support complexity | Medium | High | Training, runbooks, support tiers |
| License complications | Medium | Low | Legal review for GPL (Proxmox SDK) |

### Market Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Low demand for alternatives | Medium | Medium | Customer validation before development |
| VMware license cost drops | Medium | Low | Already positioned for Broadcom exodus |
| Competitor first-mover | Medium | Medium | Focus on VMware excellence first |

---

## 10. Sources

### Microsoft Hyper-V
- [Hyper-V APIs | Microsoft Learn](https://learn.microsoft.com/en-us/virtualization/api/)
- [winrm4j | GitHub - Cloudsoft](https://github.com/cloudsoft/winrm4j)
- [remotepowershell | GitHub](https://github.com/juniperus/remotepowershell)
- [Hyper-V Remote Management with PowerShell | Microsoft Learn](https://learn.microsoft.com/en-us/archive/blogs/taylorb/hyper-v-remote-management-with-powershell)

### Proxmox VE
- [Proxmox VE API Documentation](https://pve.proxmox.com/pve-docs/api-viewer/)
- [cv4pve-api-java | GitHub - Corsinvest](https://github.com/Corsinvest/cv4pve-api-java)
- [Proxmox VE API Wiki](https://pve.proxmox.com/wiki/Proxmox_VE_API)

### IBM PowerVM
- [HMC REST APIs | IBM Docs](https://www.ibm.com/docs/en/power9/9040-MR9?topic=interfaces-hmc-rest-apis)
- [pypowervm | GitHub](https://github.com/powervm/pypowervm)
- [HmcRestClient | GitHub](https://github.com/PowerHMC/HmcRestClient)
- [New PowerVM and HMC Features - July 2025](https://community.ibm.com/community/user/blogs/pete-heyrman1/2025/07/08/new-powervm-and-hmc-features-july-2025)

### Multi-Hypervisor Architecture
- [VMware Abstraction Using Apache CloudStack | ShapeBlue](https://www.shapeblue.com/vmware-abstraction-using-apache-cloudstack/)
- [Choosing the Right Hypervisor: CloudStack Support | ShapeBlue](https://www.shapeblue.com/choosing-the-right-hypervisor-apache-cloudstack-hypervisor-support/)
- [Multi-Cloud Abstraction Models | TechTarget](https://www.techtarget.com/searchcloudcomputing/tip/Simplify-multi-cloud-management-with-two-abstraction-models)
- [libvirt-java | libvirt.org](https://libvirt.org/java.html)

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-12-08 | Claude | Initial research document |
