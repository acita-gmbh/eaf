# Dependency Lifecycle Management

  * **Version Cadence:** Renovate automation raises weekly patch/minor updates. We conduct quarterly dependency retrospectives and plan major upgrades annually with compatibility testing.
  * **Compatibility Matrix:** `docs/architecture/compatibility-matrix.md` codifies supported combinations (e.g., Spring Boot 3.3.5 + Axon 4.9.4 + Kotlin 2.0.10). Deprecated stacks receive two release cycles of notice before removal.
  * **Licensing Compliance:** CI produces CycloneDX SBOMs; legal reviews new dependencies quarterly. GPL/SSPL packages are blocked without executive exemption.
  * **Fallback Strategies:** Keycloak upgrades execute with blue/green realms; Flowable runs in dual-write mode for one release prior to cutover; Redis deployments leverage Sentinel failover rehearsed monthly.
  * **Emergency Patching:** Critical CVEs trigger the hotfix playbook—branch from production tag, apply patch, run `./gradlew clean build` plus targeted regression suites, deploy via expedited pipeline, then backport to main.

-----
