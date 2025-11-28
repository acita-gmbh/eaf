# Sicherheitspartnerschaft Briefing: EAF/DVMM Projekt

**Erstellt für:** Strong IT SOC Team
**Datum:** 2025-11-28
**Klassifizierung:** Intern
**Version:** 1.0

---

## Zusammenfassung

Dieses Dokument bietet einen umfassenden Überblick über das **EAF (Enterprise Application Framework)** und **DVMM (Dynamic Virtual Machine Manager)** Projekt, um unserem internen SOC-Team die Evaluierung von Möglichkeiten zur Sicherheitspartnerschaft zu ermöglichen.

**Eckdaten:**
- **Produkttyp:** Multi-Tenant B2B SaaS für VMware VM-Provisionierung
- **Zielmarkt:** Deutscher Mittelstand (500-5.000 Mitarbeiter), CSPs
- **Compliance-Ziele:** ISO 27001, DSGVO
- **Tech-Stack:** Kotlin 2.2, Spring Boot 3.5, PostgreSQL 16, Keycloak, VMware vSphere API
- **Architekturmuster:** CQRS + Event Sourcing, Hexagonale Architektur

**Primäre Sicherheitsthemen:**
1. Multi-Tenant Datenisolierung (PostgreSQL RLS)
2. Keycloak OIDC-Integration und JWT-Handling
3. VMware API-Sicherheit (Credential-Management, Netzwerkisolierung)
4. DSGVO-konforme Audit-Trails mit Crypto-Shredding
5. CI/CD Sicherheits-Pipeline

---

## 1. Projektübersicht

### 1.1 Was ist DVMM?

DVMM ist ein **Self-Service-Portal**, das Folgendes ermöglicht:
- Endbenutzer können virtuelle Maschinen über eine Weboberfläche anfordern
- IT-Administratoren können Anfragen über Workflows genehmigen/ablehnen
- Automatisierte VM-Provisionierung auf VMware vSphere
- Vollständiger Audit-Trail für Compliance (ISO 27001)

**Kern-Workflow:**
```text
Benutzeranfrage → Genehmigungsworkflow → VM provisioniert → Benachrichtigung
       ↓                  ↓                     ↓                ↓
   Formular-UI      Admin-Dashboard       VMware API        E-Mail/Portal
```

### 1.2 Warum dieses Projekt wichtig ist

| Treiber | Auswirkung |
|---------|------------|
| **VMware-Migrationswelle** | 74% der IT-Leiter suchen Alternativen nach Broadcom-Übernahme |
| **Compliance-Anforderungen** | ISO 27001-Zertifizierung durch Legacy-System blockiert |
| **Multi-Tenant SaaS** | Mehrere Kunden mit strikter Datenisolierung bedienen |
| **Marktchance** | DACH SAM: 280-420 Mio. € |

### 1.3 EAF Framework

DVMM basiert auf einem wiederverwendbaren **Enterprise Application Framework (EAF)** mit:
- `eaf-core` - Domain-Primitive (reines Kotlin, keine Abhängigkeiten)
- `eaf-eventsourcing` - Event Store, Projektionen, Snapshots
- `eaf-tenant` - Multi-Tenancy mit PostgreSQL RLS
- `eaf-auth` - IdP-agnostische Authentifizierungsschnittstellen
- `eaf-audit` - Audit-Trail mit Crypto-Shredding-Utilities *(geplant)*

---

## 2. Sicherheitsarchitektur Übersicht

### 2.1 Angriffsfläche auf hoher Ebene

