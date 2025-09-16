# Resilience and Performance Engineering

## Reliability Patterns

  * **Retries & Backoff:** All outbound adapters (Keycloak Admin API, Redis, Ansible runners) employ Spring Retry with jittered exponential backoff (max 5 attempts, base 250 ms, cap 30 s). Failures publish `IntegrationFailureEvent` records for operator awareness.
  * **Circuit Breaking:** Resilience4j circuit breakers guard Keycloak and Redis access (slow-call rate threshold 50% over 10 s, wait duration in open state 20 s). Breaker metrics surface via `/actuator/health` and Prometheus.
  * **Graceful Degradation:** When projections lag, the frontend displays cached read models stamped with `lastProjectionAt`. Commands acknowledged with HTTP 202 render "pending" status banners until projections refresh.

## Capacity Targets

  * SLO: 200 concurrent operator sessions, 50 sustained writes/sec, 500 sustained reads/sec.
  * Load testing: Gatling suite runs nightly against staging; regressions flagged when 95th percentile latency exceeds 350 ms for read endpoints or 500 ms for command submissions.
  * Baseline sizing: Backend container 2 vCPU / 4 GB RAM; frontend container 0.5 vCPU / 512 MB; Postgres primary 4 vCPU / 8 GB with streaming replica.

## Caching & Scaling

  * Redis-backed projection cache (TTL 5 s) accelerates dashboard queries while preserving eventual consistency semantics.
  * Horizontal scale via active-passive pairs behind Traefik; failover promoted within 60 s leveraging Postgres replication slots.
  * Background jobs record resource utilisation to inform autoscaling thresholds for customers who deploy to orchestration platforms.

## Operational Benchmarks

  * Circuit-breaker open events trigger PagerDuty if five occurrences happen within 15 minutes.
  * Projection lag >15 s raises warning; >60 s becomes a critical alert.
  * Compose stack includes `otel-collector` to capture performance traces for SLA debugging.

-----
