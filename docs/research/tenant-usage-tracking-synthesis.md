# Tenant Usage Tracking & Billing for DVMM: Synthesized Research Findings

**Document Version:** 1.0
**Date:** 2025-12-11
**Status:** Final Synthesis from 3 Independent Research Agents

---

## Executive Summary

### The Challenge

DVMM requires a tenant usage tracking and billing system that:
- Collects accurate resource metrics (VMs, CPU, memory, storage, network)
- Applies flexible pricing models suitable for DACH enterprises
- Generates compliant invoices (ZUGFeRD/XRechnung)
- Integrates with enterprise ERP systems (primarily SAP)
- Maintains strict multi-tenant data isolation
- Aligns with DVMM's existing CQRS/Event Sourcing architecture

### Recommended Approach

**All three research sources converge on a consistent recommendation:**

| Component | Recommendation | Rationale |
|-----------|---------------|-----------|
| **Metrics Collection** | vSphere PerformanceManager API via Prometheus/OpenTelemetry | Native VMware precision + open-source flexibility |
| **Time-Series Storage** | TimescaleDB (PostgreSQL extension) | Leverages existing stack, native RLS support |
| **Billing Events** | Event Sourcing in PostgreSQL | Immutable audit trail, retroactive recalculation |
| **Metering Model** | Hybrid (Base Allocation + Usage) | DACH enterprise preference for predictability |
| **Invoice Format** | ZUGFeRD 2.1 (PDF/A-3 + XML) | German B2B/B2G compliance, human + machine readable |
| **MVP Billing** | Allocated resources, hourly metering | Simplest implementation, fastest time-to-market |

### Key Timelines

| Milestone | Deadline | Implication |
|-----------|----------|-------------|
| German e-invoice reception | **January 2025** | All businesses must accept e-invoices |
| German e-invoice issuance (>€800K) | **January 2027** | Must issue e-invoices |
| Full German B2B e-invoicing | **January 2028** | Mandatory for all B2B |

**Recommendation:** Implement ZUGFeRD 2.1 Basic profile in MVP to meet January 2025 deadline.

---

## 1. Usage Data Collection: Synthesized Findings

### 1.1 Primary Collection Method: vSphere PerformanceManager

All sources agree that **VMware's PerformanceManager API** is the authoritative source for billing-grade metrics.

#### Critical Counters for Billing

| Resource | Counter | Unit | Billing Use |
|----------|---------|------|-------------|
| **CPU** | `cpu.usagemhz.average` | MHz | Consumption billing |
| **CPU** | `cpu.usage.average` | % | Utilization tracking |
| **Memory** | `mem.consumed.average` | KB | Physical memory used |
| **Memory** | `mem.granted.average` | KB | Mapped memory (allocation) |
| **Memory** | `mem.active.average` | KB | Guest OS usage |
| **Disk** | `disk.provisioned.latest` | KB | Allocated storage |
| **Disk** | `disk.used.latest` | KB | Actual storage consumed |
| **Network** | `net.received.average` | KBps | Ingress bandwidth |
| **Network** | `net.transmitted.average` | KBps | Egress bandwidth |
| **IOPS** | `disk.numberRead.summation` | count | Read operations |
| **IOPS** | `disk.numberWrite.summation` | count | Write operations |

#### Data Granularity Levels

| Level | Interval | Retention | Billing Suitability |
|-------|----------|-----------|---------------------|
| Real-time | 20 seconds | 1 hour | Too granular, high overhead |
| **Level 1** | **5 minutes** | **1 day** | **Recommended for billing** |
| Level 2 | 30 minutes | 1 week | Acceptable |
| Level 3 | 2 hours | 1 month | Coarse but low overhead |
| Level 4 | 1 day | 1 year | Summary only |

**Consensus:** Use 5-minute rollups for billing calculations. Avoid real-time polling (20s) due to vCenter load.

#### Performance Best Practices

All sources emphasize these API optimization techniques:

1. **Cache static data** - CounterIDs, MetricIDs, ManagedObjectReferences
2. **Batch requests** - Pass multiple PerfQuerySpec to single QueryPerf call
3. **Use CSV format** - 40% reduction in serialization overhead vs. normal format
4. **Prefer rollups** - Query historical data, not real-time counters
5. **Stagger polling** - Avoid "thundering herd" on vCenter

### 1.2 Collection Architecture Options

| Approach | Pros | Cons | DVMM Fit |
|----------|------|------|----------|
| **Direct vSphere API** | Rich counters, vCenter integration | High API load if frequent polling | Good for lifecycle events |
| **Prometheus + vmware_exporter** | Flexible, open-source, Grafana dashboards | Needs exporter setup | **Excellent** |
| **OpenTelemetry vCenter Receiver** | Standardized, vendor-agnostic | Still maturing (alpha) | Good future option |
| **Telegraf + InfluxDB** | Efficient push-based, good retention | Additional infrastructure | Good alternative |
| **Aria Operations (vROps)** | Built-in chargeback, multi-tenant | Enterprise license cost | If already licensed |
| **In-Guest Agents** | Guest OS visibility | Privacy concerns, deployment overhead | Optional enhancement |