```text
┌─────────────────────────────────────────────────────────────────┐
│                        ANGRIFFSFLÄCHE                           │
├─────────────────────────────────────────────────────────────────┤
│  Internet                                                       │
│     │                                                           │
│     ▼                                                           │
│  ┌─────────────┐     ┌─────────────┐     ┌─────────────┐       │
│  │   WAF/CDN   │────▶│   Keycloak  │────▶│   DVMM API  │       │
│  │  (Geplant)  │     │    (OIDC)   │     │  (Spring)   │       │
│  └─────────────┘     └─────────────┘     └─────────────┘       │
│                                                │                │
│                                                ▼                │
│                           ┌─────────────────────────────────┐  │
│                           │         PostgreSQL              │  │
│                           │   (RLS + Verschlüsselt)         │  │
│                           └─────────────────────────────────┘  │
│                                                │                │
│                                                ▼                │
│                           ┌─────────────────────────────────┐  │
│                           │         VMware vSphere          │  │
│                           │   (Isoliertes Netzwerksegment)  │  │
│                           └─────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 Asset-Klassifizierung

| Asset | Klassifizierung | Beschreibung |
|-------|-----------------|--------------|
| VM-Anfragedaten | Vertraulich | Benutzeranfragen, Begründungen, Genehmigungen |
| Benutzer-PII | Personenbezogen | Namen, E-Mails, Audit-Trail |
| Mandantenkonfiguration | Vertraulich | VMware-Zugangsdaten, Kontingente |
| VMware-Zugangsdaten | **Geheim** | Service-Account-Passwörter |
| JWT-Token | **Geheim** | Authentifizierungstoken |
| Event Store | Vertraulich | Vollständige Audit-Historie (7 Jahre) |

### 2.3 Bedrohungsakteure

| Akteur | Motivation | Fähigkeit |
|--------|------------|-----------|
| Externer Angreifer | Datendiebstahl, Ransomware | Hoch (automatisierte Tools) |
| Böswilliger Mandant | Zugriff auf andere Mandantendaten | Mittel (authentifiziert) |
| Insider-Bedrohung | Datenexfiltration | Hoch (legitimer Zugang) |
| Kompromittierter Admin | Privilegienmissbrauch | Hoch (erhöhter Zugang) |

### 2.4 STRIDE-Analyse (Implementiert)

| Bedrohung | Komponente | Aktuelle Gegenmaßnahme |
|-----------|------------|------------------------|
| **S**poofing | Authentifizierung | Keycloak OIDC, JWT-Validierung, keine lokalen Passwörter |
| **T**ampering | Event Store | Append-only Store, kein UPDATE/DELETE |
| **R**epudiation | Audit-Trail | Unveränderliche Events mit Korrelations-IDs |
| **I**nformation Disclosure | Multi-Tenancy | PostgreSQL RLS, Fail-Closed |
| **D**enial of Service | API | Rate Limiting (100 Req/Min/Benutzer) |
| **E**levation of Privilege | Autorisierung | RBAC, Prinzip der minimalen Rechte |

---

## 3. Detaillierte Sicherheitskontrollen

### 3.1 Authentifizierung (Keycloak OIDC)

**Aktuelle Implementierung:**
```text
Benutzer → Keycloak Login → JWT-Token → DVMM API-Validierung
              ↓
        MFA (optional)
        LDAP/AD (optional)
```

**JWT-Token-Struktur:**
```json
{
  "sub": "user-uuid",
  "tenant_id": "tenant-uuid",
  "email": "user@example.com",
  "roles": ["user", "admin"],
  "iat": 1700000000,
  "exp": 1700003600
}
```

**Token-Handling:**
| Aspekt | Implementierung |
|--------|-----------------|
| Speicherung | httpOnly Cookie (Secure, SameSite=Lax) |
| Access-Token-Ablauf | 1 Stunde |
| Refresh-Token-Ablauf | 8 Stunden |
| Validierung | Bei jeder Anfrage (Signatur + Ablauf) |
| CSRF-Schutz | X-CSRF-Token Header |

**Überprüfung erforderlich:**
- [ ] Keycloak-Härtungskonfiguration
- [ ] Token-Rotationsstrategie
- [ ] Session-Management über Geräte hinweg
- [ ] MFA-Durchsetzungsrichtlinien

### 3.2 Multi-Tenant-Isolierung (PostgreSQL RLS)

**Defense in Depth - 3 Schichten:**

| Schicht | Mechanismus | Fehlerverhalten |
|---------|-------------|-----------------|
| Anwendung | TenantContext (Kotlin Coroutine) | Exception wird geworfen |
| Service | Mandantenfilter bei allen Abfragen | Leere Ergebnismenge |
| Datenbank | PostgreSQL RLS-Policy | Null Zeilen (Fail-Closed) |

**RLS-Implementierung:**
```sql
-- RLS auf allen Mandantentabellen aktivieren
ALTER TABLE vm_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE vm_requests FORCE ROW LEVEL SECURITY;

