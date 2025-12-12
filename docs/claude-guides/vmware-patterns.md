# VMware VCF SDK 9.0 Patterns

> This guide is referenced from the main CLAUDE.md documentation. Read when working on VMware/vSphere integration code.

**SDK:** `com.vmware.sdk:vsphere-utils:9.0.0.0`

## PropertyCollector Pattern

The SDK requires explicit property fetching via `PropertySpec` + `ObjectSpec` + `FilterSpec`:

```kotlin
val propSpec = PropertySpec().apply {
    type = "ClusterComputeResource"
    pathSet.add("host")
}

val objSpec = ObjectSpec().apply {
    obj = clusterRef
    isSkip = false
}

val filterSpec = PropertyFilterSpec().apply {
    propSet.add(propSpec)
    objectSet.add(objSpec)
}

val result = vimPort.retrievePropertiesEx(propertyCollector, listOf(filterSpec), RetrieveOptions())
```

## SearchIndex Navigation

Use inventory paths (datacenter/folder/object pattern):

```kotlin
val searchIndex = serviceContent.searchIndex

// Paths follow: datacenter/type/name
val datacenterRef = vimPort.findByInventoryPath(searchIndex, "MyDatacenter")
val clusterRef = vimPort.findByInventoryPath(searchIndex, "MyDatacenter/host/MyCluster")
val datastoreRef = vimPort.findByInventoryPath(searchIndex, "MyDatacenter/datastore/MyDatastore")
val vmRef = vimPort.findByInventoryPath(searchIndex, "MyDatacenter/vm/MyTemplate")
```

## Port 443 Constraint

VCF SDK's `VcenterClientFactory` only supports HTTPS on port 443:

```kotlin
// ✅ CORRECT - Hostname only (SDK assumes port 443)
val factory = VcenterClientFactory("vcenter.example.com", trustStore)

// ❌ WRONG - Custom port not supported
val factory = VcenterClientFactory("vcenter.example.com:8443", trustStore)  // URISyntaxException
```

For VCSIM testing (dynamic ports), use the `VcsimAdapter` mock instead.

## Timeout Layering (Critical)

**Outer timeout MUST be longer than all inner timeouts combined.**

```kotlin
private val vmwareToolsTimeoutMs: Long = 120_000  // IP detection
private val createVmTimeoutMs: Long = 300_000    // 5 min total

suspend fun createVm(spec: VmSpec) = executeResilient(
    name = "createVm",
    operationTimeoutMs = createVmTimeoutMs  // 5 min > clone (~60s) + IP wait (120s)
) {
    cloneVm(spec)
    waitForIpAddress(vmwareToolsTimeoutMs)
}
```

**Rule:** Calculate total worst-case inner duration, then add buffer.

## Enum Conventions (CRITICAL)

**VCF SDK 9.0 uses UPPER_SNAKE_CASE enum constants, NOT legacy camelCase.**

```kotlin
// ✅ CORRECT - VCF SDK 9.0
when (vm.powerState) {
    VirtualMachinePowerState.POWERED_ON -> handlePoweredOn()
    VirtualMachinePowerState.POWERED_OFF -> handlePoweredOff()
    VirtualMachinePowerState.SUSPENDED -> handleSuspended()
}

// ❌ WRONG - Legacy vim25 SDK (doesn't exist!)
VirtualMachinePowerState.poweredOn   // Unresolved reference!
VirtualMachinePowerState.poweredOff
```

**Warning:** AI tools (CodeRabbit, Copilot) may suggest legacy camelCase based on outdated vim25 docs. Always verify against the actual SDK binary (`javap -c`).