**Recommended Architecture:**

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   vCenter API   │────▶│ Prometheus/OTel  │────▶│  TimescaleDB    │
│ PerformanceManager   │ vmware_exporter   │     │ (hypertables)   │
└─────────────────┘     └──────────────────┘     └────────┬────────┘
                                                          │
┌─────────────────┐     ┌──────────────────┐              │
│ vCenter Events  │────▶│  DVMM Event Bus  │────▶────────▶│
│ (lifecycle)     │     │ (Kafka/RabbitMQ) │              │
└─────────────────┘     └──────────────────┘              ▼
                                                 ┌─────────────────┐
                                                 │  Rating Engine  │
                                                 │  (Billing BC)   │
                                                 └─────────────────┘
```

### 1.3 Storage Usage Tracking

Storage billing is complex due to thin/thick provisioning and vSAN abstractions.

#### Thin vs. Thick Provisioning

| Model | Bill For | Pros | Cons |
|-------|----------|------|------|
| Provisioned | Reserved capacity | Predictable revenue | Customers pay for air |
| Used | Actual consumption | Fair billing | Revenue unpredictability |
| **Hybrid** | **Provisioned × Policy Multiplier** | **Transparent, equitable** | **Slightly complex** |

**Consensus Recommendation:** Bill `Logical Capacity × Storage Policy Multiplier`
Example: 100GB data × 1.33 (RAID-5) = 133GB billed

#### vSAN-Specific Tracking

For vSAN environments, use `VsanQueryObjectIdentities(includeSpaceSummary=true)` to get per-VM consumption including:
- VM Home files
- VMDK sizes
- Swap files
- Deduplication/compression ratios

**Note:** Must query each ESXi host in vSAN cluster and aggregate.

### 1.4 Lifecycle Event Tracking

All sources agree that **vCenter Events** are essential for accurate billing:

| Event Type | Billing Use |
|------------|-------------|
| `VmCreatedEvent` | Start billing |
| `VmPoweredOnEvent` | Start compute charges |
| `VmPoweredOffEvent` | Stop compute (storage continues) |
| `VmReconfiguredEvent` | Detect resize (vCPU/RAM changes) |
| `VmRemovedEvent` | Stop all charges |

**Event Sourcing Alignment:** These events naturally fit DVMM's ES architecture. Unlike sampled metrics (which can miss short-lived VMs), events guarantee every second of lifecycle is captured.

---

## 2. Usage Metering Models: Synthesized Findings

### 2.1 Model Comparison

| Model | Description | Pros | Cons | Best For |
|-------|-------------|------|------|----------|
| **Reserved/Allocated** | Bill configured capacity | Predictable, simple | Pays for unused | Stable workloads |
| **Consumed/Utilized** | Bill actual usage | Fair, efficient | Variable costs | Dev/test, bursty |
| **Hybrid (Recommended)** | Base + overage | Predictable + fair | Medium complexity | **DACH enterprises** |

### 2.2 Recommended Hybrid Model for DACH

All sources converge on a **hybrid model** as the optimal approach for DACH enterprises:

```
Monthly Charge = Base Allocation Fee + Usage Overage

