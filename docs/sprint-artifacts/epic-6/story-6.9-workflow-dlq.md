# Story 6.9: Workflow Dead Letter Queue and Recovery

**Epic:** Epic 6 - Workflow Orchestration
**Status:** TODO
**Related Requirements:** FR007, FR018 (Error Recovery)

---

## 🔄 IMPLEMENTATION NOTE (2025-11-16)

**CRITICAL:** This story should leverage the **generic DeadLetterQueueService** implemented in framework/core as part of OWASP Top 10:2025 compliance (A10:2025 - Exception Handling).

**Generic DLQ Infrastructure Available:**
- `framework/core/src/main/kotlin/com/axians/eaf/framework/core/resilience/dlq/DeadLetterQueueService.kt`
- `framework/web/src/main/kotlin/com/axians/eaf/framework/web/dlq/DeadLetterQueueController.kt`
- REST API: `GET /api/dlq`, `POST /api/dlq/{id}/retry`, `DELETE /api/dlq/{id}`
- Comprehensive test suite with 18+ tests

**Implementation Approach:**
Instead of creating a workflow-specific DLQ, integrate with the generic infrastructure by adding workflow-specific metadata to DLQ entries. See implementation guide in `docs/owasp-top-10-2025-story-mapping.md`.

**Benefits:**
- ✅ Reuse tested infrastructure (112+ tests)
- ✅ Consistent DLQ API across all subsystems
- ✅ OWASP A10:2025 compliance
- ✅ Unified monitoring and metrics

---

## User Story

As a framework developer,
I want dead letter queue for failed workflow messages,
So that Flowable-Axon bridge failures can be investigated and retried.

---

## Acceptance Criteria

1. ✅ Dead letter queue integration for failed Axon commands from BPMN (use generic DeadLetterQueueService)
2. ✅ Failed commands stored with: process instance ID, error details, retry count (as metadata in DLQ entries)
3. ✅ Manual retry API: POST /api/dlq/:id/retry (generic API, workflow-aware retry logic)
4. ✅ Automatic retry with exponential backoff (configurable via ResilientOperationExecutor)
5. ✅ Max retry limit (default: 3) before manual intervention required (use generic DLQ retry configuration)
6. ✅ Integration test validates: command fails → DLQ → manual retry → success
7. ✅ DLQ monitoring metrics and alerts (leverage existing DLQ metrics)

---

## Prerequisites

**Story 6.3** - Axon Command Gateway Delegate

---

## Technical Notes

### Integration with Generic DLQ Service

**framework/workflow/src/main/kotlin/com/axians/eaf/framework/workflow/dlq/WorkflowDlqInterceptor.kt:**
```kotlin
@Component
class WorkflowDlqInterceptor(
    private val dlqService: DeadLetterQueueService,
    private val resilientExecutor: ResilientOperationExecutor
) : MessageHandlerInterceptor<CommandMessage<*>> {

    override fun handle(
        unitOfWork: UnitOfWork<out CommandMessage<*>>,
        interceptorChain: InterceptorChain
    ): Any {
        return resilientExecutor.execute(
            operation = "workflow-command-${unitOfWork.message.commandName}",
            block = { interceptorChain.proceed() },
            onFailure = { ex ->
                // Enqueue to generic DLQ with workflow-specific metadata
                dlqService.enqueue(
                    operation = "workflow-command",
                    payload = unitOfWork.message.payload,
                    error = ex,
                    metadata = mapOf(
                        "commandName" to unitOfWork.message.commandName,
                        "processInstanceId" to getProcessInstanceId(unitOfWork),
                        "executionId" to getExecutionId(unitOfWork),
                        "activityId" to getActivityId(unitOfWork)
                    )
                )
            }
        )
    }
}
```

### Workflow-Aware Retry Logic

```kotlin
@Component
class WorkflowDlqRetryHandler(
    private val commandGateway: CommandGateway,
    private val runtimeService: RuntimeService
) {

    fun retryWorkflowCommand(dlqEntry: DeadLetterQueueEntry): Either<DomainError, Unit> = either {
        val processInstanceId = dlqEntry.metadata["processInstanceId"] as? String
            ?: return DomainError.ValidationError("Missing processInstanceId").left()

        // Check if process instance still exists
        val processInstance = runtimeService.createProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult()
            ?: return DomainError.NotFoundError("Process instance not found").left()

        // Retry command via Axon
        commandGateway.send<Any>(dlqEntry.payload)

        Unit.right()
    }
}
```

---

## References

- PRD: FR007, FR018
- Tech Spec: Section 3 (FR007 - DLQ, FR018 - Retry strategies)
- **Generic DLQ Implementation:** `docs/owasp-top-10-2025-story-mapping.md`
- **OWASP Compliance:** A10:2025 - Exception Handling
