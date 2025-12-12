# GDPR & Crypto-Shredding

**How to delete data from an immutable history.**

DVMM uses **Event Sourcing**, meaning we store a permanent history of everything that happened. This is great for auditing (ISO 27001), but it conflicts with **GDPR Article 17 (Right to Erasure)**.

*   **The Conflict:** GDPR says "Delete user data." Event Sourcing says "Never delete history."
*   **The Solution:** Crypto-Shredding.

---

## The Concept

Instead of storing personal data (PII) like `email`, `username`, or `ip_address` in plain text, we encrypt it.

1.  **Keys:** Each User has a unique encryption key (`UserKey`).
2.  **Storage:** The Event Store contains encrypted blobs (ciphertext).
3.  **Access:** When we read an event, we fetch the `UserKey` and decrypt the data on the fly.

## The "Deletion" Process

When a user exercises their Right to Erasure:

1.  We do **not** touch the Event Store (it remains immutable).
2.  We **delete the UserKey**.

Without the key, the encrypted data in the Event Store becomes mathematically irretrievable garbage. It is effectively "erased" according to GDPR standards, but the *structure* of the event (timestamp, aggregate ID, event type) remains intact for auditing integrity.

```mermaid
flowchart LR
    Event[Event: UserRegistered] -->|Contains| Encrypted[Encrypted PII]
    Encrypted -.->|Decrypts with| Key[User Key 🔑]
    Key -->|Result| Plaintext[john.doe@example.com]
    
    Command[Delete User] -->|Destroys| Key
    
    Key -.->|Missing| Encrypted
    Encrypted -->|Result| Garbage[?????????]
```

## What About the Read Models?

The Read Models (Projections) are just standard SQL tables (`users`, `vm_requests`). Since these are mutable, we simply run a standard `DELETE` or `UPDATE` to anonymize the rows.

*   **Event Store:** Crypto-shredded (Key deleted).
*   **Projections:** Rows deleted/anonymized.
*   **Backups:** The backups contain the encrypted events. Since the key is gone (and keys are excluded from long-term backups or rotated), the backups are also compliant.

## Implementation Details

*   **Encryption Algorithm:** AES-256-GCM (Authenticated Encryption).
*   **Key Storage:** A separate, highly secured Key Vault (or a dedicated database table with strict access controls).
*   **Performance:** Encryption is fast on modern CPUs. The overhead is negligible compared to network latency.

This approach satisfies both the Auditor (who wants a complete history of *what* happened) and the Privacy Regulator (who wants personal data to be removable).