Where:
- Base Allocation = Reserved vCPU × Rate + Reserved RAM × Rate + Reserved Storage × Rate
- Usage Overage = MAX(0, Actual Usage - Base Allocation) × Overage Rate
```

**Overage Rate:** Typically 1.5-2x base rate (incentivizes accurate capacity planning)

### 2.3 Billing Granularity Standards

| Dimension | Industry Standard | DVMM Recommendation |
|-----------|-------------------|---------------------|
| **Time** | Per-second (AWS) to hourly | **Per-minute collection, hourly billing** |
| **Compute** | Per-vCPU-hour or per-MHz-hour | Per-vCPU-hour (simpler) |
| **Memory** | Per-GB-hour | Per-GB-hour |
| **Storage** | Per-GB-month | Per-GB-month |
| **Network** | Per-GB transferred | Per-GB egress (ingress free) |

### 2.4 Pricing Structure Options

| Structure | Description | Complexity | DACH Fit |
|-----------|-------------|------------|----------|
| **Flat Rate Tiers** | S/M/L bundles | Low | Good for SMB |
| **Pay-As-You-Go** | Raw metering | Medium | Good for variable |
| **Tiered Discounts** | Volume-based rates | Medium | Enterprise preference |
| **Committed Use** | 1-3yr contracts | High | Enterprise standard |

**Recommendation for MVP:** Start with flat-rate tiers (Small/Medium/Large VMs) + storage add-on. Add per-unit PAYG in Phase 2.

#### Suggested VM Tiers

| Tier | vCPUs | RAM | Storage | Monthly Price (Example) |
|------|-------|-----|---------|------------------------|
| Small | 1-2 | 2-4 GB | 20-50 GB | €50-100 |
| Medium | 2-4 | 4-8 GB | 50-100 GB | €100-200 |
| Large | 4-8 | 8-16 GB | 100-250 GB | €200-400 |

Memory-to-vCore ratios follow industry patterns: 4GB per vCPU (general), 8GB per vCPU (memory-optimized).

### 2.5 Committed Use Discounts

All sources note that committed-use discounts are standard in enterprise:

| Term | Typical Discount |
|------|-----------------|
| 1 year | 20-40% |
| 3 years | 40-72% |

**Implementation Note:** Rating engine must implement "draw-down" logic:
1. Apply usage against committed volume at discounted rate ($0)
2. Bill excess at on-demand rates

---

## 3. Data Architecture: Synthesized Findings

### 3.1 Storage Architecture Recommendation

**Unanimous consensus:** Use **TimescaleDB** (PostgreSQL extension) for usage metrics.

| Option | Pros | Cons | Verdict |
|--------|------|------|---------|
| **TimescaleDB** | Same PostgreSQL stack, native RLS, continuous aggregates | Less mature than pure TSDB for massive scale | **Recommended** |
| VictoriaMetrics | Best compression (70x vs InfluxDB), free clustering | Separate system, no RLS | If >10M time series |
| InfluxDB | Purpose-built TSDB, good ecosystem | Separate system, clustering requires Enterprise | Adds complexity |
| Pure Event Sourcing | Complete audit trail | High storage for metrics | Use for lifecycle only |

### 3.2 TimescaleDB Schema Design

```sql
-- Raw usage metrics (hypertable)
CREATE TABLE vm_usage (
    time TIMESTAMPTZ NOT NULL,
    tenant_id UUID NOT NULL,
    vm_id UUID NOT NULL,
    cpu_mhz DOUBLE PRECISION,
    memory_bytes BIGINT,
    disk_used_bytes BIGINT,
    disk_provisioned_bytes BIGINT,
    network_rx_bytes BIGINT,
    network_tx_bytes BIGINT
);

SELECT create_hypertable('vm_usage', 'time',
    chunk_time_interval => INTERVAL '1 day');

-- Enable compression for older data
ALTER TABLE vm_usage SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'tenant_id, vm_id'
);

SELECT add_compression_policy('vm_usage', INTERVAL '7 days');

-- Continuous aggregate for hourly billing
CREATE MATERIALIZED VIEW vm_usage_hourly
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 hour', time) AS bucket,
    tenant_id,
    vm_id,
    AVG(cpu_mhz) AS avg_cpu_mhz,
    MAX(memory_bytes) AS peak_memory_bytes,
    MAX(disk_used_bytes) AS max_disk_used,
    SUM(network_tx_bytes) AS total_egress
FROM vm_usage
GROUP BY bucket, tenant_id, vm_id;

-- Refresh policy
SELECT add_continuous_aggregate_policy('vm_usage_hourly',
    start_offset => INTERVAL '3 hours',
    end_offset => INTERVAL '1 hour',
    schedule_interval => INTERVAL '1 hour');
```

### 3.3 Row-Level Security for Multi-Tenancy

```sql
-- Enable RLS on usage tables
ALTER TABLE vm_usage ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON vm_usage
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE vm_usage FORCE ROW LEVEL SECURITY;

-- Same for billing tables
ALTER TABLE rated_usage ENABLE ROW LEVEL SECURITY;
ALTER TABLE invoices ENABLE ROW LEVEL SECURITY;
-- ... apply similar policies
```

### 3.4 Event Sourcing Integration

Usage tracking fits DVMM's CQRS/ES architecture:

**Domain Events for Billing:**
```kotlin
// Lifecycle events (low volume, high importance)
sealed class VmBillingEvent : DomainEvent {
    data class VmProvisioned(
        val vmId: VmId,
        val tenantId: TenantId,
        val instanceType: InstanceType,
        val cpuCores: Int,
        val memoryGb: Int,
        val storageGb: Int
    ) : VmBillingEvent()

    data class VmPoweredOn(val vmId: VmId, val timestamp: Instant) : VmBillingEvent()
    data class VmPoweredOff(val vmId: VmId, val timestamp: Instant) : VmBillingEvent()
    data class VmResized(val vmId: VmId, val newCpuCores: Int, val newMemoryGb: Int) : VmBillingEvent()
    data class VmDeprovisioned(val vmId: VmId, val timestamp: Instant) : VmBillingEvent()
}

