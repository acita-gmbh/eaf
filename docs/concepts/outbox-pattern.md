# The Outbox Pattern

**Guaranteed event delivery, even when things break.**

In a distributed system, we often need to do two things at once:
1.  Save data to our local database (e.g., "Create User").
2.  Publish an event to the world (e.g., "Send 'UserCreated' message").

**The Problem:** We cannot do both atomically if the "world" is an external Message Broker (like Kafka or RabbitMQ).
*   If we save to DB first, then publish fails... the message is lost.
*   If we publish first, then save to DB fails... we sent a phantom message.

**The Solution:** The Transactional Outbox Pattern.

---

## How It Works

Instead of publishing directly to the broker, we publish to a local database table called `outbox` **in the same transaction** as our business data.

```sql
BEGIN TRANSACTION;
  -- 1. Save the business data (The Event)
  INSERT INTO events (id, type, payload) VALUES (..., 'UserCreated', ...);

  -- 2. Save the intent to publish (The Outbox Record)
  INSERT INTO outbox (event_id, published) VALUES (..., FALSE);
COMMIT;
```

Since both are SQL `INSERT`s in the same database, they either **both succeed** or **both fail**. Atomicity is guaranteed.

## The Poller (The Mailman)

A separate background process (the "Poller") runs continuously.

1.  **Poll:** `SELECT * FROM outbox WHERE published = FALSE ORDER BY created_at ASC LIMIT 50`.
2.  **Publish:** For each record, it sends the event to the external system (Message Broker, or in our case, simply notifying other internal components/projections).
3.  **Ack:** If successful, it updates the record: `UPDATE outbox SET published = TRUE WHERE id = ...`.

## Resilience

*   **Crash Recovery:** If the Poller crashes halfway through, the transaction rolls back or the flag stays `FALSE`. When it restarts, it picks up right where it left off.
*   **At-Least-Once Delivery:** It is possible to crash *after* publishing but *before* updating the DB. In this case, the Poller will re-send the message. Consumers must be **idempotent** (handle duplicates gracefully).

## Why We Use It

DVMM uses this to ensure that **Domain Events** (our source of truth) are reliably propagated to:
1.  **Projections:** Read models must eventually match the write model.
2.  **Notifications:** We don't want to lose "VM Ready" emails.
3.  **External Integrations:** If we ever add a message bus, we are ready.

## Key Takeaways

*   **Atomicity:** DB + Outbox = Single Transaction.
*   **Reliability:** No events are ever lost.
*   **Trade-off:** Minimal latency (polling delay) for massive reliability.
