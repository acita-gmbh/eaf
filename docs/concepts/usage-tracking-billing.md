# Tenant Usage Tracking & Billing

**Turning technical metrics into invoices.**

For a SaaS platform (or internal Chargeback), we need to know exactly who used what, and for how long. We don't just count "Current VMs"—we track usage over time (GB-Hours, vCPU-Hours).

---

## The Metric: Resource-Hours

We measure usage in **Time-Integrated Units**.
*   **vCPU-Hour:** 1 vCPU running for 1 hour.
*   **GB-Hour:** 1 GB of RAM (or Disk) allocated for 1 hour.

**Example:**
A "Large" VM (4 vCPU, 16 GB RAM) running for 24 hours consumes:
*   `4 * 24 = 96 vCPU-Hours`
*   `16 * 24 = 384 GB-Hours`

## The Source: Event Stream

We do not scan the database periodically (polling misses short-lived VMs). We use the **Event Stream**.

1.  **VmProvisioned:** Start Timer.
2.  **VmDecommissioned:** Stop Timer.
3.  **VmResized:** Stop Timer (Old Size), Start Timer (New Size).

## The Aggregation Process

We use a dedicated **Billing Projection**.

1.  **Stream Processor:** Listens to `dvmm.domain.vm.*` events.
2.  **Usage Log:** Appends a record to a `vm_usage_periods` table.
    ```sql
    INSERT INTO vm_usage_periods (vm_id, tenant_id, start_time, end_time, cpu, ram) ...
    ```
3.  **Aggregation:** A nightly job sums up the periods for the day/month.
    *   `SUM(EXTRACT(EPOCH FROM (end_time - start_time)) / 3600 * cpu)`

## Pricing Models

We support flexible pricing strategies per tenant.

1.  **Flat Rate:** €50 / VM / Month.
2.  **Consumption:** €0.05 per vCPU-Hour + €0.01 per GB-Hour.
3.  **Tiered:** First 100GB free, then €0.10/GB.

## Generating Invoices

At the end of the billing cycle (e.g., 1st of month):
1.  Query aggregated usage for Tenant X.
2.  Apply Tenant X's Pricing Model.
3.  Generate PDF Invoice (via PDF engine).
4.  Send email / Webhook to ERP system (SAP/NetSuite).

## Why This Matters

*   **Fairness:** Users only pay for what they use. Deleting a VM stops the charges instantly.
*   **Visibility:** "Showback" reports let managers see exactly which project is burning the budget.