// Summary events (generated from metrics, stored in event store)
data class DailyUsageSummarized(
    val vmId: VmId,
    val tenantId: TenantId,
    val date: LocalDate,
    val cpuHours: BigDecimal,
    val memoryGbHours: BigDecimal,
    val storageGbDays: BigDecimal,
    val networkEgressGb: BigDecimal
) : DomainEvent
```

**Recommendation:** Store high-frequency metrics in TimescaleDB, emit summary events to main event store for billing projections. This provides:
- Retroactive recalculation (replay events with new rate cards)
- Immutable audit trail
- Separation of concerns (metrics vs. billing)

### 3.5 Data Retention Strategy

| Tier | Data Type | Retention | Storage |
|------|-----------|-----------|---------|
| **Hot** | Raw metrics (5-min) | 7 days | TimescaleDB, full resolution |
| **Warm** | Hourly aggregates | 90 days | TimescaleDB, compressed |
| **Cold** | Daily summaries | 10+ years | PostgreSQL/Archive |
| **Immutable** | Billing events, invoices | 10+ years | Event store, WORM |

**GDPR vs. Accounting:** Keep billing records for 10 years (German tax law). Pseudonymize personal identifiers after tenant deletion, but retain financial data.

---

## 4. Billing Engine Architecture: Synthesized Findings

### 4.1 Bounded Context Design

All sources recommend treating billing as a **separate bounded context**:

```
┌─────────────────────────────────────────────────────────────────┐
│                     Billing Bounded Context                      │
├─────────────────────────────────────────────────────────────────┤
│  Inbound Ports              │  Outbound Ports                   │
│  ─────────────              │  ──────────────                   │
│  • CalculateBill            │  • MetricRepository (Timescale)   │
│  • EstimateCost             │  • EventStore (PostgreSQL)        │
│  • UpdateRateCard           │  • InvoicePublisher (ERP)         │
│  • GenerateInvoice          │  • PaymentGateway (Stripe/Adyen)  │
│  • ApplyCredit              │  • EmailService (notifications)   │
├─────────────────────────────────────────────────────────────────┤
│  Domain Services                                                 │
│  ───────────────                                                 │
│  • RatingService (apply rate cards to usage)                    │
│  • InvoiceService (generate invoices, handle disputes)          │
│  • PricingService (manage rate cards, discounts)                │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 Rate Card Schema

```sql
-- Rate card versions (effective dating)
CREATE TABLE rate_cards (
    id UUID PRIMARY KEY,
    tenant_id UUID,  -- NULL = default rate card
    name VARCHAR(255) NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    currency CHAR(3) DEFAULT 'EUR',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    created_by UUID NOT NULL
);

-- Rate card line items
CREATE TABLE rate_card_items (
    id UUID PRIMARY KEY,
    rate_card_id UUID REFERENCES rate_cards(id),
    resource_type VARCHAR(50) NOT NULL,  -- 'CPU', 'MEMORY', 'STORAGE', 'NETWORK'
    unit VARCHAR(20) NOT NULL,           -- 'VCPU_HOUR', 'GB_HOUR', 'GB_MONTH', 'GB'
    tier_start DECIMAL(19,4) DEFAULT 0,  -- For tiered pricing
    tier_end DECIMAL(19,4),
    unit_price DECIMAL(19,4) NOT NULL,
    UNIQUE (rate_card_id, resource_type, unit, tier_start)
);

-- UN/CEFACT unit codes for ZUGFeRD compliance
CREATE TABLE unit_code_mapping (
    internal_unit VARCHAR(20) PRIMARY KEY,
    uncefact_code VARCHAR(10) NOT NULL,
    description VARCHAR(255)
);

INSERT INTO unit_code_mapping VALUES
    ('HOUR', 'HUR', 'Hour'),
    ('DAY', 'DAY', 'Day'),
    ('GIGABYTE', 'E4', 'Gigabyte (10^9 bytes)'),
    ('GIBIBYTE', 'G4', 'Gigabyte (binary)'),
    ('UNIT', 'C62', 'One (unit)');
```

### 4.3 Rating Calculation

```kotlin
// Rating service pseudocode
class RatingService(
    private val rateCardRepository: RateCardRepository,
    private val usageRepository: UsageRepository
) {
    suspend fun rateUsage(
        tenantId: TenantId,
        billingPeriod: BillingPeriod
    ): List<RatedUsageItem> {
        val usage = usageRepository.getUsageForPeriod(tenantId, billingPeriod)
        val rateCard = rateCardRepository.getEffectiveRateCard(tenantId, billingPeriod.start)

        return usage.flatMap { usageItem ->
            val rate = rateCard.getRateForResource(usageItem.resourceType, usageItem.unit)

            // Handle proration for partial periods
            val proratedQuantity = calculateProration(usageItem, billingPeriod)

            // Apply tiered pricing if applicable
            val tieredCharges = applyTiers(rate, proratedQuantity)

            tieredCharges.map { (tier, quantity, unitPrice) ->
                RatedUsageItem(
                    tenantId = tenantId,
                    resourceId = usageItem.resourceId,
                    resourceType = usageItem.resourceType,
                    quantity = quantity,
                    unit = usageItem.unit,
                    unitPrice = unitPrice,
                    amount = (quantity * unitPrice).setScale(2, RoundingMode.HALF_EVEN),
                    billingPeriod = billingPeriod,
                    rateCardId = rateCard.id
                )
            }
        }
    }
}
```