-- Fail-Closed Policy erstellen
CREATE POLICY tenant_isolation ON vm_requests
    FOR ALL
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- Verbindungskontext (pro Anfrage gesetzt, NICHT im Connection Pool Init)
-- Hinweis: SET LOCAL unterstützt keine Bind-Parameter. UUID wird im App-Code
-- validiert und sicher quotiert. Beispiel mit validierter tenantId:
SET LOCAL app.tenant_id = '123e4567-e89b-12d3-a456-426614174000';
```

**Kritische Designentscheidung:**
> Der Mandantenkontext wird über `SET LOCAL` pro Anfrage im WebFilter gesetzt, NICHT über die Connection-Pool-Initialisierung. Dies verhindert mandantenübergreifende Datenlecks bei gepoolten Verbindungen.

**Überprüfung erforderlich:**
- [ ] RLS-Policy-Vollständigkeitsaudit
- [ ] Mandantenübergreifende Abfragetests
- [ ] Mandantenkontextpropagierung bei asynchronen Operationen
- [ ] Connection-Pool-Sicherheitsüberprüfung

### 3.3 Autorisierung (RBAC)

**Rollenmatrix:**

| Rolle | Berechtigungen | Geltungsbereich |
|-------|----------------|-----------------|
| **Benutzer** | Anfragen erstellen, eigene Anfragen anzeigen | Nur eigene Daten |
| **Admin** | Genehmigen/Ablehnen, Projekte verwalten, Benutzer verwalten | Mandantenweit |
| **Manager** | Berichte, Audit-Logs, Compliance-Dashboards | Mandantenweit (lesend) |

**Berechtigungsdurchsetzungsschichten:**
1. **Controller:** `@PreAuthorize("hasRole('ADMIN')")`
2. **Service:** `TenantContext.current()` erzwungen
3. **Datenbank:** RLS-Policy aktiv

**Überprüfung erforderlich:**
- [ ] Sicherheit des Rollenzuweisungs-Workflows
- [ ] Tests zur Privilegieneskalation
- [ ] Durchsetzung von Berechtigungsgrenzen

### 3.4 Datenschutz

**Verschlüsselungsstandards:**

| Datenzustand | Methode | Schlüsselverwaltung |
|--------------|---------|---------------------|
| Während Übertragung | TLS 1.3 | Let's Encrypt / Interne CA |
| Im Ruhezustand (DB) | AES-256 | OS-Ebene (dm-crypt/LUKS) |
| Geheimnisse | AES-256-GCM | Umgebungsvariablen/Vault |
| PII in Events | AES-256 | Pro-Benutzer-Schlüssel (Crypto-Shredding) |

**Crypto-Shredding-Muster (DSGVO-Konformität):**

```text
┌─────────────────┐     ┌─────────────────┐
│   Event Store   │     │ Schlüsselspeicher│
├─────────────────┤     ├─────────────────┤
│ aggregate_id    │     │ user_id (PK)    │
│ event_type      │     │ encryption_key  │
│ encrypted_pii   │────▶│ created_at      │
│ metadata        │     │ destroyed_at    │
└─────────────────┘     └─────────────────┘
                              │
                   Schlüssel zerstört (DSGVO Art. 17)
                              │
                    encrypted_pii = unlesbarer Datenmüll
