# vcsim Containers-as-VMs: Enhanced VMware Testing

**Author:** Claude (Research Agent)
**Date:** 2025-11-28
**Version:** 1.0
**Status:** Research Complete

---

## Executive Summary

This document explores **vcsim's containers-as-VMs feature**, a powerful capability that ties Docker containers to the lifecycle of simulated virtual machines. This feature enables **realistic end-to-end VMware integration testing** without requiring actual vSphere infrastructure.

**Key Benefits:**
- Full VM lifecycle simulation with real container behavior
- Network-accessible "VMs" with actual IP addresses
- Zero VMware infrastructure costs for testing
- CI/CD-ready with Docker-based execution
- Deterministic, reproducible test environments

**Recommendation:** Adopt containers-as-VMs for all DVMM VMware integration tests to achieve higher test fidelity while maintaining fast feedback loops.

---

## 1. What is vcsim?

**vcsim** (vCenter Simulator) is an official VMware tool from the [govmomi project](https://github.com/vmware/govmomi/tree/main/vcsim) that simulates the vSphere API. It allows developers to test VMware integrations without access to real vCenter or ESXi infrastructure.

### 1.1 Core Capabilities

| Capability | Description |
|------------|-------------|
| **vSphere API Simulation** | Implements the same SOAP API as real vCenter |
| **Configurable Inventory** | Simulates datacenters, clusters, hosts, VMs, networks |
| **Minimal Resources** | ~2GB RAM for simulating hundreds of VMs |
| **Docker Ready** | Available as `vmware/vcsim` container image |
| **govc Compatible** | Works with the govc CLI for automation |

### 1.2 Basic Usage

```bash
# Run vcsim with default inventory
docker run -d -p 8989:8989 vmware/vcsim

# Configure inventory size
docker run -d -p 8989:8989 vmware/vcsim \
  -cluster 2 \
  -host 4 \
  -vm 20

# Connect with govc
export GOVC_URL=https://localhost:8989/sdk
export GOVC_USERNAME=user
export GOVC_PASSWORD=pass
export GOVC_INSECURE=true

govc ls /DC0/vm
```

---

## 2. Containers-as-VMs Feature

### 2.1 Overview

The **containers-as-VMs** feature ([documentation](https://github.com/vmware/govmomi/wiki/vcsim-features#containers-as-vms)) ties Docker containers to the lifecycle of vcsim virtual machines. When a VM is powered on, its associated container starts. When powered off, the container stops.

This transforms vcsim from a **pure API mock** into a **functional VM simulator** where VMs have:
- Real IP addresses (from Docker networking)
- Running services (SSH, HTTP, etc.)
- Actual network connectivity
- Realistic lifecycle behavior

### 2.2 How It Works

```text
┌─────────────────────────────────────────────────────────────────┐
│                          vcsim                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  VirtualMachine: DC0_H0_VM0                              │   │
│  │  ├── ExtraConfig: RUN.container = "nginx"                │   │
│  │  ├── PowerState: poweredOn                               │   │
│  │  └── Guest.IpAddress: 172.17.0.2  ◄── From container     │   │
│  └──────────────────────────────────────────────────────────┘   │
│                              │                                   │
│                              │ VM lifecycle                      │
│                              ▼                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Docker Container: vcsim-DC0_H0_VM0-{uuid}               │   │
│  │  ├── Image: nginx                                        │   │
│  │  ├── Status: running                                     │   │
│  │  └── IP: 172.17.0.2                                      │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### 2.3 Lifecycle Mapping

| VM State | VM Method | Docker Command | Container Result |
|----------|-----------|----------------|------------------|
| `poweredOff` | `PowerOn` | `docker start` | Container running |
| `poweredOn` | `PowerOff` | `docker stop` | Container stopped |
| `poweredOn` | `Suspend` | `docker pause` | Container paused |
| `suspended` | `PowerOn` | `docker unpause` | Container resumed |
| `poweredOn` | `Reset` | `docker stop; start` | Container restarted |
| Any | `Destroy` | `docker rm -f` | Container removed |

### 2.4 Configuration

Attach a container to a VM using the `RUN.container` ExtraConfig key:

```bash
# Simple container (single argument)
govc vm.change -vm DC0_H0_VM0 -e RUN.container=nginx

# Container with arguments (JSON encoded)
govc vm.change -vm DC0_H0_VM0 -e 'RUN.container=["nginx", "-g", "daemon off;"]'

# Container with environment variables
govc vm.change -vm DC0_H0_VM0 -e 'RUN.container=["myapp", "-e", "ENV=test"]'
```

### 2.5 Container Naming Convention

vcsim names containers using the pattern:

```text
vcsim-{VM_NAME}-{VM_UUID}
```

Example: `vcsim-DC0_H0_VM0-423e5c3a-6c7c-5e0b-8d2f-0f3c6e7a8b9c`

### 2.6 Guest IP Population

When the container starts, vcsim automatically populates the VM's guest information:

- `guest.ipAddress` - Container's primary IP
- `guest.net[0].macAddress` - Container's MAC address
- `guest.net[0].ipAddress` - All container IPs

```bash
# Query VM guest IP after PowerOn
govc vm.info -json DC0_H0_VM0 | jq '.virtualMachines[0].guest.ipAddress'
# Output: "172.17.0.2"
```

---

## 3. Requirements for Containers-as-VMs

### 3.1 Docker Socket Access

vcsim must have access to the Docker socket to manage containers:

```yaml
# docker-compose.yml
services:
  vcsim:
    image: vmware/vcsim:latest
    ports:
      - "8989:8989"
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
```

### 3.2 Network Configuration

Containers run on Docker's bridge network by default. For proper connectivity:

```yaml
services:
  vcsim:
    image: vmware/vcsim:latest
    networks:
      - test-network
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock

  # Application under test
  dvmm-app:
    build: .
    networks:
      - test-network  # Same network as vcsim containers

networks:
  test-network:
    driver: bridge
```

### 3.3 Pre-built Container Images

Container images must be available locally or pullable:

```bash
# Pre-pull required images
docker pull nginx
docker pull alpine
docker pull myregistry/custom-vm-image:latest
```

---

## 4. Benefits for DVMM Testing

### 4.1 Current Testing Approach

Without containers-as-VMs, VMware integration tests are limited to:

| Approach | Limitation |
|----------|------------|
| **API Mocking** | No real network behavior |
| **WireMock** | Static responses only |
| **Pure vcsim** | VMs have no actual functionality |

### 4.2 Enhanced Testing with Containers-as-VMs

| Test Scenario | Without Feature | With Feature |
|---------------|-----------------|--------------|
| **VM Provisioning** | Verify API calls only | Verify VM is actually reachable |
| **Network Configuration** | Mock IP assignment | Real IP from container |
| **Service Deployment** | Cannot test | Container runs real service |
| **VM Lifecycle** | API state changes | Container lifecycle matches |
| **Health Checks** | Mock responses | Real TCP/HTTP checks |
| **SSH/Console Access** | Not possible | Full SSH connectivity |

### 4.3 Test Scenarios Enabled

#### 4.3.1 Provisioning Verification

```kotlin
@Test
fun `provisioned VM is network accessible`() = runTest {
    // Given: VM request approved
    val vmId = provisioningService.provision(vmRequest)

    // When: VM is powered on
    vmwareClient.powerOn(vmId)

    // Then: VM is actually reachable
    val vmIp = vmwareClient.getGuestIp(vmId)
    val reachable = networkUtils.isReachable(vmIp, port = 22)

    assertThat(reachable).isTrue()
}
```

#### 4.3.2 Power State Behavior

```kotlin
@Test
fun `powered off VM is not network accessible`() = runTest {
    val vmId = createAndPowerOnVm()
    val vmIp = vmwareClient.getGuestIp(vmId)

    // Verify reachable when on
    assertThat(networkUtils.isReachable(vmIp, port = 22)).isTrue()

    // Power off
    vmwareClient.powerOff(vmId)

    // Verify not reachable when off
    assertThat(networkUtils.isReachable(vmIp, port = 22)).isFalse()
}
```

#### 4.3.3 Service Deployment Verification

```kotlin
@Test
fun `web application VM serves HTTP traffic`() = runTest {
    // Configure VM with nginx container
    govc("vm.change", "-vm", vmId, "-e", "RUN.container=nginx")
    govc("vm.power", "-on", vmId)

    val vmIp = awaitVmGuestIp(vmId)

    // Verify HTTP service is running
    val response = httpClient.get("http://$vmIp:80")
    assertThat(response.status).isEqualTo(HttpStatusCode.OK)
}
```

#### 4.3.4 VM Destruction Cleanup

```kotlin
@Test
fun `destroyed VM removes container`() = runTest {
    val vmId = createAndPowerOnVm()
    val containerName = "vcsim-$vmId-*"

    // Container exists
    assertThat(docker.containerExists(containerName)).isTrue()

    // Destroy VM
    vmwareClient.destroy(vmId)

    // Container removed
    assertThat(docker.containerExists(containerName)).isFalse()
}
```

---

## 5. Custom Container Images for Testing

### 5.1 Base VM Image

Create a lightweight container that simulates a basic VM:

```dockerfile
# test-containers/base-vm/Dockerfile
FROM alpine:3.19

# Basic utilities
RUN apk add --no-cache \
    openssh-server \
    curl \
    bash

# SSH setup (credentials injected at runtime via environment variables)
ARG VM_USER=vmuser
ARG VM_PASS
RUN ssh-keygen -A && \
    adduser -D -s /bin/bash ${VM_USER} && \
    echo "${VM_USER}:${VM_PASS}" | chpasswd && \
    sed -i 's/#PasswordAuthentication yes/PasswordAuthentication yes/' /etc/ssh/sshd_config

# Health check endpoint
RUN echo '#!/bin/sh\necho "OK"' > /healthcheck.sh && chmod +x /healthcheck.sh

EXPOSE 22

CMD ["/usr/sbin/sshd", "-D", "-e"]
```

### 5.2 Application Server VM

```dockerfile
# test-containers/app-vm/Dockerfile
FROM alpine:3.19

RUN apk add --no-cache \
    openssh-server \
    nginx \
    supervisor

# SSH + nginx supervisor config
COPY supervisord.conf /etc/supervisor/conf.d/

EXPOSE 22 80

CMD ["supervisord", "-c", "/etc/supervisor/conf.d/supervisord.conf"]
```

### 5.3 Windows-like VM (Simulated)

```dockerfile
# test-containers/windows-vm/Dockerfile
FROM mcr.microsoft.com/windows/servercore:ltsc2022

# Install OpenSSH
RUN powershell -Command \
    Add-WindowsCapability -Online -Name OpenSSH.Server~~~~0.0.1.0; \
    Start-Service sshd; \
    Set-Service -Name sshd -StartupType Automatic

EXPOSE 22 3389

CMD ["powershell", "-Command", "Start-Service sshd; Wait-Event"]
```

---

## 6. Integration with Test Framework

### 6.1 Testcontainers Setup

```kotlin
// eaf-testing/src/main/kotlin/de/acci/eaf/testing/VcsimContainer.kt

class VcsimContainer : GenericContainer<VcsimContainer>("vmware/vcsim:latest") {

    companion object {
        // VCSIM default credentials (these are simulator defaults, not real secrets)
        const val DEFAULT_USER = "user"
        const val DEFAULT_PASS = "pass"
    }

    init {
        withExposedPorts(8989)
        withFileSystemBind(
            "/var/run/docker.sock",
            "/var/run/docker.sock",
            BindMode.READ_WRITE
        )
        withCommand("-l", "0.0.0.0:8989")
        waitingFor(Wait.forHttp("/about").forPort(8989))
    }

    val sdkUrl: String
        get() = "https://${host}:${getMappedPort(8989)}/sdk"

    fun attachContainer(vmPath: String, containerImage: String) {
        execGovc("vm.change", "-vm", vmPath, "-e", "RUN.container=$containerImage")
    }

    fun powerOn(vmPath: String) {
        execGovc("vm.power", "-on", vmPath)
    }

    fun powerOff(vmPath: String) {
        execGovc("vm.power", "-off", vmPath)
    }

    fun getGuestIp(vmPath: String): String? {
        val result = execGovc("vm.info", "-json", vmPath)
        return JsonPath.read(result, "$.virtualMachines[0].guest.ipAddress")
    }

    private fun execGovc(vararg args: String): String {
        val result = execInContainer(
            "govc", *args,
            "-u", "https://localhost:8989/sdk",
            "-k"
        )
        if (result.exitCode != 0) {
            throw RuntimeException("govc failed: ${result.stderr}")
        }
        return result.stdout
    }
}
```

### 6.2 JUnit 5 Extension

```kotlin
// eaf-testing/src/main/kotlin/de/acci/eaf/testing/VcsimExtension.kt

class VcsimExtension : BeforeAllCallback, AfterAllCallback, ParameterResolver {

    private lateinit var vcsim: VcsimContainer

    override fun beforeAll(context: ExtensionContext) {
        vcsim = VcsimContainer()
        vcsim.start()

        // Store in extension context
        context.getStore(NAMESPACE).put("vcsim", vcsim)
    }

    override fun afterAll(context: ExtensionContext) {
        vcsim.stop()
    }

    override fun supportsParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext
    ): Boolean {
        return parameterContext.parameter.type == VcsimContainer::class.java
    }

    override fun resolveParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext
    ): Any {
        return extensionContext.getStore(NAMESPACE).get("vcsim", VcsimContainer::class.java)
    }

    companion object {
        private val NAMESPACE = ExtensionContext.Namespace.create(VcsimExtension::class.java)
    }
}
```

### 6.3 Test Base Class

```kotlin
// eaf-testing/src/main/kotlin/de/acci/eaf/testing/VcsimIntegrationTest.kt

@ExtendWith(VcsimExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class VcsimIntegrationTest {

    protected lateinit var vcsim: VcsimContainer
    protected lateinit var vmwareClient: VmwareClient

    @BeforeAll
    fun setupVmware(vcsim: VcsimContainer) {
        this.vcsim = vcsim
        // VCSIM default credentials (not real secrets)
        this.vmwareClient = VmwareClient(
            url = vcsim.sdkUrl,
            username = VcsimContainer.DEFAULT_USER,
            password = VcsimContainer.DEFAULT_PASS,
            insecure = true
        )
    }

    protected fun createVmWithContainer(
        vmPath: String = "/DC0/vm/DC0_H0_VM0",
        containerImage: String = "alpine"
    ): VmTestContext {
        vcsim.attachContainer(vmPath, containerImage)
        vcsim.powerOn(vmPath)

        val ip = await().atMost(Duration.ofSeconds(30)).until({
            vcsim.getGuestIp(vmPath)
        }) { it != null }

        return VmTestContext(vmPath, ip!!)
    }

    data class VmTestContext(
        val vmPath: String,
        val ipAddress: String
    )
}
```

---

## 7. CI/CD Integration

### 7.1 GitHub Actions Workflow

```yaml
# .github/workflows/vmware-integration.yml
name: VMware Integration Tests

on:
  push:
    paths:
      - 'dvmm/dvmm-infrastructure/**'
      - 'eaf/eaf-testing/**'

jobs:
  vmware-tests:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Build test container images
        run: |
          docker build -t dvmm-test-vm:latest test-containers/base-vm/
          docker build -t dvmm-app-vm:latest test-containers/app-vm/

      - name: Run VMware integration tests
        run: ./gradlew :dvmm:dvmm-infrastructure:test --tests "*Vcsim*"
        env:
          TESTCONTAINERS_RYUK_DISABLED: true

      - name: Upload test results
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: vmware-test-results
          path: dvmm/dvmm-infrastructure/build/reports/tests/
```

### 7.2 Docker Compose for Local Development

```yaml
# docker-compose.vcsim.yml
services:
  vcsim:
    image: vmware/vcsim:latest
    ports:
      - "8989:8989"
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
    command: ["-l", "0.0.0.0:8989", "-cluster", "2", "-host", "4", "-vm", "20"]
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8989/about"]
      interval: 10s
      timeout: 5s
      retries: 3

  govc:
    image: vmware/govc:latest
    depends_on:
      vcsim:
        condition: service_healthy
    environment:
      - GOVC_URL=https://vcsim:8989/sdk
      - GOVC_USERNAME=user
      - GOVC_PASSWORD=pass
      - GOVC_INSECURE=true
    entrypoint: ["tail", "-f", "/dev/null"]  # Keep alive for exec
```

---

## 8. Comparison with Other Approaches

| Approach | API Fidelity | Network Reality | Setup Complexity | CI/CD Ready |
|----------|--------------|-----------------|------------------|-------------|
| **vcsim + containers-as-VMs** | High | Full | Medium | Yes |
| **Pure vcsim** | High | None | Low | Yes |
| **WireMock/Pact** | Medium | None | Low | Yes |
| **Real vCenter** | Perfect | Full | High | No |
| **Nested ESXi** | Perfect | Full | Very High | No |

---

## 9. Limitations

| Limitation | Impact | Workaround |
|------------|--------|------------|
| **Docker socket required** | Security consideration | Use dedicated CI runners |
| **Linux containers only** | No real Windows VMs | Use Windows Server containers |
| **No VMware Tools** | Limited guest info | Mock VMware Tools responses |
| **Network differences** | Docker networking != vSphere | Test on same Docker network |
| **API coverage gaps** | Some APIs not implemented | Focus on used APIs |

---

## 10. Recommendations for DVMM

### 10.1 Adoption Strategy

1. **Phase 1: Infrastructure Setup**
   - Add `VcsimContainer` to `eaf-testing` module
   - Create base test container images
   - Update CI/CD pipeline

2. **Phase 2: Migration**
   - Convert existing WireMock tests to vcsim
   - Add network reachability assertions
   - Implement lifecycle tests

3. **Phase 3: Advanced Testing**
   - Multi-VM orchestration tests
   - Failure injection scenarios
   - Performance testing with scaled inventory

### 10.2 Test Categories

| Category | Use containers-as-VMs | Rationale |
|----------|----------------------|-----------|
| **Unit Tests** | No | Pure business logic, no VMware |
| **API Contract Tests** | Optional | vcsim sufficient for API shape |
| **Integration Tests** | Yes | Network behavior validation |
| **E2E Tests** | Yes | Full stack verification |
| **Performance Tests** | Maybe | Depends on what's being measured |

---

## 11. References

- [govmomi vcsim README](https://github.com/vmware/govmomi/tree/main/vcsim)
- [vcsim Features Wiki](https://github.com/vmware/govmomi/wiki/vcsim-features)
- [Containers-as-VMs Documentation](https://github.com/vmware/govmomi/wiki/vcsim-features#containers-as-vms)
- [vcsim Docker Image](https://hub.docker.com/r/vmware/vcsim)
- [govc CLI Documentation](https://github.com/vmware/govmomi/tree/main/govc)
- [vCenter Simulator for CI/CD](https://enterpriseadmins.org/blog/virtualization/scaling-your-tests-how-to-set-up-a-vcenter-server-simulator/)

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-11-28 | Claude (Research Agent) | Initial research document |

---

*This document provides the foundation for enhanced VMware testing in DVMM. Implementation details will be refined during sprint planning.*