**Decimal Precision Rules:**
- Never use floating-point for money
- Use `DECIMAL(19,4)` for currency storage
- Apply banker's rounding (HALF_EVEN) to eliminate systematic bias
- Round at line item level (2 decimal places), then sum

### 4.4 Invoice Generation with ZUGFeRD/XRechnung

**ZUGFeRD 2.x** is the DACH standard: PDF/A-3 with embedded XML (UN/CEFACT CII syntax).

| Profile | Use Case | Complexity |
|---------|----------|------------|
| MINIMUM | Receipt only | Low |
| BASIC | Standard invoices | **Recommended for MVP** |
| EN 16931 | EU-compliant | B2G required |
| EXTENDED | Full detail | Enterprise |

**XRechnung** is required for German B2G (public sector):
- Pure XML format (no PDF)
- Submit via ZRE (Zentrale Rechnungseingangsplattform) or Peppol network
- Requires Leitweg-ID for routing

**Implementation Libraries:**

| Language | Library | Features |
|----------|---------|----------|
| Java/Kotlin | **Mustang Project** | ZUGFeRD creation, validation, PDF merging |
| PHP | horstoeko/zugferd | All profiles, PDF merging |
| .NET | ZUGFeRD-csharp | Basic profile support |

**Invoice Structure:**
```xml
<!-- ZUGFeRD 2.1 Basic Profile (simplified) -->
<rsm:CrossIndustryInvoice>
  <rsm:ExchangedDocument>
    <ram:ID>INV-2025-001234</ram:ID>
    <ram:TypeCode>380</ram:TypeCode> <!-- Commercial Invoice -->
    <ram:IssueDateTime>2025-12-11</ram:IssueDateTime>
  </rsm:ExchangedDocument>

  <rsm:SupplyChainTradeTransaction>
    <!-- Seller (DVMM Operator) -->
    <ram:ApplicableHeaderTradeAgreement>
      <ram:SellerTradeParty>
        <ram:Name>DVMM Services GmbH</ram:Name>
        <ram:SpecifiedTaxRegistration>
          <ram:ID schemeID="VA">DE123456789</ram:ID>
        </ram:SpecifiedTaxRegistration>
      </ram:SellerTradeParty>
      <ram:BuyerTradeParty>
        <ram:Name>Customer Corp</ram:Name>
      </ram:BuyerTradeParty>
    </ram:ApplicableHeaderTradeAgreement>

    <!-- Line Items -->
    <ram:IncludedSupplyChainTradeLineItem>
      <ram:AssociatedDocumentLineDocument>
        <ram:LineID>1</ram:LineID>
      </ram:AssociatedDocumentLineDocument>
      <ram:SpecifiedTradeProduct>
        <ram:Name>VM Compute (vCPU-Hours)</ram:Name>
      </ram:SpecifiedTradeProduct>
      <ram:SpecifiedLineTradeSettlement>
        <ram:BilledQuantity unitCode="HUR">720</ram:BilledQuantity>
        <ram:NetPriceProductTradePrice>
          <ram:ChargeAmount>0.05</ram:ChargeAmount>
        </ram:NetPriceProductTradePrice>
        <ram:LineTotalAmount>36.00</ram:LineTotalAmount>
      </ram:SpecifiedLineTradeSettlement>
    </ram:IncludedSupplyChainTradeLineItem>
  </rsm:SupplyChainTradeTransaction>
</rsm:CrossIndustryInvoice>
```

### 4.5 ERP Integration

**SAP dominates DACH** (22.3% of European ERP market).

| Method | Type | Use Case |
|--------|------|----------|
| **BAPI** | Synchronous RFC | Real-time invoice creation |
| **IDoc** | Asynchronous messaging | Batch invoice import |
| **OData/REST** | Modern API | S/4HANA integration |

**Key SAP BAPIs:**
- `BAPI_BILLINGDOC_CREATEMULTIPLE` - Create billing documents
- `BAPI_BILLINGDOC_GETDETAIL` - Retrieve billing details

**Invoice IDoc:** `INVOIC02` format with segments:
- `E1EDK01` - Header data
- `E1EDP01` - Line item data
- `E1EDS01` - Totals/summary

**Integration Pattern:**
1. Generate invoices in ZUGFeRD/XRechnung XML
2. Provide REST API for ERP retrieval
3. Support scheduled file-based export (CSV, XML) for legacy systems

---

## 5. Compliance Requirements: Synthesized Findings

### 5.1 German Accounting Standards (GoBD)

**GoBD (Grundsätze zur ordnungsmäßigen Führung und Aufbewahrung von Büchern):**

| Requirement | Implementation |
|-------------|----------------|
| Unalterable records | Event sourcing (append-only) |
| 10-year retention | Archive invoices + billing events |
| Traceable | Audit trail linking usage → rated → invoice |
| Verifiable | Original format preservation |
| Procedural documentation | Document billing logic and rate card changes |