```

**DSGVO-Löschprozess:**
1. Benutzer fordert Löschung an (Art. 17)
2. System zerstört den Verschlüsselungsschlüssel des Benutzers
3. PII in Events wird unlesbar
4. Audit-Struktur bleibt erhalten (Compliance)
5. Berichte zeigen "[DSGVO GELÖSCHT]"

**Überprüfung erforderlich:**
- [ ] Überprüfung der Schlüsselverwaltungsimplementierung
- [ ] Schlüsselrotationsverfahren
- [ ] Crypto-Shredding-Implementierungsaudit
- [ ] TLS-Konfigurationshärtung

### 3.5 VMware-Integrationssicherheit

**Credential-Management:**
| Geheimnistyp | Aktuelle Speicherung | Rotation |
|--------------|----------------------|----------|
| VMware-Service-Account | Verschlüsselte DB-Spalte | Manuell (Admin) |
| VMware-API-Zertifikate | Umgebungsvariablen/Vault | Bei Kompromittierung |

**Netzwerkisolierung:**
- VMware vSphere in isoliertem VLAN
- Kein direkter Internetzugang
- DVMM API ist einziges Gateway

**Überprüfung erforderlich:**
- [ ] Sicherheit der VMware-Credential-Speicherung
- [ ] Überprüfung der API-Aufruf-Authentifizierung
- [ ] Validierung der Netzwerksegmentierung
- [ ] Privileged Access Management für VMware-Accounts

### 3.6 API-Sicherheit

| Kontrolle | Implementierung |
|-----------|-----------------|
| Authentifizierung | Bearer JWT (erforderlich) |
| Rate Limiting | 100 Req/Min/Benutzer (WebFilter) |
| Eingabevalidierung | Bean Validation + Custom Validators |
| Ausgabe-Encoding | Jackson-Standardeinstellungen |
| CORS | Nur Whitelist-Origins |
| CSRF | Token-basierter Schutz |

**Fehlerbehandlung (keine Informationslecks):**
```json
{
    "error": "VALIDATION_FAILED",
    "message": "Ungültige Eingabe",
    "correlationId": "abc-123"
    // KEINE Stack-Traces, KEINE internen Details
}
```

**Überprüfung erforderlich:**
- [ ] API-Endpoint-Sicherheitsaudit
- [ ] Vollständigkeit der Eingabevalidierung
- [ ] Effektivität des Rate Limitings
- [ ] CORS-Policy-Überprüfung

---

## 4. Compliance-Anforderungen

### 4.1 ISO 27001 Kontrollzuordnung

| ISO-Kontrolle | Beschreibung | DVMM-Implementierung |
|---------------|--------------|----------------------|
| A.9.2.3 | Verwaltung privilegierter Zugänge | RBAC, Genehmigungsworkflow |
| A.9.4.1 | Einschränkung des Informationszugangs | RLS, Mandantenisolierung |
| A.12.4.1 | Ereignisprotokollierung | Event Sourcing Audit-Trail |
| A.12.4.3 | Admin-Aktivitätsprotokolle | Alle Admin-Aktionen protokolliert |
| A.18.1.3 | Schutz von Aufzeichnungen | Unveränderlicher Event Store |

### 4.2 DSGVO-Anforderungen

| Anforderung | Implementierung |
|-------------|-----------------|
| Auskunftsrecht (Art. 15) | Export-Funktion geplant |
| Recht auf Löschung (Art. 17) | Crypto-Shredding-Muster |
| Datenresidenz | Nur Deutschland (DE) |
| Audit-Trail | 7 Jahre Aufbewahrung |

### 4.3 Datenresidenz

| Datentyp | Standort |
|----------|----------|
| Anwendungshosting | Deutschland (DE) |
| PostgreSQL-Datenbank | Deutschland (DE) |
| Backups | Deutschland (DE) |
| Log-Aggregation | EU (maximal) |

---

## 5. CI/CD Sicherheits-Pipeline

### 5.1 Pipeline-Sicherheits-Gates

```yaml
quality_gates:
  coverage:
    threshold: 80%
    tool: kover

  mutation_testing:
    threshold: 70%
    tool: pitest

  architecture_tests:
    tool: konsist
    rules:
      - "eaf-Module haben keine dvmm-Abhängigkeiten"
      - "Domain hat keine Infrastructure-Abhängigkeiten"

  security:
    sast:
      tool: detekt, sonarqube
      critical_vulnerabilities: 0
      high_vulnerabilities: 0

    dependencies:
      tool: owasp-dependency-check
      critical_cve: 0
      high_cve: 0

    container:
      tool: trivy
      severity: CRITICAL,HIGH
      exit_code: 1
```

### 5.2 Sicherheitsteststrategie

| Testtyp | Häufigkeit | Tools |
|---------|------------|-------|
| SAST | Jeder Commit | SonarQube, Detekt |
| Abhängigkeitsscan | Jeder Build | OWASP Dependency-Check |
| Container-Scan | Jeder Build | Trivy |
| DAST | Wöchentlich (Growth-Phase) | OWASP ZAP |
| Penetrationstest | Jährlich | **Externer Anbieter (Strong IT?)** |

### 5.3 Branch-Schutz

- `main`-Branch geschützt
- PR mit CI-Erfolg erforderlich
- 1 Code-Review-Genehmigung erforderlich
- Keine direkten Pushes erlaubt
- Automatisches Löschen gemergter Branches

---

## 6. Infrastruktur & Betrieb

### 6.1 Monitoring-Stack

```text
Grafana (Dashboards) ← Prometheus (Metriken) ← DVMM API (/actuator)
        ↑
       Loki (Logs) ← Promtail (Log-Shipping)
        ↑
   AlertManager → PagerDuty / Slack
