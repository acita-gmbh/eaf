# Database Schema

(Logical separation of the single Postgres instance).

1.  **`flowable` schema:** Auto-managed by the Flowable engine (Epic 7.1).
2.  **`eaf_event_store` schema (Axon):** Standard Axon DDL (e.g., `domain_event_entry`), **modified** to add the mandatory `tenant_id` column required for RLS (Epic 4.3).
3.  **`eaf_projections` schema (Read Models):** Custom, denormalized tables (managed by jOOQ) for our projections:
      * `tenant_projection` (Strict RLS)
      * `product_projection` (Global-Read / Admin-Write RLS Policy)
      * `license_projection` (Strict RLS, includes `license_limits JSONB` column)

-----
