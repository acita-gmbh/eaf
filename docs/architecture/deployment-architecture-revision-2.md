# Deployment Architecture (Revision 2)

(Replaced "Unified Container" strategy).

  * **Strategy:** We will ship **two separate application containers** (a "Two-Container" approach):
    1.  **Frontend Container:** A lightweight NGINX container serving the static (production-built) React-Admin files.
    2.  **Backend Container:** The EAF Spring Boot "Modular Monolith" (containing the Java/Kotlin/Axon/Flowable code).
  * **CI/CD Pipeline:** The GitHub Actions pipeline (Validate Job) runs Testcontainers on `amd64`. The Build Job MUST create multi-arch images (`linux/amd64`, `linux/arm64`, `linux/ppc64le`) for BOTH containers (NGINX and Spring Boot).
  * **HA/DR:** Meets RTO/RPO goals. The FE (NGINX) container is stateless. The BE (Spring) container requires the Active-Passive model, coordinated with Postgres Streaming Replication.

-----
