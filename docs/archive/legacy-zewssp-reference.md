# ZEWSSP Legacy System Reference

**Purpose:** Comprehensive documentation of the ZEWSSP legacy system and feature mapping to DCM successor.

**Author:** Winston (Architect)
**Date:** 2025-11-28
**Version:** 1.0

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [ZEWSSP Architecture](#2-zewssp-architecture)
3. [Feature Inventory](#3-feature-inventory)
4. [Feature Mapping: ZEWSSP → DCM](#4-feature-mapping-zewssp--dcm)
5. [Ansible Playbook Reference](#5-ansible-playbook-reference)
6. [POPS/DCA Collection Reference](#6-popsdca-collection-reference)
7. [Docket System (Post-MVP)](#7-docket-system-post-mvp)
8. [Architectural Improvements in DCM](#8-architectural-improvements-in-dcm)
9. [Migration Notes](#9-migration-notes)

---

## 1. Executive Summary

### 1.1 What is ZEWSSP?

**ZEWSSP (ZEW Self Service Portal)** is an enterprise-grade, web-based self-service platform for IT infrastructure management, originally developed for a single customer (ZEW - Zentrum für Europäische Wirtschaftsforschung).

**Core Functionality:**
- Virtual Machine request and provisioning via VMware vCenter
- Multi-step approval workflows for resource requests
- Automation via Ansible playbooks (Docket system)
- RBAC-based user and team management
- Real-time monitoring and activity tracking

**Version:** 0.5.1 (Legacy/Pre-production)

### 1.2 Why DCM as Successor?

DCM (Dynamic Cloud Manager) replaces ZEWSSP to address:

| Challenge in ZEWSSP | Solution in DCM |
|---------------------|------------------|
| Single-tenant design | Multi-tenant with PostgreSQL RLS |
| JSONB document store | Event Sourcing with audit trail |
| JavaScript (no type safety) | Kotlin 2.2 with compile-time checks |
| Minimal test coverage | 80% coverage + mutation testing |
| Monolithic architecture | Hexagonal/CQRS for maintainability |
| LDAP-only authentication | Keycloak OIDC (IdP-agnostic) |

### 1.3 Key Differences at a Glance

```
ZEWSSP                          DCM
──────────────────────────────────────────────────────────
Node.js/Fastify          →      Kotlin/Spring Boot 3.5
React 18 + Redux         →      React + shadcn-ui
GraphQL Yoga             →      REST API (GraphQL optional)
PostgreSQL JSONB         →      PostgreSQL + Event Sourcing
JWT + LDAP               →      Keycloak OIDC
Ansible-driven VM ops    →      Direct vSphere API
Single-tenant            →      Multi-tenant (RLS)
```

---

## 2. ZEWSSP Architecture

### 2.1 Technology Stack

#### Backend
| Component | Technology | Version |
|-----------|------------|---------|
| Runtime | Node.js | 16+ |
| Framework | Fastify | 4.14.1 |
| API | GraphQL Yoga | 3.7.2 |
| ORM | Massive | 6.11.2 |
| Authentication | ldapjs | 2.3.3 |
| Email | Nodemailer | 6.9.1 |
| Caching | Redis | 5.3.1 |

#### Frontend
| Component | Technology | Version |
|-----------|------------|---------|
| Framework | React | 18.2.0 |
| State | Redux | 4.2.1 |
| GraphQL Client | Apollo Client | 3.7.10 |
| UI Libraries | Bootstrap, Material-UI | 4.6, 5.x |
| Build Tool | Vite | 4.1.4 |
| i18n | i18next | 22.4.10 |

#### Infrastructure
| Component | Technology |
|-----------|------------|
| Database | PostgreSQL 14+ (JSONB) |
| Cache | Redis |
| Metrics | InfluxDB |
| Reverse Proxy | Traefik v2 |
| Static Assets | Nginx |
| Containers | Docker Compose |
| VM Provisioning | Ansible + VMware vCenter |

### 2.2 Module Structure

```
zewssp/
├── src/
│   ├── clients/
│   │   ├── client/          # End-user portal (port 3903)
│   │   ├── admin/           # Admin dashboard (port 4903)
│   │   └── app/             # Shared components
│   ├── server/
│   │   ├── domain/          # Business logic
│   │   │   ├── authentication/
│   │   │   ├── users/
│   │   │   ├── vmware/
│   │   │   ├── commands/
│   │   │   ├── requests/
│   │   │   ├── dockets/
│   │   │   ├── roles/
│   │   │   └── ...
│   │   ├── services/        # Infrastructure services
│   │   └── graphql/         # API layer
│   └── ansible/
│       ├── playbooks/       # VM provisioning playbooks
│       └── collections/     # Custom Ansible collections
│           ├── axians/pops/ # POPS collection
│           └── axians/dca/  # DCA collection
```

### 2.3 Data Model (JSONB-based)

ZEWSSP uses PostgreSQL with JSONB columns for flexible schema:

#### Core Tables (Inferred)
```sql
-- Users and Authentication
users (id, username, password_hash, email, ldap_dn, profile_json)
sessions (id, user_id, token, expires_at)

-- Access Control
roles (id, name, capabilities_json)
groups (id, name, members_json)
capabilities (id, name, description)

-- Virtual Machines
vms (id, name, config_json, status, owner_id)
vm_templates (id, name, template_json, vcenter_id)
vm_requests (id, status, config_json, approval_chain_json, requestor_id)

-- Automation
dockets (id, name, type, sequence_json, enabled)
commands (id, type, config_json, parent_docket_id)

-- Infrastructure
ansible_hosts (id, hostname, config_json, credentials_json)
ansible_vaults (id, name, secret_data_json)

-- Audit
audit_logs (id, action, entity_type, entity_id, user_id, timestamp, details_json)
```

**Note:** The JSONB approach allows schema flexibility but makes querying and migrations harder. DCM uses normalized tables with Event Sourcing instead.

### 2.4 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Users                                │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              Nginx + Traefik (SSL/TLS)                      │
└─────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│  Client App     │ │   Admin App     │ │   GraphQL API   │
│  (React SPA)    │ │   (React SPA)   │ │   (Fastify)     │
└─────────────────┘ └─────────────────┘ └─────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    AppNode (Fastify)                        │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐           │
│  │  Auth       │ │  Requests   │ │  Dockets    │           │
│  │  Service    │ │  Service    │ │  Service    │           │
│  └─────────────┘ └─────────────┘ └─────────────┘           │
└─────────────────────────────────────────────────────────────┘
              │               │               │
              ▼               ▼               ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│   PostgreSQL    │ │     Redis       │ │  Ansible Svc    │
│   (JSONB)       │ │   (Cache)       │ │  (Playbooks)    │
└─────────────────┘ └─────────────────┘ └─────────────────┘
                                              │
                                              ▼
                              ┌─────────────────────────────┐
                              │     VMware vCenter          │
                              └─────────────────────────────┘
```

---

## 3. Feature Inventory

### 3.1 VM Management

#### VM Request Creation
- Users submit requests with: VM name, size, project, justification
- Template-based provisioning from vCenter templates
- Network options: DHCP or Static IP configuration

#### VM Sizes (Configurable)
| Size | vCPU | RAM | Disk |
|------|------|-----|------|
| S | 2 | 4 GB | 50 GB |
| M | 4 | 8 GB | 100 GB |
| L | 8 | 16 GB | 200 GB |
| XL | 16 | 32 GB | 500 GB |

#### VM Lifecycle
- Create (from template)
- Delete (with cleanup)
- Power on/off
- Status monitoring

### 3.2 Request Workflow

#### Workflow States
```
SUBMITTED → PENDING_APPROVAL → APPROVED → PROVISIONING → READY
                            ↘ REJECTED
                                        ↘ FAILED
```

#### Multi-Step Approval Chains
ZEWSSP supports configurable approval chains:
```json
{
  "approvalChain": [
    {"role": "team_lead", "required": true},
    {"role": "budget_owner", "required": false},
    {"role": "admin", "required": true}
  ]
}
```

**DCM MVP Note:** Single-approval workflow only. Multi-step chains planned for Post-MVP.

#### Email Notifications
- On request submission (to approvers)
- On approval/rejection (to requester)
- On VM ready (to requester)
- On failure (to requester + admins)

### 3.3 Docket System (Automation)

The Docket system enables complex automation workflows:

#### Docket Structure
```json
{
  "id": "provision-webapp",
  "name": "Provision Web Application Server",
  "type": "provisioning",
  "enabled": true,
  "sequence": [
    {
      "step": 1,
      "command": "create_vm",
      "params": {"template": "linux-ubuntu-22.04", "size": "M"}
    },
    {
      "step": 2,
      "command": "run_ansible",
      "params": {"playbook": "configure-nginx.yml"}
    },
    {
      "step": 3,
      "command": "run_ansible",
      "params": {"playbook": "deploy-application.yml"}
    },
    {
      "step": 4,
      "command": "notify",
      "params": {"template": "webapp-ready"}
    }
  ]
}
```

#### Docket Features
- Command sequencing with dependencies
- Variable interpolation (`{{vm.ip}}`, `{{user.email}}`)
- Conditional execution
- Error handling and retry logic
- Progress tracking with event stream
- Execution logs for audit

**DCM Note:** Docket system is planned for Post-MVP. See [Section 7](#7-docket-system-post-mvp).

### 3.4 Administration

#### RBAC (Role-Based Access Control)
| Role | Capabilities |
|------|-------------|
| User | Create requests, view own VMs |
| Project Admin | Manage project members, view project VMs |
| Tenant Admin | Approve requests, configure settings, manage users |
| Super Admin | Cross-tenant access, system configuration |

#### User Management
- LDAP/AD integration for authentication
- Local user creation
- Group/team assignments
- Session management

#### Configuration
- VMware vCenter connection settings
- SMTP email configuration
- Ansible host/vault management
- Billing item definitions

### 3.5 Monitoring & Reporting

#### Activity Tracking
- Request timeline with all state changes
- Command execution logs
- User session history

#### Metrics (via InfluxDB)
- VM resource utilization (CPU, RAM, Disk, Network)
- Request processing times
- API response times

---

## 4. Feature Mapping: ZEWSSP → DCM

### 4.1 Complete Feature Mapping Table

| ZEWSSP Feature | DCM Epic | DCM Story | Status | Notes |
|----------------|-----------|------------|--------|-------|
| **Authentication** |
| JWT + LDAP login | Epic 2 | Story 2.1 | Mapped | Replaced by Keycloak OIDC |
| Session management | Epic 2 | Story 2.1 | Mapped | httpOnly cookie + CSRF |
| Logout | Epic 2 | Story 2.1 | Mapped | Keycloak logout |
| Password reset | - | - | GAP (Keycloak) | Delegated to Keycloak |
| **VM Requests** |
| Create VM request | Epic 2 | Story 2.4-2.6 | Mapped | Simplified form |
| Select VM size (S/M/L/XL) | Epic 2 | Story 2.5 | Mapped | Configurable sizes |
| Select project | Epic 2 | Story 2.4 | Mapped | |
| View my requests | Epic 2 | Story 2.7 | Mapped | |
| Cancel pending request | Epic 2 | Story 2.7 | Mapped | |
| Request status timeline | Epic 2 | Story 2.8 | Mapped | Event-sourced |
| **Approval Workflow** |
| View pending requests (Admin) | Epic 2 | Story 2.9 | Mapped | |
| Approve request | Epic 2 | Story 2.11 | Mapped | |
| Reject request (with reason) | Epic 2 | Story 2.11 | Mapped | |
| Multi-step approval chains | - | - | **GAP** | Post-MVP |
| **VM Provisioning** |
| VMware vCenter connection | Epic 3 | Story 3.1 | Mapped | Direct vSphere API |
| Template-based VM creation | Epic 3 | Story 3.4 | Mapped | |
| DHCP network config | Epic 3 | Story 3.4 | Mapped | |
| Static IP network config | Epic 3 | Story 3.4 | Mapped | |
| Provisioning progress tracking | Epic 3 | Story 3.5 | Mapped | |
| Provisioning error handling | Epic 3 | Story 3.6 | Mapped | |
| VM ready notification | Epic 3 | Story 3.8 | Mapped | |
| **Ansible Integration** |
| Ansible playbook execution | - | - | **GAP** | Post-MVP |
| POPS/DCA collections | - | - | **GAP** | Not needed for MVP |
| **Docket System** |
| Command sequences | - | - | **GAP** | Post-MVP (See Section 7) |
| Variable interpolation | - | - | **GAP** | Post-MVP |
| Execution logging | - | - | **GAP** | Post-MVP |
| **Project Management** |
| View projects | Epic 4 | Story 4.2 | Mapped | |
| Create project | Epic 4 | Story 4.3 | Mapped | |
| Edit project | Epic 4 | Story 4.4 | Mapped | |
| Archive project | Epic 4 | Story 4.5 | Mapped | |
| View VMs in project | Epic 4 | Story 4.6 | Mapped | |
| **Quota Management** |
| Define tenant quotas | Epic 4 | Story 4.7 | Mapped | |
| View quota before submit | Epic 4 | Story 4.8 | Mapped | |
| Enforce quota limits | Epic 4 | Story 4.8 | Mapped | Synchronous enforcement |
| **Administration** |
| RBAC roles/capabilities | Epic 2 | Story 2.1 | Mapped | Via Keycloak roles |
| User management | - | - | GAP (Keycloak) | Delegated to Keycloak |
| Team management | - | - | **GAP** | Post-MVP |
| VMware config settings | Epic 3 | Story 3.1 | Mapped | |
| SMTP config settings | Epic 2 | Story 2.12 | Mapped | |
| Billing integration | - | - | **GAP** | Post-MVP |
| **Audit & Compliance** |
| Audit trail | Epic 5 | Story 5.4-5.5 | Mapped | Event Sourcing |
| Admin activity log | Epic 5 | Story 5.9 | Mapped | |
| Request history export | Epic 5 | Story 5.3 | Mapped | CSV export |
| ISO 27001 mapping | Epic 5 | Story 5.6 | Mapped | |
| **Monitoring** |
| System health dashboard | Epic 5 | Story 5.8 | Mapped | |
| Resource utilization | Epic 4 | Story 4.9 | Mapped | |
| Real-time metrics | - | - | **GAP** | Post-MVP (InfluxDB) |
| **Multi-Tenancy** |
| Data isolation | Epic 5 | Story 5.7 | Mapped | PostgreSQL RLS |
| Tenant context | Epic 1 | Story 1.5-1.6 | Mapped | Coroutine + RLS |

### 4.2 Gap Summary

#### Not in MVP (Post-MVP Planned)
| Feature | Priority | Notes |
|---------|----------|-------|
| Docket System | High | Automation workflows |
| Multi-step approval chains | Medium | Complex approval flows |
| Ansible playbook execution | Medium | Post-provisioning configuration |
| Team management | Medium | Group-based permissions |
| Billing integration | Low | Cost tracking |
| Real-time metrics (InfluxDB) | Low | Advanced monitoring |

#### Replaced by Keycloak
| ZEWSSP Feature | Keycloak Equivalent |
|----------------|---------------------|
| LDAP authentication | LDAP Federation |
| Local user creation | User Management UI |
| Password reset | Account Management |
| Session management | Token Management |

---

## 5. Ansible Playbook Reference

### 5.1 add_vm_vmware.yml (VM Creation)

This is the primary playbook for VM provisioning in ZEWSSP.

**Location:** `/Users/michael/zewssp/src/ansible/add_vm_vmware.yml`

#### Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `vcenter_host` | string | Yes | vCenter server hostname |
| `vcenter_user` | string | Yes | vCenter service account username |
| `vcenter_pass` | string | Yes | vCenter service account password |
| `vcenter_dc` | string | Yes | Datacenter name |
| `vcenter_cluster` | string | Yes | Cluster name |
| `vcenter_datastore` | string | Yes | Datastore name |
| `vcenter_template_name` | string | Yes | Template name to clone |
| `vcenter_network` | string | Yes | Network/portgroup name |
| `vcenter_folder` | string | No | VM folder path |
| `vm_name` | string | Yes | New VM name |
| `vm_hostname` | string | Yes | Guest OS hostname |
| `vm_cpu` | int | Yes | Number of vCPUs |
| `vm_mem` | int | Yes | Memory in MB |
| `vm_ip` | string | No* | Static IP address |
| `vm_nm` | string | No* | Netmask |
| `vm_gw` | string | No* | Gateway |
| `vm_dns_srv` | string | No* | DNS server |
| `vm_domain` | string | No* | Domain name |

*Required for static IP configuration

#### Code Example (DHCP)

```yaml
---
- name: Create VM from template (DHCP)
  hosts: localhost
  gather_facts: false

  tasks:
    - name: Clone VM from template with DHCP
      community.vmware.vmware_guest:
        hostname: '{{ vcenter_host }}'
        username: '{{ vcenter_user }}'
        password: '{{ vcenter_pass }}'
        validate_certs: no
        datacenter: '{{ vcenter_dc }}'
        cluster: '{{ vcenter_cluster }}'
        datastore: '{{ vcenter_datastore }}'
        folder: '{{ vcenter_dc }}/vm/{{ vcenter_folder }}'
        template: '{{ vcenter_template_name }}'
        name: '{{ vm_name }}'
        state: poweredon
        wait_for_ip_address: yes
        customization:
          hostname: '{{ vm_hostname }}'
        networks:
          - name: '{{ vcenter_network }}'
            device_type: 'vmxnet3'
            start_connected: True
            type: dhcp
        hardware:
          num_cpus: '{{ vm_cpu }}'
          memory_mb: '{{ vm_mem }}'
          hotadd_cpu: True
          hotadd_memory: True
          scsi: paravirtual
      delegate_to: localhost
      register: vm_result

    - name: Display VM info
      debug:
        msg: "VM {{ vm_name }} created with IP {{ vm_result.instance.ipv4 }}"
```

#### Code Example (Static IP)

```yaml
---
- name: Create VM from template (Static IP)
  hosts: localhost
  gather_facts: false

  tasks:
    - name: Clone VM from template with static IP
      community.vmware.vmware_guest:
        hostname: '{{ vcenter_host }}'
        username: '{{ vcenter_user }}'
        password: '{{ vcenter_pass }}'
        validate_certs: no
        datacenter: '{{ vcenter_dc }}'
        cluster: '{{ vcenter_cluster }}'
        datastore: '{{ vcenter_datastore }}'
        folder: '{{ vcenter_dc }}/vm/{{ vcenter_folder }}'
        template: '{{ vcenter_template_name }}'
        name: '{{ vm_name }}'
        state: poweredon
        wait_for_ip_address: yes
        customization:
          hostname: '{{ vm_hostname }}'
          dns_servers: '{{ vm_dns_srv }}'
          domain: '{{ vm_domain }}'
        networks:
          - name: '{{ vcenter_network }}'
            device_type: 'vmxnet3'
            start_connected: True
            type: static
            ip: '{{ vm_ip }}'
            netmask: '{{ vm_nm }}'
            gateway: '{{ vm_gw }}'
        hardware:
          num_cpus: '{{ vm_cpu }}'
          memory_mb: '{{ vm_mem }}'
          hotadd_cpu: True
          hotadd_memory: True
          scsi: paravirtual
      delegate_to: localhost
      register: vm_result
```

### 5.2 del_vm_vmware.yml (VM Deletion)

**Location:** `/Users/michael/zewssp/src/ansible/del_vm_vmware.yml`

```yaml
---
- name: Delete VM from vCenter
  hosts: localhost
  gather_facts: false

  tasks:
    - name: Remove VM
      community.vmware.vmware_guest:
        hostname: '{{ vcenter_host }}'
        username: '{{ vcenter_user }}'
        password: '{{ vcenter_pass }}'
        validate_certs: no
        datacenter: '{{ vcenter_dc }}'
        cluster: '{{ vcenter_cluster }}'
        name: '{{ vm_name }}'
        state: absent
        force: True
      delegate_to: localhost
```

### 5.3 vcenter-templates.yml (Template Discovery)

**Location:** `/Users/michael/zewssp/src/ansible/vcenter-templates.yml`

```yaml
---
- name: List all VM templates in vCenter
  hosts: localhost
  gather_facts: false

  tasks:
    - name: Get VM template information
      community.vmware.vmware_vm_info:
        hostname: '{{ vcenter_host }}'
        username: '{{ vcenter_user }}'
        password: '{{ vcenter_pass }}'
        validate_certs: no
        vm_type: template
      delegate_to: localhost
      register: template_info

    - name: Display templates
      debug:
        var: template_info.virtual_machines
```

### 5.4 Hardware Configuration Details

#### Network Adapter
| Setting | Value | Notes |
|---------|-------|-------|
| `device_type` | vmxnet3 | Enhanced NIC performance |
| `start_connected` | True | Connected on boot |

#### CPU & Memory
| Setting | Value | Notes |
|---------|-------|-------|
| `hotadd_cpu` | True | Add CPU without reboot |
| `hotadd_memory` | True | Add RAM without reboot |

#### Storage
| Setting | Value | Notes |
|---------|-------|-------|
| `scsi` | paravirtual | Paravirtual SCSI controller |

---

## 6. POPS/DCA Collection Reference

### 6.1 Overview

ZEWSSP includes two custom Ansible collections for API communication:

| Collection | Purpose |
|------------|---------|
| `axians.pops` | Patch Operator - General infrastructure management |
| `axians.dca` | Data Center Automation - API automation |

### 6.2 POPS Collection

**Location:** `/Users/michael/zewssp/src/ansible/collections/ansible_collections/axians/pops`

#### Modules

**api_connection_check**
```yaml
- name: Check API connectivity
  axians.pops.api_connection_check:
  register: conn_result

# Returns:
# - apiCheck: Basic API connectivity result
# - apiGqlRestrictedCheck: GraphQL authentication result
```

#### Lookup Plugins

**api_query** - Execute GraphQL queries
```yaml
- name: Query user information
  debug:
    msg: "{{ lookup('axians.pops.api_query', 'query') }}"
  vars:
    query: |
      {
        userInfo(username: "john.doe") {
          displayName
          email
          phone
        }
      }
```

#### Inventory Plugins

**maas.py** - Dynamic inventory from MAAS
```yaml
# maas.yml
plugin: axians.pops.maas
api_url: http://maas-api:3932/api
api_token: your-token-here
```

**Inventory Structure:**
- Creates group `satellites` with all hosts
- Creates groups `customer_<id>` per customer
- Host variables include: IP, netmask, DNS, gateway, OS info, etc.

#### Configuration (dca-appnode.json)

```json
{
  "patchOperatorConfig": {
    "currentInstance": "default",
    "instances": [
      {
        "id": "default",
        "url": "http://pops-appnode:3932/api",
        "token": "<service-api-token>",
        "retries": 1,
        "timeout": 5
      }
    ]
  }
}
```

### 6.3 DCA Collection

**Location:** `/Users/michael/zewssp/src/ansible/collections/ansible_collections/axians/dca`

Similar to POPS but with more detailed connection checking:

```yaml
- name: Check DCA API connectivity
  axians.dca.api_connection_check:
    api_url: "{{ dca_api_url }}"
    check_cmd: "/getVersion"
    token: "{{ dca_api_token }}"
    api_query: "{ systemInfo { version } }"
```

### 6.4 DCM Relevance

| Component | DCM Usage |
|-----------|------------|
| api_connection_check | Not needed - direct vSphere API |
| api_query | Not needed - no GraphQL |
| maas.py inventory | Not needed - no MAAS integration |
| GraphQL communication | Replaced by REST API |

**Conclusion:** POPS/DCA collections are not required for DCM MVP. The GraphQL-based communication pattern is replaced by direct vSphere API calls.

---

## 7. Docket System (Post-MVP)

### 7.1 Overview

The Docket system provides automation workflow capabilities in ZEWSSP. This is **planned for Post-MVP** in DCM.

### 7.2 Architecture

```
┌─────────────────────────────────────────────────────┐
│                 Docket Execution                    │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐       │
│  │  Step 1  │ → │  Step 2  │ → │  Step 3  │ → ... │
│  │ Command  │   │ Command  │   │ Command  │       │
│  └──────────┘   └──────────┘   └──────────┘       │
│       │              │              │              │
│       ▼              ▼              ▼              │
│  ┌──────────────────────────────────────┐         │
│  │         Event Stream                  │         │
│  │  (Progress, Logs, Errors)            │         │
│  └──────────────────────────────────────┘         │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### 7.3 Docket Types

| Type | Description | Use Case |
|------|-------------|----------|
| `provisioning` | VM creation + configuration | Web server setup |
| `maintenance` | Scheduled operations | Patching, updates |
| `deployment` | Application deployment | Release pipeline |
| `custom` | User-defined workflows | Ad-hoc automation |

### 7.4 Command Types

| Command | Description | Parameters |
|---------|-------------|------------|
| `create_vm` | Create VM from template | template, size, network |
| `delete_vm` | Delete VM | vm_id, force |
| `run_ansible` | Execute Ansible playbook | playbook, variables |
| `wait` | Wait for condition | condition, timeout |
| `notify` | Send notification | template, recipients |
| `http_request` | Call external API | url, method, body |

### 7.5 Variable Interpolation

```json
{
  "step": 1,
  "command": "notify",
  "params": {
    "template": "vm-ready",
    "variables": {
      "vm_name": "{{request.vm_name}}",
      "vm_ip": "{{vm.ip_address}}",
      "requester_email": "{{user.email}}",
      "provisioning_time": "{{execution.duration_minutes}}"
    }
  }
}
```

**Available Variables:**
- `{{request.*}}` - Request properties
- `{{vm.*}}` - Provisioned VM properties
- `{{user.*}}` - Requester information
- `{{project.*}}` - Project context
- `{{execution.*}}` - Execution metadata

### 7.6 Error Handling

```json
{
  "step": 2,
  "command": "run_ansible",
  "params": {"playbook": "configure-app.yml"},
  "on_error": {
    "action": "retry",
    "max_retries": 3,
    "retry_delay_seconds": 30,
    "fallback": {
      "command": "notify",
      "params": {"template": "deployment-failed"}
    }
  }
}
```

### 7.7 DCM Implementation Recommendations

When implementing Docket system in DCM Post-MVP:

1. **Use Event Sourcing** for execution state
   - `DocketExecutionStarted`
   - `DocketStepCompleted`
   - `DocketStepFailed`
   - `DocketExecutionCompleted`

2. **Implement as Saga/Process Manager**
   - Long-running process pattern
   - Compensation for failures
   - Resume from checkpoint

3. **Consider Spring Integration or Camunda**
   - Workflow engine for complex orchestration
   - Visual workflow designer

4. **Keep Ansible Support** for post-provisioning configuration
   - Not needed for VM creation (direct vSphere)
   - Useful for software installation, configuration

---

## 8. Architectural Improvements in DCM

### 8.1 Comparison Table

| Aspect | ZEWSSP | DCM | Improvement |
|--------|--------|------|-------------|
| **Persistence** | JSONB Documents | Event Sourcing | Complete audit trail, temporal queries, replay capability |
| **Multi-Tenancy** | Implicit (app-level) | PostgreSQL RLS | Database-level isolation, fail-closed security |
| **Type Safety** | JavaScript | Kotlin 2.2 | Compile-time type checks, null safety |
| **API** | GraphQL | REST (+ optional GraphQL) | Simpler tooling, better caching |
| **Authentication** | JWT + LDAP | Keycloak OIDC | Industry standard, extensible, IdP-agnostic |
| **Architecture** | Monolithic | Hexagonal/CQRS | Clear boundaries, testability, maintainability |
| **Testing** | ~20% coverage | 80% + mutation | Quality gates, regression prevention |
| **VM Integration** | Ansible playbooks | Direct vSphere API | Simpler, faster, fewer moving parts |

### 8.2 Event Sourcing Benefits

```
ZEWSSP (JSONB):                    DCM (Event Sourcing):
─────────────────────              ──────────────────────
vm_requests:                       domain_events:
┌─────────────────────┐            ┌─────────────────────┐
│ id: uuid            │            │ VmRequestCreated    │
│ status: "approved"  │            │ VmRequestApproved   │
│ config_json: {...}  │            │ VmProvisionStarted  │
│ updated_at: now()   │            │ VmProvisioned       │
└─────────────────────┘            └─────────────────────┘
                                         ↓
Only current state                 Full history
No history                         When, Who, What
No audit trail                     Immutable audit
```

### 8.3 Multi-Tenancy Security

```
ZEWSSP:                            DCM:
─────────                          ─────
Application-level checks           Database-level RLS

if (user.tenantId !== data.tenantId) {    SET app.tenant_id = 'uuid';
  throw new Error("Access denied");        -- RLS policy automatically
}                                          -- filters all queries

Risk: Developer forgets check            Guaranteed: No bypass possible
```

### 8.4 Type Safety Comparison

```javascript
// ZEWSSP (JavaScript)
function createVm(request) {
  // No type checking
  // Runtime errors possible
  return vmware.clone(request.template, request.name);
}
```

```kotlin
// DCM (Kotlin)
suspend fun createVm(command: CreateVmCommand): Result<VmId, ProvisioningError> {
    // Compile-time type checking
    // Explicit error handling
    // Null safety enforced
    return vspherePort.createVm(command.toVmSpec())
}
```

---

## 9. Migration Notes

### 9.1 What Cannot Be Migrated

| Component | Reason |
|-----------|--------|
| JSONB data model | Incompatible with Event Sourcing |
| GraphQL schema | REST API in DCM |
| Ansible collection code | Python → not needed |
| LDAP integration code | Replaced by Keycloak |
| Redux state structure | Different frontend patterns |

### 9.2 Conceptually Transferable

| Component | How to Transfer |
|-----------|-----------------|
| VM Request workflow logic | Re-implement as aggregate |
| Approval chain rules | Domain events + handlers |
| VMware integration patterns | Adapt to vSphere SDK |
| Email templates | Copy and adapt |
| UI/UX patterns | Design reference |
| Error messages | Localization files |

### 9.3 Data Migration Strategy

Since ZEWSSP was single-tenant and uses JSONB:

1. **No direct migration** - DCM starts fresh
2. **Historical reference** - Keep ZEWSSP read-only for history
3. **Export reports** - Provide CSV exports for records
4. **Parallel operation** - Run both during transition

### 9.4 Knowledge Transfer Items

| Topic | Source in ZEWSSP |
|-------|------------------|
| VMware template naming | Template-<OS>-<VERSION> |
| Network configuration | DHCP vs Static IP logic |
| VM size definitions | S/M/L/XL specifications |
| Email notification triggers | Request state machine |
| Approval workflow states | Request status enum |

---

## Appendix A: File Locations Summary

| Content | ZEWSSP Path |
|---------|-------------|
| Ansible playbooks | `/src/ansible/*.yml` |
| POPS collection | `/src/ansible/collections/ansible_collections/axians/pops/` |
| DCA collection | `/src/ansible/collections/ansible_collections/axians/dca/` |
| Server domain logic | `/src/server/domain/` |
| Client app | `/src/clients/client/` |
| Admin app | `/src/clients/admin/` |
| GraphQL schema | `/src/server/graphql/` |
| Docker Compose | `/docker-compose.yml` |
| Environment template | `/.env.template` |

---

## Appendix B: Glossary

| Term | Definition |
|------|------------|
| **ZEWSSP** | ZEW Self Service Portal - Legacy system |
| **DCM** | Dynamic Cloud Manager - Successor |
| **POPS** | Patch Operator - Ansible collection |
| **DCA** | Data Center Automation - API collection |
| **Docket** | Automation workflow definition |
| **RLS** | Row-Level Security (PostgreSQL) |
| **CQRS** | Command Query Responsibility Segregation |
| **ES** | Event Sourcing |

---

*Document generated by Winston (Architect) as part of DCM project documentation.*