**GDPdU Export:** For tax audits, provide CSV export with INDEX.xml descriptor.

### 5.2 Revenue Recognition (ASC 606 / IFRS 15)

Usage-based billing affects revenue timing:

| Scenario | Recognition |
|----------|-------------|
| Usage-based fees | Recognize as consumption occurs |
| Prepaid credits | Deferred revenue until consumed |
| Committed contracts | Ratable over contract term |

**Implementation:** Track "unbilled revenue" (accrued but not invoiced) for finance reporting.

### 5.3 Data Retention by Country

| Country | Invoice Retention | Commercial Books |
|---------|-------------------|------------------|
| **Germany** | 8 years (reduced from 10 in 2024) | 10 years |
| **Austria** | 7 years standard | 22 years (immovable property) |
| **Switzerland** | 10 years | 10 years with timestamp verification |

### 5.4 GDPR Considerations

**Is usage data personal data?**
- CPU/memory metrics alone: Generally no
- With IP addresses: Yes (CJEU Breyer ruling C-582/14)
- With user identifiers: Yes

**Resolution for Tenant Deletion:**
1. Pseudonymize identifying information in billing records
2. Retain pseudonymized records for statutory period
3. Anonymize (irreversibly) after retention expires
4. Document policy in DPA (Data Processing Agreement)

**GDPR Article 17(3) exemption:** Erasure right doesn't apply when data is required for legal obligations (accounting/tax).

### 5.5 VAT Compliance

| Scenario | VAT Treatment |
|----------|---------------|
| Domestic B2B (Germany) | 19% VAT charged |
| Intra-EU B2B | Reverse charge (0% VAT, customer accounts) |
| B2C EU | VAT at seller's country rate (19%) |
| Non-EU B2B | 0% VAT (export) |

**Requirements:**
- Validate customer VAT IDs via VIES or BZSt portal
- Include VAT ID and Leitweg-ID on invoices
- Apply correct VAT category codes in ZUGFeRD

### 5.6 SOC 2 Type II Implications

| Control Area | Billing System Requirement |
|--------------|---------------------------|
| Security | MFA, least privilege access |
| Availability | High-availability architecture |
| Processing Integrity | Rate card change management |
| Confidentiality | Tenant data isolation (RLS) |
| Privacy | Pseudonymization, data minimization |

---

## 6. Competitive Analysis: Synthesized Findings

### 6.1 Direct Competitors

| Competitor | Billing Approach | Strengths | Gaps |
|------------|------------------|-----------|------|
| **Nutanix NCM** | Multi-cloud chargeback, tag-based | Self-service portal | Relies on TCO estimates, not hypervisor metrics |
| **OpenNebula** | Native showback (vCPU + memory rates) | Simple, open-source | No billing/payment integration |
| **Proxmox VE** | No native billing | - | Relies on 3rd party (WHMCS, HostBill) |
| **XenOrchestra** | Resource accounting only | Usage reports | No billing engine |

**DVMM Differentiation:** Precise hypervisor-level metering (not estimates) + ZUGFeRD compliance + event-sourced audit trail.

### 6.2 Public Cloud Patterns Worth Adopting

| Cloud | Feature | DVMM Application |
|-------|---------|------------------|
| **AWS** | Per-second billing | Consider for CI/CD workloads |
| **AWS** | Reserved Instances draw-down | Committed use discount logic |
| **Azure** | Cost Management hierarchy | Tenant → Department → Project |
| **GCP** | Sustained use discounts | Auto-discount for long-running VMs |
| **All** | Budget alerts | Threshold notifications |
| **All** | Anomaly detection | Unusual spending alerts |

### 6.3 FinOps Foundation Guidance

**Showback vs. Chargeback:**
- **Showback** (reporting costs): Mandatory for visibility, builds cost awareness
- **Chargeback** (billing departments): Depends on corporate policy

**Recommendation:** Start with showback to develop allocation methodology, then enable chargeback.

**Shared Cost Allocation Options:**
- Proportional (usage-based distribution)
- Even split
- Direct assignment (primary consumer)
- Fixed allocation (predetermined %)

Document methodology transparently—allocation disputes are common.

---

## 7. Implementation Roadmap: Synthesized Recommendation

All three sources propose a phased approach. The synthesized roadmap:

### Phase 1: MVP (3-4 months)

**Scope:**
- VM lifecycle events via vCenter EventHistoryCollector
- Hourly allocated resource snapshots (vCPU, memory, storage provisioned)
- Allocated-resource billing (tier-based: Small/Medium/Large)
- Monthly billing cycle
- ZUGFeRD 2.1 Basic profile (PDF + XML)

**Data Architecture:**
- TimescaleDB hypertable for usage metrics
- Event sourcing for lifecycle events
- Single billing bounded context

**Invoice:**
- Manual generation trigger
- EUR currency only
- PDF download from portal

**Portal:**
- Basic cost dashboard (current month)
- Invoice history/download
- Usage summary by VM

