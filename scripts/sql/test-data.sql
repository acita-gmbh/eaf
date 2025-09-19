-- Sample developer data for local onboarding
INSERT INTO eaf_event.outbox (id, aggregate_id, aggregate_type, payload)
VALUES (
  '00000000-0000-0000-0000-000000000001',
  'seed-tenant',
  'TENANT_PROVISIONED',
  jsonb_build_object('tenantId', 'seed-tenant', 'createdAt', NOW())
)
ON CONFLICT (id) DO NOTHING;
