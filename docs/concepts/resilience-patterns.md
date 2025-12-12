# Resilience Patterns

**Building a system that bends but doesn't break.**

Distributed systems fail. Networks glitch, databases stall, and third-party APIs (like VMware) go offline. If we don't plan for this, a single failing component can bring down the entire application (Cascading Failure).

We use **Resilience4j** to implement standard stability patterns.

---

## 1. Circuit Breaker

**Stop beating a dead horse.**

If a service (e.g., VMware API) is failing repeatedly, stop calling it.

*   **Closed State:** Normal operation. Calls go through.
*   **Open State:** Failure rate exceeded threshold (e.g., 50%). Calls fail *immediately* with a `CallNotPermittedException`. This gives the failing system time to recover.
*   **Half-Open State:** After a wait time, allow a few "test" calls. If they pass, close the circuit. If they fail, open it again.

**DCM Use Case:** Wraps all calls to `VsphereClient`. If vCenter dies, we stop hammering it and queue requests instead.

## 2. Retry

**Try again, maybe it was a blip.**

For transient errors (network glitch, timeout), a simple retry often works.

*   **Policy:** 3 attempts.
*   **Backoff:** Exponential (Wait 1s, then 2s, then 4s). This prevents us from overwhelming a struggling service.
*   **Jitter:** Add random noise to the wait time so all our threads don't retry at the same millisecond (Thundering Herd).

**DCM Use Case:** Database connection drops, temporary DNS failures.

## 3. Timeout

**Don't wait forever.**

Every external call must have a deadline. If it takes too long, abort.

*   **Rule:** Set timeouts shorter than the user's patience. If the user waits 5s, the DB call shouldn't timeout after 30s.
*   **Layering:** Outer timeout > Sum of Inner timeouts.

**DCM Use Case:** All HTTP client calls, Database queries.

## 4. Bulkhead

**Don't let one leak sink the ship.**

Isolate resources so a failure in one area doesn't consume everything.

*   **Concept:** Limit the number of concurrent calls to a specific service.
*   **Example:** Only allow 10 concurrent calls to the "Reporting Service". If 10 are running, the 11th fails immediately. This preserves threads for critical functions like "Login".

**DCM Use Case:** "Separating 'Admin Reporting' threads from 'User Login' threads."

---

## Summary Table

| Pattern | Problem Solved | Behavior |
| :--- | :--- | :--- |
| **Circuit Breaker** | System Overload / Cascading Failure | Stop calling failing service |
| **Retry** | Transient Glitches | Try again with delay |
| **Timeout** | Indefinite Waiting | Abort if too slow |
| **Bulkhead** | Resource Exhaustion | Limit concurrency per feature |