**Deliverables:**
- [ ] Prometheus/OTel vmware_exporter configured
- [ ] TimescaleDB schema deployed
- [ ] Rate card management (3 tiers)
- [ ] Invoice generation service
- [ ] Tenant portal (basic)

### Phase 2: Growth Features (4-6 months)

**Metering Expansion:**
- Consumed-resource metrics (CPU utilization, memory active)
- Per-VM storage used (not just provisioned)
- Network egress tracking
- 95th percentile option for bandwidth

**Billing Enhancements:**
- Custom rate cards per tenant
- Committed use discounts (1yr, 3yr)
- Burst pricing (base + overage)
- Proration for mid-cycle changes
- Multi-currency (EUR, CHF)

**Compliance:**
- XRechnung profile for B2G
- ZUGFeRD Extended profile
- Automated invoice generation

**Portal:**
- Budget alerts (email notifications)
- Cost breakdown by resource type
- Historical trends
- CSV/XLSX export

**Integrations:**
- REST API for billing data
- Scheduled report delivery
- Basic ERP export (CSV, XML)

### Phase 3: Enterprise-Grade (6-12 months)

**Advanced Metering:**
- Real-time metrics streaming (Kafka)
- Snapshot billing
- License tracking (Windows, SQL Server)
- IOPS/throughput for premium storage

**Billing Sophistication:**
- Tenant hierarchies with inherited/override pricing
- Partner/reseller billing with margins
- Volume-based tiered discounts
- Promotional credits and adjustments
- Dispute workflow with audit trail

**Compliance Hardening:**
- GoBD-compliant archiving
- GDPdU export for tax audits
- SOC 2 Type II evidence collection
- BSI C5 alignment (government customers)

**Integrations:**
- SAP IDoc integration
- Peppol network delivery
- Payment gateway (Stripe/Adyen)
- FinOps tool integration (Apptio, Flexera)

**Portal:**
- 12-month forecasting
- Anomaly detection alerts
- Self-service rate card viewing
- White-label customization

---

## 8. Answers to Specific Questions

### Technical Questions

| Question | Answer |
|----------|--------|
| **1. Minimum viable metering for MVP?** | VM lifecycle events (create/delete, power on/off) + hourly allocated resource snapshots (vCPU count, memory GB, storage provisioned). Calculate billing from allocation, not consumption. |
| **2. Main PostgreSQL vs. separate time-series DB?** | Use **TimescaleDB extension** on existing PostgreSQL. No new technology, same ops model, native RLS. Only consider VictoriaMetrics if >10M active time series. |
| **3. Retroactive pricing change handling?** | Event sourcing enables re-projection: store immutable usage events, re-apply new rate cards. Offer customers choice: prospective only (default) or retrospective with credit/debit (requires approval). |
| **4. Tenant deletion: GDPR vs. accounting?** | Pseudonymize tenant identity, retain for statutory period (8-10 years), then anonymize. Document in DPA. |
| **5. Storage tracking granularity?** | Start VM-level (total provisioned, total used). Add VMDK-level in Phase 2. Track snapshots separately—bill as storage overage or separate line item. |

### Business Model Questions

| Question | Answer |
|----------|--------|
| **1. Common DACH pricing models?** | Hybrid (base allocation + usage overage). Reserved instances with 1-3yr commitments offer 40-60% discounts. Monthly billing standard; annual for committed. |
| **2. Expected billing detail level?** | Line-item by resource type, cost allocation by tag/department/cost center, clear discount/credit/tax separation. Drill-down: Org → Dept → Project → VM. |
| **3. Real-time cost visibility?** | **Must-have** for enterprises >€50K/month. Daily minimum; hourly preferred. Critical for anomaly detection. |
| **4. Budget alerting importance?** | **Critical.** Threshold alerts (50%, 75%, 90%, 100%), anomaly detection, 12-month forecasting, finance system integration. |
| **5. Reseller/partner billing?** | Essential for MSP customers. Multi-tenant billing, per-reseller conditions, white-label invoicing, margin management. |

### Integration Questions

| Question | Answer |
|----------|--------|
| **1. Common DACH billing systems?** | SAP (dominant, 22% European ERP), Microsoft Dynamics, Oracle, Infor. ZUGFeRD/XRechnung export mandatory. DATEV common for accounting. |
| **2. ERP integration as prerequisite?** | **Increasingly yes.** 60%+ German enterprises prioritize integration. Native SAP integration is strongest differentiator. |
| **3. Self-service portal features?** | Real-time dashboards, VM provisioning with cost estimate, usage reports (CSV/XLSX/PDF), budget management, chargeback/showback reports, API access. |
| **4. Competitor data exposure?** | REST APIs with OData (Nutanix), CLI with XML/JSON/CSV (OpenNebula), scheduled reports, Power BI/data lake export. |

---

## 9. Risk Analysis

