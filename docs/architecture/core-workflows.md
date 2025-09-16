# Core Workflows

### Flow 1: CQRS "Walking Skeleton" (Create Widget & Query)

  * **Summary:** Illustrates the core end-to-end CQRS pattern, showing the decoupled Write Side (POST $\rightarrow$ 202 Accepted $\rightarrow$ Event Store) and Read Side (Async Projection $\rightarrow$ Read DB $\rightarrow$ GET Query).
  * **Risk Mitigation:** Identifies the need for the Frontend to handle Eventual Consistency (via WebSocket push).

### Flow 2: Tenant Creation Saga (Error & Compensation Path)

  * **Summary:** Illustrates our most complex distributed transaction, managed by Flowable.
  * **Flow:** API $\rightarrow$ Start BPMN $\rightarrow$ Task 1 (Axon Command: Create Tenant) $\rightarrow$ Task 2 (Call Keycloak Admin API).
  * **Mitigation:** If Task 2 fails, the BPMN Error Event triggers Task 3 (Compensating Axon Command: MarkTenantFailed), ensuring data consistency.

### Flow 3: Dockets/Flowable Orchestration (Happy Path)

  * **Summary:** Visualizes the Post-MVP replacement pattern for Dockets.
  * **Flow:** BPMN orchestrates: Task 1 (Ansible PRESCRIPT) $\rightarrow$ Task 2 (Axon CORECOMMAND) $\rightarrow$ (Wait for Event) $\rightarrow$ Task 3 (Ansible POSTSCRIPT). This matches the legacy requirement.

-----
