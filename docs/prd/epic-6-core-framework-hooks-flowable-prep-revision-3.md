# Epic 6: Core Framework Hooks (Flowable Prep) (Revision 3)

**Epic Goal:** This epic replaces the core risk of the legacy "Dockets" engine. It integrates the **Flowable BPMN engine** into the EAF stack, providing a robust, industry-standard workflow platform. This epic delivers the core engine integration and the adapters needed for Flowable to communicate with Axon and Ansible.

### Story 6.1: Integrate Flowable Engine & Database Schema
* **As a** Core Developer, **I want** the Flowable BPMN Engine libraries integrated into the stack and configured to use our primary PostgreSQL database, **so that** we have a foundational workflow capability managed within our single database instance.
* **AC 1:** Required Flowable Spring Boot Starter dependencies are added via convention plugins.
* **AC 2:** Flowable is configured to use the main PostgreSQL database (from Story 1.3) but in its own dedicated schema (e.g., `flowable`).
* **AC 3:** On application startup, Flowable correctly runs its migration scripts (or validates its schema) in Postgres.
* **AC 4:** An integration test confirms the Flowable Engine beans (e.g., `ProcessEngine`, `RuntimeService`) are correctly initialized in the Spring context.

### Story 6.2: Create Flowable-to-Axon Bridge (Command Dispatch)
* **As a** BPMN Process, **I want** to execute a Java Delegate (Service Task) that dispatches an Axon Command via the `CommandGateway`, **so that** a workflow can initiate business logic in our CQRS aggregates.
* **AC 1:** A reusable Java Delegate (e.g., `DispatchAxonCommandTask`) is created that can be configured in a BPMN model.
* **AC 2:** The task correctly retrieves variables from the Flowable context and uses them to build and dispatch a valid Axon Command (e.g., `CreateWidgetCommand`).

### Story 6.3: Create Axon-to-Flowable Bridge (Event Signal)
* **As a** BPMN Process, **I want** to pause and wait for a specific Axon Event, **so that** the workflow can react to business logic that has successfully completed.
* **AC 1:** A standard Axon Event Handler (Projection) is created that listens for specific events (e.g., `WidgetCreatedEvent`).
* **AC 2:** The handler correctly correlates the event to the running BPMN process (perhaps via an ID) and uses the Flowable `RuntimeService` to send a message or signal to a waiting process instance.
* **AC 3:** Integration tests confirm a BPMN process (with a "Receive Event Task") successfully pauses and resumes when the corresponding Axon event is published.

### Story 6.4: Create Ansible Service Task Adapter
* **As a** Core Developer, **I want** a custom Flowable Java Delegate (Service Task) that can execute Ansible playbooks, **so that** we can replicate the core function of the legacy Dockets system.
* **AC 1:** A new Service Task (e.g., `RunAnsiblePlaybookTask`) is created.
* **AC 2:** This task is configurable via the BPMN model (e.g., passing in the playbook name, inventory, and variables).
* **AC 3:** This task utilizes the connection configuration and execution logic identified in the Dockets Analysis (connecting via SSH, running playbooks).
* **AC 4:** Integration tests confirm this service task can successfully execute a simple "hello world" Ansible playbook.

### Story 6.5: Implement Workflow Error Handling (Compensating Actions)
* **As a** Core Developer, **I want** the framework to support compensating commands for failed workflows, **so that** we can manage the distributed transaction risk identified in our analysis (Risk Assessment 5).
* **AC 1:** A "compensating command" (e.g., `CancelWidgetCreationCommand`) and corresponding event handler are added to the Widget aggregate (from Epic 2).
* **AC 2:** A BPMN "Error Boundary Event" is configured in the workflow.
* **AC 3:** If a downstream step (like the Ansible Task) fails, the BPMN error path must successfully trigger the `DispatchAxonCommandTask` (from 6.2) to send the `CancelWidgetCreationCommand`, reversing the initial transaction.

### Story 6.6: Implement "Dockets Pattern" BPMN Template
* **As a** Core Developer, **I want** a template BPMN 2.0 XML file that replicates the legacy "Dockets Hook" pattern, **so that** we have a clear path for migrating DPCM/ZEWSSP automation.
* **AC 1:** A BPMN 2.0 XML file is created that defines a workflow: (1) Start Event, (2) Service Task (Ansible PRESCRIPT), (3) Service Task (Dispatch Axon CORECOMMAND), (4) Receive Event Task (wait for Axon Event), (5) Service Task (Ansible POSTSCRIPT), (6) Error Boundary Event (Compensation), (7) End Event.
* **AC 2:** This BPMN process definition successfully deploys to the Flowable engine.

---
