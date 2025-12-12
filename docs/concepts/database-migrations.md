# Database Migration Strategy

**Changing the engine while the plane is flying.**

Database schemas evolve. We add tables, rename columns, and change types. We use **Flyway** to manage these changes safely and predictably.

---

## How Flyway Works

Flyway looks for SQL files in a specific folder. Each file has a version number.
1.  Check `flyway_schema_history` table in DB.
2.  See that we are at Version 3.
3.  Find all files with Version > 3 (e.g., `V4__add_users.sql`).
4.  Run them in order.
5.  Update history table.

## Naming Convention

`V<Version>__<Description>.sql`

*   `V001__initial_schema.sql`
*   `V002__add_vm_table.sql`
*   `V003__add_tenant_id_to_vms.sql`

## Zero-Downtime Migrations

In production, we cannot stop the app to upgrade the DB. The old app code must run against the new DB schema for a short time during deployment.

**The Golden Rule:** All changes must be **Backwards Compatible**.

### Example: Renaming a Column
**Bad:** `ALTER TABLE users RENAME COLUMN name TO full_name;`
*   *Crash!* The running app is still trying to read `name`.

**Good (The 5-Step Process):**
1.  **V1:** Add new column `full_name`. (App ignores it).
2.  **Code Change:** Update App to write to *both* `name` and `full_name`, but read from `name`.
3.  **V2 (Data Migration):** Copy all existing data from `name` to `full_name` (e.g., `UPDATE users SET full_name = name WHERE full_name IS NULL;`).
4.  **Code Change:** Switch App to read/write `full_name`.
5.  **V3:** Remove old column `name`.

## Testcontainers Integration

We don't trust migrations until we run them. In our tests, **Testcontainers**:
1.  Spins up a fresh, empty PostgreSQL Docker container.
2.  Runs Flyway to apply *all* migrations from V1 to Latest.
3.  Runs the tests.

This guarantees that our migrations are syntactically correct and result in the expected schema.

## Summary

1.  **Immutable Scripts:** Never change a V-script after it has been merged. Create a new one.
2.  **No Downward Migrations:** We don't support "Undo". Roll forward (fix the bug in a new migration).
3.  **Local Dev:** `./gradlew flywayMigrate` applies changes to your local Docker DB.