```

### 6.2 Incident-Response-Klassifizierung

| Schweregrad | Definition | Reaktionszeit |
|-------------|------------|---------------|
| P1 Kritisch | Aktiver Angriff, Datenverlust | Sofort |
| P2 Hoch | Schwachstelle ausgenutzt | < 4 Stunden |
| P3 Mittel | Schwachstelle entdeckt | < 24 Stunden |
| P4 Niedrig | Sicherheitsverbesserung | Nächster Sprint |

### 6.3 Disaster Recovery

| Metrik | Ziel |
|--------|------|
| RTO (Recovery Time Objective) | < 4 Stunden |
| RPO (Recovery Point Objective) | < 1 Stunde |
| Backup-Häufigkeit | Täglich + WAL (kontinuierlich) |
| Backup-Aufbewahrung | 30 Tage |

---

## 7. Identifizierte Bereiche für Strong IT Unterstützung

Basierend auf dem Serviceportfolio von Strong IT und unseren Projektanforderungen identifizieren wir folgende Zusammenarbeitsmöglichkeiten:

### 7.1 Attack Services (Offensive Sicherheit)

| Service | Unser Bedarf | Priorität |
|---------|--------------|-----------|
| **Web Application Pentest** | Vollständige Sicherheitsbewertung des DVMM-Portals | **HOCH** |
| **API-Sicherheitstests** | REST-API-Schwachstellenbewertung | **HOCH** |
| **Lateral Movement Analyse** | Verifizierung der Multi-Tenant-Isolierung | **HOCH** |
| **MITRE ATT&CK Assessment** | Validierung des Bedrohungsmodells | MITTEL |
| **Phishing-Simulation** | Tests zum Schutz von Admin-Accounts | NIEDRIG (Growth) |

### 7.2 Defense Services (Härtung)

| Service | Unser Bedarf | Priorität |
|---------|--------------|-----------|
| **Active Directory Härtung** | Keycloak/LDAP-Integrationssicherheit | MITTEL |
| **Infrastruktursicherheit** | PostgreSQL, Kubernetes Härtung | **HOCH** |
| **Privileged Access Management** | VMware Credential-Management (Delinea?) | **HOCH** |
| **Endpoint Protection** | API-Server-Härtung (CrowdStrike?) | MITTEL |

### 7.3 Hunting Services (Erkennung & Reaktion)

| Service | Unser Bedarf | Priorität |
|---------|--------------|-----------|
| **24/7 MDR** | Produktionsüberwachung (Growth-Phase) | NIEDRIG |
| **SIEM-Implementierung** | Log-Analyse, Anomalieerkennung | MITTEL |
| **Tabletop-Übungen** | Incident-Response-Vorbereitung | MITTEL |

### 7.4 Beratung & Architektur

| Service | Unser Bedarf | Priorität |
|---------|--------------|-----------|
| **Sicherheitsarchitektur-Review** | Validierung unserer Designentscheidungen | **HOCH** |
| **ISO 27001 Readiness Assessment** | Vor-Audit-Vorbereitung | **HOCH** |
| **Threat-Modeling-Workshop** | Erweiterung der STRIDE-Analyse | **HOCH** |
| **Secure SDLC Integration** | Optimierung der CI/CD-Sicherheits-Gates | MITTEL |

---

## 8. Spezifische Fragen an Strong IT

### 8.1 Architektur-Review
1. Ist PostgreSQL RLS ausreichend für Multi-Tenant-Isolierung, oder sollten wir zusätzliche Anwendungsebenen-Verschlüsselung hinzufügen?
2. Welcher Ansatz wird für VMware Credential-Management in einer Multi-Tenant-Umgebung empfohlen?
3. Wie sollten wir JWT-Token-Widerruf für sofortige Session-Beendigung handhaben?

### 8.2 Compliance
4. Welche Lücken sehen Sie in unserer ISO 27001-Kontrollzuordnung?
5. Ist unser Crypto-Shredding-Ansatz ausreichend für DSGVO Artikel 17-Konformität?
6. Sollten wir eine TISAX-Zertifizierung für Automobilkunden in Betracht ziehen?

### 8.3 Tests
7. Welcher Penetrationstest-Umfang wird für eine Multi-Tenant-SaaS-Anwendung empfohlen?
8. Wie sollten wir speziell mandantenübergreifende Isolierungsschwachstellen testen?
9. Welche DAST-Tools/Konfigurationen würden Sie für unsere API empfehlen?

### 8.4 Betrieb
10. Welches MDR-Service-Level würden Sie für ein B2B-SaaS empfehlen, das vertrauliche Daten verarbeitet?
11. Sollten wir eine WAF implementieren? Wenn ja, welche Regeln/Konfigurationen?
12. Welcher Ansatz für Secrets-Management wird empfohlen (Vault vs. Cloud KMS)?

---

## 9. Vorgeschlagenes Engagement-Modell

### 9.1 Phase 1: Assessment (Sofort)
- Sicherheitsarchitektur-Review
- Validierung des Bedrohungsmodells
- ISO 27001 Gap-Analyse
- Definition des Pentest-Umfangs

### 9.2 Phase 2: Tests (Vor MVP)
- Web-Application-Penetrationstest
- API-Sicherheitsbewertung
- Verifizierung der Multi-Tenant-Isolierung
- Unterstützung bei Schwachstellenbehebung

### 9.3 Phase 3: Härtung (MVP-Launch)
- Empfehlungen zur Infrastrukturhärtung
- PAM-Implementierung für VMware-Zugangsdaten
- Optimierung der CI/CD-Sicherheits-Gates
- Entwicklung von Incident-Response-Playbooks

### 9.4 Phase 4: Betrieb (Nach Launch)
- MDR-Service-Evaluierung
- SIEM-Integration
- Laufendes Schwachstellenmanagement
- Jährliche Penetrationstests

---

## 10. Referenzdokumente

| Dokument | Beschreibung | Speicherort |
|----------|--------------|-------------|
| Security Architecture | Detailliertes Sicherheitsdesign | `docs/security-architecture.md` |
| System Architecture | Technische Architektur, ADRs | `docs/architecture.md` |
| Product Requirements | FRs/NFRs inkl. Sicherheit | `docs/prd.md` |
| DevOps Strategy | CI/CD, Monitoring, DR | `docs/devops-strategy.md` |
| Product Brief | Geschäftskontext | `docs/product-brief-dvmm-2025-11-24.md` |

---

## Anhang A: Technologie-Stack

| Komponente | Technologie | Version |
|------------|-------------|---------|
| Sprache | Kotlin | 2.2 |
| Framework | Spring Boot | 3.5 |
| Datenbank | PostgreSQL | 16 |
| Identity Provider | Keycloak | 26+ |
| Container Runtime | Docker / Kubernetes | Aktuell |
| CI/CD | GitHub Actions | - |
| Monitoring | Grafana + Prometheus + Loki | - |
| SAST | SonarQube, Detekt | - |
| Abhängigkeitsscan | OWASP Dependency-Check | - |
| Container-Scan | Trivy | - |

## Anhang B: NFR Sicherheitsanforderungen Zusammenfassung

| NFR-ID | Anforderung | Ziel |
|--------|-------------|------|
| NFR-SEC-1 | TLS-Verschlüsselung | 100% des Datenverkehrs |
| NFR-SEC-2 | OIDC-Authentifizierung | Erforderlich |
| NFR-SEC-3 | Mandantenisolierung (RLS) | Datenbankgestützt |
| NFR-SEC-4 | Session-Timeout | 30 Min. Inaktivität (App-Level, unabhängig von 1h Token-Lifetime) |
| NFR-SEC-5 | Passwortrichtlinie | Keycloak-verwaltet |
| NFR-SEC-6 | API-Rate-Limiting | 100 Req/Min/Benutzer |
| NFR-SEC-7 | Eingabevalidierung | Alle Endpoints |
| NFR-SEC-8 | SQL-Injection-Prävention | Parametrisierte Abfragen |
| NFR-SEC-9 | XSS-Prävention | CSP-Header |
| NFR-SEC-10 | CSRF-Schutz | Token-basiert |
| NFR-SEC-11 | Secrets-Management | Vault/Umgebungsvariablen |
| NFR-SEC-12 | Abhängigkeitsscan | Null kritische CVEs |
| NFR-SEC-13 | Penetrationstests | Jährlich |
| NFR-SEC-14 | MFA-Unterstützung | Keycloak-bereitgestellt (Growth) |

---

*Dokument erstellt für die Evaluierung der Sicherheitspartnerschaft durch das Strong IT SOC-Team.*
*Kontakt: Michael Walloschke (michael.walloschke@axians.de)*