### Technical Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| vCenter API performance under polling | High | 5-minute minimum intervals, batch collection, use rollups |
| TimescaleDB scaling limits | Medium | Monitor active series count, plan VictoriaMetrics migration path |
| Event sourcing storage growth | Medium | Aggressive compression, data lifecycle policies |
| Metrics data loss | High | Redundant collection, idempotent processing |

### Compliance Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| GDPR vs. accounting retention conflict | Medium | Documented pseudonymization approach in DPA |
| E-invoicing format evolution | Medium | Abstract generation behind interface |
| Tax rate changes | Low | Externalize VAT configuration with effective dating |
| Incorrect VAT application | High | VAT ID validation, tax engine integration |

### Business Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Pricing model mismatch with market | High | Start simple (tiers), add flexibility based on feedback |
| Billing disputes eroding trust | Medium | Immutable event log for audit, clear dispute workflow |
| ERP integration complexity | Medium | Start with file export, add native integration in Phase 3 |

### Vendor Lock-In Risks

| Risk | Mitigation |
|------|------------|
| VMware API dependency | Maintain abstraction layer; OpenTelemetry provides portability |
| TimescaleDB-specific features | Use standard SQL where possible, document extension usage |
| ZUGFeRD library dependency | Evaluate multiple libraries, maintain test suite |

---

## 10. Success Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| **Metering accuracy** | <1% variance | Compare collected vs. actual (audit) |
| **Invoice generation time** | <24 hours from cycle close | Automation monitoring |
| **Portal latency** | <2 seconds dashboard load | Performance monitoring |
| **API availability** | 99.9% uptime | Uptime monitoring |
| **Customer disputes** | <1% of invoices | Support ticket tracking |
| **ERP integration success** | >95% first-attempt import | Integration logs |

---

## Appendix A: UN/CEFACT Unit Code Mapping

| Resource Type | Internal Unit | UN/CEFACT Code | Description |
|---------------|---------------|----------------|-------------|
| Compute Time | Hours | HUR | Hour |
| Duration | Days | DAY | Day |
| Storage Capacity | Gigabyte | E4 | Gigabyte (10^9 bytes) |
| Memory | Gibibyte | G4 | Gigabyte (binary) |
| Count | Unit/Piece | C62 | One (unit) |
| Throughput | Mbit/s | A99 | Bit per second (requires scaling) |

---

## Appendix B: Reference Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────────┐
│                              DVMM Platform                                  │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐                 │
│  │   vCenter    │    │  Prometheus  │    │  TimescaleDB │                 │
│  │              │───▶│  + vmware_   │───▶│  (hypertables│                 │
│  │ Performance  │    │   exporter   │    │   + RLS)     │                 │
│  │   Manager    │    └──────────────┘    └──────┬───────┘                 │
│  └──────────────┘                               │                         │
│         │                                       │                         │
│         │ Events                                ▼                         │
│         │                              ┌──────────────┐                   │
│         ▼                              │   Metering   │                   │
│  ┌──────────────┐                      │   Context    │                   │
│  │  Event Bus   │─────────────────────▶│  (aggregate  │                   │
│  │ (Kafka/RMQ)  │                      │   usage)     │                   │
│  └──────────────┘                      └──────┬───────┘                   │
│                                               │                           │
│                                               ▼                           │
│                                       ┌──────────────┐                   │
│                                       │   Rating     │◀── Rate Cards     │
│                                       │   Engine     │                   │
│                                       └──────┬───────┘                   │
│                                               │                           │
│                                               ▼                           │
│  ┌──────────────┐                     ┌──────────────┐                   │
│  │   Tenant     │◀────────────────────│   Invoice    │                   │
│  │   Portal     │                     │   Service    │                   │
│  │ (dashboards) │                     └──────┬───────┘                   │
│  └──────────────┘                            │                           │
│                                              ▼                           │
│                                    ┌─────────────────┐                   │
│                                    │  ZUGFeRD/       │──▶ SAP/ERP       │
│                                    │  XRechnung      │                   │
│                                    │  Generator      │──▶ PDF/Archive   │
│                                    └─────────────────┘                   │
│                                                                          │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## Appendix C: Key Sources Referenced

### VMware Documentation
- vSphere Performance Manager API (PerfCounterInfo, QueryPerf)
- VMware Aria Operations Chargeback Guide
- vCloud Director Metrics API
- VCF Usage Meter 9.0 Documentation

### Industry Standards
- ZUGFeRD 2.x Specification (German e-invoice)
- XRechnung (German B2G standard)
- UN/CEFACT CII (Core Invoice)
- GoBD (German accounting principles)

### Open Source Projects
- OpenStack CloudKitty (Rating-as-a-Service pattern)
- Kubecost / OpenCost (cost allocation methodology)
- Prometheus vmware_exporter
- OpenTelemetry vCenter Receiver
- Mustang Project (ZUGFeRD library)

### FinOps Guidance
- FinOps Foundation Framework
- Showback vs. Chargeback best practices
- Shared cost allocation methods

---

*Synthesis completed: 2025-12-11*
*Sources: 3 independent research agents*
*Total research coverage: 65+ external references*
