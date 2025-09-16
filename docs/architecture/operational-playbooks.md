# Operational Playbooks

## Deployment Pipeline

  * GitHub Actions workflow stages: **Build** (compile, lint, test), **Image Publish** (multi-arch Docker build), **Staging Deploy** (docker compose target with smoke tests), **Production Approval** (manual gate), **Production Deploy** (rolling update, post-deploy verification).
  * Each stage uploads artefacts (SBOM, test reports) to enable traceability and audit.

## Environment Promotion

  * Environments: `dev` (shared), `staging` (release candidate), `prod` (customer). Promotion requires green integration tests plus manual QA sign-off documented in PR notes.
  * Database migrations run via Flyway with the `baselineOnMigrate` flag; staging migrations execute 24 h before production to catch schema drift.

## Rollback & Recovery

  * Playbook distinguishes configuration rollback (redeploy previous Helm/compose config) from code rollback (redeploy previous image tag). PITR (Point-In-Time Recovery) for Postgres is rehearsed quarterly; recovery point objective 15 minutes, recovery time objective 4 hours.
  * License issuance commands replay automatically after recovery via Axon tracking tokens.

## Infrastructure as Code

  * Terraform modules describe Docker hosts, Vault, Redis, Postgres, and monitoring stack. CI runs `terraform fmt`, `validate`, and `tflint` before applying changes.
  * State stored in Terraform Cloud with workspace-per-environment, guarded by Sentinel policies (e.g., forbid public security groups).

## Runbook Library & Escalation

  * `docs/runbooks/` contains operator guides for Keycloak outage, projection lag, Postgres failover, and Vault token exhaustion.
  * Escalation matrix: L1 (on-call engineer) -> L2 (platform specialist) -> L3 (vendor liaison). SLA: acknowledge P1 incidents within 15 minutes, resolve within 4 hours.

-----
