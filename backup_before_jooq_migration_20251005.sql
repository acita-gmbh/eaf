--
-- PostgreSQL database dump
--

\restrict 2lxNCZdgQ6A29A647dlEmLkb9gnBiZifbkfvWOUDmIOk6VaWeQeORHM3Z8sqLYR

-- Dumped from database version 16.1
-- Dumped by pg_dump version 16.10 (Homebrew)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: eaf_event; Type: SCHEMA; Schema: -; Owner: eaf
--

CREATE SCHEMA eaf_event;


ALTER SCHEMA eaf_event OWNER TO eaf;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: outbox; Type: TABLE; Schema: eaf_event; Owner: eaf
--

CREATE TABLE eaf_event.outbox (
    id uuid NOT NULL,
    aggregate_id character varying(255) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    payload jsonb NOT NULL,
    recorded_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE eaf_event.outbox OWNER TO eaf;

--
-- Name: admin_event_entity; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.admin_event_entity (
    id character varying(36) NOT NULL,
    admin_event_time bigint,
    realm_id character varying(255),
    operation_type character varying(255),
    auth_realm_id character varying(255),
    auth_client_id character varying(255),
    auth_user_id character varying(255),
    ip_address character varying(255),
    resource_path character varying(2550),
    representation text,
    error character varying(255),
    resource_type character varying(64)
);


ALTER TABLE public.admin_event_entity OWNER TO eaf;

--
-- Name: associated_policy; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.associated_policy (
    policy_id character varying(36) NOT NULL,
    associated_policy_id character varying(36) NOT NULL
);


ALTER TABLE public.associated_policy OWNER TO eaf;

--
-- Name: authentication_execution; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.authentication_execution (
    id character varying(36) NOT NULL,
    alias character varying(255),
    authenticator character varying(36),
    realm_id character varying(36),
    flow_id character varying(36),
    requirement integer,
    priority integer,
    authenticator_flow boolean DEFAULT false NOT NULL,
    auth_flow_id character varying(36),
    auth_config character varying(36)
);


ALTER TABLE public.authentication_execution OWNER TO eaf;

--
-- Name: authentication_flow; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.authentication_flow (
    id character varying(36) NOT NULL,
    alias character varying(255),
    description character varying(255),
    realm_id character varying(36),
    provider_id character varying(36) DEFAULT 'basic-flow'::character varying NOT NULL,
    top_level boolean DEFAULT false NOT NULL,
    built_in boolean DEFAULT false NOT NULL
);


ALTER TABLE public.authentication_flow OWNER TO eaf;

--
-- Name: authenticator_config; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.authenticator_config (
    id character varying(36) NOT NULL,
    alias character varying(255),
    realm_id character varying(36)
);


ALTER TABLE public.authenticator_config OWNER TO eaf;

--
-- Name: authenticator_config_entry; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.authenticator_config_entry (
    authenticator_id character varying(36) NOT NULL,
    value text,
    name character varying(255) NOT NULL
);


ALTER TABLE public.authenticator_config_entry OWNER TO eaf;

--
-- Name: broker_link; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.broker_link (
    identity_provider character varying(255) NOT NULL,
    storage_provider_id character varying(255),
    realm_id character varying(36) NOT NULL,
    broker_user_id character varying(255),
    broker_username character varying(255),
    token text,
    user_id character varying(255) NOT NULL
);


ALTER TABLE public.broker_link OWNER TO eaf;

--
-- Name: client; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.client (
    id character varying(36) NOT NULL,
    enabled boolean DEFAULT false NOT NULL,
    full_scope_allowed boolean DEFAULT false NOT NULL,
    client_id character varying(255),
    not_before integer,
    public_client boolean DEFAULT false NOT NULL,
    secret character varying(255),
    base_url character varying(255),
    bearer_only boolean DEFAULT false NOT NULL,
    management_url character varying(255),
    surrogate_auth_required boolean DEFAULT false NOT NULL,
    realm_id character varying(36),
    protocol character varying(255),
    node_rereg_timeout integer DEFAULT 0,
    frontchannel_logout boolean DEFAULT false NOT NULL,
    consent_required boolean DEFAULT false NOT NULL,
    name character varying(255),
    service_accounts_enabled boolean DEFAULT false NOT NULL,
    client_authenticator_type character varying(255),
    root_url character varying(255),
    description character varying(255),
    registration_token character varying(255),
    standard_flow_enabled boolean DEFAULT true NOT NULL,
    implicit_flow_enabled boolean DEFAULT false NOT NULL,
    direct_access_grants_enabled boolean DEFAULT false NOT NULL,
    always_display_in_console boolean DEFAULT false NOT NULL
);


ALTER TABLE public.client OWNER TO eaf;

--
-- Name: client_attributes; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.client_attributes (
    client_id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    value text
);


ALTER TABLE public.client_attributes OWNER TO eaf;

--
-- Name: client_auth_flow_bindings; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.client_auth_flow_bindings (
    client_id character varying(36) NOT NULL,
    flow_id character varying(36),
    binding_name character varying(255) NOT NULL
);


ALTER TABLE public.client_auth_flow_bindings OWNER TO eaf;

--
-- Name: client_initial_access; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.client_initial_access (
    id character varying(36) NOT NULL,
    realm_id character varying(36) NOT NULL,
    "timestamp" integer,
    expiration integer,
    count integer,
    remaining_count integer
);


ALTER TABLE public.client_initial_access OWNER TO eaf;

--
-- Name: client_node_registrations; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.client_node_registrations (
    client_id character varying(36) NOT NULL,
    value integer,
    name character varying(255) NOT NULL
);


ALTER TABLE public.client_node_registrations OWNER TO eaf;

--
-- Name: client_scope; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.client_scope (
    id character varying(36) NOT NULL,
    name character varying(255),
    realm_id character varying(36),
    description character varying(255),
    protocol character varying(255)
);


ALTER TABLE public.client_scope OWNER TO eaf;

--
-- Name: client_scope_attributes; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.client_scope_attributes (
    scope_id character varying(36) NOT NULL,
    value character varying(2048),
    name character varying(255) NOT NULL
);


ALTER TABLE public.client_scope_attributes OWNER TO eaf;

--
-- Name: client_scope_client; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.client_scope_client (
    client_id character varying(255) NOT NULL,
    scope_id character varying(255) NOT NULL,
    default_scope boolean DEFAULT false NOT NULL
);


ALTER TABLE public.client_scope_client OWNER TO eaf;

--
-- Name: client_scope_role_mapping; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.client_scope_role_mapping (
    scope_id character varying(36) NOT NULL,
    role_id character varying(36) NOT NULL
);


ALTER TABLE public.client_scope_role_mapping OWNER TO eaf;

--
-- Name: component; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.component (
    id character varying(36) NOT NULL,
    name character varying(255),
    parent_id character varying(36),
    provider_id character varying(36),
    provider_type character varying(255),
    realm_id character varying(36),
    sub_type character varying(255)
);


ALTER TABLE public.component OWNER TO eaf;

--
-- Name: component_config; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.component_config (
    id character varying(36) NOT NULL,
    component_id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    value text
);


ALTER TABLE public.component_config OWNER TO eaf;

--
-- Name: composite_role; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.composite_role (
    composite character varying(36) NOT NULL,
    child_role character varying(36) NOT NULL
);


ALTER TABLE public.composite_role OWNER TO eaf;

--
-- Name: credential; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.credential (
    id character varying(36) NOT NULL,
    salt bytea,
    type character varying(255),
    user_id character varying(36),
    created_date bigint,
    user_label character varying(255),
    secret_data text,
    credential_data text,
    priority integer
);


ALTER TABLE public.credential OWNER TO eaf;

--
-- Name: databasechangelog; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.databasechangelog (
    id character varying(255) NOT NULL,
    author character varying(255) NOT NULL,
    filename character varying(255) NOT NULL,
    dateexecuted timestamp without time zone NOT NULL,
    orderexecuted integer NOT NULL,
    exectype character varying(10) NOT NULL,
    md5sum character varying(35),
    description character varying(255),
    comments character varying(255),
    tag character varying(255),
    liquibase character varying(20),
    contexts character varying(255),
    labels character varying(255),
    deployment_id character varying(10)
);


ALTER TABLE public.databasechangelog OWNER TO eaf;

--
-- Name: databasechangeloglock; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.databasechangeloglock (
    id integer NOT NULL,
    locked boolean NOT NULL,
    lockgranted timestamp without time zone,
    lockedby character varying(255)
);


ALTER TABLE public.databasechangeloglock OWNER TO eaf;

--
-- Name: default_client_scope; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.default_client_scope (
    realm_id character varying(36) NOT NULL,
    scope_id character varying(36) NOT NULL,
    default_scope boolean DEFAULT false NOT NULL
);


ALTER TABLE public.default_client_scope OWNER TO eaf;

--
-- Name: event_entity; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.event_entity (
    id character varying(36) NOT NULL,
    client_id character varying(255),
    details_json character varying(2550),
    error character varying(255),
    ip_address character varying(255),
    realm_id character varying(255),
    session_id character varying(255),
    event_time bigint,
    type character varying(255),
    user_id character varying(255),
    details_json_long_value text
);


ALTER TABLE public.event_entity OWNER TO eaf;

--
-- Name: fed_user_attribute; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.fed_user_attribute (
    id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    realm_id character varying(36) NOT NULL,
    storage_provider_id character varying(36),
    value character varying(2024),
    long_value_hash bytea,
    long_value_hash_lower_case bytea,
    long_value text
);


ALTER TABLE public.fed_user_attribute OWNER TO eaf;

--
-- Name: fed_user_consent; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.fed_user_consent (
    id character varying(36) NOT NULL,
    client_id character varying(255),
    user_id character varying(255) NOT NULL,
    realm_id character varying(36) NOT NULL,
    storage_provider_id character varying(36),
    created_date bigint,
    last_updated_date bigint,
    client_storage_provider character varying(36),
    external_client_id character varying(255)
);


ALTER TABLE public.fed_user_consent OWNER TO eaf;

--
-- Name: fed_user_consent_cl_scope; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.fed_user_consent_cl_scope (
    user_consent_id character varying(36) NOT NULL,
    scope_id character varying(36) NOT NULL
);


ALTER TABLE public.fed_user_consent_cl_scope OWNER TO eaf;

--
-- Name: fed_user_credential; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.fed_user_credential (
    id character varying(36) NOT NULL,
    salt bytea,
    type character varying(255),
    created_date bigint,
    user_id character varying(255) NOT NULL,
    realm_id character varying(36) NOT NULL,
    storage_provider_id character varying(36),
    user_label character varying(255),
    secret_data text,
    credential_data text,
    priority integer
);


ALTER TABLE public.fed_user_credential OWNER TO eaf;

--
-- Name: fed_user_group_membership; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.fed_user_group_membership (
    group_id character varying(36) NOT NULL,
    user_id character varying(255) NOT NULL,
    realm_id character varying(36) NOT NULL,
    storage_provider_id character varying(36)
);


ALTER TABLE public.fed_user_group_membership OWNER TO eaf;

--
-- Name: fed_user_required_action; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.fed_user_required_action (
    required_action character varying(255) DEFAULT ' '::character varying NOT NULL,
    user_id character varying(255) NOT NULL,
    realm_id character varying(36) NOT NULL,
    storage_provider_id character varying(36)
);


ALTER TABLE public.fed_user_required_action OWNER TO eaf;

--
-- Name: fed_user_role_mapping; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.fed_user_role_mapping (
    role_id character varying(36) NOT NULL,
    user_id character varying(255) NOT NULL,
    realm_id character varying(36) NOT NULL,
    storage_provider_id character varying(36)
);


ALTER TABLE public.fed_user_role_mapping OWNER TO eaf;

--
-- Name: federated_identity; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.federated_identity (
    identity_provider character varying(255) NOT NULL,
    realm_id character varying(36),
    federated_user_id character varying(255),
    federated_username character varying(255),
    token text,
    user_id character varying(36) NOT NULL
);


ALTER TABLE public.federated_identity OWNER TO eaf;

--
-- Name: federated_user; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.federated_user (
    id character varying(255) NOT NULL,
    storage_provider_id character varying(255),
    realm_id character varying(36) NOT NULL
);


ALTER TABLE public.federated_user OWNER TO eaf;

--
-- Name: group_attribute; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.group_attribute (
    id character varying(36) DEFAULT 'sybase-needs-something-here'::character varying NOT NULL,
    name character varying(255) NOT NULL,
    value character varying(255),
    group_id character varying(36) NOT NULL
);


ALTER TABLE public.group_attribute OWNER TO eaf;

--
-- Name: group_role_mapping; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.group_role_mapping (
    role_id character varying(36) NOT NULL,
    group_id character varying(36) NOT NULL
);


ALTER TABLE public.group_role_mapping OWNER TO eaf;

--
-- Name: identity_provider; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.identity_provider (
    internal_id character varying(36) NOT NULL,
    enabled boolean DEFAULT false NOT NULL,
    provider_alias character varying(255),
    provider_id character varying(255),
    store_token boolean DEFAULT false NOT NULL,
    authenticate_by_default boolean DEFAULT false NOT NULL,
    realm_id character varying(36),
    add_token_role boolean DEFAULT true NOT NULL,
    trust_email boolean DEFAULT false NOT NULL,
    first_broker_login_flow_id character varying(36),
    post_broker_login_flow_id character varying(36),
    provider_display_name character varying(255),
    link_only boolean DEFAULT false NOT NULL,
    organization_id character varying(255),
    hide_on_login boolean DEFAULT false
);


ALTER TABLE public.identity_provider OWNER TO eaf;

--
-- Name: identity_provider_config; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.identity_provider_config (
    identity_provider_id character varying(36) NOT NULL,
    value text,
    name character varying(255) NOT NULL
);


ALTER TABLE public.identity_provider_config OWNER TO eaf;

--
-- Name: identity_provider_mapper; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.identity_provider_mapper (
    id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    idp_alias character varying(255) NOT NULL,
    idp_mapper_name character varying(255) NOT NULL,
    realm_id character varying(36) NOT NULL
);


ALTER TABLE public.identity_provider_mapper OWNER TO eaf;

--
-- Name: idp_mapper_config; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.idp_mapper_config (
    idp_mapper_id character varying(36) NOT NULL,
    value text,
    name character varying(255) NOT NULL
);


ALTER TABLE public.idp_mapper_config OWNER TO eaf;

--
-- Name: keycloak_group; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.keycloak_group (
    id character varying(36) NOT NULL,
    name character varying(255),
    parent_group character varying(36) NOT NULL,
    realm_id character varying(36),
    type integer DEFAULT 0 NOT NULL
);


ALTER TABLE public.keycloak_group OWNER TO eaf;

--
-- Name: keycloak_role; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.keycloak_role (
    id character varying(36) NOT NULL,
    client_realm_constraint character varying(255),
    client_role boolean DEFAULT false NOT NULL,
    description character varying(255),
    name character varying(255),
    realm_id character varying(255),
    client character varying(36),
    realm character varying(36)
);


ALTER TABLE public.keycloak_role OWNER TO eaf;

--
-- Name: migration_model; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.migration_model (
    id character varying(36) NOT NULL,
    version character varying(36),
    update_time bigint DEFAULT 0 NOT NULL
);


ALTER TABLE public.migration_model OWNER TO eaf;

--
-- Name: offline_client_session; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.offline_client_session (
    user_session_id character varying(36) NOT NULL,
    client_id character varying(255) NOT NULL,
    offline_flag character varying(4) NOT NULL,
    "timestamp" integer,
    data text,
    client_storage_provider character varying(36) DEFAULT 'local'::character varying NOT NULL,
    external_client_id character varying(255) DEFAULT 'local'::character varying NOT NULL,
    version integer DEFAULT 0
);


ALTER TABLE public.offline_client_session OWNER TO eaf;

--
-- Name: offline_user_session; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.offline_user_session (
    user_session_id character varying(36) NOT NULL,
    user_id character varying(255) NOT NULL,
    realm_id character varying(36) NOT NULL,
    created_on integer NOT NULL,
    offline_flag character varying(4) NOT NULL,
    data text,
    last_session_refresh integer DEFAULT 0 NOT NULL,
    broker_session_id character varying(1024),
    version integer DEFAULT 0
);


ALTER TABLE public.offline_user_session OWNER TO eaf;

--
-- Name: org; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.org (
    id character varying(255) NOT NULL,
    enabled boolean NOT NULL,
    realm_id character varying(255) NOT NULL,
    group_id character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    description character varying(4000),
    alias character varying(255) NOT NULL,
    redirect_url character varying(2048)
);


ALTER TABLE public.org OWNER TO eaf;

--
-- Name: org_domain; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.org_domain (
    id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    verified boolean NOT NULL,
    org_id character varying(255) NOT NULL
);


ALTER TABLE public.org_domain OWNER TO eaf;

--
-- Name: policy_config; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.policy_config (
    policy_id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    value text
);


ALTER TABLE public.policy_config OWNER TO eaf;

--
-- Name: protocol_mapper; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.protocol_mapper (
    id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    protocol character varying(255) NOT NULL,
    protocol_mapper_name character varying(255) NOT NULL,
    client_id character varying(36),
    client_scope_id character varying(36)
);


ALTER TABLE public.protocol_mapper OWNER TO eaf;

--
-- Name: protocol_mapper_config; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.protocol_mapper_config (
    protocol_mapper_id character varying(36) NOT NULL,
    value text,
    name character varying(255) NOT NULL
);


ALTER TABLE public.protocol_mapper_config OWNER TO eaf;

--
-- Name: realm; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.realm (
    id character varying(36) NOT NULL,
    access_code_lifespan integer,
    user_action_lifespan integer,
    access_token_lifespan integer,
    account_theme character varying(255),
    admin_theme character varying(255),
    email_theme character varying(255),
    enabled boolean DEFAULT false NOT NULL,
    events_enabled boolean DEFAULT false NOT NULL,
    events_expiration bigint,
    login_theme character varying(255),
    name character varying(255),
    not_before integer,
    password_policy character varying(2550),
    registration_allowed boolean DEFAULT false NOT NULL,
    remember_me boolean DEFAULT false NOT NULL,
    reset_password_allowed boolean DEFAULT false NOT NULL,
    social boolean DEFAULT false NOT NULL,
    ssl_required character varying(255),
    sso_idle_timeout integer,
    sso_max_lifespan integer,
    update_profile_on_soc_login boolean DEFAULT false NOT NULL,
    verify_email boolean DEFAULT false NOT NULL,
    master_admin_client character varying(36),
    login_lifespan integer,
    internationalization_enabled boolean DEFAULT false NOT NULL,
    default_locale character varying(255),
    reg_email_as_username boolean DEFAULT false NOT NULL,
    admin_events_enabled boolean DEFAULT false NOT NULL,
    admin_events_details_enabled boolean DEFAULT false NOT NULL,
    edit_username_allowed boolean DEFAULT false NOT NULL,
    otp_policy_counter integer DEFAULT 0,
    otp_policy_window integer DEFAULT 1,
    otp_policy_period integer DEFAULT 30,
    otp_policy_digits integer DEFAULT 6,
    otp_policy_alg character varying(36) DEFAULT 'HmacSHA1'::character varying,
    otp_policy_type character varying(36) DEFAULT 'totp'::character varying,
    browser_flow character varying(36),
    registration_flow character varying(36),
    direct_grant_flow character varying(36),
    reset_credentials_flow character varying(36),
    client_auth_flow character varying(36),
    offline_session_idle_timeout integer DEFAULT 0,
    revoke_refresh_token boolean DEFAULT false NOT NULL,
    access_token_life_implicit integer DEFAULT 0,
    login_with_email_allowed boolean DEFAULT true NOT NULL,
    duplicate_emails_allowed boolean DEFAULT false NOT NULL,
    docker_auth_flow character varying(36),
    refresh_token_max_reuse integer DEFAULT 0,
    allow_user_managed_access boolean DEFAULT false NOT NULL,
    sso_max_lifespan_remember_me integer DEFAULT 0 NOT NULL,
    sso_idle_timeout_remember_me integer DEFAULT 0 NOT NULL,
    default_role character varying(255)
);


ALTER TABLE public.realm OWNER TO eaf;

--
-- Name: realm_attribute; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.realm_attribute (
    name character varying(255) NOT NULL,
    realm_id character varying(36) NOT NULL,
    value text
);


ALTER TABLE public.realm_attribute OWNER TO eaf;

--
-- Name: realm_default_groups; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.realm_default_groups (
    realm_id character varying(36) NOT NULL,
    group_id character varying(36) NOT NULL
);


ALTER TABLE public.realm_default_groups OWNER TO eaf;

--
-- Name: realm_enabled_event_types; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.realm_enabled_event_types (
    realm_id character varying(36) NOT NULL,
    value character varying(255) NOT NULL
);


ALTER TABLE public.realm_enabled_event_types OWNER TO eaf;

--
-- Name: realm_events_listeners; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.realm_events_listeners (
    realm_id character varying(36) NOT NULL,
    value character varying(255) NOT NULL
);


ALTER TABLE public.realm_events_listeners OWNER TO eaf;

--
-- Name: realm_localizations; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.realm_localizations (
    realm_id character varying(255) NOT NULL,
    locale character varying(255) NOT NULL,
    texts text NOT NULL
);


ALTER TABLE public.realm_localizations OWNER TO eaf;

--
-- Name: realm_required_credential; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.realm_required_credential (
    type character varying(255) NOT NULL,
    form_label character varying(255),
    input boolean DEFAULT false NOT NULL,
    secret boolean DEFAULT false NOT NULL,
    realm_id character varying(36) NOT NULL
);


ALTER TABLE public.realm_required_credential OWNER TO eaf;

--
-- Name: realm_smtp_config; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.realm_smtp_config (
    realm_id character varying(36) NOT NULL,
    value character varying(255),
    name character varying(255) NOT NULL
);


ALTER TABLE public.realm_smtp_config OWNER TO eaf;

--
-- Name: realm_supported_locales; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.realm_supported_locales (
    realm_id character varying(36) NOT NULL,
    value character varying(255) NOT NULL
);


ALTER TABLE public.realm_supported_locales OWNER TO eaf;

--
-- Name: redirect_uris; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.redirect_uris (
    client_id character varying(36) NOT NULL,
    value character varying(255) NOT NULL
);


ALTER TABLE public.redirect_uris OWNER TO eaf;

--
-- Name: required_action_config; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.required_action_config (
    required_action_id character varying(36) NOT NULL,
    value text,
    name character varying(255) NOT NULL
);


ALTER TABLE public.required_action_config OWNER TO eaf;

--
-- Name: required_action_provider; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.required_action_provider (
    id character varying(36) NOT NULL,
    alias character varying(255),
    name character varying(255),
    realm_id character varying(36),
    enabled boolean DEFAULT false NOT NULL,
    default_action boolean DEFAULT false NOT NULL,
    provider_id character varying(255),
    priority integer
);


ALTER TABLE public.required_action_provider OWNER TO eaf;

--
-- Name: resource_attribute; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.resource_attribute (
    id character varying(36) DEFAULT 'sybase-needs-something-here'::character varying NOT NULL,
    name character varying(255) NOT NULL,
    value character varying(255),
    resource_id character varying(36) NOT NULL
);


ALTER TABLE public.resource_attribute OWNER TO eaf;

--
-- Name: resource_policy; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.resource_policy (
    resource_id character varying(36) NOT NULL,
    policy_id character varying(36) NOT NULL
);


ALTER TABLE public.resource_policy OWNER TO eaf;

--
-- Name: resource_scope; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.resource_scope (
    resource_id character varying(36) NOT NULL,
    scope_id character varying(36) NOT NULL
);


ALTER TABLE public.resource_scope OWNER TO eaf;

--
-- Name: resource_server; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.resource_server (
    id character varying(36) NOT NULL,
    allow_rs_remote_mgmt boolean DEFAULT false NOT NULL,
    policy_enforce_mode smallint NOT NULL,
    decision_strategy smallint DEFAULT 1 NOT NULL
);


ALTER TABLE public.resource_server OWNER TO eaf;

--
-- Name: resource_server_perm_ticket; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.resource_server_perm_ticket (
    id character varying(36) NOT NULL,
    owner character varying(255) NOT NULL,
    requester character varying(255) NOT NULL,
    created_timestamp bigint NOT NULL,
    granted_timestamp bigint,
    resource_id character varying(36) NOT NULL,
    scope_id character varying(36),
    resource_server_id character varying(36) NOT NULL,
    policy_id character varying(36)
);


ALTER TABLE public.resource_server_perm_ticket OWNER TO eaf;

--
-- Name: resource_server_policy; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.resource_server_policy (
    id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    description character varying(255),
    type character varying(255) NOT NULL,
    decision_strategy smallint,
    logic smallint,
    resource_server_id character varying(36) NOT NULL,
    owner character varying(255)
);


ALTER TABLE public.resource_server_policy OWNER TO eaf;

--
-- Name: resource_server_resource; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.resource_server_resource (
    id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    type character varying(255),
    icon_uri character varying(255),
    owner character varying(255) NOT NULL,
    resource_server_id character varying(36) NOT NULL,
    owner_managed_access boolean DEFAULT false NOT NULL,
    display_name character varying(255)
);


ALTER TABLE public.resource_server_resource OWNER TO eaf;

--
-- Name: resource_server_scope; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.resource_server_scope (
    id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    icon_uri character varying(255),
    resource_server_id character varying(36) NOT NULL,
    display_name character varying(255)
);


ALTER TABLE public.resource_server_scope OWNER TO eaf;

--
-- Name: resource_uris; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.resource_uris (
    resource_id character varying(36) NOT NULL,
    value character varying(255) NOT NULL
);


ALTER TABLE public.resource_uris OWNER TO eaf;

--
-- Name: revoked_token; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.revoked_token (
    id character varying(255) NOT NULL,
    expire bigint NOT NULL
);


ALTER TABLE public.revoked_token OWNER TO eaf;

--
-- Name: role_attribute; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.role_attribute (
    id character varying(36) NOT NULL,
    role_id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    value character varying(255)
);


ALTER TABLE public.role_attribute OWNER TO eaf;

--
-- Name: scope_mapping; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.scope_mapping (
    client_id character varying(36) NOT NULL,
    role_id character varying(36) NOT NULL
);


ALTER TABLE public.scope_mapping OWNER TO eaf;

--
-- Name: scope_policy; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.scope_policy (
    scope_id character varying(36) NOT NULL,
    policy_id character varying(36) NOT NULL
);


ALTER TABLE public.scope_policy OWNER TO eaf;

--
-- Name: user_attribute; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.user_attribute (
    name character varying(255) NOT NULL,
    value character varying(255),
    user_id character varying(36) NOT NULL,
    id character varying(36) DEFAULT 'sybase-needs-something-here'::character varying NOT NULL,
    long_value_hash bytea,
    long_value_hash_lower_case bytea,
    long_value text
);


ALTER TABLE public.user_attribute OWNER TO eaf;

--
-- Name: user_consent; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.user_consent (
    id character varying(36) NOT NULL,
    client_id character varying(255),
    user_id character varying(36) NOT NULL,
    created_date bigint,
    last_updated_date bigint,
    client_storage_provider character varying(36),
    external_client_id character varying(255)
);


ALTER TABLE public.user_consent OWNER TO eaf;

--
-- Name: user_consent_client_scope; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.user_consent_client_scope (
    user_consent_id character varying(36) NOT NULL,
    scope_id character varying(36) NOT NULL
);


ALTER TABLE public.user_consent_client_scope OWNER TO eaf;

--
-- Name: user_entity; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.user_entity (
    id character varying(36) NOT NULL,
    email character varying(255),
    email_constraint character varying(255),
    email_verified boolean DEFAULT false NOT NULL,
    enabled boolean DEFAULT false NOT NULL,
    federation_link character varying(255),
    first_name character varying(255),
    last_name character varying(255),
    realm_id character varying(255),
    username character varying(255),
    created_timestamp bigint,
    service_account_client_link character varying(255),
    not_before integer DEFAULT 0 NOT NULL
);


ALTER TABLE public.user_entity OWNER TO eaf;

--
-- Name: user_federation_config; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.user_federation_config (
    user_federation_provider_id character varying(36) NOT NULL,
    value character varying(255),
    name character varying(255) NOT NULL
);


ALTER TABLE public.user_federation_config OWNER TO eaf;

--
-- Name: user_federation_mapper; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.user_federation_mapper (
    id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    federation_provider_id character varying(36) NOT NULL,
    federation_mapper_type character varying(255) NOT NULL,
    realm_id character varying(36) NOT NULL
);


ALTER TABLE public.user_federation_mapper OWNER TO eaf;

--
-- Name: user_federation_mapper_config; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.user_federation_mapper_config (
    user_federation_mapper_id character varying(36) NOT NULL,
    value character varying(255),
    name character varying(255) NOT NULL
);


ALTER TABLE public.user_federation_mapper_config OWNER TO eaf;

--
-- Name: user_federation_provider; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.user_federation_provider (
    id character varying(36) NOT NULL,
    changed_sync_period integer,
    display_name character varying(255),
    full_sync_period integer,
    last_sync integer,
    priority integer,
    provider_name character varying(255),
    realm_id character varying(36)
);


ALTER TABLE public.user_federation_provider OWNER TO eaf;

--
-- Name: user_group_membership; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.user_group_membership (
    group_id character varying(36) NOT NULL,
    user_id character varying(36) NOT NULL,
    membership_type character varying(255) NOT NULL
);


ALTER TABLE public.user_group_membership OWNER TO eaf;

--
-- Name: user_required_action; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.user_required_action (
    user_id character varying(36) NOT NULL,
    required_action character varying(255) DEFAULT ' '::character varying NOT NULL
);


ALTER TABLE public.user_required_action OWNER TO eaf;

--
-- Name: user_role_mapping; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.user_role_mapping (
    role_id character varying(255) NOT NULL,
    user_id character varying(36) NOT NULL
);


ALTER TABLE public.user_role_mapping OWNER TO eaf;

--
-- Name: username_login_failure; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.username_login_failure (
    realm_id character varying(36) NOT NULL,
    username character varying(255) NOT NULL,
    failed_login_not_before integer,
    last_failure bigint,
    last_ip_failure character varying(255),
    num_failures integer
);


ALTER TABLE public.username_login_failure OWNER TO eaf;

--
-- Name: web_origins; Type: TABLE; Schema: public; Owner: eaf
--

CREATE TABLE public.web_origins (
    client_id character varying(36) NOT NULL,
    value character varying(255) NOT NULL
);


ALTER TABLE public.web_origins OWNER TO eaf;

--
-- Data for Name: outbox; Type: TABLE DATA; Schema: eaf_event; Owner: eaf
--

COPY eaf_event.outbox (id, aggregate_id, aggregate_type, payload, recorded_at) FROM stdin;
00000000-0000-0000-0000-000000000001	seed-tenant	TENANT_PROVISIONED	{"tenantId": "seed-tenant", "createdAt": "2025-10-05T18:47:27.554089+00:00"}	2025-10-05 18:47:27.554089+00
\.


--
-- Data for Name: admin_event_entity; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.admin_event_entity (id, admin_event_time, realm_id, operation_type, auth_realm_id, auth_client_id, auth_user_id, ip_address, resource_path, representation, error, resource_type) FROM stdin;
\.


--
-- Data for Name: associated_policy; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.associated_policy (policy_id, associated_policy_id) FROM stdin;
\.


--
-- Data for Name: authentication_execution; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.authentication_execution (id, alias, authenticator, realm_id, flow_id, requirement, priority, authenticator_flow, auth_flow_id, auth_config) FROM stdin;
043da829-16c5-4566-a617-1318983d2635	\N	auth-cookie	818f97fc-7b35-4bae-bdd7-76b5035dce69	f2312c70-9614-4eb7-aabc-743a2aee10ea	2	10	f	\N	\N
e0f8bf9e-3242-4720-b096-721104f04b75	\N	auth-spnego	818f97fc-7b35-4bae-bdd7-76b5035dce69	f2312c70-9614-4eb7-aabc-743a2aee10ea	3	20	f	\N	\N
3cf8d5fc-fa33-47ea-a961-ab685fbaa31a	\N	identity-provider-redirector	818f97fc-7b35-4bae-bdd7-76b5035dce69	f2312c70-9614-4eb7-aabc-743a2aee10ea	2	25	f	\N	\N
64bef7ac-4f60-446e-8596-62c30fad6a27	\N	\N	818f97fc-7b35-4bae-bdd7-76b5035dce69	f2312c70-9614-4eb7-aabc-743a2aee10ea	2	30	t	2e8b98d5-d9d8-420b-8ac6-a3f69b6a6975	\N
3cd24f13-304d-4ff8-b135-fecf36233bba	\N	auth-username-password-form	818f97fc-7b35-4bae-bdd7-76b5035dce69	2e8b98d5-d9d8-420b-8ac6-a3f69b6a6975	0	10	f	\N	\N
52730017-ee71-4ae0-b7d6-421e15f33f4a	\N	\N	818f97fc-7b35-4bae-bdd7-76b5035dce69	2e8b98d5-d9d8-420b-8ac6-a3f69b6a6975	1	20	t	48bb540e-142a-447e-980c-1363b97fdd80	\N
26c487a7-c2f5-4d07-a30f-272890b4394a	\N	conditional-user-configured	818f97fc-7b35-4bae-bdd7-76b5035dce69	48bb540e-142a-447e-980c-1363b97fdd80	0	10	f	\N	\N
17a564e3-d525-4b71-bd64-e72887e53a7b	\N	auth-otp-form	818f97fc-7b35-4bae-bdd7-76b5035dce69	48bb540e-142a-447e-980c-1363b97fdd80	0	20	f	\N	\N
6048c800-d43e-47a0-a89c-c1aea32b0dec	\N	direct-grant-validate-username	818f97fc-7b35-4bae-bdd7-76b5035dce69	8f281943-5ab4-431a-b40d-d01be9ffcb56	0	10	f	\N	\N
f84451b8-fbee-4eee-9008-bbe13b654659	\N	direct-grant-validate-password	818f97fc-7b35-4bae-bdd7-76b5035dce69	8f281943-5ab4-431a-b40d-d01be9ffcb56	0	20	f	\N	\N
09d69e3e-39f1-415f-b591-6bce2a712457	\N	\N	818f97fc-7b35-4bae-bdd7-76b5035dce69	8f281943-5ab4-431a-b40d-d01be9ffcb56	1	30	t	a73a7981-b62d-4970-8ecf-aa820a1cc0f8	\N
3b056d12-60ba-410c-8218-bc556b4e2ebb	\N	conditional-user-configured	818f97fc-7b35-4bae-bdd7-76b5035dce69	a73a7981-b62d-4970-8ecf-aa820a1cc0f8	0	10	f	\N	\N
f90b573f-dd59-4c15-85e4-7a62c9272393	\N	direct-grant-validate-otp	818f97fc-7b35-4bae-bdd7-76b5035dce69	a73a7981-b62d-4970-8ecf-aa820a1cc0f8	0	20	f	\N	\N
db8235eb-3244-4840-9b4c-3faf1d44477c	\N	registration-page-form	818f97fc-7b35-4bae-bdd7-76b5035dce69	3e041e39-2fdc-45b6-b800-5e01f588181a	0	10	t	b300fcfc-7599-4071-b0c0-473f223afb87	\N
639522d2-6ec7-45ac-a7f1-ca9d15656f7c	\N	registration-user-creation	818f97fc-7b35-4bae-bdd7-76b5035dce69	b300fcfc-7599-4071-b0c0-473f223afb87	0	20	f	\N	\N
f67b3271-5765-4ef2-b977-e0f377983b75	\N	registration-password-action	818f97fc-7b35-4bae-bdd7-76b5035dce69	b300fcfc-7599-4071-b0c0-473f223afb87	0	50	f	\N	\N
ff3e9d40-56aa-4d11-b90b-cc8979fe22bb	\N	registration-recaptcha-action	818f97fc-7b35-4bae-bdd7-76b5035dce69	b300fcfc-7599-4071-b0c0-473f223afb87	3	60	f	\N	\N
c2b4f536-c2fc-40cc-8500-5fea1c4a6ead	\N	registration-terms-and-conditions	818f97fc-7b35-4bae-bdd7-76b5035dce69	b300fcfc-7599-4071-b0c0-473f223afb87	3	70	f	\N	\N
b9899ec6-9c6f-4b9e-a3b8-a3e4d37443d2	\N	reset-credentials-choose-user	818f97fc-7b35-4bae-bdd7-76b5035dce69	f7275f2d-27e4-4132-a896-9a07c914e15c	0	10	f	\N	\N
bb4932d4-4735-4d5e-bd74-e96173706c32	\N	reset-credential-email	818f97fc-7b35-4bae-bdd7-76b5035dce69	f7275f2d-27e4-4132-a896-9a07c914e15c	0	20	f	\N	\N
f44c7acb-54be-42e0-8738-90703868d29e	\N	reset-password	818f97fc-7b35-4bae-bdd7-76b5035dce69	f7275f2d-27e4-4132-a896-9a07c914e15c	0	30	f	\N	\N
d95e1213-5037-421c-867c-611674fb0625	\N	\N	818f97fc-7b35-4bae-bdd7-76b5035dce69	f7275f2d-27e4-4132-a896-9a07c914e15c	1	40	t	665c4748-d0e4-4344-b4a9-c27f8788ac74	\N
6f916c77-f8af-4e27-99b5-0b5df4ebe9ae	\N	conditional-user-configured	818f97fc-7b35-4bae-bdd7-76b5035dce69	665c4748-d0e4-4344-b4a9-c27f8788ac74	0	10	f	\N	\N
4e0d0f8e-460f-4d98-8648-f2999133ad3d	\N	reset-otp	818f97fc-7b35-4bae-bdd7-76b5035dce69	665c4748-d0e4-4344-b4a9-c27f8788ac74	0	20	f	\N	\N
ac88b7f8-3f34-42e8-a9d4-8ca67df4528e	\N	client-secret	818f97fc-7b35-4bae-bdd7-76b5035dce69	78e843e8-00a3-4ad6-a834-55492827b279	2	10	f	\N	\N
4a73cd4a-4355-48c4-a29f-9178dbb6bdb1	\N	client-jwt	818f97fc-7b35-4bae-bdd7-76b5035dce69	78e843e8-00a3-4ad6-a834-55492827b279	2	20	f	\N	\N
60717a7f-01de-40ac-9c69-c084a09a4ba0	\N	client-secret-jwt	818f97fc-7b35-4bae-bdd7-76b5035dce69	78e843e8-00a3-4ad6-a834-55492827b279	2	30	f	\N	\N
8e8f202f-1320-4f88-a631-1a345460ffa9	\N	client-x509	818f97fc-7b35-4bae-bdd7-76b5035dce69	78e843e8-00a3-4ad6-a834-55492827b279	2	40	f	\N	\N
56d6b2ac-5023-4f83-bfb1-6dcc5f0aabc0	\N	idp-review-profile	818f97fc-7b35-4bae-bdd7-76b5035dce69	7f97d87f-3f8d-4f78-ac23-467c123140b6	0	10	f	\N	38d428db-e248-4210-ba29-36d922bb93f9
94f2d8b6-76ee-4f6d-b68a-35677b466f5f	\N	\N	818f97fc-7b35-4bae-bdd7-76b5035dce69	7f97d87f-3f8d-4f78-ac23-467c123140b6	0	20	t	0a489445-bca8-481f-ae5c-e370d2da632b	\N
7690e711-cffa-4f01-93c7-ecf74cd3cab8	\N	idp-create-user-if-unique	818f97fc-7b35-4bae-bdd7-76b5035dce69	0a489445-bca8-481f-ae5c-e370d2da632b	2	10	f	\N	cf86ba59-b58f-40fe-b439-ebb173736284
dc5c497d-8eb9-44fa-aceb-a2373578347c	\N	\N	818f97fc-7b35-4bae-bdd7-76b5035dce69	0a489445-bca8-481f-ae5c-e370d2da632b	2	20	t	b8558172-8ad4-44a5-9707-d2470da99751	\N
7db072c4-6d38-4c85-a515-0a88682780d0	\N	idp-confirm-link	818f97fc-7b35-4bae-bdd7-76b5035dce69	b8558172-8ad4-44a5-9707-d2470da99751	0	10	f	\N	\N
685c14c2-5eca-43b6-b844-199960f39976	\N	\N	818f97fc-7b35-4bae-bdd7-76b5035dce69	b8558172-8ad4-44a5-9707-d2470da99751	0	20	t	191081f7-60bd-45d8-871c-eef88f59ca8d	\N
56636a1b-6f35-4075-9c39-d7ec70a728d9	\N	idp-email-verification	818f97fc-7b35-4bae-bdd7-76b5035dce69	191081f7-60bd-45d8-871c-eef88f59ca8d	2	10	f	\N	\N
124970a5-5314-4044-8942-39c56ca9bc49	\N	\N	818f97fc-7b35-4bae-bdd7-76b5035dce69	191081f7-60bd-45d8-871c-eef88f59ca8d	2	20	t	ff3dbcb2-8c45-45ce-a674-16cd980323d4	\N
29a538f2-58a3-49e0-a6ea-1c45ec0ada6f	\N	idp-username-password-form	818f97fc-7b35-4bae-bdd7-76b5035dce69	ff3dbcb2-8c45-45ce-a674-16cd980323d4	0	10	f	\N	\N
e2513ff5-841a-4009-876e-7e9896bafd4f	\N	\N	818f97fc-7b35-4bae-bdd7-76b5035dce69	ff3dbcb2-8c45-45ce-a674-16cd980323d4	1	20	t	7f665693-b554-4497-9fcc-4a9761809012	\N
d5240d6f-36c0-41f9-b5a0-bbec92fcb071	\N	conditional-user-configured	818f97fc-7b35-4bae-bdd7-76b5035dce69	7f665693-b554-4497-9fcc-4a9761809012	0	10	f	\N	\N
3ae9f991-ff4f-4c5a-9faf-e4fba28592ed	\N	auth-otp-form	818f97fc-7b35-4bae-bdd7-76b5035dce69	7f665693-b554-4497-9fcc-4a9761809012	0	20	f	\N	\N
075a5b3d-513c-4d2a-91bc-c208d4089df1	\N	http-basic-authenticator	818f97fc-7b35-4bae-bdd7-76b5035dce69	a4f16621-5451-4d59-9540-e8c18a2cca77	0	10	f	\N	\N
437dcfb6-b51b-4864-bee6-946c5632d22d	\N	docker-http-basic-authenticator	818f97fc-7b35-4bae-bdd7-76b5035dce69	6dd75b98-03e9-4093-b9c1-35554173afeb	0	10	f	\N	\N
31de6aa3-1d07-4d07-b576-f394fcdb60fc	\N	auth-cookie	c43fd595-cebd-4062-ac8d-8943c15bd7de	56d6e51d-f268-4e22-8606-8c6ce35bdd8d	2	10	f	\N	\N
73cfb72d-ae2a-4eeb-a6bf-c280a4519ccb	\N	auth-spnego	c43fd595-cebd-4062-ac8d-8943c15bd7de	56d6e51d-f268-4e22-8606-8c6ce35bdd8d	3	20	f	\N	\N
1e1cbdec-1f27-4144-9003-6ef1740c6ee0	\N	identity-provider-redirector	c43fd595-cebd-4062-ac8d-8943c15bd7de	56d6e51d-f268-4e22-8606-8c6ce35bdd8d	2	25	f	\N	\N
89c5b753-ae73-44e3-bb5d-9889f67eb7f4	\N	\N	c43fd595-cebd-4062-ac8d-8943c15bd7de	56d6e51d-f268-4e22-8606-8c6ce35bdd8d	2	30	t	e9449abc-0d54-41f5-8c9f-092a8afdb512	\N
260c0322-87f7-4426-9c4f-76652774500e	\N	auth-username-password-form	c43fd595-cebd-4062-ac8d-8943c15bd7de	e9449abc-0d54-41f5-8c9f-092a8afdb512	0	10	f	\N	\N
2b365993-ec23-405c-b5e7-e1af52ea238b	\N	\N	c43fd595-cebd-4062-ac8d-8943c15bd7de	e9449abc-0d54-41f5-8c9f-092a8afdb512	1	20	t	925a8eb2-33c3-497f-a344-f95debba0b43	\N
1283f40e-ade5-4038-a2b1-df45dc40fa88	\N	conditional-user-configured	c43fd595-cebd-4062-ac8d-8943c15bd7de	925a8eb2-33c3-497f-a344-f95debba0b43	0	10	f	\N	\N
20084009-b2ad-4ef9-9294-3e1434f73589	\N	auth-otp-form	c43fd595-cebd-4062-ac8d-8943c15bd7de	925a8eb2-33c3-497f-a344-f95debba0b43	0	20	f	\N	\N
b1a2d116-7922-422c-9460-b00711775180	\N	\N	c43fd595-cebd-4062-ac8d-8943c15bd7de	56d6e51d-f268-4e22-8606-8c6ce35bdd8d	2	26	t	b5075bf0-36f2-47fe-8e6a-8ad5167cde50	\N
9be1f45f-6259-4ab4-8d42-702d72cba5c8	\N	\N	c43fd595-cebd-4062-ac8d-8943c15bd7de	b5075bf0-36f2-47fe-8e6a-8ad5167cde50	1	10	t	04add92b-320e-444e-8f0a-0c3a5cd3dbe0	\N
041a1f93-4af8-4a56-b2a5-acfb54617f21	\N	conditional-user-configured	c43fd595-cebd-4062-ac8d-8943c15bd7de	04add92b-320e-444e-8f0a-0c3a5cd3dbe0	0	10	f	\N	\N
9f6a9c75-718e-4cde-ac7e-351b0b69ea65	\N	organization	c43fd595-cebd-4062-ac8d-8943c15bd7de	04add92b-320e-444e-8f0a-0c3a5cd3dbe0	2	20	f	\N	\N
f6e41720-cd3f-46b1-9573-9a4eba15fa18	\N	direct-grant-validate-username	c43fd595-cebd-4062-ac8d-8943c15bd7de	aad40e8a-8f47-4600-b2e1-403a3621dcb1	0	10	f	\N	\N
91ec8500-f61c-4fd7-a718-9949ca0420f3	\N	direct-grant-validate-password	c43fd595-cebd-4062-ac8d-8943c15bd7de	aad40e8a-8f47-4600-b2e1-403a3621dcb1	0	20	f	\N	\N
1f462e04-16f7-45ea-9dcb-36a4344b9328	\N	\N	c43fd595-cebd-4062-ac8d-8943c15bd7de	aad40e8a-8f47-4600-b2e1-403a3621dcb1	1	30	t	e23e60e5-ae0c-454d-8bdc-3fe461d73b13	\N
2200376d-c536-4b17-b7b2-3e10039343d3	\N	conditional-user-configured	c43fd595-cebd-4062-ac8d-8943c15bd7de	e23e60e5-ae0c-454d-8bdc-3fe461d73b13	0	10	f	\N	\N
536b0d44-fa03-4398-8f3b-f175f3539e91	\N	direct-grant-validate-otp	c43fd595-cebd-4062-ac8d-8943c15bd7de	e23e60e5-ae0c-454d-8bdc-3fe461d73b13	0	20	f	\N	\N
fc83c341-5b9d-44f9-afd0-f5407015772b	\N	registration-page-form	c43fd595-cebd-4062-ac8d-8943c15bd7de	ff8c6b13-ca94-41cc-9bb5-d586330e110b	0	10	t	98a06969-41cd-408e-9bb1-875de4aaffc7	\N
ff79bef0-bcce-43e2-b3cc-98e7168942a8	\N	registration-user-creation	c43fd595-cebd-4062-ac8d-8943c15bd7de	98a06969-41cd-408e-9bb1-875de4aaffc7	0	20	f	\N	\N
3d1b8496-4cf8-4a67-81ee-8a4e6f5b0b09	\N	registration-password-action	c43fd595-cebd-4062-ac8d-8943c15bd7de	98a06969-41cd-408e-9bb1-875de4aaffc7	0	50	f	\N	\N
f54bfe64-ff87-439a-8eb2-f6fd51b742c5	\N	registration-recaptcha-action	c43fd595-cebd-4062-ac8d-8943c15bd7de	98a06969-41cd-408e-9bb1-875de4aaffc7	3	60	f	\N	\N
e83d39c8-bfb4-493e-b791-7467fccfde60	\N	registration-terms-and-conditions	c43fd595-cebd-4062-ac8d-8943c15bd7de	98a06969-41cd-408e-9bb1-875de4aaffc7	3	70	f	\N	\N
55520096-7173-4c5f-9f05-c25af436aec2	\N	reset-credentials-choose-user	c43fd595-cebd-4062-ac8d-8943c15bd7de	637683bb-96f5-4dd7-b144-befa65b3407c	0	10	f	\N	\N
ab986431-2b44-4661-98fc-b703145bb072	\N	reset-credential-email	c43fd595-cebd-4062-ac8d-8943c15bd7de	637683bb-96f5-4dd7-b144-befa65b3407c	0	20	f	\N	\N
37b01334-f61a-41ac-b4ef-fcb972ff18fa	\N	reset-password	c43fd595-cebd-4062-ac8d-8943c15bd7de	637683bb-96f5-4dd7-b144-befa65b3407c	0	30	f	\N	\N
53182842-37ec-4124-8f50-d2edebadb624	\N	\N	c43fd595-cebd-4062-ac8d-8943c15bd7de	637683bb-96f5-4dd7-b144-befa65b3407c	1	40	t	a0076752-9cce-4169-ae7b-6349ad29217e	\N
206d9a80-b324-4dcb-8e83-338e7a42fb38	\N	conditional-user-configured	c43fd595-cebd-4062-ac8d-8943c15bd7de	a0076752-9cce-4169-ae7b-6349ad29217e	0	10	f	\N	\N
f20339fa-a188-4a8f-981f-953642500858	\N	reset-otp	c43fd595-cebd-4062-ac8d-8943c15bd7de	a0076752-9cce-4169-ae7b-6349ad29217e	0	20	f	\N	\N
11c4c7d8-6a7e-4cbc-a382-5447f0069241	\N	client-secret	c43fd595-cebd-4062-ac8d-8943c15bd7de	b07f243e-78de-488b-bafa-aa450f0f13e7	2	10	f	\N	\N
ba30c447-6c82-4aca-b6ae-6a702c84e410	\N	client-jwt	c43fd595-cebd-4062-ac8d-8943c15bd7de	b07f243e-78de-488b-bafa-aa450f0f13e7	2	20	f	\N	\N
ec9ee4b8-3560-40cf-a971-7752e5936009	\N	client-secret-jwt	c43fd595-cebd-4062-ac8d-8943c15bd7de	b07f243e-78de-488b-bafa-aa450f0f13e7	2	30	f	\N	\N
d7c4984f-a1b6-4f1d-9a78-21fa8e8fb11f	\N	client-x509	c43fd595-cebd-4062-ac8d-8943c15bd7de	b07f243e-78de-488b-bafa-aa450f0f13e7	2	40	f	\N	\N
7523c2c8-e3a3-46e9-849a-d8dcd80331f6	\N	idp-review-profile	c43fd595-cebd-4062-ac8d-8943c15bd7de	6bda4140-643b-4da5-9a8f-c13095bd3054	0	10	f	\N	d0344bbb-cd6a-4caa-a252-86c96a92c09a
34560b64-7663-463a-9316-fdb90ee88371	\N	\N	c43fd595-cebd-4062-ac8d-8943c15bd7de	6bda4140-643b-4da5-9a8f-c13095bd3054	0	20	t	9c6ee7fe-b7a9-4e8a-920a-36c8a61dcb00	\N
1cf7f248-ad7b-4599-8191-fb9e746b846c	\N	idp-create-user-if-unique	c43fd595-cebd-4062-ac8d-8943c15bd7de	9c6ee7fe-b7a9-4e8a-920a-36c8a61dcb00	2	10	f	\N	1f16fb8a-6591-42a0-b5cb-6aa270d1984b
39427dc4-396f-4db7-aa7f-6d99783bc1dd	\N	\N	c43fd595-cebd-4062-ac8d-8943c15bd7de	9c6ee7fe-b7a9-4e8a-920a-36c8a61dcb00	2	20	t	823fbc7c-da41-4a69-adbf-fba10391ed60	\N
4ef4aad4-518f-4a5f-9943-5030ff52a6aa	\N	idp-confirm-link	c43fd595-cebd-4062-ac8d-8943c15bd7de	823fbc7c-da41-4a69-adbf-fba10391ed60	0	10	f	\N	\N
31eeebba-a642-44ca-879c-2a53bb2a8230	\N	\N	c43fd595-cebd-4062-ac8d-8943c15bd7de	823fbc7c-da41-4a69-adbf-fba10391ed60	0	20	t	4a5ee04d-8a67-4e2e-bea5-9ad2688f134b	\N
ba67e56e-32c5-43e6-9d80-387507541c23	\N	idp-email-verification	c43fd595-cebd-4062-ac8d-8943c15bd7de	4a5ee04d-8a67-4e2e-bea5-9ad2688f134b	2	10	f	\N	\N
cb901bb1-5387-4bf7-94d4-742ecb18312d	\N	\N	c43fd595-cebd-4062-ac8d-8943c15bd7de	4a5ee04d-8a67-4e2e-bea5-9ad2688f134b	2	20	t	5d723a6f-1294-4fca-85e3-9c2b1592c0b5	\N
9b965f0e-9dc6-4554-818b-e9cad3b58687	\N	idp-username-password-form	c43fd595-cebd-4062-ac8d-8943c15bd7de	5d723a6f-1294-4fca-85e3-9c2b1592c0b5	0	10	f	\N	\N
9485fff8-9a3a-4377-b50a-3d9f896d957d	\N	\N	c43fd595-cebd-4062-ac8d-8943c15bd7de	5d723a6f-1294-4fca-85e3-9c2b1592c0b5	1	20	t	704ec24a-c5a0-4d2f-ad17-9ac413635856	\N
9d9dd95a-d186-41fc-8838-9e404f2248c8	\N	conditional-user-configured	c43fd595-cebd-4062-ac8d-8943c15bd7de	704ec24a-c5a0-4d2f-ad17-9ac413635856	0	10	f	\N	\N
7ae8e1d2-60ae-4a32-88da-5db6019ff055	\N	auth-otp-form	c43fd595-cebd-4062-ac8d-8943c15bd7de	704ec24a-c5a0-4d2f-ad17-9ac413635856	0	20	f	\N	\N
d5e6c83a-fc17-48d1-8dae-4f6b80060aac	\N	\N	c43fd595-cebd-4062-ac8d-8943c15bd7de	6bda4140-643b-4da5-9a8f-c13095bd3054	1	50	t	f30c3567-85ba-42c1-b025-4ab1c6ee434e	\N
d00beac8-0fdb-46fc-873b-58d505c84a98	\N	conditional-user-configured	c43fd595-cebd-4062-ac8d-8943c15bd7de	f30c3567-85ba-42c1-b025-4ab1c6ee434e	0	10	f	\N	\N
d5114f85-42f0-483a-95c0-502ac4ccfc53	\N	idp-add-organization-member	c43fd595-cebd-4062-ac8d-8943c15bd7de	f30c3567-85ba-42c1-b025-4ab1c6ee434e	0	20	f	\N	\N
a5142de7-6ab4-414e-94d1-8c6572ef0225	\N	http-basic-authenticator	c43fd595-cebd-4062-ac8d-8943c15bd7de	098dc41f-a119-43f3-b11b-150cc050760a	0	10	f	\N	\N
fd90a88f-6076-4542-a57b-890e8672b250	\N	docker-http-basic-authenticator	c43fd595-cebd-4062-ac8d-8943c15bd7de	b8b3f788-8f30-4a90-9042-4ae0cada4c34	0	10	f	\N	\N
\.


--
-- Data for Name: authentication_flow; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.authentication_flow (id, alias, description, realm_id, provider_id, top_level, built_in) FROM stdin;
f2312c70-9614-4eb7-aabc-743a2aee10ea	browser	Browser based authentication	818f97fc-7b35-4bae-bdd7-76b5035dce69	basic-flow	t	t
2e8b98d5-d9d8-420b-8ac6-a3f69b6a6975	forms	Username, password, otp and other auth forms.	818f97fc-7b35-4bae-bdd7-76b5035dce69	basic-flow	f	t
48bb540e-142a-447e-980c-1363b97fdd80	Browser - Conditional OTP	Flow to determine if the OTP is required for the authentication	818f97fc-7b35-4bae-bdd7-76b5035dce69	basic-flow	f	t
8f281943-5ab4-431a-b40d-d01be9ffcb56	direct grant	OpenID Connect Resource Owner Grant	818f97fc-7b35-4bae-bdd7-76b5035dce69	basic-flow	t	t
a73a7981-b62d-4970-8ecf-aa820a1cc0f8	Direct Grant - Conditional OTP	Flow to determine if the OTP is required for the authentication	818f97fc-7b35-4bae-bdd7-76b5035dce69	basic-flow	f	t
3e041e39-2fdc-45b6-b800-5e01f588181a	registration	Registration flow	818f97fc-7b35-4bae-bdd7-76b5035dce69	basic-flow	t	t
b300fcfc-7599-4071-b0c0-473f223afb87	registration form	Registration form	818f97fc-7b35-4bae-bdd7-76b5035dce69	form-flow	f	t
f7275f2d-27e4-4132-a896-9a07c914e15c	reset credentials	Reset credentials for a user if they forgot their password or something	818f97fc-7b35-4bae-bdd7-76b5035dce69	basic-flow	t	t
665c4748-d0e4-4344-b4a9-c27f8788ac74	Reset - Conditional OTP	Flow to determine if the OTP should be reset or not. Set to REQUIRED to force.	818f97fc-7b35-4bae-bdd7-76b5035dce69	basic-flow	f	t
78e843e8-00a3-4ad6-a834-55492827b279	clients	Base authentication for clients	818f97fc-7b35-4bae-bdd7-76b5035dce69	client-flow	t	t
7f97d87f-3f8d-4f78-ac23-467c123140b6	first broker login	Actions taken after first broker login with identity provider account, which is not yet linked to any Keycloak account	818f97fc-7b35-4bae-bdd7-76b5035dce69	basic-flow	t	t
0a489445-bca8-481f-ae5c-e370d2da632b	User creation or linking	Flow for the existing/non-existing user alternatives	818f97fc-7b35-4bae-bdd7-76b5035dce69	basic-flow	f	t
b8558172-8ad4-44a5-9707-d2470da99751	Handle Existing Account	Handle what to do if there is existing account with same email/username like authenticated identity provider	818f97fc-7b35-4bae-bdd7-76b5035dce69	basic-flow	f	t
191081f7-60bd-45d8-871c-eef88f59ca8d	Account verification options	Method with which to verity the existing account	818f97fc-7b35-4bae-bdd7-76b5035dce69	basic-flow	f	t
ff3dbcb2-8c45-45ce-a674-16cd980323d4	Verify Existing Account by Re-authentication	Reauthentication of existing account	818f97fc-7b35-4bae-bdd7-76b5035dce69	basic-flow	f	t
7f665693-b554-4497-9fcc-4a9761809012	First broker login - Conditional OTP	Flow to determine if the OTP is required for the authentication	818f97fc-7b35-4bae-bdd7-76b5035dce69	basic-flow	f	t
a4f16621-5451-4d59-9540-e8c18a2cca77	saml ecp	SAML ECP Profile Authentication Flow	818f97fc-7b35-4bae-bdd7-76b5035dce69	basic-flow	t	t
6dd75b98-03e9-4093-b9c1-35554173afeb	docker auth	Used by Docker clients to authenticate against the IDP	818f97fc-7b35-4bae-bdd7-76b5035dce69	basic-flow	t	t
56d6e51d-f268-4e22-8606-8c6ce35bdd8d	browser	Browser based authentication	c43fd595-cebd-4062-ac8d-8943c15bd7de	basic-flow	t	t
e9449abc-0d54-41f5-8c9f-092a8afdb512	forms	Username, password, otp and other auth forms.	c43fd595-cebd-4062-ac8d-8943c15bd7de	basic-flow	f	t
925a8eb2-33c3-497f-a344-f95debba0b43	Browser - Conditional OTP	Flow to determine if the OTP is required for the authentication	c43fd595-cebd-4062-ac8d-8943c15bd7de	basic-flow	f	t
b5075bf0-36f2-47fe-8e6a-8ad5167cde50	Organization	\N	c43fd595-cebd-4062-ac8d-8943c15bd7de	basic-flow	f	t
04add92b-320e-444e-8f0a-0c3a5cd3dbe0	Browser - Conditional Organization	Flow to determine if the organization identity-first login is to be used	c43fd595-cebd-4062-ac8d-8943c15bd7de	basic-flow	f	t
aad40e8a-8f47-4600-b2e1-403a3621dcb1	direct grant	OpenID Connect Resource Owner Grant	c43fd595-cebd-4062-ac8d-8943c15bd7de	basic-flow	t	t
e23e60e5-ae0c-454d-8bdc-3fe461d73b13	Direct Grant - Conditional OTP	Flow to determine if the OTP is required for the authentication	c43fd595-cebd-4062-ac8d-8943c15bd7de	basic-flow	f	t
ff8c6b13-ca94-41cc-9bb5-d586330e110b	registration	Registration flow	c43fd595-cebd-4062-ac8d-8943c15bd7de	basic-flow	t	t
98a06969-41cd-408e-9bb1-875de4aaffc7	registration form	Registration form	c43fd595-cebd-4062-ac8d-8943c15bd7de	form-flow	f	t
637683bb-96f5-4dd7-b144-befa65b3407c	reset credentials	Reset credentials for a user if they forgot their password or something	c43fd595-cebd-4062-ac8d-8943c15bd7de	basic-flow	t	t
a0076752-9cce-4169-ae7b-6349ad29217e	Reset - Conditional OTP	Flow to determine if the OTP should be reset or not. Set to REQUIRED to force.	c43fd595-cebd-4062-ac8d-8943c15bd7de	basic-flow	f	t
b07f243e-78de-488b-bafa-aa450f0f13e7	clients	Base authentication for clients	c43fd595-cebd-4062-ac8d-8943c15bd7de	client-flow	t	t
6bda4140-643b-4da5-9a8f-c13095bd3054	first broker login	Actions taken after first broker login with identity provider account, which is not yet linked to any Keycloak account	c43fd595-cebd-4062-ac8d-8943c15bd7de	basic-flow	t	t
9c6ee7fe-b7a9-4e8a-920a-36c8a61dcb00	User creation or linking	Flow for the existing/non-existing user alternatives	c43fd595-cebd-4062-ac8d-8943c15bd7de	basic-flow	f	t
823fbc7c-da41-4a69-adbf-fba10391ed60	Handle Existing Account	Handle what to do if there is existing account with same email/username like authenticated identity provider	c43fd595-cebd-4062-ac8d-8943c15bd7de	basic-flow	f	t
4a5ee04d-8a67-4e2e-bea5-9ad2688f134b	Account verification options	Method with which to verity the existing account	c43fd595-cebd-4062-ac8d-8943c15bd7de	basic-flow	f	t
5d723a6f-1294-4fca-85e3-9c2b1592c0b5	Verify Existing Account by Re-authentication	Reauthentication of existing account	c43fd595-cebd-4062-ac8d-8943c15bd7de	basic-flow	f	t
704ec24a-c5a0-4d2f-ad17-9ac413635856	First broker login - Conditional OTP	Flow to determine if the OTP is required for the authentication	c43fd595-cebd-4062-ac8d-8943c15bd7de	basic-flow	f	t
f30c3567-85ba-42c1-b025-4ab1c6ee434e	First Broker Login - Conditional Organization	Flow to determine if the authenticator that adds organization members is to be used	c43fd595-cebd-4062-ac8d-8943c15bd7de	basic-flow	f	t
098dc41f-a119-43f3-b11b-150cc050760a	saml ecp	SAML ECP Profile Authentication Flow	c43fd595-cebd-4062-ac8d-8943c15bd7de	basic-flow	t	t
b8b3f788-8f30-4a90-9042-4ae0cada4c34	docker auth	Used by Docker clients to authenticate against the IDP	c43fd595-cebd-4062-ac8d-8943c15bd7de	basic-flow	t	t
\.


--
-- Data for Name: authenticator_config; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.authenticator_config (id, alias, realm_id) FROM stdin;
38d428db-e248-4210-ba29-36d922bb93f9	review profile config	818f97fc-7b35-4bae-bdd7-76b5035dce69
cf86ba59-b58f-40fe-b439-ebb173736284	create unique user config	818f97fc-7b35-4bae-bdd7-76b5035dce69
d0344bbb-cd6a-4caa-a252-86c96a92c09a	review profile config	c43fd595-cebd-4062-ac8d-8943c15bd7de
1f16fb8a-6591-42a0-b5cb-6aa270d1984b	create unique user config	c43fd595-cebd-4062-ac8d-8943c15bd7de
\.


--
-- Data for Name: authenticator_config_entry; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.authenticator_config_entry (authenticator_id, value, name) FROM stdin;
38d428db-e248-4210-ba29-36d922bb93f9	missing	update.profile.on.first.login
cf86ba59-b58f-40fe-b439-ebb173736284	false	require.password.update.after.registration
1f16fb8a-6591-42a0-b5cb-6aa270d1984b	false	require.password.update.after.registration
d0344bbb-cd6a-4caa-a252-86c96a92c09a	missing	update.profile.on.first.login
\.


--
-- Data for Name: broker_link; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.broker_link (identity_provider, storage_provider_id, realm_id, broker_user_id, broker_username, token, user_id) FROM stdin;
\.


--
-- Data for Name: client; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.client (id, enabled, full_scope_allowed, client_id, not_before, public_client, secret, base_url, bearer_only, management_url, surrogate_auth_required, realm_id, protocol, node_rereg_timeout, frontchannel_logout, consent_required, name, service_accounts_enabled, client_authenticator_type, root_url, description, registration_token, standard_flow_enabled, implicit_flow_enabled, direct_access_grants_enabled, always_display_in_console) FROM stdin;
b62619cb-bb63-4412-a21c-5daadfdd4996	t	f	master-realm	0	f	\N	\N	t	\N	f	818f97fc-7b35-4bae-bdd7-76b5035dce69	\N	0	f	f	master Realm	f	client-secret	\N	\N	\N	t	f	f	f
c98fef2f-a93f-4a7e-9aa9-2fdc9ac7f43b	t	f	account	0	t	\N	/realms/master/account/	f	\N	f	818f97fc-7b35-4bae-bdd7-76b5035dce69	openid-connect	0	f	f	${client_account}	f	client-secret	${authBaseUrl}	\N	\N	t	f	f	f
3ed9a267-16aa-4320-b9a3-2d38f6ad94fa	t	f	account-console	0	t	\N	/realms/master/account/	f	\N	f	818f97fc-7b35-4bae-bdd7-76b5035dce69	openid-connect	0	f	f	${client_account-console}	f	client-secret	${authBaseUrl}	\N	\N	t	f	f	f
7ab4da4d-4c05-421f-95b8-b794e97f5393	t	f	broker	0	f	\N	\N	t	\N	f	818f97fc-7b35-4bae-bdd7-76b5035dce69	openid-connect	0	f	f	${client_broker}	f	client-secret	\N	\N	\N	t	f	f	f
1e451ada-fbdd-4a7f-ad23-b5229b440909	t	t	security-admin-console	0	t	\N	/admin/master/console/	f	\N	f	818f97fc-7b35-4bae-bdd7-76b5035dce69	openid-connect	0	f	f	${client_security-admin-console}	f	client-secret	${authAdminUrl}	\N	\N	t	f	f	f
074f0272-7808-4075-81e8-128c21528edb	t	t	admin-cli	0	t	\N	\N	f	\N	f	818f97fc-7b35-4bae-bdd7-76b5035dce69	openid-connect	0	f	f	${client_admin-cli}	f	client-secret	\N	\N	\N	f	f	t	f
e16e81cd-d3d9-497a-af75-107cbf3c4a34	t	f	eaf-test-realm	0	f	\N	\N	t	\N	f	818f97fc-7b35-4bae-bdd7-76b5035dce69	\N	0	f	f	eaf-test Realm	f	client-secret	\N	\N	\N	t	f	f	f
c3f69b94-df50-4fef-8e18-636bb984c0f4	t	f	realm-management	0	f	\N	\N	t	\N	f	c43fd595-cebd-4062-ac8d-8943c15bd7de	openid-connect	0	f	f	${client_realm-management}	f	client-secret	\N	\N	\N	t	f	f	f
26103ba8-ed16-476a-be4f-1b15363631d0	t	f	account	0	t	\N	/realms/eaf-test/account/	f	\N	f	c43fd595-cebd-4062-ac8d-8943c15bd7de	openid-connect	0	f	f	${client_account}	f	client-secret	${authBaseUrl}	\N	\N	t	f	f	f
73f93ba6-a168-4560-8a23-1522043206c0	t	f	account-console	0	t	\N	/realms/eaf-test/account/	f	\N	f	c43fd595-cebd-4062-ac8d-8943c15bd7de	openid-connect	0	f	f	${client_account-console}	f	client-secret	${authBaseUrl}	\N	\N	t	f	f	f
b95ff58d-6af0-4fa2-a73e-5381a7533c37	t	f	broker	0	f	\N	\N	t	\N	f	c43fd595-cebd-4062-ac8d-8943c15bd7de	openid-connect	0	f	f	${client_broker}	f	client-secret	\N	\N	\N	t	f	f	f
9bb712ff-a545-4dbd-9df3-3ac5e67dd486	t	t	security-admin-console	0	t	\N	/admin/eaf-test/console/	f	\N	f	c43fd595-cebd-4062-ac8d-8943c15bd7de	openid-connect	0	f	f	${client_security-admin-console}	f	client-secret	${authAdminUrl}	\N	\N	t	f	f	f
aacd80cd-18d9-41c6-b783-c8020389ed96	t	t	admin-cli	0	t	\N	\N	f	\N	f	c43fd595-cebd-4062-ac8d-8943c15bd7de	openid-connect	0	f	f	${client_admin-cli}	f	client-secret	\N	\N	\N	f	f	t	f
\.


--
-- Data for Name: client_attributes; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.client_attributes (client_id, name, value) FROM stdin;
c98fef2f-a93f-4a7e-9aa9-2fdc9ac7f43b	post.logout.redirect.uris	+
3ed9a267-16aa-4320-b9a3-2d38f6ad94fa	post.logout.redirect.uris	+
3ed9a267-16aa-4320-b9a3-2d38f6ad94fa	pkce.code.challenge.method	S256
1e451ada-fbdd-4a7f-ad23-b5229b440909	post.logout.redirect.uris	+
1e451ada-fbdd-4a7f-ad23-b5229b440909	pkce.code.challenge.method	S256
1e451ada-fbdd-4a7f-ad23-b5229b440909	client.use.lightweight.access.token.enabled	true
074f0272-7808-4075-81e8-128c21528edb	client.use.lightweight.access.token.enabled	true
26103ba8-ed16-476a-be4f-1b15363631d0	post.logout.redirect.uris	+
73f93ba6-a168-4560-8a23-1522043206c0	post.logout.redirect.uris	+
73f93ba6-a168-4560-8a23-1522043206c0	pkce.code.challenge.method	S256
9bb712ff-a545-4dbd-9df3-3ac5e67dd486	post.logout.redirect.uris	+
9bb712ff-a545-4dbd-9df3-3ac5e67dd486	pkce.code.challenge.method	S256
9bb712ff-a545-4dbd-9df3-3ac5e67dd486	client.use.lightweight.access.token.enabled	true
aacd80cd-18d9-41c6-b783-c8020389ed96	client.use.lightweight.access.token.enabled	true
\.


--
-- Data for Name: client_auth_flow_bindings; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.client_auth_flow_bindings (client_id, flow_id, binding_name) FROM stdin;
\.


--
-- Data for Name: client_initial_access; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.client_initial_access (id, realm_id, "timestamp", expiration, count, remaining_count) FROM stdin;
\.


--
-- Data for Name: client_node_registrations; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.client_node_registrations (client_id, value, name) FROM stdin;
\.


--
-- Data for Name: client_scope; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.client_scope (id, name, realm_id, description, protocol) FROM stdin;
30a1a6be-de4f-4589-9ef2-83e83833e01d	offline_access	818f97fc-7b35-4bae-bdd7-76b5035dce69	OpenID Connect built-in scope: offline_access	openid-connect
ed16778f-75a9-49ca-8f43-08546fa6b7f9	role_list	818f97fc-7b35-4bae-bdd7-76b5035dce69	SAML role list	saml
64bce3f2-8039-4e6e-8bba-8a09f5fa01f7	saml_organization	818f97fc-7b35-4bae-bdd7-76b5035dce69	Organization Membership	saml
ffce7315-b3dd-4dbd-bae9-2113339971e9	profile	818f97fc-7b35-4bae-bdd7-76b5035dce69	OpenID Connect built-in scope: profile	openid-connect
b8b4a138-8568-40f9-ae77-640d689ee351	email	818f97fc-7b35-4bae-bdd7-76b5035dce69	OpenID Connect built-in scope: email	openid-connect
88572d2f-b484-4e8a-a07c-5142e7c3d652	address	818f97fc-7b35-4bae-bdd7-76b5035dce69	OpenID Connect built-in scope: address	openid-connect
08aeccde-4fee-4f6e-8c1d-de4b798be445	phone	818f97fc-7b35-4bae-bdd7-76b5035dce69	OpenID Connect built-in scope: phone	openid-connect
10d2d5eb-ef10-4929-87c6-cbf2ba34dbf8	roles	818f97fc-7b35-4bae-bdd7-76b5035dce69	OpenID Connect scope for add user roles to the access token	openid-connect
170f72d8-f885-40a8-9a54-c2de31fb86a9	web-origins	818f97fc-7b35-4bae-bdd7-76b5035dce69	OpenID Connect scope for add allowed web origins to the access token	openid-connect
491f228c-d4ff-4558-a930-a1827af3d97c	microprofile-jwt	818f97fc-7b35-4bae-bdd7-76b5035dce69	Microprofile - JWT built-in scope	openid-connect
3f3195c0-df74-4157-a3b3-4888e23686c4	acr	818f97fc-7b35-4bae-bdd7-76b5035dce69	OpenID Connect scope for add acr (authentication context class reference) to the token	openid-connect
5aefe0fb-a5e0-4af2-8c61-4aced9ca12df	basic	818f97fc-7b35-4bae-bdd7-76b5035dce69	OpenID Connect scope for add all basic claims to the token	openid-connect
648f874f-c49f-486c-9018-383f959ccb95	organization	818f97fc-7b35-4bae-bdd7-76b5035dce69	Additional claims about the organization a subject belongs to	openid-connect
e1c1fbc7-6160-473c-aaa2-9c3c48563692	offline_access	c43fd595-cebd-4062-ac8d-8943c15bd7de	OpenID Connect built-in scope: offline_access	openid-connect
8344e632-4402-4372-9ad9-b84c5c71cf42	role_list	c43fd595-cebd-4062-ac8d-8943c15bd7de	SAML role list	saml
df00c71c-a425-442d-bea1-d0572fa8c9a7	saml_organization	c43fd595-cebd-4062-ac8d-8943c15bd7de	Organization Membership	saml
41fba862-fab4-4ad5-b49e-9801130b762e	profile	c43fd595-cebd-4062-ac8d-8943c15bd7de	OpenID Connect built-in scope: profile	openid-connect
50a5108d-53c8-4592-8720-28aecb224e47	email	c43fd595-cebd-4062-ac8d-8943c15bd7de	OpenID Connect built-in scope: email	openid-connect
51d4c7f8-6f73-434b-b638-07809009cb75	address	c43fd595-cebd-4062-ac8d-8943c15bd7de	OpenID Connect built-in scope: address	openid-connect
dba36531-8daf-4849-bf80-ff8ef431ebdb	phone	c43fd595-cebd-4062-ac8d-8943c15bd7de	OpenID Connect built-in scope: phone	openid-connect
98aae9b8-0b25-4b90-a9dc-a0f157e2afe2	roles	c43fd595-cebd-4062-ac8d-8943c15bd7de	OpenID Connect scope for add user roles to the access token	openid-connect
a809a4eb-789f-4cdd-b800-2a2a2b1ee9c5	web-origins	c43fd595-cebd-4062-ac8d-8943c15bd7de	OpenID Connect scope for add allowed web origins to the access token	openid-connect
91dd31ee-b71e-457d-a181-3cb748c0b729	microprofile-jwt	c43fd595-cebd-4062-ac8d-8943c15bd7de	Microprofile - JWT built-in scope	openid-connect
16e8f9e7-0b2b-457c-87f9-149e032aeaf7	acr	c43fd595-cebd-4062-ac8d-8943c15bd7de	OpenID Connect scope for add acr (authentication context class reference) to the token	openid-connect
a1f423bd-7673-4235-8124-5460a1225ad3	basic	c43fd595-cebd-4062-ac8d-8943c15bd7de	OpenID Connect scope for add all basic claims to the token	openid-connect
e3abe23c-a247-4e24-bcc6-c42a3d2b8da3	organization	c43fd595-cebd-4062-ac8d-8943c15bd7de	Additional claims about the organization a subject belongs to	openid-connect
\.


--
-- Data for Name: client_scope_attributes; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.client_scope_attributes (scope_id, value, name) FROM stdin;
30a1a6be-de4f-4589-9ef2-83e83833e01d	true	display.on.consent.screen
30a1a6be-de4f-4589-9ef2-83e83833e01d	${offlineAccessScopeConsentText}	consent.screen.text
ed16778f-75a9-49ca-8f43-08546fa6b7f9	true	display.on.consent.screen
ed16778f-75a9-49ca-8f43-08546fa6b7f9	${samlRoleListScopeConsentText}	consent.screen.text
64bce3f2-8039-4e6e-8bba-8a09f5fa01f7	false	display.on.consent.screen
ffce7315-b3dd-4dbd-bae9-2113339971e9	true	display.on.consent.screen
ffce7315-b3dd-4dbd-bae9-2113339971e9	${profileScopeConsentText}	consent.screen.text
ffce7315-b3dd-4dbd-bae9-2113339971e9	true	include.in.token.scope
b8b4a138-8568-40f9-ae77-640d689ee351	true	display.on.consent.screen
b8b4a138-8568-40f9-ae77-640d689ee351	${emailScopeConsentText}	consent.screen.text
b8b4a138-8568-40f9-ae77-640d689ee351	true	include.in.token.scope
88572d2f-b484-4e8a-a07c-5142e7c3d652	true	display.on.consent.screen
88572d2f-b484-4e8a-a07c-5142e7c3d652	${addressScopeConsentText}	consent.screen.text
88572d2f-b484-4e8a-a07c-5142e7c3d652	true	include.in.token.scope
08aeccde-4fee-4f6e-8c1d-de4b798be445	true	display.on.consent.screen
08aeccde-4fee-4f6e-8c1d-de4b798be445	${phoneScopeConsentText}	consent.screen.text
08aeccde-4fee-4f6e-8c1d-de4b798be445	true	include.in.token.scope
10d2d5eb-ef10-4929-87c6-cbf2ba34dbf8	true	display.on.consent.screen
10d2d5eb-ef10-4929-87c6-cbf2ba34dbf8	${rolesScopeConsentText}	consent.screen.text
10d2d5eb-ef10-4929-87c6-cbf2ba34dbf8	false	include.in.token.scope
170f72d8-f885-40a8-9a54-c2de31fb86a9	false	display.on.consent.screen
170f72d8-f885-40a8-9a54-c2de31fb86a9		consent.screen.text
170f72d8-f885-40a8-9a54-c2de31fb86a9	false	include.in.token.scope
491f228c-d4ff-4558-a930-a1827af3d97c	false	display.on.consent.screen
491f228c-d4ff-4558-a930-a1827af3d97c	true	include.in.token.scope
3f3195c0-df74-4157-a3b3-4888e23686c4	false	display.on.consent.screen
3f3195c0-df74-4157-a3b3-4888e23686c4	false	include.in.token.scope
5aefe0fb-a5e0-4af2-8c61-4aced9ca12df	false	display.on.consent.screen
5aefe0fb-a5e0-4af2-8c61-4aced9ca12df	false	include.in.token.scope
648f874f-c49f-486c-9018-383f959ccb95	true	display.on.consent.screen
648f874f-c49f-486c-9018-383f959ccb95	${organizationScopeConsentText}	consent.screen.text
648f874f-c49f-486c-9018-383f959ccb95	true	include.in.token.scope
e1c1fbc7-6160-473c-aaa2-9c3c48563692	true	display.on.consent.screen
e1c1fbc7-6160-473c-aaa2-9c3c48563692	${offlineAccessScopeConsentText}	consent.screen.text
8344e632-4402-4372-9ad9-b84c5c71cf42	true	display.on.consent.screen
8344e632-4402-4372-9ad9-b84c5c71cf42	${samlRoleListScopeConsentText}	consent.screen.text
df00c71c-a425-442d-bea1-d0572fa8c9a7	false	display.on.consent.screen
41fba862-fab4-4ad5-b49e-9801130b762e	true	display.on.consent.screen
41fba862-fab4-4ad5-b49e-9801130b762e	${profileScopeConsentText}	consent.screen.text
41fba862-fab4-4ad5-b49e-9801130b762e	true	include.in.token.scope
50a5108d-53c8-4592-8720-28aecb224e47	true	display.on.consent.screen
50a5108d-53c8-4592-8720-28aecb224e47	${emailScopeConsentText}	consent.screen.text
50a5108d-53c8-4592-8720-28aecb224e47	true	include.in.token.scope
51d4c7f8-6f73-434b-b638-07809009cb75	true	display.on.consent.screen
51d4c7f8-6f73-434b-b638-07809009cb75	${addressScopeConsentText}	consent.screen.text
51d4c7f8-6f73-434b-b638-07809009cb75	true	include.in.token.scope
dba36531-8daf-4849-bf80-ff8ef431ebdb	true	display.on.consent.screen
dba36531-8daf-4849-bf80-ff8ef431ebdb	${phoneScopeConsentText}	consent.screen.text
dba36531-8daf-4849-bf80-ff8ef431ebdb	true	include.in.token.scope
98aae9b8-0b25-4b90-a9dc-a0f157e2afe2	true	display.on.consent.screen
98aae9b8-0b25-4b90-a9dc-a0f157e2afe2	${rolesScopeConsentText}	consent.screen.text
98aae9b8-0b25-4b90-a9dc-a0f157e2afe2	false	include.in.token.scope
a809a4eb-789f-4cdd-b800-2a2a2b1ee9c5	false	display.on.consent.screen
a809a4eb-789f-4cdd-b800-2a2a2b1ee9c5		consent.screen.text
a809a4eb-789f-4cdd-b800-2a2a2b1ee9c5	false	include.in.token.scope
91dd31ee-b71e-457d-a181-3cb748c0b729	false	display.on.consent.screen
91dd31ee-b71e-457d-a181-3cb748c0b729	true	include.in.token.scope
16e8f9e7-0b2b-457c-87f9-149e032aeaf7	false	display.on.consent.screen
16e8f9e7-0b2b-457c-87f9-149e032aeaf7	false	include.in.token.scope
a1f423bd-7673-4235-8124-5460a1225ad3	false	display.on.consent.screen
a1f423bd-7673-4235-8124-5460a1225ad3	false	include.in.token.scope
e3abe23c-a247-4e24-bcc6-c42a3d2b8da3	true	display.on.consent.screen
e3abe23c-a247-4e24-bcc6-c42a3d2b8da3	${organizationScopeConsentText}	consent.screen.text
e3abe23c-a247-4e24-bcc6-c42a3d2b8da3	true	include.in.token.scope
\.


--
-- Data for Name: client_scope_client; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.client_scope_client (client_id, scope_id, default_scope) FROM stdin;
c98fef2f-a93f-4a7e-9aa9-2fdc9ac7f43b	10d2d5eb-ef10-4929-87c6-cbf2ba34dbf8	t
c98fef2f-a93f-4a7e-9aa9-2fdc9ac7f43b	b8b4a138-8568-40f9-ae77-640d689ee351	t
c98fef2f-a93f-4a7e-9aa9-2fdc9ac7f43b	170f72d8-f885-40a8-9a54-c2de31fb86a9	t
c98fef2f-a93f-4a7e-9aa9-2fdc9ac7f43b	ffce7315-b3dd-4dbd-bae9-2113339971e9	t
c98fef2f-a93f-4a7e-9aa9-2fdc9ac7f43b	3f3195c0-df74-4157-a3b3-4888e23686c4	t
c98fef2f-a93f-4a7e-9aa9-2fdc9ac7f43b	5aefe0fb-a5e0-4af2-8c61-4aced9ca12df	t
c98fef2f-a93f-4a7e-9aa9-2fdc9ac7f43b	648f874f-c49f-486c-9018-383f959ccb95	f
c98fef2f-a93f-4a7e-9aa9-2fdc9ac7f43b	30a1a6be-de4f-4589-9ef2-83e83833e01d	f
c98fef2f-a93f-4a7e-9aa9-2fdc9ac7f43b	08aeccde-4fee-4f6e-8c1d-de4b798be445	f
c98fef2f-a93f-4a7e-9aa9-2fdc9ac7f43b	88572d2f-b484-4e8a-a07c-5142e7c3d652	f
c98fef2f-a93f-4a7e-9aa9-2fdc9ac7f43b	491f228c-d4ff-4558-a930-a1827af3d97c	f
3ed9a267-16aa-4320-b9a3-2d38f6ad94fa	10d2d5eb-ef10-4929-87c6-cbf2ba34dbf8	t
3ed9a267-16aa-4320-b9a3-2d38f6ad94fa	b8b4a138-8568-40f9-ae77-640d689ee351	t
3ed9a267-16aa-4320-b9a3-2d38f6ad94fa	170f72d8-f885-40a8-9a54-c2de31fb86a9	t
3ed9a267-16aa-4320-b9a3-2d38f6ad94fa	ffce7315-b3dd-4dbd-bae9-2113339971e9	t
3ed9a267-16aa-4320-b9a3-2d38f6ad94fa	3f3195c0-df74-4157-a3b3-4888e23686c4	t
3ed9a267-16aa-4320-b9a3-2d38f6ad94fa	5aefe0fb-a5e0-4af2-8c61-4aced9ca12df	t
3ed9a267-16aa-4320-b9a3-2d38f6ad94fa	648f874f-c49f-486c-9018-383f959ccb95	f
3ed9a267-16aa-4320-b9a3-2d38f6ad94fa	30a1a6be-de4f-4589-9ef2-83e83833e01d	f
3ed9a267-16aa-4320-b9a3-2d38f6ad94fa	08aeccde-4fee-4f6e-8c1d-de4b798be445	f
3ed9a267-16aa-4320-b9a3-2d38f6ad94fa	88572d2f-b484-4e8a-a07c-5142e7c3d652	f
3ed9a267-16aa-4320-b9a3-2d38f6ad94fa	491f228c-d4ff-4558-a930-a1827af3d97c	f
074f0272-7808-4075-81e8-128c21528edb	10d2d5eb-ef10-4929-87c6-cbf2ba34dbf8	t
074f0272-7808-4075-81e8-128c21528edb	b8b4a138-8568-40f9-ae77-640d689ee351	t
074f0272-7808-4075-81e8-128c21528edb	170f72d8-f885-40a8-9a54-c2de31fb86a9	t
074f0272-7808-4075-81e8-128c21528edb	ffce7315-b3dd-4dbd-bae9-2113339971e9	t
074f0272-7808-4075-81e8-128c21528edb	3f3195c0-df74-4157-a3b3-4888e23686c4	t
074f0272-7808-4075-81e8-128c21528edb	5aefe0fb-a5e0-4af2-8c61-4aced9ca12df	t
074f0272-7808-4075-81e8-128c21528edb	648f874f-c49f-486c-9018-383f959ccb95	f
074f0272-7808-4075-81e8-128c21528edb	30a1a6be-de4f-4589-9ef2-83e83833e01d	f
074f0272-7808-4075-81e8-128c21528edb	08aeccde-4fee-4f6e-8c1d-de4b798be445	f
074f0272-7808-4075-81e8-128c21528edb	88572d2f-b484-4e8a-a07c-5142e7c3d652	f
074f0272-7808-4075-81e8-128c21528edb	491f228c-d4ff-4558-a930-a1827af3d97c	f
7ab4da4d-4c05-421f-95b8-b794e97f5393	10d2d5eb-ef10-4929-87c6-cbf2ba34dbf8	t
7ab4da4d-4c05-421f-95b8-b794e97f5393	b8b4a138-8568-40f9-ae77-640d689ee351	t
7ab4da4d-4c05-421f-95b8-b794e97f5393	170f72d8-f885-40a8-9a54-c2de31fb86a9	t
7ab4da4d-4c05-421f-95b8-b794e97f5393	ffce7315-b3dd-4dbd-bae9-2113339971e9	t
7ab4da4d-4c05-421f-95b8-b794e97f5393	3f3195c0-df74-4157-a3b3-4888e23686c4	t
7ab4da4d-4c05-421f-95b8-b794e97f5393	5aefe0fb-a5e0-4af2-8c61-4aced9ca12df	t
7ab4da4d-4c05-421f-95b8-b794e97f5393	648f874f-c49f-486c-9018-383f959ccb95	f
7ab4da4d-4c05-421f-95b8-b794e97f5393	30a1a6be-de4f-4589-9ef2-83e83833e01d	f
7ab4da4d-4c05-421f-95b8-b794e97f5393	08aeccde-4fee-4f6e-8c1d-de4b798be445	f
7ab4da4d-4c05-421f-95b8-b794e97f5393	88572d2f-b484-4e8a-a07c-5142e7c3d652	f
7ab4da4d-4c05-421f-95b8-b794e97f5393	491f228c-d4ff-4558-a930-a1827af3d97c	f
b62619cb-bb63-4412-a21c-5daadfdd4996	10d2d5eb-ef10-4929-87c6-cbf2ba34dbf8	t
b62619cb-bb63-4412-a21c-5daadfdd4996	b8b4a138-8568-40f9-ae77-640d689ee351	t
b62619cb-bb63-4412-a21c-5daadfdd4996	170f72d8-f885-40a8-9a54-c2de31fb86a9	t
b62619cb-bb63-4412-a21c-5daadfdd4996	ffce7315-b3dd-4dbd-bae9-2113339971e9	t
b62619cb-bb63-4412-a21c-5daadfdd4996	3f3195c0-df74-4157-a3b3-4888e23686c4	t
b62619cb-bb63-4412-a21c-5daadfdd4996	5aefe0fb-a5e0-4af2-8c61-4aced9ca12df	t
b62619cb-bb63-4412-a21c-5daadfdd4996	648f874f-c49f-486c-9018-383f959ccb95	f
b62619cb-bb63-4412-a21c-5daadfdd4996	30a1a6be-de4f-4589-9ef2-83e83833e01d	f
b62619cb-bb63-4412-a21c-5daadfdd4996	08aeccde-4fee-4f6e-8c1d-de4b798be445	f
b62619cb-bb63-4412-a21c-5daadfdd4996	88572d2f-b484-4e8a-a07c-5142e7c3d652	f
b62619cb-bb63-4412-a21c-5daadfdd4996	491f228c-d4ff-4558-a930-a1827af3d97c	f
1e451ada-fbdd-4a7f-ad23-b5229b440909	10d2d5eb-ef10-4929-87c6-cbf2ba34dbf8	t
1e451ada-fbdd-4a7f-ad23-b5229b440909	b8b4a138-8568-40f9-ae77-640d689ee351	t
1e451ada-fbdd-4a7f-ad23-b5229b440909	170f72d8-f885-40a8-9a54-c2de31fb86a9	t
1e451ada-fbdd-4a7f-ad23-b5229b440909	ffce7315-b3dd-4dbd-bae9-2113339971e9	t
1e451ada-fbdd-4a7f-ad23-b5229b440909	3f3195c0-df74-4157-a3b3-4888e23686c4	t
1e451ada-fbdd-4a7f-ad23-b5229b440909	5aefe0fb-a5e0-4af2-8c61-4aced9ca12df	t
1e451ada-fbdd-4a7f-ad23-b5229b440909	648f874f-c49f-486c-9018-383f959ccb95	f
1e451ada-fbdd-4a7f-ad23-b5229b440909	30a1a6be-de4f-4589-9ef2-83e83833e01d	f
1e451ada-fbdd-4a7f-ad23-b5229b440909	08aeccde-4fee-4f6e-8c1d-de4b798be445	f
1e451ada-fbdd-4a7f-ad23-b5229b440909	88572d2f-b484-4e8a-a07c-5142e7c3d652	f
1e451ada-fbdd-4a7f-ad23-b5229b440909	491f228c-d4ff-4558-a930-a1827af3d97c	f
26103ba8-ed16-476a-be4f-1b15363631d0	a809a4eb-789f-4cdd-b800-2a2a2b1ee9c5	t
26103ba8-ed16-476a-be4f-1b15363631d0	98aae9b8-0b25-4b90-a9dc-a0f157e2afe2	t
26103ba8-ed16-476a-be4f-1b15363631d0	41fba862-fab4-4ad5-b49e-9801130b762e	t
26103ba8-ed16-476a-be4f-1b15363631d0	16e8f9e7-0b2b-457c-87f9-149e032aeaf7	t
26103ba8-ed16-476a-be4f-1b15363631d0	50a5108d-53c8-4592-8720-28aecb224e47	t
26103ba8-ed16-476a-be4f-1b15363631d0	a1f423bd-7673-4235-8124-5460a1225ad3	t
26103ba8-ed16-476a-be4f-1b15363631d0	e3abe23c-a247-4e24-bcc6-c42a3d2b8da3	f
26103ba8-ed16-476a-be4f-1b15363631d0	51d4c7f8-6f73-434b-b638-07809009cb75	f
26103ba8-ed16-476a-be4f-1b15363631d0	e1c1fbc7-6160-473c-aaa2-9c3c48563692	f
26103ba8-ed16-476a-be4f-1b15363631d0	dba36531-8daf-4849-bf80-ff8ef431ebdb	f
26103ba8-ed16-476a-be4f-1b15363631d0	91dd31ee-b71e-457d-a181-3cb748c0b729	f
73f93ba6-a168-4560-8a23-1522043206c0	a809a4eb-789f-4cdd-b800-2a2a2b1ee9c5	t
73f93ba6-a168-4560-8a23-1522043206c0	98aae9b8-0b25-4b90-a9dc-a0f157e2afe2	t
73f93ba6-a168-4560-8a23-1522043206c0	41fba862-fab4-4ad5-b49e-9801130b762e	t
73f93ba6-a168-4560-8a23-1522043206c0	16e8f9e7-0b2b-457c-87f9-149e032aeaf7	t
73f93ba6-a168-4560-8a23-1522043206c0	50a5108d-53c8-4592-8720-28aecb224e47	t
73f93ba6-a168-4560-8a23-1522043206c0	a1f423bd-7673-4235-8124-5460a1225ad3	t
73f93ba6-a168-4560-8a23-1522043206c0	e3abe23c-a247-4e24-bcc6-c42a3d2b8da3	f
73f93ba6-a168-4560-8a23-1522043206c0	51d4c7f8-6f73-434b-b638-07809009cb75	f
73f93ba6-a168-4560-8a23-1522043206c0	e1c1fbc7-6160-473c-aaa2-9c3c48563692	f
73f93ba6-a168-4560-8a23-1522043206c0	dba36531-8daf-4849-bf80-ff8ef431ebdb	f
73f93ba6-a168-4560-8a23-1522043206c0	91dd31ee-b71e-457d-a181-3cb748c0b729	f
aacd80cd-18d9-41c6-b783-c8020389ed96	a809a4eb-789f-4cdd-b800-2a2a2b1ee9c5	t
aacd80cd-18d9-41c6-b783-c8020389ed96	98aae9b8-0b25-4b90-a9dc-a0f157e2afe2	t
aacd80cd-18d9-41c6-b783-c8020389ed96	41fba862-fab4-4ad5-b49e-9801130b762e	t
aacd80cd-18d9-41c6-b783-c8020389ed96	16e8f9e7-0b2b-457c-87f9-149e032aeaf7	t
aacd80cd-18d9-41c6-b783-c8020389ed96	50a5108d-53c8-4592-8720-28aecb224e47	t
aacd80cd-18d9-41c6-b783-c8020389ed96	a1f423bd-7673-4235-8124-5460a1225ad3	t
aacd80cd-18d9-41c6-b783-c8020389ed96	e3abe23c-a247-4e24-bcc6-c42a3d2b8da3	f
aacd80cd-18d9-41c6-b783-c8020389ed96	51d4c7f8-6f73-434b-b638-07809009cb75	f
aacd80cd-18d9-41c6-b783-c8020389ed96	e1c1fbc7-6160-473c-aaa2-9c3c48563692	f
aacd80cd-18d9-41c6-b783-c8020389ed96	dba36531-8daf-4849-bf80-ff8ef431ebdb	f
aacd80cd-18d9-41c6-b783-c8020389ed96	91dd31ee-b71e-457d-a181-3cb748c0b729	f
b95ff58d-6af0-4fa2-a73e-5381a7533c37	a809a4eb-789f-4cdd-b800-2a2a2b1ee9c5	t
b95ff58d-6af0-4fa2-a73e-5381a7533c37	98aae9b8-0b25-4b90-a9dc-a0f157e2afe2	t
b95ff58d-6af0-4fa2-a73e-5381a7533c37	41fba862-fab4-4ad5-b49e-9801130b762e	t
b95ff58d-6af0-4fa2-a73e-5381a7533c37	16e8f9e7-0b2b-457c-87f9-149e032aeaf7	t
b95ff58d-6af0-4fa2-a73e-5381a7533c37	50a5108d-53c8-4592-8720-28aecb224e47	t
b95ff58d-6af0-4fa2-a73e-5381a7533c37	a1f423bd-7673-4235-8124-5460a1225ad3	t
b95ff58d-6af0-4fa2-a73e-5381a7533c37	e3abe23c-a247-4e24-bcc6-c42a3d2b8da3	f
b95ff58d-6af0-4fa2-a73e-5381a7533c37	51d4c7f8-6f73-434b-b638-07809009cb75	f
b95ff58d-6af0-4fa2-a73e-5381a7533c37	e1c1fbc7-6160-473c-aaa2-9c3c48563692	f
b95ff58d-6af0-4fa2-a73e-5381a7533c37	dba36531-8daf-4849-bf80-ff8ef431ebdb	f
b95ff58d-6af0-4fa2-a73e-5381a7533c37	91dd31ee-b71e-457d-a181-3cb748c0b729	f
c3f69b94-df50-4fef-8e18-636bb984c0f4	a809a4eb-789f-4cdd-b800-2a2a2b1ee9c5	t
c3f69b94-df50-4fef-8e18-636bb984c0f4	98aae9b8-0b25-4b90-a9dc-a0f157e2afe2	t
c3f69b94-df50-4fef-8e18-636bb984c0f4	41fba862-fab4-4ad5-b49e-9801130b762e	t
c3f69b94-df50-4fef-8e18-636bb984c0f4	16e8f9e7-0b2b-457c-87f9-149e032aeaf7	t
c3f69b94-df50-4fef-8e18-636bb984c0f4	50a5108d-53c8-4592-8720-28aecb224e47	t
c3f69b94-df50-4fef-8e18-636bb984c0f4	a1f423bd-7673-4235-8124-5460a1225ad3	t
c3f69b94-df50-4fef-8e18-636bb984c0f4	e3abe23c-a247-4e24-bcc6-c42a3d2b8da3	f
c3f69b94-df50-4fef-8e18-636bb984c0f4	51d4c7f8-6f73-434b-b638-07809009cb75	f
c3f69b94-df50-4fef-8e18-636bb984c0f4	e1c1fbc7-6160-473c-aaa2-9c3c48563692	f
c3f69b94-df50-4fef-8e18-636bb984c0f4	dba36531-8daf-4849-bf80-ff8ef431ebdb	f
c3f69b94-df50-4fef-8e18-636bb984c0f4	91dd31ee-b71e-457d-a181-3cb748c0b729	f
9bb712ff-a545-4dbd-9df3-3ac5e67dd486	a809a4eb-789f-4cdd-b800-2a2a2b1ee9c5	t
9bb712ff-a545-4dbd-9df3-3ac5e67dd486	98aae9b8-0b25-4b90-a9dc-a0f157e2afe2	t
9bb712ff-a545-4dbd-9df3-3ac5e67dd486	41fba862-fab4-4ad5-b49e-9801130b762e	t
9bb712ff-a545-4dbd-9df3-3ac5e67dd486	16e8f9e7-0b2b-457c-87f9-149e032aeaf7	t
9bb712ff-a545-4dbd-9df3-3ac5e67dd486	50a5108d-53c8-4592-8720-28aecb224e47	t
9bb712ff-a545-4dbd-9df3-3ac5e67dd486	a1f423bd-7673-4235-8124-5460a1225ad3	t
9bb712ff-a545-4dbd-9df3-3ac5e67dd486	e3abe23c-a247-4e24-bcc6-c42a3d2b8da3	f
9bb712ff-a545-4dbd-9df3-3ac5e67dd486	51d4c7f8-6f73-434b-b638-07809009cb75	f
9bb712ff-a545-4dbd-9df3-3ac5e67dd486	e1c1fbc7-6160-473c-aaa2-9c3c48563692	f
9bb712ff-a545-4dbd-9df3-3ac5e67dd486	dba36531-8daf-4849-bf80-ff8ef431ebdb	f
9bb712ff-a545-4dbd-9df3-3ac5e67dd486	91dd31ee-b71e-457d-a181-3cb748c0b729	f
\.


--
-- Data for Name: client_scope_role_mapping; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.client_scope_role_mapping (scope_id, role_id) FROM stdin;
30a1a6be-de4f-4589-9ef2-83e83833e01d	b3961954-2961-4db0-889f-bb4abf624f81
e1c1fbc7-6160-473c-aaa2-9c3c48563692	f96fdd65-e423-4407-8593-5d72429e8616
\.


--
-- Data for Name: component; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.component (id, name, parent_id, provider_id, provider_type, realm_id, sub_type) FROM stdin;
ec5a7a3a-36fe-44c4-9996-a0fbede3ced6	Trusted Hosts	818f97fc-7b35-4bae-bdd7-76b5035dce69	trusted-hosts	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	818f97fc-7b35-4bae-bdd7-76b5035dce69	anonymous
970acbaf-232a-4daa-a8a8-4d27cf8e859c	Consent Required	818f97fc-7b35-4bae-bdd7-76b5035dce69	consent-required	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	818f97fc-7b35-4bae-bdd7-76b5035dce69	anonymous
c4913670-8cf7-4bf3-846f-48660bab999e	Full Scope Disabled	818f97fc-7b35-4bae-bdd7-76b5035dce69	scope	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	818f97fc-7b35-4bae-bdd7-76b5035dce69	anonymous
3deda59d-28a9-4976-90c7-bbbafc30e0be	Max Clients Limit	818f97fc-7b35-4bae-bdd7-76b5035dce69	max-clients	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	818f97fc-7b35-4bae-bdd7-76b5035dce69	anonymous
28728acd-d93a-41f2-b318-bfca444efb7f	Allowed Protocol Mapper Types	818f97fc-7b35-4bae-bdd7-76b5035dce69	allowed-protocol-mappers	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	818f97fc-7b35-4bae-bdd7-76b5035dce69	anonymous
4aafeca1-f111-4c32-b3e9-58a4b4e333f8	Allowed Client Scopes	818f97fc-7b35-4bae-bdd7-76b5035dce69	allowed-client-templates	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	818f97fc-7b35-4bae-bdd7-76b5035dce69	anonymous
72e703ec-de2b-428f-a892-3a9b77f1f353	Allowed Protocol Mapper Types	818f97fc-7b35-4bae-bdd7-76b5035dce69	allowed-protocol-mappers	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	818f97fc-7b35-4bae-bdd7-76b5035dce69	authenticated
58c79ebb-0eaf-44a2-83a3-1f65a58d9803	Allowed Client Scopes	818f97fc-7b35-4bae-bdd7-76b5035dce69	allowed-client-templates	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	818f97fc-7b35-4bae-bdd7-76b5035dce69	authenticated
d5ace45e-0171-4cfa-bc97-67bcb023696c	rsa-generated	818f97fc-7b35-4bae-bdd7-76b5035dce69	rsa-generated	org.keycloak.keys.KeyProvider	818f97fc-7b35-4bae-bdd7-76b5035dce69	\N
678473aa-2867-428e-99b5-f0fc367aa552	rsa-enc-generated	818f97fc-7b35-4bae-bdd7-76b5035dce69	rsa-enc-generated	org.keycloak.keys.KeyProvider	818f97fc-7b35-4bae-bdd7-76b5035dce69	\N
2da5fb2f-eac8-40d8-afbe-72fd6469025d	hmac-generated-hs512	818f97fc-7b35-4bae-bdd7-76b5035dce69	hmac-generated	org.keycloak.keys.KeyProvider	818f97fc-7b35-4bae-bdd7-76b5035dce69	\N
621bf805-cbfb-4800-9295-7ecd527692b5	aes-generated	818f97fc-7b35-4bae-bdd7-76b5035dce69	aes-generated	org.keycloak.keys.KeyProvider	818f97fc-7b35-4bae-bdd7-76b5035dce69	\N
3f42d6ad-e961-47bf-8e9b-3410e4d9f2e7	\N	818f97fc-7b35-4bae-bdd7-76b5035dce69	declarative-user-profile	org.keycloak.userprofile.UserProfileProvider	818f97fc-7b35-4bae-bdd7-76b5035dce69	\N
08b36877-37e4-49c1-9419-beeb8ff3ce02	rsa-generated	c43fd595-cebd-4062-ac8d-8943c15bd7de	rsa-generated	org.keycloak.keys.KeyProvider	c43fd595-cebd-4062-ac8d-8943c15bd7de	\N
e87f97df-7297-47b4-8462-23ddf4127929	rsa-enc-generated	c43fd595-cebd-4062-ac8d-8943c15bd7de	rsa-enc-generated	org.keycloak.keys.KeyProvider	c43fd595-cebd-4062-ac8d-8943c15bd7de	\N
72c6d005-09fc-44ed-b07c-360d59a7415a	hmac-generated-hs512	c43fd595-cebd-4062-ac8d-8943c15bd7de	hmac-generated	org.keycloak.keys.KeyProvider	c43fd595-cebd-4062-ac8d-8943c15bd7de	\N
e7f0848a-a0e4-4c9f-9524-6d20c72b3dfe	aes-generated	c43fd595-cebd-4062-ac8d-8943c15bd7de	aes-generated	org.keycloak.keys.KeyProvider	c43fd595-cebd-4062-ac8d-8943c15bd7de	\N
9e95a836-3baf-45fd-878d-3b01e14450aa	Trusted Hosts	c43fd595-cebd-4062-ac8d-8943c15bd7de	trusted-hosts	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	c43fd595-cebd-4062-ac8d-8943c15bd7de	anonymous
8e35b6c7-7e2b-447f-8bb9-6266929a3fdf	Consent Required	c43fd595-cebd-4062-ac8d-8943c15bd7de	consent-required	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	c43fd595-cebd-4062-ac8d-8943c15bd7de	anonymous
ea332bb7-8618-40a2-96e7-4e5599c60c8c	Full Scope Disabled	c43fd595-cebd-4062-ac8d-8943c15bd7de	scope	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	c43fd595-cebd-4062-ac8d-8943c15bd7de	anonymous
39db879f-b3a6-4f93-b7d2-a40d279cf326	Max Clients Limit	c43fd595-cebd-4062-ac8d-8943c15bd7de	max-clients	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	c43fd595-cebd-4062-ac8d-8943c15bd7de	anonymous
186da7b3-3cee-4cf3-9ab3-bf6648966ff2	Allowed Protocol Mapper Types	c43fd595-cebd-4062-ac8d-8943c15bd7de	allowed-protocol-mappers	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	c43fd595-cebd-4062-ac8d-8943c15bd7de	anonymous
81455906-29b5-4314-88d4-31ec8dd5e4a2	Allowed Client Scopes	c43fd595-cebd-4062-ac8d-8943c15bd7de	allowed-client-templates	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	c43fd595-cebd-4062-ac8d-8943c15bd7de	anonymous
a0217514-3ed1-4193-95d9-e8582590131c	Allowed Protocol Mapper Types	c43fd595-cebd-4062-ac8d-8943c15bd7de	allowed-protocol-mappers	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	c43fd595-cebd-4062-ac8d-8943c15bd7de	authenticated
8c42210b-d836-4c55-86e1-e8ed6391880d	Allowed Client Scopes	c43fd595-cebd-4062-ac8d-8943c15bd7de	allowed-client-templates	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	c43fd595-cebd-4062-ac8d-8943c15bd7de	authenticated
\.


--
-- Data for Name: component_config; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.component_config (id, component_id, name, value) FROM stdin;
6a956226-b6d9-4ef3-b28e-64eb4f859f4c	ec5a7a3a-36fe-44c4-9996-a0fbede3ced6	host-sending-registration-request-must-match	true
2fad71f1-cfb2-4208-bcc0-ccdb7f1c2aeb	ec5a7a3a-36fe-44c4-9996-a0fbede3ced6	client-uris-must-match	true
53f26290-9a63-44d7-a10a-715ac2d2a024	72e703ec-de2b-428f-a892-3a9b77f1f353	allowed-protocol-mapper-types	oidc-full-name-mapper
f1d514ad-9cfb-4c26-965a-9f7af398771c	72e703ec-de2b-428f-a892-3a9b77f1f353	allowed-protocol-mapper-types	oidc-address-mapper
ec4c34f0-64d1-4b0f-a520-dc73414c546a	72e703ec-de2b-428f-a892-3a9b77f1f353	allowed-protocol-mapper-types	saml-user-attribute-mapper
87c47b70-a310-4384-834e-46561f0321d6	72e703ec-de2b-428f-a892-3a9b77f1f353	allowed-protocol-mapper-types	saml-user-property-mapper
cfd0963d-527b-4244-9094-83bace56f787	72e703ec-de2b-428f-a892-3a9b77f1f353	allowed-protocol-mapper-types	oidc-usermodel-property-mapper
b287663b-ea8f-47f1-99bf-a643e9c0be2e	72e703ec-de2b-428f-a892-3a9b77f1f353	allowed-protocol-mapper-types	oidc-usermodel-attribute-mapper
789ee8d6-86d2-411c-b1b6-9b679b61fc74	72e703ec-de2b-428f-a892-3a9b77f1f353	allowed-protocol-mapper-types	oidc-sha256-pairwise-sub-mapper
fe181914-77d2-4bc4-9ad8-171b6e78d206	72e703ec-de2b-428f-a892-3a9b77f1f353	allowed-protocol-mapper-types	saml-role-list-mapper
67f1ece9-c276-4eb2-9970-d2e2fec181f8	58c79ebb-0eaf-44a2-83a3-1f65a58d9803	allow-default-scopes	true
a1580631-e361-40fe-ad38-fb50fa0673c9	3deda59d-28a9-4976-90c7-bbbafc30e0be	max-clients	200
811b13df-7c4d-4e8a-8aed-85ac2656c02c	4aafeca1-f111-4c32-b3e9-58a4b4e333f8	allow-default-scopes	true
87bd2168-7653-47cf-81be-19adffc2a231	28728acd-d93a-41f2-b318-bfca444efb7f	allowed-protocol-mapper-types	oidc-sha256-pairwise-sub-mapper
daeea215-e846-49ea-a3bd-78c7dfa5c42c	28728acd-d93a-41f2-b318-bfca444efb7f	allowed-protocol-mapper-types	oidc-full-name-mapper
22246957-9f47-4bbc-91d3-daa9e7888935	28728acd-d93a-41f2-b318-bfca444efb7f	allowed-protocol-mapper-types	oidc-address-mapper
b7ac64b8-7de2-41d3-bc27-6b679e5de244	28728acd-d93a-41f2-b318-bfca444efb7f	allowed-protocol-mapper-types	oidc-usermodel-property-mapper
cc57479f-8449-45b9-82cf-b197b88a0ae6	28728acd-d93a-41f2-b318-bfca444efb7f	allowed-protocol-mapper-types	saml-user-property-mapper
bbd38511-113e-4602-bd70-3f3b780a6151	28728acd-d93a-41f2-b318-bfca444efb7f	allowed-protocol-mapper-types	oidc-usermodel-attribute-mapper
28a49f65-4fec-42a5-8c97-53c5571db499	28728acd-d93a-41f2-b318-bfca444efb7f	allowed-protocol-mapper-types	saml-user-attribute-mapper
134c6734-2b03-4663-94c7-63cc63838508	28728acd-d93a-41f2-b318-bfca444efb7f	allowed-protocol-mapper-types	saml-role-list-mapper
dc4fb7c8-e0cd-4f1f-a5c5-efe780e7e2e9	2da5fb2f-eac8-40d8-afbe-72fd6469025d	secret	WMzVdvVZ6Tmixx6BkCIWpeFYjqpoag_xDZ3RJqhNRI3nodwTGCzRZUec3MGSgwfBAsRnmOV-4x-U-m7EeUfhnzZ99A7TyR3BPJWD4pOiqjgPQnInrT4Y7HawYWFwq01DTfxNCSe4eL-tdpXH3knTkTNUt6ZP5Q7TwZR-NryqYFc
316dc296-2f31-4dfd-92e8-277aa728370a	2da5fb2f-eac8-40d8-afbe-72fd6469025d	kid	82afe6b1-70f6-49fc-8743-adf6eed025dd
c3edbf18-e109-42eb-8624-900b418d2700	2da5fb2f-eac8-40d8-afbe-72fd6469025d	priority	100
e44bf288-270c-4b05-a7a9-1b67a593087c	2da5fb2f-eac8-40d8-afbe-72fd6469025d	algorithm	HS512
4592308a-3ee0-45fd-94c3-71cf7f27fcfb	3f42d6ad-e961-47bf-8e9b-3410e4d9f2e7	kc.user.profile.config	{"attributes":[{"name":"username","displayName":"${username}","validations":{"length":{"min":3,"max":255},"username-prohibited-characters":{},"up-username-not-idn-homograph":{}},"permissions":{"view":["admin","user"],"edit":["admin","user"]},"multivalued":false},{"name":"email","displayName":"${email}","validations":{"email":{},"length":{"max":255}},"permissions":{"view":["admin","user"],"edit":["admin","user"]},"multivalued":false},{"name":"firstName","displayName":"${firstName}","validations":{"length":{"max":255},"person-name-prohibited-characters":{}},"permissions":{"view":["admin","user"],"edit":["admin","user"]},"multivalued":false},{"name":"lastName","displayName":"${lastName}","validations":{"length":{"max":255},"person-name-prohibited-characters":{}},"permissions":{"view":["admin","user"],"edit":["admin","user"]},"multivalued":false}],"groups":[{"name":"user-metadata","displayHeader":"User metadata","displayDescription":"Attributes, which refer to user metadata"}]}
8b70211f-0249-48b6-a8b0-74abca0c3d9b	d5ace45e-0171-4cfa-bc97-67bcb023696c	keyUse	SIG
895bfb00-1252-42af-814b-edf5e631ee15	d5ace45e-0171-4cfa-bc97-67bcb023696c	certificate	MIICmzCCAYMCBgGZtbN8ezANBgkqhkiG9w0BAQsFADARMQ8wDQYDVQQDDAZtYXN0ZXIwHhcNMjUxMDA1MTg0NjAyWhcNMzUxMDA1MTg0NzQyWjARMQ8wDQYDVQQDDAZtYXN0ZXIwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCNVa6tk7IqUEbOWQM6Np3vub8O/rkV6y8gsen9db99n7Eg368jOMuTwfw/+7DBFp50emZ4g1ab1WBXTUKbi30yxSamv04QEmi5nGW1UxNAhcm3PEyt2U9JTar/T+oAH8mclKxFxeyrDe1x3+B95jcmePavDzQ4EPrvyke2G3lFO+MP+rhZYeeo4QBCH3LSaybDYJ4d87xnzK5MqyGIoX3hutgkOXG76VP6vmFw6Q3E9BX5nM+a3a0QYZZHLUc4RJHlpLr0StZM7O7ktjb+CrE3CgoGyPvtiKGcGlfFrNXh21+gKHreiZGxKClvzoZQuSFnL/U4a8rmW/+RTDC16sFPAgMBAAEwDQYJKoZIhvcNAQELBQADggEBAHn3Lzb4iR+Jq+0e0hnBGwNZ5W7dfQ3ENHosbWAd39kffFW9lxzMjDEWyRIQzNO0qTRfRm/hNhsMM3PZKgK8904uuP/bZRPcOwObtmFsKfKKNLZlWn+40WPZKQXa4874cECyjCG9nOvzfp4yaK/JhNhbIRGEk0Ok9Vx+6mLQSjwyEJHdE9yQobMw+yKAomrDbCxmA5zx8cGCDGydnKF0CecLtP5wLGmF/NCoISJof8Q9DU67hOBnElyr1rygd3pUfn5UdPzv+CCbap2Yr7SGnF+Mfs28WaITI+r4RQLk4/KWixTdNS1edfAeY9/Gj/8qoaDSc03uSYayY9OGYBQ3N98=
54f7b150-7484-4a22-882d-d0a657915ade	d5ace45e-0171-4cfa-bc97-67bcb023696c	priority	100
5d01fa81-682c-4262-afa3-2c20d921430e	d5ace45e-0171-4cfa-bc97-67bcb023696c	privateKey	MIIEpAIBAAKCAQEAjVWurZOyKlBGzlkDOjad77m/Dv65FesvILHp/XW/fZ+xIN+vIzjLk8H8P/uwwRaedHpmeINWm9VgV01Cm4t9MsUmpr9OEBJouZxltVMTQIXJtzxMrdlPSU2q/0/qAB/JnJSsRcXsqw3tcd/gfeY3Jnj2rw80OBD678pHtht5RTvjD/q4WWHnqOEAQh9y0msmw2CeHfO8Z8yuTKshiKF94brYJDlxu+lT+r5hcOkNxPQV+ZzPmt2tEGGWRy1HOESR5aS69ErWTOzu5LY2/gqxNwoKBsj77YihnBpXxazV4dtfoCh63omRsSgpb86GULkhZy/1OGvK5lv/kUwwterBTwIDAQABAoIBAAc8C4+hr80FWZqBE+6MPSli6KoWf9g6DFTaxo94J4UsAh/HU2pfeIw9Bxx/VbI3O4UwKW6hynJ6SaQuYD/LXsKVNOqe1ZrHq8n/J3Wl+vuiiDYeRWPgwR46IOFQxeRGZvDiQK88TREJys25bVRZzxjjuDzdxQku//6mQWV2TTmDSR1ewiKPeG0Xt7MQfAVZiOZtlG+sFgMnI8GHwvHvYVG+UL/cHfpjFx8L4RkJ/4D6EVHmwcwxhjO+HY+6Vt3rjaYGN2+4/sQfqlmJ4RP3zSJXJc2t5ZV/OnBMffnzRdsYrmy1o3sP6JRIfSAlCuYs5SCn3qMuZ99z0XcHWgPsboECgYEAwCvQyD/8gNRfpi3rQOF9lmfbYS/BgCFOAOsAOy/QpZ5jkh7IzwfchZRYDTo/hMdu4l8HQal/Ac9WReMRfg/cde6v65LErogkYPAx91Q3W7zmcSe3tho4OPFoJ07NuAPdPZVYlGnCo+0DQaDKuIUqDnfdj2DZl/4jUxgeH0ggAtsCgYEAvEdG30/Pml+vymabPBPpoKPgy/7X2w/Ej2qOecDUQmcvMp/uJ7OPvl03g3TmVugIfFtKYpOB5Q1u7afhfTvB/IFyW7+ByzGSIjaIur1/aKVcXKy5hQ7GcT/w/4pP3oAWo96L6uEvp9miJwxOQVb4b+qqxQy0w43KTtnHbPTUU50CgYEAkP8+bCnjcq1Lck7JZ837/YVlgQIQhYYbnhOyu/XK/3vv8yupJpTu7hdNDMX7cUstPj6ncgLa4tm3Zcn7w9th49XyBNRb9LiigkYZ3V0828o/Mffe5NcJvuVfSfFqxUKdJz9Nc8YM57UItPcGno1Te1Ez6mAQLl/B9YuIBuBbzusCgYA/alqKuN0aCASzzZ+XurVEMC51R175H/R6wc8/Xx2fSBXznT6gDLDr04XxNxymRP0mtbD2RuzHc6DbV2JxxgDuxcL4+CYFhKrN70RacZ1KZ/gWFu9dy743Quwvhiykqsv1Ye4KrNJaXMlrEZACHgML3ySpvj/cZhOwP1YWcthZWQKBgQCGY+jZJ03J0YS9I5FDTPUweDXOnmE5YkhUmZpJwNpdecPHK19hfQIWRsaGzU+t9nSfOa8BozoaZ9/Lh5HVXSK3JznHxDUOn1Gf/2bCxBIkiprPUBoAIQ+8rYmNZ+rOG1/P8cCm/XbzUfOO7QMwpp4eeFrc4T4rY859gJPhxJDqfw==
e6efcbbd-6a61-49b3-81b6-4aa360801d16	621bf805-cbfb-4800-9295-7ecd527692b5	kid	f930df58-4c27-4f6d-98b6-c9c2bbab023b
c57fa3d1-ed92-471e-be2c-93d344ae7967	621bf805-cbfb-4800-9295-7ecd527692b5	priority	100
5f3ac761-7deb-4743-9871-22ba6c1c1e54	621bf805-cbfb-4800-9295-7ecd527692b5	secret	UINf-G6-gxvemO-B_VYyTg
de44e7fa-819d-4cce-85c5-451c84d9d0c6	678473aa-2867-428e-99b5-f0fc367aa552	algorithm	RSA-OAEP
9208c29e-9d9b-4999-9afe-130be708afa0	678473aa-2867-428e-99b5-f0fc367aa552	certificate	MIICmzCCAYMCBgGZtbN8+DANBgkqhkiG9w0BAQsFADARMQ8wDQYDVQQDDAZtYXN0ZXIwHhcNMjUxMDA1MTg0NjAzWhcNMzUxMDA1MTg0NzQzWjARMQ8wDQYDVQQDDAZtYXN0ZXIwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDiyn4cDXXeY9lK04k4vxY473MJO761Ksl1H9xHpzNn1nA5undbpKu5nLT5uVGvUgEbFpqd2Q/EdBiD1FNgfCQXhjGJtYWwVZsrXAxBO3iizm2EzPRf14KfvJ1KqWVb4cNJ8leJmc2g+MklvSDeGTwzLDjHHoyeivtes/J9fl1Vw51Kv+C4L6cVflEj/jOmxZ1nFBy/KrACfYsUUJKxZe/RqGtdYnvrhMf6I8osgDXQ1HO6ozVg7ywh8vuCszS3iTdu6YJx2UWAjYLOjiupDumxHLkSwQ7J+ClaNvfqJEDyf7++55y9xrCEmW0GSTL97KRKfchL6pnKjkyKhGSMS7EVAgMBAAEwDQYJKoZIhvcNAQELBQADggEBAHrth2e/s+oXYyjp08RM2srQA127VQxuIf6/gxzhsiCkz619JnnurZbJOI/vxrQwiAWXk1xUQqmIRqAmGESWhKVfYfpGFqgYylViVRrbN3WPW4vsuQ7MOsAgQXR5CQNCQK+OPCqTPYlmljQqqZqif2fEuwprSWiz4zYb7ei+RJM+6YQhraKsWiYmzhbT5k2I7M2AaVP5DGvoqrWGE6iEdvjddGLEjdk6UcEo9NEsr9MCeiiCriuP5dOsgBSItpZ7KhOofQf3xDnSTPkONQUlfVRoE7IXoaevYF25rHNI24PrYu6cyTgy8fqCYW8SUmCYwUSwOmsi0r8A22GKdYw0LdA=
2ca8beef-e6f8-42b4-aeba-57872b86cc73	678473aa-2867-428e-99b5-f0fc367aa552	keyUse	ENC
a9a9932f-229e-4a16-81e6-e2dd0da9dfd9	678473aa-2867-428e-99b5-f0fc367aa552	privateKey	MIIEowIBAAKCAQEA4sp+HA113mPZStOJOL8WOO9zCTu+tSrJdR/cR6czZ9ZwObp3W6SruZy0+blRr1IBGxaandkPxHQYg9RTYHwkF4YxibWFsFWbK1wMQTt4os5thMz0X9eCn7ydSqllW+HDSfJXiZnNoPjJJb0g3hk8Myw4xx6Mnor7XrPyfX5dVcOdSr/guC+nFX5RI/4zpsWdZxQcvyqwAn2LFFCSsWXv0ahrXWJ764TH+iPKLIA10NRzuqM1YO8sIfL7grM0t4k3bumCcdlFgI2Czo4rqQ7psRy5EsEOyfgpWjb36iRA8n+/vuecvcawhJltBkky/eykSn3IS+qZyo5MioRkjEuxFQIDAQABAoIBAAGV4nwmDfEQPU3Rcm/5SgRQ4ZAqy5eu+mSSufKtn7rgnBKTPqVfM9d4LyhgcXU/0K5/THUWYOQB3xqrf9UOA8nppWwDNFw6D6Z7Hhd7UnRqQV1rJLmMzfwkO0HxMuUJttihVJergcKtKpQpy9KHAPg8nWhVoCxgPVyj9mnb2cwxLrMC4y0TPlRJ2lmTjSELk15GQt4+n7gk2Xz7vf3JwL5M7YzaXg2DDNTNtYrE8YtUtjXQwZkt7m5ch+dAl/bh3LfpTjVNzEqBlKoBcAfVN2P6ixDKPI1KLH6E+XJSy+R7VdfSmC4ZG3P2/OjoYdZNb93JIV6Jo6cbqiRfB4lTPxECgYEA8dbR1mtVQu6qJYNn5khuwb+Os+p4UobRqiYnemkDQdNPv/zR6Kv/NtMpREeZTSNX5+S7nKVyh7OxiF92vEdEyFh7A+PFxsuIJWulnAxOSc434XJVg7c3HYqUBQie7XiUZr4unrkGE/kTgjn1JJQBnwKw/J47SFxfhteCA6RLzw0CgYEA8BIZtEYZlU2/DAEenX65tjV2zWcADScrMxEL9f71tMMF9wYX8Ck1GgjIme2SFVE+OQhNzXoYwDRacgxUSE53gNCTHmOblx9BPfR/rqhw+J5MR3g1J1JrHpuBEgEj+7C8Uww2Plpz8PgL5pKdNWxHfggylmkMDp1n53vImYOjqCkCgYEAmVuHb3ha9fnQzb8ZqO9vxM9LmA3sxTCXTlWpnTdwiFKO9QlGt0wyqDqZpaHdtgvel7/1tO05xpIN15PqAJUVKhLp11qeNWUl7czolR88mhL6Xq2ZStBpVANnwBVqat6XBNw/RgnAr/O4ClHkgZCVwpb7UwaoQ0fIkAKjgNmz9RUCgYA4gAoMo3Lh8KpF6P2Lk4ehmnObY+JfNhi0rA5klty87ik5OfXoT8pvliGgGImI+tXqFko5UcMBeVDQBpbPbagyaOQ3Arpblr+EFUb3lC0CVj8dkuzvlDYhvfdihWIymiW4ZedeRWuECtzCJz9Yk2NABi3huFdeGS2bDUbo2lJqAQKBgDffckjFyU+h7ClAUBjqyJ3YyiuDwJXxSNBkQohrWn+eUFGt+blGvUXv+AUOXnvmu+SvEog/yyJ05bmgJOL7xmMVTT4bh1liRnmVbtTsGLiYS401oNUyUlW9IEcJCr0y++9Oeli50cKGMZkMPiW4scPCnnWK58bcHf7t4kdWuP7d
7256c0f3-8e68-42ec-8f79-26d1520f027d	678473aa-2867-428e-99b5-f0fc367aa552	priority	100
0a6f8d81-ab6d-4554-83af-6acf03c6b68c	e87f97df-7297-47b4-8462-23ddf4127929	keyUse	ENC
53cc9cdf-0f01-4828-be4a-614d99fd4882	e87f97df-7297-47b4-8462-23ddf4127929	privateKey	MIIEowIBAAKCAQEArrlD7KxYZWLT5VUnWrwVWQLitADB3ObPUVFc51A/hA3nIvd8yYEp4A9x7y9CEk76Sw7yhWFqOEkbBwFRweukMPrAjiHpKCvpT0UYXDp2Wnx9F2OBCmK1EkW06zHBUFdpY6WNJVIimjt9WZXyJ4bxBQAwyWMJ85cF7N76sXeS83k432vQOrMgB6anpnmsuKFXBnnB/BQf04busJfRrjKfGUAYdjE+/1a0oSKY/xDIfeo3bAQo29FjXYgNsm6Ez1L4RluoP/1ZIFjXkU5k5xJe9lE7TaWLld3byY3kAevRmYPHcyzjRSPay7CEvfsJGZegI+EPqf1zmknq9qh1dc7DGQIDAQABAoIBABHk4bDbGDz+7Xhc25hSb6XDcsQQWUpmPYiEmo0KlgywT1IQkmBBORCMSGUAfCXrnnziKaKRHtunmgviPdUsTM8i2SZdUPeHIsB2cTed2anr+rQy4b/2ari1uKEHQhb4MtAMoAtY0Xgl/FEY1xlm+6dhVP3FBbuDpazUC8zx0/QZE+0MeGoAHNyixlUg8VUs+e4E76xzrHiXv23STLhuXpg6Q8XG/BlKpjiQorp7iMEVzKOwEtiz04MNn4wYDI9WH2l2jobWEAKmBSpET9EUO+AkEay4ddVYx+b2ntZsEHly44xQm6hAoODtl//k4ELjIe9F4PfEoW+FE6h9OUuCqBECgYEA9rlI4OBWMrvnqfoQ1EhF+No4na82/cJEvIWPXFqbVNvPEYjGotHUzm6n5JAzqxDN5LXMjJcyXnwAMi4jX2Dyiv510nkLWgcMQS1npMk1SOFLOrpTml2P4ozee34H/3VjeLT54j4Hi4P45qHxotYweTy1WJZsM/kGJsZQ9L1nfukCgYEAtUr67k4iBdqs8k0vFiz6Wye02bz4ydr5nyZMr9OZdowJgbPsP2q/ijh99tjR5uJhAwotYyGdeI8Le/bitF2QTJJFFX3YD7rdYkhwwwKRGJqusbZB1vxthquQ5eOTdgKa7DE2msuSEO0pRG4cs927TuD1+W1ggXOPtWMxiVwlZLECgYBTbaoAKLvdIf8fUij4r4vBr4DviT0uJ8/4c61bjGP0GPP3OOn6SrwMJgzt0/k0wlTtHrCRZdspagLi9QkPCohZidwrlLTK95EkRQdECKnVACHhNPpqO3MZJ4tf9OkdcAN34LKS12jTu4eh7Wb5KjZv+KBV2V+zX2S+HddoOtfRaQKBgHzRzQa6IM6rCpwg7CR2f0C3i9rGMvy3mVosAmykYWqva/QzLptTIeFGl+OW0J4IiwaOwUUZZdQVmLCXAOtx5+Hwa0QqytG5tKbwfUKmx8OK7HBKrfYZAkfQZAkDsQl1LbklARV+x2iEAP+uWHC2XzolxPUgi2tVZ0JZs0eBe/VhAoGBAMjVDjYIRaJmQ094qe/o2xPm1HJXgALyOt3bTcXWjryQQJqKoyB5yJD+S0rK3EM6Ja6zv33rBBh47U21VFpRX9QtuXQNZ5sDEqW9aSbdY3bS0ncg281H/4zbOxKPUWqHjERGT+157CS34VUAG4ng+er36Y7graVPANGa6Oyie2RN
581c1fdf-4879-4a7c-8937-c1c87c0d4d7b	e87f97df-7297-47b4-8462-23ddf4127929	certificate	MIICnzCCAYcCBgGZtbOA3jANBgkqhkiG9w0BAQsFADATMREwDwYDVQQDDAhlYWYtdGVzdDAeFw0yNTEwMDUxODQ2MDRaFw0zNTEwMDUxODQ3NDRaMBMxETAPBgNVBAMMCGVhZi10ZXN0MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArrlD7KxYZWLT5VUnWrwVWQLitADB3ObPUVFc51A/hA3nIvd8yYEp4A9x7y9CEk76Sw7yhWFqOEkbBwFRweukMPrAjiHpKCvpT0UYXDp2Wnx9F2OBCmK1EkW06zHBUFdpY6WNJVIimjt9WZXyJ4bxBQAwyWMJ85cF7N76sXeS83k432vQOrMgB6anpnmsuKFXBnnB/BQf04busJfRrjKfGUAYdjE+/1a0oSKY/xDIfeo3bAQo29FjXYgNsm6Ez1L4RluoP/1ZIFjXkU5k5xJe9lE7TaWLld3byY3kAevRmYPHcyzjRSPay7CEvfsJGZegI+EPqf1zmknq9qh1dc7DGQIDAQABMA0GCSqGSIb3DQEBCwUAA4IBAQBCV8hSwEE+vq6/W6skSUPs09FWu4/j4fALa1kiodEO6v6SRh9Rz5+zesKRhsQBzg61oKyv85efuT40gcjGn8d3YM4v6j+trYiqMwxgDyVAQ25jeFOSU2LOmQzR09sB09bx0kQWs1PO2qduv6e2+cx68NGpdyTf/xpwS9T6fP1oNAHpt/f5vvhesT99GECAbimPJIn59u0BZ0L2za419Ui4NeZV+4qTnxQjjdu5emSzwYRflfLEgfHjAKDXcJi9eBIuDgLSUyhmgehK+VAZHYvHR7FZRBJquOMfmSIASA8RBxMsylPOW1qDybBAkVI7Iv4Uq9OFbyAderZDGqHc5HOG
eb93bc3b-74e5-42c8-bc98-9c5dc1042a70	e87f97df-7297-47b4-8462-23ddf4127929	priority	100
f12b7754-3351-40f2-ba3c-e1c9400d1f59	e87f97df-7297-47b4-8462-23ddf4127929	algorithm	RSA-OAEP
42eb0c64-d227-4a17-b2f2-33bc8829dc01	e7f0848a-a0e4-4c9f-9524-6d20c72b3dfe	secret	R_M_-gRgwM32urJ5hR6kKg
607f1e99-d1bd-47fb-8668-f73c903ad83d	e7f0848a-a0e4-4c9f-9524-6d20c72b3dfe	kid	46070e58-e9d0-4a71-a41c-120ec95ce4ec
385b7b44-380d-46ad-8bc8-76d3645746d1	e7f0848a-a0e4-4c9f-9524-6d20c72b3dfe	priority	100
1df8cb46-1e56-410f-9886-2025d962f8a3	08b36877-37e4-49c1-9419-beeb8ff3ce02	certificate	MIICnzCCAYcCBgGZtbN/xDANBgkqhkiG9w0BAQsFADATMREwDwYDVQQDDAhlYWYtdGVzdDAeFw0yNTEwMDUxODQ2MDNaFw0zNTEwMDUxODQ3NDNaMBMxETAPBgNVBAMMCGVhZi10ZXN0MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAwNTMkkvejo/sQPaX+WcDxCWf1Sm308Z7ZnwzCoymE7Jqk07Kq5wuSVvIX1bsv6tz11o8A8xQ3a6/NkIDNCvDMAVewN/xvlZ6gBD/mmD8cuJnBx4ZxI3t+DeX1eZY0cQKjRIJmKH7e8CVAhUb2WDAToPDCIM5NiXZ9MYtEKYJGsdduUBXagBKbEKLBpU558A0gA+rxYJbFUZfzk8OJ4gtKX+9ZCG4flROlC2uqRSfNl8gBG5MqUzvJnn3cnaSYIP922dKjW2+GI+nkM42Bz9tynAEqzeCnXE7ZshGnCoGsEBxfVxIgtURfksOJXqwyazDlMSg99ShkD5/VGmfoU1S1QIDAQABMA0GCSqGSIb3DQEBCwUAA4IBAQAcf6IOPOw2i7HjWlk2SV1LLAsU4WwNUPaOTsaDqx6iiNiwtyxGdkYt0f503qsKTkzB7bRde8s8scNpiX2ezzBMtsKFPTlceUC5m4k/NYpaX1AP1hx6rtfnQuCT7zPDCuvU7EzhlDSUnINH2NPBrOgbBV/KZ78ClYSiokPIyOZBjylRgPWV3HH9FWXjM7QoH+3rRBBF6meEJdTLdWXKnDARBMfEfOTq07bjuhz7kol2u8hjw8Fe/Q0o2+dtRIjD5TTeu5rUt7WD16rwAh5glCjflJFv95y8pRJzfYwvooyrBUAdBrsDrD3jxzWHsMreJPFDmpxUhVDaH8DQr0n54b+f
06ccb281-4ce4-4807-8539-e5b44f3ffa8a	08b36877-37e4-49c1-9419-beeb8ff3ce02	keyUse	SIG
2000cb97-217e-4beb-a283-066080cd8c12	08b36877-37e4-49c1-9419-beeb8ff3ce02	privateKey	MIIEowIBAAKCAQEAwNTMkkvejo/sQPaX+WcDxCWf1Sm308Z7ZnwzCoymE7Jqk07Kq5wuSVvIX1bsv6tz11o8A8xQ3a6/NkIDNCvDMAVewN/xvlZ6gBD/mmD8cuJnBx4ZxI3t+DeX1eZY0cQKjRIJmKH7e8CVAhUb2WDAToPDCIM5NiXZ9MYtEKYJGsdduUBXagBKbEKLBpU558A0gA+rxYJbFUZfzk8OJ4gtKX+9ZCG4flROlC2uqRSfNl8gBG5MqUzvJnn3cnaSYIP922dKjW2+GI+nkM42Bz9tynAEqzeCnXE7ZshGnCoGsEBxfVxIgtURfksOJXqwyazDlMSg99ShkD5/VGmfoU1S1QIDAQABAoIBAA9P1n63b5C6dPQjoKW5PF9S7YIrseErHZlhu1vXF0jxHe1ckFNYL4S7uQl5CYuevbAYBjP3AWTY/237h+2YnAMwKfn+zl9i1o9cyi8kDGfC+pNiHIGzE3ZmU7Q0elEN1BinmgN4uVN2U1669l8og5y4ccA3S0+1ab06D1IHHSn3DzXcT712quT781RBAkMIxbXgr/5MJPH4gi3wpTMdMxWdQL16SbWtIA7XXZrygR40fB2W/8re3bfD8Om0j4YJvb/EWMSxRzAN+NgwQMBXDotuHVQ7qLt44+G2bBsZt15bw6+yW3yo6sLIUXgLP+Oi78EJVqNcVexmYqWvZIVOkgECgYEA++Otoi4u+huqkzwkRdJK4Q5isbopfXjh1D7QX4uAtR6gZx7BhPBvRXeNfYeAHFkHrvEli5V7Anly0KPsNMTsylMLzWmmEKamKLywTNBl8BRreZm7qwIa1vDWOMwVOB2Jz8LXcvEX6mQxHqZdpenS0Y2Yr/LD50ufhgXi//v1gTUCgYEAw/pklamx0AlrSQwld1QUR+ajC83ANURdQdjy0dftMKw1aD1bIBNc+ySGZJJTLgVndnud42ulbNdy8vmPnjfD4QLX0nzRIOJokNNGmLSYnHtY68O116ZG4RYBHa0pvXNFx+3GmJ5P+RJ49sJfwFpNFjw/C1pRZv9Xl7RacDCBXyECgYBuLEqQ6jRf7uPv3FldNLrNr1YZuHxXRh04kXTgpYrZLZkpIXmxZNy2fLElW7P8MjC7IvyyDeKs1WQTv+8bsZqEcEh0QyaAl/OLU+Sk5G4B0Vyk5koAZT5KDzMTyqfpUHorpmRIAQ3d1o7pggjp/djZEV7cbomjTB9tJnwATTs3lQKBgQCruG6EWVHt+SLhudvmaGzw5528TQf7ZLDWbtOtnac++9Y8RW7gdzcE9GcyHqj17VRQFFcvm/YyncIiNxxpxXFEa2pTDH1udBStugLk8XG7w7URuMZQdGks90v5Be9KCoR9f9KBjk6C73XAumaONFHRhu6y8uva9Sh6TFTmcwoZIQKBgEqKnEUSslR/zN9YKdQ3IFKsCj4pKgC8B3f+kKTh4C/NaTACICThp4PBlkwzWvnfcqiWGPkFWseDDOmR89sau11UL2B5IkpkMGqZv2Xj7A+hMzmgOUFEE/XXx926zSg2/XrC6FyhsSoyuMd8ogbmZshteEDKOKUhnCQZNYzd95Y0
08c4aadc-6f9b-4719-a888-612f132a1351	08b36877-37e4-49c1-9419-beeb8ff3ce02	priority	100
ea12bd6e-6d75-4747-911e-108bbd069fb4	72c6d005-09fc-44ed-b07c-360d59a7415a	kid	db29a578-0664-4443-9666-78bd464896cd
d0b11fce-6f8e-45cd-b96a-ac3e79b2c16f	72c6d005-09fc-44ed-b07c-360d59a7415a	secret	q4D3YvzZS30-AbF6vQ4ycloJkDRZLA6l-GGsLICeGMSWuSyFOob1YyFqAvToOAc7Vx2VyaqttgU_yx1DLl-EpDpBvmU7Yj688NY7s8T9G6j7nWjhzVV1L9co1hAzVS83KMO9JqxBO6MECtszMDnQ6Gs6lyyl06M5KhDWQYWvTqY
088f9db0-cf1f-472f-a7ea-a2ceaa6841ba	72c6d005-09fc-44ed-b07c-360d59a7415a	algorithm	HS512
b43ac874-b5eb-40e6-bfa2-f6b716cfdff2	72c6d005-09fc-44ed-b07c-360d59a7415a	priority	100
5924c9a4-eae7-4319-857e-060f0c0e80f5	8c42210b-d836-4c55-86e1-e8ed6391880d	allow-default-scopes	true
89ef5e14-090c-43ed-9719-a87cb6ecc9c7	81455906-29b5-4314-88d4-31ec8dd5e4a2	allow-default-scopes	true
8549b0c0-9854-4a38-b54f-362826839e94	39db879f-b3a6-4f93-b7d2-a40d279cf326	max-clients	200
2b7ad0f3-b236-407c-ac71-7ec28daab705	a0217514-3ed1-4193-95d9-e8582590131c	allowed-protocol-mapper-types	oidc-full-name-mapper
b1047919-0431-4615-8288-e2ed9e51f9fe	a0217514-3ed1-4193-95d9-e8582590131c	allowed-protocol-mapper-types	saml-role-list-mapper
ab473d23-8649-48be-b879-3c68b3d40e13	a0217514-3ed1-4193-95d9-e8582590131c	allowed-protocol-mapper-types	oidc-usermodel-attribute-mapper
4a0a5adb-15be-48b3-befe-1db27bc7a2cc	a0217514-3ed1-4193-95d9-e8582590131c	allowed-protocol-mapper-types	saml-user-attribute-mapper
451a8b4c-0bfa-4210-a0e0-9ffba9c8501e	a0217514-3ed1-4193-95d9-e8582590131c	allowed-protocol-mapper-types	saml-user-property-mapper
a342c751-12ef-4afa-96e0-00ccf511e267	a0217514-3ed1-4193-95d9-e8582590131c	allowed-protocol-mapper-types	oidc-address-mapper
5532d0cd-4943-4e6c-9fa4-8115c016cb47	a0217514-3ed1-4193-95d9-e8582590131c	allowed-protocol-mapper-types	oidc-usermodel-property-mapper
d341e1e2-22d5-44a6-a994-a33960e8d6bf	a0217514-3ed1-4193-95d9-e8582590131c	allowed-protocol-mapper-types	oidc-sha256-pairwise-sub-mapper
530f4b85-0cf0-4cac-87a5-6c8fa3a8c6ac	9e95a836-3baf-45fd-878d-3b01e14450aa	client-uris-must-match	true
9ec8fdbf-3670-4269-ad95-d309c2368637	9e95a836-3baf-45fd-878d-3b01e14450aa	host-sending-registration-request-must-match	true
a1da381c-6e22-48b5-aa6f-244538a6d81b	186da7b3-3cee-4cf3-9ab3-bf6648966ff2	allowed-protocol-mapper-types	oidc-usermodel-property-mapper
e6f39ad4-2a0c-4e1a-b0af-84c38061ef6d	186da7b3-3cee-4cf3-9ab3-bf6648966ff2	allowed-protocol-mapper-types	oidc-address-mapper
e9903fb3-9f35-413b-85cc-d305d5b0ec9d	186da7b3-3cee-4cf3-9ab3-bf6648966ff2	allowed-protocol-mapper-types	saml-user-attribute-mapper
54aa4f83-2662-40a6-ba1b-932936007ec4	186da7b3-3cee-4cf3-9ab3-bf6648966ff2	allowed-protocol-mapper-types	oidc-usermodel-attribute-mapper
81520620-a289-4611-b99a-ae61e2536780	186da7b3-3cee-4cf3-9ab3-bf6648966ff2	allowed-protocol-mapper-types	oidc-sha256-pairwise-sub-mapper
9eb8f48a-6535-4795-83fa-f4611bc83ac4	186da7b3-3cee-4cf3-9ab3-bf6648966ff2	allowed-protocol-mapper-types	saml-role-list-mapper
4aa383fd-147d-42bd-b5e2-95c2f30530a3	186da7b3-3cee-4cf3-9ab3-bf6648966ff2	allowed-protocol-mapper-types	saml-user-property-mapper
d8766dbf-85fc-4ecd-b87a-d9d8dd2bfd3c	186da7b3-3cee-4cf3-9ab3-bf6648966ff2	allowed-protocol-mapper-types	oidc-full-name-mapper
\.


--
-- Data for Name: composite_role; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.composite_role (composite, child_role) FROM stdin;
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	b5509541-1a2e-4f2b-b349-76da6dd28a01
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	44364503-acaa-4439-9f59-4e6301cea366
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	4afef63d-66c2-42c6-b2e1-66fba9242ecf
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	ed90c43d-fcc1-487d-a7c2-8a1a5555487a
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	64eb6e37-64d6-4a35-bd9d-7d9ff6eb2572
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	b7149392-4110-445a-b7be-2ac8bf76f59c
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	8d9cf821-967c-42a9-abfc-5b4fb62ee571
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	9e03630a-89a0-4db4-9def-744be7845aa3
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	b0112c42-db3b-473f-b638-aa851e42d1d3
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	232bcf85-a479-4608-a4d5-5f9902584220
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	d4aa5ba9-6d0e-4e81-bd14-3f4f7d1e4b54
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	a714ed78-1388-4ae2-a6ec-0e9f8e216650
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	b4ffe2a2-643e-4532-9fbc-acbc99612a6b
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	2fa6a297-eb34-4eb5-b302-9c5099fce8be
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	ef2d3534-915a-48ee-b63d-100c95569e96
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	388b4549-176e-4909-b870-5c2077c340a3
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	210a7e99-9e88-4c94-8ae0-cc3d59a094ac
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	fd061c98-f939-4566-a492-e501ce9a96e1
64eb6e37-64d6-4a35-bd9d-7d9ff6eb2572	388b4549-176e-4909-b870-5c2077c340a3
e32d865e-3691-40e1-97eb-0422b52e4e68	7a3f815c-74e2-40af-a67a-10909fc7f286
ed90c43d-fcc1-487d-a7c2-8a1a5555487a	ef2d3534-915a-48ee-b63d-100c95569e96
ed90c43d-fcc1-487d-a7c2-8a1a5555487a	fd061c98-f939-4566-a492-e501ce9a96e1
e32d865e-3691-40e1-97eb-0422b52e4e68	4f5e9358-636b-4d56-9e2e-d30c23f894d8
4f5e9358-636b-4d56-9e2e-d30c23f894d8	498b9c20-5d38-4cca-a3b1-de7d39afeb6e
4cb3b9fe-bceb-4e0b-9bc1-88713087103d	c17bb60a-fa29-4035-918a-c28fdd4f3773
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	2288a5d7-165c-49ec-884c-8c44aa7217bd
e32d865e-3691-40e1-97eb-0422b52e4e68	b3961954-2961-4db0-889f-bb4abf624f81
e32d865e-3691-40e1-97eb-0422b52e4e68	f9d8dac5-880f-4397-8ee9-cb62413dcadc
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	caecac3a-b9fd-4f39-8a4c-af7f42dc73db
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	b373583d-3352-4e61-9b6c-b526f2be1d0f
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	574c5fa3-3708-4497-abd1-5ae05a6ba70b
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	7f5067e0-4d7a-416b-9a18-0f729ad25b5f
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	3eb2a5f5-de39-47ff-8d02-5711c37bb6df
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	5dc69cf5-2b03-46bb-b38e-7ab651888614
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	94004920-5b81-447f-89a0-2689105a02cd
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	b592917b-8de8-4559-a18d-f90245eb690f
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	659c8f92-8151-4df7-8e5d-3a2d64b871e2
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	6c7bca9f-1587-4fad-9791-9e191d0d4a63
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	ee4abf42-e67c-4c39-9804-4913931a20ec
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	878289c6-cfbf-428f-91f0-501860881138
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	dd3eee53-9e7d-47d6-b927-c31f38900598
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	6b577a88-49ed-4bd1-9156-22c4a76be406
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	f7e6ee35-77f4-4668-b1bd-290c02e550f1
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	173ac7d6-d034-44bc-80ba-7e913e8b1d88
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	bc65f015-d318-42be-9815-a74022d304fc
574c5fa3-3708-4497-abd1-5ae05a6ba70b	bc65f015-d318-42be-9815-a74022d304fc
574c5fa3-3708-4497-abd1-5ae05a6ba70b	6b577a88-49ed-4bd1-9156-22c4a76be406
7f5067e0-4d7a-416b-9a18-0f729ad25b5f	f7e6ee35-77f4-4668-b1bd-290c02e550f1
c1ffaa7d-8f2b-4cc5-8239-2b3bcc982d51	8ce0b23d-7559-4790-9055-00acb015b55c
c1ffaa7d-8f2b-4cc5-8239-2b3bcc982d51	36a86ce3-c59e-485b-8851-c53930fa1252
c1ffaa7d-8f2b-4cc5-8239-2b3bcc982d51	9445d466-4bdd-4462-810a-9aa67461e64a
c1ffaa7d-8f2b-4cc5-8239-2b3bcc982d51	d2ac2f03-f35b-4eb0-8ed0-8a3d88750c6e
c1ffaa7d-8f2b-4cc5-8239-2b3bcc982d51	124183ce-072f-4bdd-8628-6b114cddc710
c1ffaa7d-8f2b-4cc5-8239-2b3bcc982d51	3b7f1fae-70a9-4e60-b8d5-310dcbb63bc2
c1ffaa7d-8f2b-4cc5-8239-2b3bcc982d51	80ec7cdd-ae0d-4009-822f-f3c77e0157f6
c1ffaa7d-8f2b-4cc5-8239-2b3bcc982d51	1cd58d10-c6eb-4505-b396-c1619ae4f37f
c1ffaa7d-8f2b-4cc5-8239-2b3bcc982d51	4e576312-42c6-4eec-9ca8-62488235a9a6
c1ffaa7d-8f2b-4cc5-8239-2b3bcc982d51	d0121f54-d65d-4aa7-bbda-e4717f0b1dad
c1ffaa7d-8f2b-4cc5-8239-2b3bcc982d51	c522481e-c299-4219-b6c6-7ff2ea5b8c06
c1ffaa7d-8f2b-4cc5-8239-2b3bcc982d51	9b17afba-410e-4ca2-9c94-e66b6c5fef3e
c1ffaa7d-8f2b-4cc5-8239-2b3bcc982d51	c12793ca-e7db-44bc-a0f0-9127b61c3c74
c1ffaa7d-8f2b-4cc5-8239-2b3bcc982d51	7d92004d-37c2-483d-9846-c406c1ae5ff2
c1ffaa7d-8f2b-4cc5-8239-2b3bcc982d51	8fcf6c3a-2154-4c63-afd9-c8017dd103ed
c1ffaa7d-8f2b-4cc5-8239-2b3bcc982d51	337e4814-6657-4363-99fb-f40a1e7e0a74
c1ffaa7d-8f2b-4cc5-8239-2b3bcc982d51	e10a8be5-e246-4a26-ace2-56e0752d55a3
824567dd-42a8-4317-93be-6ad83ed43903	aa2f1228-bbee-4e22-ba4c-87be72192594
9445d466-4bdd-4462-810a-9aa67461e64a	e10a8be5-e246-4a26-ace2-56e0752d55a3
9445d466-4bdd-4462-810a-9aa67461e64a	7d92004d-37c2-483d-9846-c406c1ae5ff2
d2ac2f03-f35b-4eb0-8ed0-8a3d88750c6e	8fcf6c3a-2154-4c63-afd9-c8017dd103ed
824567dd-42a8-4317-93be-6ad83ed43903	818fc192-bf1e-4443-bd28-8eb6a3883ac5
818fc192-bf1e-4443-bd28-8eb6a3883ac5	0fbbf9fe-79da-4bd0-ba91-6fa3a4b41105
f849a0fb-0a5e-45da-8d7a-5eead5c96973	4bf696f6-e576-4ada-a884-ef91ee7a932c
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	1eca9faa-b374-49da-850f-504562e9c36c
c1ffaa7d-8f2b-4cc5-8239-2b3bcc982d51	12bb4521-a50c-4718-8781-9f1cf8bcdf28
824567dd-42a8-4317-93be-6ad83ed43903	f96fdd65-e423-4407-8593-5d72429e8616
824567dd-42a8-4317-93be-6ad83ed43903	9a6ca847-44ec-4656-9551-68c87b01ef78
\.


--
-- Data for Name: credential; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.credential (id, salt, type, user_id, created_date, user_label, secret_data, credential_data, priority) FROM stdin;
88c2f235-6295-4ae4-97d4-05cef43a5ea5	\N	password	3a9e8f9d-f2ec-4e97-812c-282abc3f7721	1759690064196	\N	{"value":"QNerdis2DK3HQrx1r5+nOdxeW223f0YFmeoHTC4S26w=","salt":"NUb7Yaf+MYgTVDfILdgpCQ==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
\.


--
-- Data for Name: databasechangelog; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, comments, tag, liquibase, contexts, labels, deployment_id) FROM stdin;
1.0.0.Final-KEYCLOAK-5461	sthorger@redhat.com	META-INF/jpa-changelog-1.0.0.Final.xml	2025-10-05 18:47:38.302394	1	EXECUTED	9:6f1016664e21e16d26517a4418f5e3df	createTable tableName=APPLICATION_DEFAULT_ROLES; createTable tableName=CLIENT; createTable tableName=CLIENT_SESSION; createTable tableName=CLIENT_SESSION_ROLE; createTable tableName=COMPOSITE_ROLE; createTable tableName=CREDENTIAL; createTable tab...		\N	4.29.1	\N	\N	9690057994
1.0.0.Final-KEYCLOAK-5461	sthorger@redhat.com	META-INF/db2-jpa-changelog-1.0.0.Final.xml	2025-10-05 18:47:38.315542	2	MARK_RAN	9:828775b1596a07d1200ba1d49e5e3941	createTable tableName=APPLICATION_DEFAULT_ROLES; createTable tableName=CLIENT; createTable tableName=CLIENT_SESSION; createTable tableName=CLIENT_SESSION_ROLE; createTable tableName=COMPOSITE_ROLE; createTable tableName=CREDENTIAL; createTable tab...		\N	4.29.1	\N	\N	9690057994
1.1.0.Beta1	sthorger@redhat.com	META-INF/jpa-changelog-1.1.0.Beta1.xml	2025-10-05 18:47:38.357183	3	EXECUTED	9:5f090e44a7d595883c1fb61f4b41fd38	delete tableName=CLIENT_SESSION_ROLE; delete tableName=CLIENT_SESSION; delete tableName=USER_SESSION; createTable tableName=CLIENT_ATTRIBUTES; createTable tableName=CLIENT_SESSION_NOTE; createTable tableName=APP_NODE_REGISTRATIONS; addColumn table...		\N	4.29.1	\N	\N	9690057994
1.1.0.Final	sthorger@redhat.com	META-INF/jpa-changelog-1.1.0.Final.xml	2025-10-05 18:47:38.364018	4	EXECUTED	9:c07e577387a3d2c04d1adc9aaad8730e	renameColumn newColumnName=EVENT_TIME, oldColumnName=TIME, tableName=EVENT_ENTITY		\N	4.29.1	\N	\N	9690057994
1.2.0.Beta1	psilva@redhat.com	META-INF/jpa-changelog-1.2.0.Beta1.xml	2025-10-05 18:47:38.452873	5	EXECUTED	9:b68ce996c655922dbcd2fe6b6ae72686	delete tableName=CLIENT_SESSION_ROLE; delete tableName=CLIENT_SESSION_NOTE; delete tableName=CLIENT_SESSION; delete tableName=USER_SESSION; createTable tableName=PROTOCOL_MAPPER; createTable tableName=PROTOCOL_MAPPER_CONFIG; createTable tableName=...		\N	4.29.1	\N	\N	9690057994
1.2.0.Beta1	psilva@redhat.com	META-INF/db2-jpa-changelog-1.2.0.Beta1.xml	2025-10-05 18:47:38.461026	6	MARK_RAN	9:543b5c9989f024fe35c6f6c5a97de88e	delete tableName=CLIENT_SESSION_ROLE; delete tableName=CLIENT_SESSION_NOTE; delete tableName=CLIENT_SESSION; delete tableName=USER_SESSION; createTable tableName=PROTOCOL_MAPPER; createTable tableName=PROTOCOL_MAPPER_CONFIG; createTable tableName=...		\N	4.29.1	\N	\N	9690057994
1.2.0.RC1	bburke@redhat.com	META-INF/jpa-changelog-1.2.0.CR1.xml	2025-10-05 18:47:38.551816	7	EXECUTED	9:765afebbe21cf5bbca048e632df38336	delete tableName=CLIENT_SESSION_ROLE; delete tableName=CLIENT_SESSION_NOTE; delete tableName=CLIENT_SESSION; delete tableName=USER_SESSION_NOTE; delete tableName=USER_SESSION; createTable tableName=MIGRATION_MODEL; createTable tableName=IDENTITY_P...		\N	4.29.1	\N	\N	9690057994
1.2.0.RC1	bburke@redhat.com	META-INF/db2-jpa-changelog-1.2.0.CR1.xml	2025-10-05 18:47:38.556415	8	MARK_RAN	9:db4a145ba11a6fdaefb397f6dbf829a1	delete tableName=CLIENT_SESSION_ROLE; delete tableName=CLIENT_SESSION_NOTE; delete tableName=CLIENT_SESSION; delete tableName=USER_SESSION_NOTE; delete tableName=USER_SESSION; createTable tableName=MIGRATION_MODEL; createTable tableName=IDENTITY_P...		\N	4.29.1	\N	\N	9690057994
1.2.0.Final	keycloak	META-INF/jpa-changelog-1.2.0.Final.xml	2025-10-05 18:47:38.561914	9	EXECUTED	9:9d05c7be10cdb873f8bcb41bc3a8ab23	update tableName=CLIENT; update tableName=CLIENT; update tableName=CLIENT		\N	4.29.1	\N	\N	9690057994
1.3.0	bburke@redhat.com	META-INF/jpa-changelog-1.3.0.xml	2025-10-05 18:47:38.642579	10	EXECUTED	9:18593702353128d53111f9b1ff0b82b8	delete tableName=CLIENT_SESSION_ROLE; delete tableName=CLIENT_SESSION_PROT_MAPPER; delete tableName=CLIENT_SESSION_NOTE; delete tableName=CLIENT_SESSION; delete tableName=USER_SESSION_NOTE; delete tableName=USER_SESSION; createTable tableName=ADMI...		\N	4.29.1	\N	\N	9690057994
1.4.0	bburke@redhat.com	META-INF/jpa-changelog-1.4.0.xml	2025-10-05 18:47:38.678704	11	EXECUTED	9:6122efe5f090e41a85c0f1c9e52cbb62	delete tableName=CLIENT_SESSION_AUTH_STATUS; delete tableName=CLIENT_SESSION_ROLE; delete tableName=CLIENT_SESSION_PROT_MAPPER; delete tableName=CLIENT_SESSION_NOTE; delete tableName=CLIENT_SESSION; delete tableName=USER_SESSION_NOTE; delete table...		\N	4.29.1	\N	\N	9690057994
1.4.0	bburke@redhat.com	META-INF/db2-jpa-changelog-1.4.0.xml	2025-10-05 18:47:38.682335	12	MARK_RAN	9:e1ff28bf7568451453f844c5d54bb0b5	delete tableName=CLIENT_SESSION_AUTH_STATUS; delete tableName=CLIENT_SESSION_ROLE; delete tableName=CLIENT_SESSION_PROT_MAPPER; delete tableName=CLIENT_SESSION_NOTE; delete tableName=CLIENT_SESSION; delete tableName=USER_SESSION_NOTE; delete table...		\N	4.29.1	\N	\N	9690057994
1.5.0	bburke@redhat.com	META-INF/jpa-changelog-1.5.0.xml	2025-10-05 18:47:38.69395	13	EXECUTED	9:7af32cd8957fbc069f796b61217483fd	delete tableName=CLIENT_SESSION_AUTH_STATUS; delete tableName=CLIENT_SESSION_ROLE; delete tableName=CLIENT_SESSION_PROT_MAPPER; delete tableName=CLIENT_SESSION_NOTE; delete tableName=CLIENT_SESSION; delete tableName=USER_SESSION_NOTE; delete table...		\N	4.29.1	\N	\N	9690057994
1.6.1_from15	mposolda@redhat.com	META-INF/jpa-changelog-1.6.1.xml	2025-10-05 18:47:38.712045	14	EXECUTED	9:6005e15e84714cd83226bf7879f54190	addColumn tableName=REALM; addColumn tableName=KEYCLOAK_ROLE; addColumn tableName=CLIENT; createTable tableName=OFFLINE_USER_SESSION; createTable tableName=OFFLINE_CLIENT_SESSION; addPrimaryKey constraintName=CONSTRAINT_OFFL_US_SES_PK2, tableName=...		\N	4.29.1	\N	\N	9690057994
1.6.1_from16-pre	mposolda@redhat.com	META-INF/jpa-changelog-1.6.1.xml	2025-10-05 18:47:38.714427	15	MARK_RAN	9:bf656f5a2b055d07f314431cae76f06c	delete tableName=OFFLINE_CLIENT_SESSION; delete tableName=OFFLINE_USER_SESSION		\N	4.29.1	\N	\N	9690057994
1.6.1_from16	mposolda@redhat.com	META-INF/jpa-changelog-1.6.1.xml	2025-10-05 18:47:38.717435	16	MARK_RAN	9:f8dadc9284440469dcf71e25ca6ab99b	dropPrimaryKey constraintName=CONSTRAINT_OFFLINE_US_SES_PK, tableName=OFFLINE_USER_SESSION; dropPrimaryKey constraintName=CONSTRAINT_OFFLINE_CL_SES_PK, tableName=OFFLINE_CLIENT_SESSION; addColumn tableName=OFFLINE_USER_SESSION; update tableName=OF...		\N	4.29.1	\N	\N	9690057994
1.6.1	mposolda@redhat.com	META-INF/jpa-changelog-1.6.1.xml	2025-10-05 18:47:38.720258	17	EXECUTED	9:d41d8cd98f00b204e9800998ecf8427e	empty		\N	4.29.1	\N	\N	9690057994
1.7.0	bburke@redhat.com	META-INF/jpa-changelog-1.7.0.xml	2025-10-05 18:47:38.755762	18	EXECUTED	9:3368ff0be4c2855ee2dd9ca813b38d8e	createTable tableName=KEYCLOAK_GROUP; createTable tableName=GROUP_ROLE_MAPPING; createTable tableName=GROUP_ATTRIBUTE; createTable tableName=USER_GROUP_MEMBERSHIP; createTable tableName=REALM_DEFAULT_GROUPS; addColumn tableName=IDENTITY_PROVIDER; ...		\N	4.29.1	\N	\N	9690057994
1.8.0	mposolda@redhat.com	META-INF/jpa-changelog-1.8.0.xml	2025-10-05 18:47:38.841724	19	EXECUTED	9:8ac2fb5dd030b24c0570a763ed75ed20	addColumn tableName=IDENTITY_PROVIDER; createTable tableName=CLIENT_TEMPLATE; createTable tableName=CLIENT_TEMPLATE_ATTRIBUTES; createTable tableName=TEMPLATE_SCOPE_MAPPING; dropNotNullConstraint columnName=CLIENT_ID, tableName=PROTOCOL_MAPPER; ad...		\N	4.29.1	\N	\N	9690057994
1.8.0-2	keycloak	META-INF/jpa-changelog-1.8.0.xml	2025-10-05 18:47:38.891294	20	EXECUTED	9:f91ddca9b19743db60e3057679810e6c	dropDefaultValue columnName=ALGORITHM, tableName=CREDENTIAL; update tableName=CREDENTIAL		\N	4.29.1	\N	\N	9690057994
26.0.0-33201-org-redirect-url	keycloak	META-INF/jpa-changelog-26.0.0.xml	2025-10-05 18:47:41.667124	144	EXECUTED	9:4d0e22b0ac68ebe9794fa9cb752ea660	addColumn tableName=ORG		\N	4.29.1	\N	\N	9690057994
1.8.0	mposolda@redhat.com	META-INF/db2-jpa-changelog-1.8.0.xml	2025-10-05 18:47:38.896589	21	MARK_RAN	9:831e82914316dc8a57dc09d755f23c51	addColumn tableName=IDENTITY_PROVIDER; createTable tableName=CLIENT_TEMPLATE; createTable tableName=CLIENT_TEMPLATE_ATTRIBUTES; createTable tableName=TEMPLATE_SCOPE_MAPPING; dropNotNullConstraint columnName=CLIENT_ID, tableName=PROTOCOL_MAPPER; ad...		\N	4.29.1	\N	\N	9690057994
1.8.0-2	keycloak	META-INF/db2-jpa-changelog-1.8.0.xml	2025-10-05 18:47:38.900139	22	MARK_RAN	9:f91ddca9b19743db60e3057679810e6c	dropDefaultValue columnName=ALGORITHM, tableName=CREDENTIAL; update tableName=CREDENTIAL		\N	4.29.1	\N	\N	9690057994
1.9.0	mposolda@redhat.com	META-INF/jpa-changelog-1.9.0.xml	2025-10-05 18:47:38.976223	23	EXECUTED	9:bc3d0f9e823a69dc21e23e94c7a94bb1	update tableName=REALM; update tableName=REALM; update tableName=REALM; update tableName=REALM; update tableName=CREDENTIAL; update tableName=CREDENTIAL; update tableName=CREDENTIAL; update tableName=REALM; update tableName=REALM; customChange; dr...		\N	4.29.1	\N	\N	9690057994
1.9.1	keycloak	META-INF/jpa-changelog-1.9.1.xml	2025-10-05 18:47:38.98233	24	EXECUTED	9:c9999da42f543575ab790e76439a2679	modifyDataType columnName=PRIVATE_KEY, tableName=REALM; modifyDataType columnName=PUBLIC_KEY, tableName=REALM; modifyDataType columnName=CERTIFICATE, tableName=REALM		\N	4.29.1	\N	\N	9690057994
1.9.1	keycloak	META-INF/db2-jpa-changelog-1.9.1.xml	2025-10-05 18:47:38.984394	25	MARK_RAN	9:0d6c65c6f58732d81569e77b10ba301d	modifyDataType columnName=PRIVATE_KEY, tableName=REALM; modifyDataType columnName=CERTIFICATE, tableName=REALM		\N	4.29.1	\N	\N	9690057994
1.9.2	keycloak	META-INF/jpa-changelog-1.9.2.xml	2025-10-05 18:47:39.199154	26	EXECUTED	9:fc576660fc016ae53d2d4778d84d86d0	createIndex indexName=IDX_USER_EMAIL, tableName=USER_ENTITY; createIndex indexName=IDX_USER_ROLE_MAPPING, tableName=USER_ROLE_MAPPING; createIndex indexName=IDX_USER_GROUP_MAPPING, tableName=USER_GROUP_MEMBERSHIP; createIndex indexName=IDX_USER_CO...		\N	4.29.1	\N	\N	9690057994
authz-2.0.0	psilva@redhat.com	META-INF/jpa-changelog-authz-2.0.0.xml	2025-10-05 18:47:39.255069	27	EXECUTED	9:43ed6b0da89ff77206289e87eaa9c024	createTable tableName=RESOURCE_SERVER; addPrimaryKey constraintName=CONSTRAINT_FARS, tableName=RESOURCE_SERVER; addUniqueConstraint constraintName=UK_AU8TT6T700S9V50BU18WS5HA6, tableName=RESOURCE_SERVER; createTable tableName=RESOURCE_SERVER_RESOU...		\N	4.29.1	\N	\N	9690057994
authz-2.5.1	psilva@redhat.com	META-INF/jpa-changelog-authz-2.5.1.xml	2025-10-05 18:47:39.258018	28	EXECUTED	9:44bae577f551b3738740281eceb4ea70	update tableName=RESOURCE_SERVER_POLICY		\N	4.29.1	\N	\N	9690057994
2.1.0-KEYCLOAK-5461	bburke@redhat.com	META-INF/jpa-changelog-2.1.0.xml	2025-10-05 18:47:39.297209	29	EXECUTED	9:bd88e1f833df0420b01e114533aee5e8	createTable tableName=BROKER_LINK; createTable tableName=FED_USER_ATTRIBUTE; createTable tableName=FED_USER_CONSENT; createTable tableName=FED_USER_CONSENT_ROLE; createTable tableName=FED_USER_CONSENT_PROT_MAPPER; createTable tableName=FED_USER_CR...		\N	4.29.1	\N	\N	9690057994
2.2.0	bburke@redhat.com	META-INF/jpa-changelog-2.2.0.xml	2025-10-05 18:47:39.305243	30	EXECUTED	9:a7022af5267f019d020edfe316ef4371	addColumn tableName=ADMIN_EVENT_ENTITY; createTable tableName=CREDENTIAL_ATTRIBUTE; createTable tableName=FED_CREDENTIAL_ATTRIBUTE; modifyDataType columnName=VALUE, tableName=CREDENTIAL; addForeignKeyConstraint baseTableName=FED_CREDENTIAL_ATTRIBU...		\N	4.29.1	\N	\N	9690057994
2.3.0	bburke@redhat.com	META-INF/jpa-changelog-2.3.0.xml	2025-10-05 18:47:39.314912	31	EXECUTED	9:fc155c394040654d6a79227e56f5e25a	createTable tableName=FEDERATED_USER; addPrimaryKey constraintName=CONSTR_FEDERATED_USER, tableName=FEDERATED_USER; dropDefaultValue columnName=TOTP, tableName=USER_ENTITY; dropColumn columnName=TOTP, tableName=USER_ENTITY; addColumn tableName=IDE...		\N	4.29.1	\N	\N	9690057994
2.4.0	bburke@redhat.com	META-INF/jpa-changelog-2.4.0.xml	2025-10-05 18:47:39.317962	32	EXECUTED	9:eac4ffb2a14795e5dc7b426063e54d88	customChange		\N	4.29.1	\N	\N	9690057994
2.5.0	bburke@redhat.com	META-INF/jpa-changelog-2.5.0.xml	2025-10-05 18:47:39.321744	33	EXECUTED	9:54937c05672568c4c64fc9524c1e9462	customChange; modifyDataType columnName=USER_ID, tableName=OFFLINE_USER_SESSION		\N	4.29.1	\N	\N	9690057994
2.5.0-unicode-oracle	hmlnarik@redhat.com	META-INF/jpa-changelog-2.5.0.xml	2025-10-05 18:47:39.323613	34	MARK_RAN	9:3a32bace77c84d7678d035a7f5a8084e	modifyDataType columnName=DESCRIPTION, tableName=AUTHENTICATION_FLOW; modifyDataType columnName=DESCRIPTION, tableName=CLIENT_TEMPLATE; modifyDataType columnName=DESCRIPTION, tableName=RESOURCE_SERVER_POLICY; modifyDataType columnName=DESCRIPTION,...		\N	4.29.1	\N	\N	9690057994
2.5.0-unicode-other-dbs	hmlnarik@redhat.com	META-INF/jpa-changelog-2.5.0.xml	2025-10-05 18:47:39.341639	35	EXECUTED	9:33d72168746f81f98ae3a1e8e0ca3554	modifyDataType columnName=DESCRIPTION, tableName=AUTHENTICATION_FLOW; modifyDataType columnName=DESCRIPTION, tableName=CLIENT_TEMPLATE; modifyDataType columnName=DESCRIPTION, tableName=RESOURCE_SERVER_POLICY; modifyDataType columnName=DESCRIPTION,...		\N	4.29.1	\N	\N	9690057994
2.5.0-duplicate-email-support	slawomir@dabek.name	META-INF/jpa-changelog-2.5.0.xml	2025-10-05 18:47:39.346483	36	EXECUTED	9:61b6d3d7a4c0e0024b0c839da283da0c	addColumn tableName=REALM		\N	4.29.1	\N	\N	9690057994
2.5.0-unique-group-names	hmlnarik@redhat.com	META-INF/jpa-changelog-2.5.0.xml	2025-10-05 18:47:39.350311	37	EXECUTED	9:8dcac7bdf7378e7d823cdfddebf72fda	addUniqueConstraint constraintName=SIBLING_NAMES, tableName=KEYCLOAK_GROUP		\N	4.29.1	\N	\N	9690057994
2.5.1	bburke@redhat.com	META-INF/jpa-changelog-2.5.1.xml	2025-10-05 18:47:39.353262	38	EXECUTED	9:a2b870802540cb3faa72098db5388af3	addColumn tableName=FED_USER_CONSENT		\N	4.29.1	\N	\N	9690057994
3.0.0	bburke@redhat.com	META-INF/jpa-changelog-3.0.0.xml	2025-10-05 18:47:39.356751	39	EXECUTED	9:132a67499ba24bcc54fb5cbdcfe7e4c0	addColumn tableName=IDENTITY_PROVIDER		\N	4.29.1	\N	\N	9690057994
3.2.0-fix	keycloak	META-INF/jpa-changelog-3.2.0.xml	2025-10-05 18:47:39.357816	40	MARK_RAN	9:938f894c032f5430f2b0fafb1a243462	addNotNullConstraint columnName=REALM_ID, tableName=CLIENT_INITIAL_ACCESS		\N	4.29.1	\N	\N	9690057994
3.2.0-fix-with-keycloak-5416	keycloak	META-INF/jpa-changelog-3.2.0.xml	2025-10-05 18:47:39.359181	41	MARK_RAN	9:845c332ff1874dc5d35974b0babf3006	dropIndex indexName=IDX_CLIENT_INIT_ACC_REALM, tableName=CLIENT_INITIAL_ACCESS; addNotNullConstraint columnName=REALM_ID, tableName=CLIENT_INITIAL_ACCESS; createIndex indexName=IDX_CLIENT_INIT_ACC_REALM, tableName=CLIENT_INITIAL_ACCESS		\N	4.29.1	\N	\N	9690057994
3.2.0-fix-offline-sessions	hmlnarik	META-INF/jpa-changelog-3.2.0.xml	2025-10-05 18:47:39.36202	42	EXECUTED	9:fc86359c079781adc577c5a217e4d04c	customChange		\N	4.29.1	\N	\N	9690057994
3.2.0-fixed	keycloak	META-INF/jpa-changelog-3.2.0.xml	2025-10-05 18:47:40.125866	43	EXECUTED	9:59a64800e3c0d09b825f8a3b444fa8f4	addColumn tableName=REALM; dropPrimaryKey constraintName=CONSTRAINT_OFFL_CL_SES_PK2, tableName=OFFLINE_CLIENT_SESSION; dropColumn columnName=CLIENT_SESSION_ID, tableName=OFFLINE_CLIENT_SESSION; addPrimaryKey constraintName=CONSTRAINT_OFFL_CL_SES_P...		\N	4.29.1	\N	\N	9690057994
3.3.0	keycloak	META-INF/jpa-changelog-3.3.0.xml	2025-10-05 18:47:40.133211	44	EXECUTED	9:d48d6da5c6ccf667807f633fe489ce88	addColumn tableName=USER_ENTITY		\N	4.29.1	\N	\N	9690057994
authz-3.4.0.CR1-resource-server-pk-change-part1	glavoie@gmail.com	META-INF/jpa-changelog-authz-3.4.0.CR1.xml	2025-10-05 18:47:40.139003	45	EXECUTED	9:dde36f7973e80d71fceee683bc5d2951	addColumn tableName=RESOURCE_SERVER_POLICY; addColumn tableName=RESOURCE_SERVER_RESOURCE; addColumn tableName=RESOURCE_SERVER_SCOPE		\N	4.29.1	\N	\N	9690057994
authz-3.4.0.CR1-resource-server-pk-change-part2-KEYCLOAK-6095	hmlnarik@redhat.com	META-INF/jpa-changelog-authz-3.4.0.CR1.xml	2025-10-05 18:47:40.142798	46	EXECUTED	9:b855e9b0a406b34fa323235a0cf4f640	customChange		\N	4.29.1	\N	\N	9690057994
authz-3.4.0.CR1-resource-server-pk-change-part3-fixed	glavoie@gmail.com	META-INF/jpa-changelog-authz-3.4.0.CR1.xml	2025-10-05 18:47:40.14496	47	MARK_RAN	9:51abbacd7b416c50c4421a8cabf7927e	dropIndex indexName=IDX_RES_SERV_POL_RES_SERV, tableName=RESOURCE_SERVER_POLICY; dropIndex indexName=IDX_RES_SRV_RES_RES_SRV, tableName=RESOURCE_SERVER_RESOURCE; dropIndex indexName=IDX_RES_SRV_SCOPE_RES_SRV, tableName=RESOURCE_SERVER_SCOPE		\N	4.29.1	\N	\N	9690057994
authz-3.4.0.CR1-resource-server-pk-change-part3-fixed-nodropindex	glavoie@gmail.com	META-INF/jpa-changelog-authz-3.4.0.CR1.xml	2025-10-05 18:47:40.220179	48	EXECUTED	9:bdc99e567b3398bac83263d375aad143	addNotNullConstraint columnName=RESOURCE_SERVER_CLIENT_ID, tableName=RESOURCE_SERVER_POLICY; addNotNullConstraint columnName=RESOURCE_SERVER_CLIENT_ID, tableName=RESOURCE_SERVER_RESOURCE; addNotNullConstraint columnName=RESOURCE_SERVER_CLIENT_ID, ...		\N	4.29.1	\N	\N	9690057994
authn-3.4.0.CR1-refresh-token-max-reuse	glavoie@gmail.com	META-INF/jpa-changelog-authz-3.4.0.CR1.xml	2025-10-05 18:47:40.226412	49	EXECUTED	9:d198654156881c46bfba39abd7769e69	addColumn tableName=REALM		\N	4.29.1	\N	\N	9690057994
3.4.0	keycloak	META-INF/jpa-changelog-3.4.0.xml	2025-10-05 18:47:40.266639	50	EXECUTED	9:cfdd8736332ccdd72c5256ccb42335db	addPrimaryKey constraintName=CONSTRAINT_REALM_DEFAULT_ROLES, tableName=REALM_DEFAULT_ROLES; addPrimaryKey constraintName=CONSTRAINT_COMPOSITE_ROLE, tableName=COMPOSITE_ROLE; addPrimaryKey constraintName=CONSTR_REALM_DEFAULT_GROUPS, tableName=REALM...		\N	4.29.1	\N	\N	9690057994
3.4.0-KEYCLOAK-5230	hmlnarik@redhat.com	META-INF/jpa-changelog-3.4.0.xml	2025-10-05 18:47:40.460941	51	EXECUTED	9:7c84de3d9bd84d7f077607c1a4dcb714	createIndex indexName=IDX_FU_ATTRIBUTE, tableName=FED_USER_ATTRIBUTE; createIndex indexName=IDX_FU_CONSENT, tableName=FED_USER_CONSENT; createIndex indexName=IDX_FU_CONSENT_RU, tableName=FED_USER_CONSENT; createIndex indexName=IDX_FU_CREDENTIAL, t...		\N	4.29.1	\N	\N	9690057994
3.4.1	psilva@redhat.com	META-INF/jpa-changelog-3.4.1.xml	2025-10-05 18:47:40.465231	52	EXECUTED	9:5a6bb36cbefb6a9d6928452c0852af2d	modifyDataType columnName=VALUE, tableName=CLIENT_ATTRIBUTES		\N	4.29.1	\N	\N	9690057994
3.4.2	keycloak	META-INF/jpa-changelog-3.4.2.xml	2025-10-05 18:47:40.468358	53	EXECUTED	9:8f23e334dbc59f82e0a328373ca6ced0	update tableName=REALM		\N	4.29.1	\N	\N	9690057994
3.4.2-KEYCLOAK-5172	mkanis@redhat.com	META-INF/jpa-changelog-3.4.2.xml	2025-10-05 18:47:40.471287	54	EXECUTED	9:9156214268f09d970cdf0e1564d866af	update tableName=CLIENT		\N	4.29.1	\N	\N	9690057994
4.0.0-KEYCLOAK-6335	bburke@redhat.com	META-INF/jpa-changelog-4.0.0.xml	2025-10-05 18:47:40.479603	55	EXECUTED	9:db806613b1ed154826c02610b7dbdf74	createTable tableName=CLIENT_AUTH_FLOW_BINDINGS; addPrimaryKey constraintName=C_CLI_FLOW_BIND, tableName=CLIENT_AUTH_FLOW_BINDINGS		\N	4.29.1	\N	\N	9690057994
4.0.0-CLEANUP-UNUSED-TABLE	bburke@redhat.com	META-INF/jpa-changelog-4.0.0.xml	2025-10-05 18:47:40.48776	56	EXECUTED	9:229a041fb72d5beac76bb94a5fa709de	dropTable tableName=CLIENT_IDENTITY_PROV_MAPPING		\N	4.29.1	\N	\N	9690057994
4.0.0-KEYCLOAK-6228	bburke@redhat.com	META-INF/jpa-changelog-4.0.0.xml	2025-10-05 18:47:40.522634	57	EXECUTED	9:079899dade9c1e683f26b2aa9ca6ff04	dropUniqueConstraint constraintName=UK_JKUWUVD56ONTGSUHOGM8UEWRT, tableName=USER_CONSENT; dropNotNullConstraint columnName=CLIENT_ID, tableName=USER_CONSENT; addColumn tableName=USER_CONSENT; addUniqueConstraint constraintName=UK_JKUWUVD56ONTGSUHO...		\N	4.29.1	\N	\N	9690057994
4.0.0-KEYCLOAK-5579-fixed	mposolda@redhat.com	META-INF/jpa-changelog-4.0.0.xml	2025-10-05 18:47:40.719323	58	EXECUTED	9:139b79bcbbfe903bb1c2d2a4dbf001d9	dropForeignKeyConstraint baseTableName=CLIENT_TEMPLATE_ATTRIBUTES, constraintName=FK_CL_TEMPL_ATTR_TEMPL; renameTable newTableName=CLIENT_SCOPE_ATTRIBUTES, oldTableName=CLIENT_TEMPLATE_ATTRIBUTES; renameColumn newColumnName=SCOPE_ID, oldColumnName...		\N	4.29.1	\N	\N	9690057994
authz-4.0.0.CR1	psilva@redhat.com	META-INF/jpa-changelog-authz-4.0.0.CR1.xml	2025-10-05 18:47:40.741462	59	EXECUTED	9:b55738ad889860c625ba2bf483495a04	createTable tableName=RESOURCE_SERVER_PERM_TICKET; addPrimaryKey constraintName=CONSTRAINT_FAPMT, tableName=RESOURCE_SERVER_PERM_TICKET; addForeignKeyConstraint baseTableName=RESOURCE_SERVER_PERM_TICKET, constraintName=FK_FRSRHO213XCX4WNKOG82SSPMT...		\N	4.29.1	\N	\N	9690057994
authz-4.0.0.Beta3	psilva@redhat.com	META-INF/jpa-changelog-authz-4.0.0.Beta3.xml	2025-10-05 18:47:40.74665	60	EXECUTED	9:e0057eac39aa8fc8e09ac6cfa4ae15fe	addColumn tableName=RESOURCE_SERVER_POLICY; addColumn tableName=RESOURCE_SERVER_PERM_TICKET; addForeignKeyConstraint baseTableName=RESOURCE_SERVER_PERM_TICKET, constraintName=FK_FRSRPO2128CX4WNKOG82SSRFY, referencedTableName=RESOURCE_SERVER_POLICY		\N	4.29.1	\N	\N	9690057994
authz-4.2.0.Final	mhajas@redhat.com	META-INF/jpa-changelog-authz-4.2.0.Final.xml	2025-10-05 18:47:40.753078	61	EXECUTED	9:42a33806f3a0443fe0e7feeec821326c	createTable tableName=RESOURCE_URIS; addForeignKeyConstraint baseTableName=RESOURCE_URIS, constraintName=FK_RESOURCE_SERVER_URIS, referencedTableName=RESOURCE_SERVER_RESOURCE; customChange; dropColumn columnName=URI, tableName=RESOURCE_SERVER_RESO...		\N	4.29.1	\N	\N	9690057994
authz-4.2.0.Final-KEYCLOAK-9944	hmlnarik@redhat.com	META-INF/jpa-changelog-authz-4.2.0.Final.xml	2025-10-05 18:47:40.760462	62	EXECUTED	9:9968206fca46eecc1f51db9c024bfe56	addPrimaryKey constraintName=CONSTRAINT_RESOUR_URIS_PK, tableName=RESOURCE_URIS		\N	4.29.1	\N	\N	9690057994
4.2.0-KEYCLOAK-6313	wadahiro@gmail.com	META-INF/jpa-changelog-4.2.0.xml	2025-10-05 18:47:40.764793	63	EXECUTED	9:92143a6daea0a3f3b8f598c97ce55c3d	addColumn tableName=REQUIRED_ACTION_PROVIDER		\N	4.29.1	\N	\N	9690057994
4.3.0-KEYCLOAK-7984	wadahiro@gmail.com	META-INF/jpa-changelog-4.3.0.xml	2025-10-05 18:47:40.767488	64	EXECUTED	9:82bab26a27195d889fb0429003b18f40	update tableName=REQUIRED_ACTION_PROVIDER		\N	4.29.1	\N	\N	9690057994
4.6.0-KEYCLOAK-7950	psilva@redhat.com	META-INF/jpa-changelog-4.6.0.xml	2025-10-05 18:47:40.770277	65	EXECUTED	9:e590c88ddc0b38b0ae4249bbfcb5abc3	update tableName=RESOURCE_SERVER_RESOURCE		\N	4.29.1	\N	\N	9690057994
4.6.0-KEYCLOAK-8377	keycloak	META-INF/jpa-changelog-4.6.0.xml	2025-10-05 18:47:40.792891	66	EXECUTED	9:5c1f475536118dbdc38d5d7977950cc0	createTable tableName=ROLE_ATTRIBUTE; addPrimaryKey constraintName=CONSTRAINT_ROLE_ATTRIBUTE_PK, tableName=ROLE_ATTRIBUTE; addForeignKeyConstraint baseTableName=ROLE_ATTRIBUTE, constraintName=FK_ROLE_ATTRIBUTE_ID, referencedTableName=KEYCLOAK_ROLE...		\N	4.29.1	\N	\N	9690057994
4.6.0-KEYCLOAK-8555	gideonray@gmail.com	META-INF/jpa-changelog-4.6.0.xml	2025-10-05 18:47:40.817891	67	EXECUTED	9:e7c9f5f9c4d67ccbbcc215440c718a17	createIndex indexName=IDX_COMPONENT_PROVIDER_TYPE, tableName=COMPONENT		\N	4.29.1	\N	\N	9690057994
4.7.0-KEYCLOAK-1267	sguilhen@redhat.com	META-INF/jpa-changelog-4.7.0.xml	2025-10-05 18:47:40.822816	68	EXECUTED	9:88e0bfdda924690d6f4e430c53447dd5	addColumn tableName=REALM		\N	4.29.1	\N	\N	9690057994
4.7.0-KEYCLOAK-7275	keycloak	META-INF/jpa-changelog-4.7.0.xml	2025-10-05 18:47:40.843615	69	EXECUTED	9:f53177f137e1c46b6a88c59ec1cb5218	renameColumn newColumnName=CREATED_ON, oldColumnName=LAST_SESSION_REFRESH, tableName=OFFLINE_USER_SESSION; addNotNullConstraint columnName=CREATED_ON, tableName=OFFLINE_USER_SESSION; addColumn tableName=OFFLINE_USER_SESSION; customChange; createIn...		\N	4.29.1	\N	\N	9690057994
4.8.0-KEYCLOAK-8835	sguilhen@redhat.com	META-INF/jpa-changelog-4.8.0.xml	2025-10-05 18:47:40.848668	70	EXECUTED	9:a74d33da4dc42a37ec27121580d1459f	addNotNullConstraint columnName=SSO_MAX_LIFESPAN_REMEMBER_ME, tableName=REALM; addNotNullConstraint columnName=SSO_IDLE_TIMEOUT_REMEMBER_ME, tableName=REALM		\N	4.29.1	\N	\N	9690057994
authz-7.0.0-KEYCLOAK-10443	psilva@redhat.com	META-INF/jpa-changelog-authz-7.0.0.xml	2025-10-05 18:47:40.85347	71	EXECUTED	9:fd4ade7b90c3b67fae0bfcfcb42dfb5f	addColumn tableName=RESOURCE_SERVER		\N	4.29.1	\N	\N	9690057994
8.0.0-adding-credential-columns	keycloak	META-INF/jpa-changelog-8.0.0.xml	2025-10-05 18:47:40.858627	72	EXECUTED	9:aa072ad090bbba210d8f18781b8cebf4	addColumn tableName=CREDENTIAL; addColumn tableName=FED_USER_CREDENTIAL		\N	4.29.1	\N	\N	9690057994
8.0.0-updating-credential-data-not-oracle-fixed	keycloak	META-INF/jpa-changelog-8.0.0.xml	2025-10-05 18:47:40.86216	73	EXECUTED	9:1ae6be29bab7c2aa376f6983b932be37	update tableName=CREDENTIAL; update tableName=CREDENTIAL; update tableName=CREDENTIAL; update tableName=FED_USER_CREDENTIAL; update tableName=FED_USER_CREDENTIAL; update tableName=FED_USER_CREDENTIAL		\N	4.29.1	\N	\N	9690057994
8.0.0-updating-credential-data-oracle-fixed	keycloak	META-INF/jpa-changelog-8.0.0.xml	2025-10-05 18:47:40.863556	74	MARK_RAN	9:14706f286953fc9a25286dbd8fb30d97	update tableName=CREDENTIAL; update tableName=CREDENTIAL; update tableName=CREDENTIAL; update tableName=FED_USER_CREDENTIAL; update tableName=FED_USER_CREDENTIAL; update tableName=FED_USER_CREDENTIAL		\N	4.29.1	\N	\N	9690057994
8.0.0-credential-cleanup-fixed	keycloak	META-INF/jpa-changelog-8.0.0.xml	2025-10-05 18:47:40.873446	75	EXECUTED	9:2b9cc12779be32c5b40e2e67711a218b	dropDefaultValue columnName=COUNTER, tableName=CREDENTIAL; dropDefaultValue columnName=DIGITS, tableName=CREDENTIAL; dropDefaultValue columnName=PERIOD, tableName=CREDENTIAL; dropDefaultValue columnName=ALGORITHM, tableName=CREDENTIAL; dropColumn ...		\N	4.29.1	\N	\N	9690057994
8.0.0-resource-tag-support	keycloak	META-INF/jpa-changelog-8.0.0.xml	2025-10-05 18:47:40.898045	76	EXECUTED	9:91fa186ce7a5af127a2d7a91ee083cc5	addColumn tableName=MIGRATION_MODEL; createIndex indexName=IDX_UPDATE_TIME, tableName=MIGRATION_MODEL		\N	4.29.1	\N	\N	9690057994
9.0.0-always-display-client	keycloak	META-INF/jpa-changelog-9.0.0.xml	2025-10-05 18:47:40.902515	77	EXECUTED	9:6335e5c94e83a2639ccd68dd24e2e5ad	addColumn tableName=CLIENT		\N	4.29.1	\N	\N	9690057994
9.0.0-drop-constraints-for-column-increase	keycloak	META-INF/jpa-changelog-9.0.0.xml	2025-10-05 18:47:40.904289	78	MARK_RAN	9:6bdb5658951e028bfe16fa0a8228b530	dropUniqueConstraint constraintName=UK_FRSR6T700S9V50BU18WS5PMT, tableName=RESOURCE_SERVER_PERM_TICKET; dropUniqueConstraint constraintName=UK_FRSR6T700S9V50BU18WS5HA6, tableName=RESOURCE_SERVER_RESOURCE; dropPrimaryKey constraintName=CONSTRAINT_O...		\N	4.29.1	\N	\N	9690057994
9.0.0-increase-column-size-federated-fk	keycloak	META-INF/jpa-changelog-9.0.0.xml	2025-10-05 18:47:40.922242	79	EXECUTED	9:d5bc15a64117ccad481ce8792d4c608f	modifyDataType columnName=CLIENT_ID, tableName=FED_USER_CONSENT; modifyDataType columnName=CLIENT_REALM_CONSTRAINT, tableName=KEYCLOAK_ROLE; modifyDataType columnName=OWNER, tableName=RESOURCE_SERVER_POLICY; modifyDataType columnName=CLIENT_ID, ta...		\N	4.29.1	\N	\N	9690057994
9.0.0-recreate-constraints-after-column-increase	keycloak	META-INF/jpa-changelog-9.0.0.xml	2025-10-05 18:47:40.927459	80	MARK_RAN	9:077cba51999515f4d3e7ad5619ab592c	addNotNullConstraint columnName=CLIENT_ID, tableName=OFFLINE_CLIENT_SESSION; addNotNullConstraint columnName=OWNER, tableName=RESOURCE_SERVER_PERM_TICKET; addNotNullConstraint columnName=REQUESTER, tableName=RESOURCE_SERVER_PERM_TICKET; addNotNull...		\N	4.29.1	\N	\N	9690057994
9.0.1-add-index-to-client.client_id	keycloak	META-INF/jpa-changelog-9.0.1.xml	2025-10-05 18:47:40.978954	81	EXECUTED	9:be969f08a163bf47c6b9e9ead8ac2afb	createIndex indexName=IDX_CLIENT_ID, tableName=CLIENT		\N	4.29.1	\N	\N	9690057994
9.0.1-KEYCLOAK-12579-drop-constraints	keycloak	META-INF/jpa-changelog-9.0.1.xml	2025-10-05 18:47:40.981903	82	MARK_RAN	9:6d3bb4408ba5a72f39bd8a0b301ec6e3	dropUniqueConstraint constraintName=SIBLING_NAMES, tableName=KEYCLOAK_GROUP		\N	4.29.1	\N	\N	9690057994
9.0.1-KEYCLOAK-12579-add-not-null-constraint	keycloak	META-INF/jpa-changelog-9.0.1.xml	2025-10-05 18:47:40.988067	83	EXECUTED	9:966bda61e46bebf3cc39518fbed52fa7	addNotNullConstraint columnName=PARENT_GROUP, tableName=KEYCLOAK_GROUP		\N	4.29.1	\N	\N	9690057994
9.0.1-KEYCLOAK-12579-recreate-constraints	keycloak	META-INF/jpa-changelog-9.0.1.xml	2025-10-05 18:47:40.991516	84	MARK_RAN	9:8dcac7bdf7378e7d823cdfddebf72fda	addUniqueConstraint constraintName=SIBLING_NAMES, tableName=KEYCLOAK_GROUP		\N	4.29.1	\N	\N	9690057994
9.0.1-add-index-to-events	keycloak	META-INF/jpa-changelog-9.0.1.xml	2025-10-05 18:47:41.022076	85	EXECUTED	9:7d93d602352a30c0c317e6a609b56599	createIndex indexName=IDX_EVENT_TIME, tableName=EVENT_ENTITY		\N	4.29.1	\N	\N	9690057994
map-remove-ri	keycloak	META-INF/jpa-changelog-11.0.0.xml	2025-10-05 18:47:41.028505	86	EXECUTED	9:71c5969e6cdd8d7b6f47cebc86d37627	dropForeignKeyConstraint baseTableName=REALM, constraintName=FK_TRAF444KK6QRKMS7N56AIWQ5Y; dropForeignKeyConstraint baseTableName=KEYCLOAK_ROLE, constraintName=FK_KJHO5LE2C0RAL09FL8CM9WFW9		\N	4.29.1	\N	\N	9690057994
map-remove-ri	keycloak	META-INF/jpa-changelog-12.0.0.xml	2025-10-05 18:47:41.037391	87	EXECUTED	9:a9ba7d47f065f041b7da856a81762021	dropForeignKeyConstraint baseTableName=REALM_DEFAULT_GROUPS, constraintName=FK_DEF_GROUPS_GROUP; dropForeignKeyConstraint baseTableName=REALM_DEFAULT_ROLES, constraintName=FK_H4WPD7W4HSOOLNI3H0SW7BTJE; dropForeignKeyConstraint baseTableName=CLIENT...		\N	4.29.1	\N	\N	9690057994
12.1.0-add-realm-localization-table	keycloak	META-INF/jpa-changelog-12.0.0.xml	2025-10-05 18:47:41.052284	88	EXECUTED	9:fffabce2bc01e1a8f5110d5278500065	createTable tableName=REALM_LOCALIZATIONS; addPrimaryKey tableName=REALM_LOCALIZATIONS		\N	4.29.1	\N	\N	9690057994
default-roles	keycloak	META-INF/jpa-changelog-13.0.0.xml	2025-10-05 18:47:41.058973	89	EXECUTED	9:fa8a5b5445e3857f4b010bafb5009957	addColumn tableName=REALM; customChange		\N	4.29.1	\N	\N	9690057994
default-roles-cleanup	keycloak	META-INF/jpa-changelog-13.0.0.xml	2025-10-05 18:47:41.09076	90	EXECUTED	9:67ac3241df9a8582d591c5ed87125f39	dropTable tableName=REALM_DEFAULT_ROLES; dropTable tableName=CLIENT_DEFAULT_ROLES		\N	4.29.1	\N	\N	9690057994
13.0.0-KEYCLOAK-16844	keycloak	META-INF/jpa-changelog-13.0.0.xml	2025-10-05 18:47:41.12957	91	EXECUTED	9:ad1194d66c937e3ffc82386c050ba089	createIndex indexName=IDX_OFFLINE_USS_PRELOAD, tableName=OFFLINE_USER_SESSION		\N	4.29.1	\N	\N	9690057994
map-remove-ri-13.0.0	keycloak	META-INF/jpa-changelog-13.0.0.xml	2025-10-05 18:47:41.139145	92	EXECUTED	9:d9be619d94af5a2f5d07b9f003543b91	dropForeignKeyConstraint baseTableName=DEFAULT_CLIENT_SCOPE, constraintName=FK_R_DEF_CLI_SCOPE_SCOPE; dropForeignKeyConstraint baseTableName=CLIENT_SCOPE_CLIENT, constraintName=FK_C_CLI_SCOPE_SCOPE; dropForeignKeyConstraint baseTableName=CLIENT_SC...		\N	4.29.1	\N	\N	9690057994
13.0.0-KEYCLOAK-17992-drop-constraints	keycloak	META-INF/jpa-changelog-13.0.0.xml	2025-10-05 18:47:41.141021	93	MARK_RAN	9:544d201116a0fcc5a5da0925fbbc3bde	dropPrimaryKey constraintName=C_CLI_SCOPE_BIND, tableName=CLIENT_SCOPE_CLIENT; dropIndex indexName=IDX_CLSCOPE_CL, tableName=CLIENT_SCOPE_CLIENT; dropIndex indexName=IDX_CL_CLSCOPE, tableName=CLIENT_SCOPE_CLIENT		\N	4.29.1	\N	\N	9690057994
13.0.0-increase-column-size-federated	keycloak	META-INF/jpa-changelog-13.0.0.xml	2025-10-05 18:47:41.148912	94	EXECUTED	9:43c0c1055b6761b4b3e89de76d612ccf	modifyDataType columnName=CLIENT_ID, tableName=CLIENT_SCOPE_CLIENT; modifyDataType columnName=SCOPE_ID, tableName=CLIENT_SCOPE_CLIENT		\N	4.29.1	\N	\N	9690057994
13.0.0-KEYCLOAK-17992-recreate-constraints	keycloak	META-INF/jpa-changelog-13.0.0.xml	2025-10-05 18:47:41.15319	95	MARK_RAN	9:8bd711fd0330f4fe980494ca43ab1139	addNotNullConstraint columnName=CLIENT_ID, tableName=CLIENT_SCOPE_CLIENT; addNotNullConstraint columnName=SCOPE_ID, tableName=CLIENT_SCOPE_CLIENT; addPrimaryKey constraintName=C_CLI_SCOPE_BIND, tableName=CLIENT_SCOPE_CLIENT; createIndex indexName=...		\N	4.29.1	\N	\N	9690057994
json-string-accomodation-fixed	keycloak	META-INF/jpa-changelog-13.0.0.xml	2025-10-05 18:47:41.158798	96	EXECUTED	9:e07d2bc0970c348bb06fb63b1f82ddbf	addColumn tableName=REALM_ATTRIBUTE; update tableName=REALM_ATTRIBUTE; dropColumn columnName=VALUE, tableName=REALM_ATTRIBUTE; renameColumn newColumnName=VALUE, oldColumnName=VALUE_NEW, tableName=REALM_ATTRIBUTE		\N	4.29.1	\N	\N	9690057994
14.0.0-KEYCLOAK-11019	keycloak	META-INF/jpa-changelog-14.0.0.xml	2025-10-05 18:47:41.207073	97	EXECUTED	9:24fb8611e97f29989bea412aa38d12b7	createIndex indexName=IDX_OFFLINE_CSS_PRELOAD, tableName=OFFLINE_CLIENT_SESSION; createIndex indexName=IDX_OFFLINE_USS_BY_USER, tableName=OFFLINE_USER_SESSION; createIndex indexName=IDX_OFFLINE_USS_BY_USERSESS, tableName=OFFLINE_USER_SESSION		\N	4.29.1	\N	\N	9690057994
14.0.0-KEYCLOAK-18286	keycloak	META-INF/jpa-changelog-14.0.0.xml	2025-10-05 18:47:41.209521	98	MARK_RAN	9:259f89014ce2506ee84740cbf7163aa7	createIndex indexName=IDX_CLIENT_ATT_BY_NAME_VALUE, tableName=CLIENT_ATTRIBUTES		\N	4.29.1	\N	\N	9690057994
14.0.0-KEYCLOAK-18286-revert	keycloak	META-INF/jpa-changelog-14.0.0.xml	2025-10-05 18:47:41.216329	99	MARK_RAN	9:04baaf56c116ed19951cbc2cca584022	dropIndex indexName=IDX_CLIENT_ATT_BY_NAME_VALUE, tableName=CLIENT_ATTRIBUTES		\N	4.29.1	\N	\N	9690057994
14.0.0-KEYCLOAK-18286-supported-dbs	keycloak	META-INF/jpa-changelog-14.0.0.xml	2025-10-05 18:47:41.235384	100	EXECUTED	9:60ca84a0f8c94ec8c3504a5a3bc88ee8	createIndex indexName=IDX_CLIENT_ATT_BY_NAME_VALUE, tableName=CLIENT_ATTRIBUTES		\N	4.29.1	\N	\N	9690057994
14.0.0-KEYCLOAK-18286-unsupported-dbs	keycloak	META-INF/jpa-changelog-14.0.0.xml	2025-10-05 18:47:41.236883	101	MARK_RAN	9:d3d977031d431db16e2c181ce49d73e9	createIndex indexName=IDX_CLIENT_ATT_BY_NAME_VALUE, tableName=CLIENT_ATTRIBUTES		\N	4.29.1	\N	\N	9690057994
KEYCLOAK-17267-add-index-to-user-attributes	keycloak	META-INF/jpa-changelog-14.0.0.xml	2025-10-05 18:47:41.256786	102	EXECUTED	9:0b305d8d1277f3a89a0a53a659ad274c	createIndex indexName=IDX_USER_ATTRIBUTE_NAME, tableName=USER_ATTRIBUTE		\N	4.29.1	\N	\N	9690057994
KEYCLOAK-18146-add-saml-art-binding-identifier	keycloak	META-INF/jpa-changelog-14.0.0.xml	2025-10-05 18:47:41.259717	103	EXECUTED	9:2c374ad2cdfe20e2905a84c8fac48460	customChange		\N	4.29.1	\N	\N	9690057994
15.0.0-KEYCLOAK-18467	keycloak	META-INF/jpa-changelog-15.0.0.xml	2025-10-05 18:47:41.263785	104	EXECUTED	9:47a760639ac597360a8219f5b768b4de	addColumn tableName=REALM_LOCALIZATIONS; update tableName=REALM_LOCALIZATIONS; dropColumn columnName=TEXTS, tableName=REALM_LOCALIZATIONS; renameColumn newColumnName=TEXTS, oldColumnName=TEXTS_NEW, tableName=REALM_LOCALIZATIONS; addNotNullConstrai...		\N	4.29.1	\N	\N	9690057994
17.0.0-9562	keycloak	META-INF/jpa-changelog-17.0.0.xml	2025-10-05 18:47:41.283759	105	EXECUTED	9:a6272f0576727dd8cad2522335f5d99e	createIndex indexName=IDX_USER_SERVICE_ACCOUNT, tableName=USER_ENTITY		\N	4.29.1	\N	\N	9690057994
18.0.0-10625-IDX_ADMIN_EVENT_TIME	keycloak	META-INF/jpa-changelog-18.0.0.xml	2025-10-05 18:47:41.302884	106	EXECUTED	9:015479dbd691d9cc8669282f4828c41d	createIndex indexName=IDX_ADMIN_EVENT_TIME, tableName=ADMIN_EVENT_ENTITY		\N	4.29.1	\N	\N	9690057994
18.0.15-30992-index-consent	keycloak	META-INF/jpa-changelog-18.0.15.xml	2025-10-05 18:47:41.323934	107	EXECUTED	9:80071ede7a05604b1f4906f3bf3b00f0	createIndex indexName=IDX_USCONSENT_SCOPE_ID, tableName=USER_CONSENT_CLIENT_SCOPE		\N	4.29.1	\N	\N	9690057994
19.0.0-10135	keycloak	META-INF/jpa-changelog-19.0.0.xml	2025-10-05 18:47:41.326776	108	EXECUTED	9:9518e495fdd22f78ad6425cc30630221	customChange		\N	4.29.1	\N	\N	9690057994
20.0.0-12964-supported-dbs	keycloak	META-INF/jpa-changelog-20.0.0.xml	2025-10-05 18:47:41.34587	109	EXECUTED	9:e5f243877199fd96bcc842f27a1656ac	createIndex indexName=IDX_GROUP_ATT_BY_NAME_VALUE, tableName=GROUP_ATTRIBUTE		\N	4.29.1	\N	\N	9690057994
20.0.0-12964-unsupported-dbs	keycloak	META-INF/jpa-changelog-20.0.0.xml	2025-10-05 18:47:41.347587	110	MARK_RAN	9:1a6fcaa85e20bdeae0a9ce49b41946a5	createIndex indexName=IDX_GROUP_ATT_BY_NAME_VALUE, tableName=GROUP_ATTRIBUTE		\N	4.29.1	\N	\N	9690057994
client-attributes-string-accomodation-fixed	keycloak	META-INF/jpa-changelog-20.0.0.xml	2025-10-05 18:47:41.352144	111	EXECUTED	9:3f332e13e90739ed0c35b0b25b7822ca	addColumn tableName=CLIENT_ATTRIBUTES; update tableName=CLIENT_ATTRIBUTES; dropColumn columnName=VALUE, tableName=CLIENT_ATTRIBUTES; renameColumn newColumnName=VALUE, oldColumnName=VALUE_NEW, tableName=CLIENT_ATTRIBUTES		\N	4.29.1	\N	\N	9690057994
21.0.2-17277	keycloak	META-INF/jpa-changelog-21.0.2.xml	2025-10-05 18:47:41.3549	112	EXECUTED	9:7ee1f7a3fb8f5588f171fb9a6ab623c0	customChange		\N	4.29.1	\N	\N	9690057994
21.1.0-19404	keycloak	META-INF/jpa-changelog-21.1.0.xml	2025-10-05 18:47:41.3751	113	EXECUTED	9:3d7e830b52f33676b9d64f7f2b2ea634	modifyDataType columnName=DECISION_STRATEGY, tableName=RESOURCE_SERVER_POLICY; modifyDataType columnName=LOGIC, tableName=RESOURCE_SERVER_POLICY; modifyDataType columnName=POLICY_ENFORCE_MODE, tableName=RESOURCE_SERVER		\N	4.29.1	\N	\N	9690057994
21.1.0-19404-2	keycloak	META-INF/jpa-changelog-21.1.0.xml	2025-10-05 18:47:41.377775	114	MARK_RAN	9:627d032e3ef2c06c0e1f73d2ae25c26c	addColumn tableName=RESOURCE_SERVER_POLICY; update tableName=RESOURCE_SERVER_POLICY; dropColumn columnName=DECISION_STRATEGY, tableName=RESOURCE_SERVER_POLICY; renameColumn newColumnName=DECISION_STRATEGY, oldColumnName=DECISION_STRATEGY_NEW, tabl...		\N	4.29.1	\N	\N	9690057994
22.0.0-17484-updated	keycloak	META-INF/jpa-changelog-22.0.0.xml	2025-10-05 18:47:41.382505	115	EXECUTED	9:90af0bfd30cafc17b9f4d6eccd92b8b3	customChange		\N	4.29.1	\N	\N	9690057994
22.0.5-24031	keycloak	META-INF/jpa-changelog-22.0.0.xml	2025-10-05 18:47:41.383789	116	MARK_RAN	9:a60d2d7b315ec2d3eba9e2f145f9df28	customChange		\N	4.29.1	\N	\N	9690057994
23.0.0-12062	keycloak	META-INF/jpa-changelog-23.0.0.xml	2025-10-05 18:47:41.38707	117	EXECUTED	9:2168fbe728fec46ae9baf15bf80927b8	addColumn tableName=COMPONENT_CONFIG; update tableName=COMPONENT_CONFIG; dropColumn columnName=VALUE, tableName=COMPONENT_CONFIG; renameColumn newColumnName=VALUE, oldColumnName=VALUE_NEW, tableName=COMPONENT_CONFIG		\N	4.29.1	\N	\N	9690057994
23.0.0-17258	keycloak	META-INF/jpa-changelog-23.0.0.xml	2025-10-05 18:47:41.39227	118	EXECUTED	9:36506d679a83bbfda85a27ea1864dca8	addColumn tableName=EVENT_ENTITY		\N	4.29.1	\N	\N	9690057994
24.0.0-9758	keycloak	META-INF/jpa-changelog-24.0.0.xml	2025-10-05 18:47:41.452794	119	EXECUTED	9:502c557a5189f600f0f445a9b49ebbce	addColumn tableName=USER_ATTRIBUTE; addColumn tableName=FED_USER_ATTRIBUTE; createIndex indexName=USER_ATTR_LONG_VALUES, tableName=USER_ATTRIBUTE; createIndex indexName=FED_USER_ATTR_LONG_VALUES, tableName=FED_USER_ATTRIBUTE; createIndex indexName...		\N	4.29.1	\N	\N	9690057994
24.0.0-9758-2	keycloak	META-INF/jpa-changelog-24.0.0.xml	2025-10-05 18:47:41.455098	120	EXECUTED	9:bf0fdee10afdf597a987adbf291db7b2	customChange		\N	4.29.1	\N	\N	9690057994
24.0.0-26618-drop-index-if-present	keycloak	META-INF/jpa-changelog-24.0.0.xml	2025-10-05 18:47:41.459196	121	MARK_RAN	9:04baaf56c116ed19951cbc2cca584022	dropIndex indexName=IDX_CLIENT_ATT_BY_NAME_VALUE, tableName=CLIENT_ATTRIBUTES		\N	4.29.1	\N	\N	9690057994
24.0.0-26618-reindex	keycloak	META-INF/jpa-changelog-24.0.0.xml	2025-10-05 18:47:41.475119	122	EXECUTED	9:08707c0f0db1cef6b352db03a60edc7f	createIndex indexName=IDX_CLIENT_ATT_BY_NAME_VALUE, tableName=CLIENT_ATTRIBUTES		\N	4.29.1	\N	\N	9690057994
24.0.2-27228	keycloak	META-INF/jpa-changelog-24.0.2.xml	2025-10-05 18:47:41.477571	123	EXECUTED	9:eaee11f6b8aa25d2cc6a84fb86fc6238	customChange		\N	4.29.1	\N	\N	9690057994
24.0.2-27967-drop-index-if-present	keycloak	META-INF/jpa-changelog-24.0.2.xml	2025-10-05 18:47:41.478653	124	MARK_RAN	9:04baaf56c116ed19951cbc2cca584022	dropIndex indexName=IDX_CLIENT_ATT_BY_NAME_VALUE, tableName=CLIENT_ATTRIBUTES		\N	4.29.1	\N	\N	9690057994
24.0.2-27967-reindex	keycloak	META-INF/jpa-changelog-24.0.2.xml	2025-10-05 18:47:41.479888	125	MARK_RAN	9:d3d977031d431db16e2c181ce49d73e9	createIndex indexName=IDX_CLIENT_ATT_BY_NAME_VALUE, tableName=CLIENT_ATTRIBUTES		\N	4.29.1	\N	\N	9690057994
25.0.0-28265-tables	keycloak	META-INF/jpa-changelog-25.0.0.xml	2025-10-05 18:47:41.482863	126	EXECUTED	9:deda2df035df23388af95bbd36c17cef	addColumn tableName=OFFLINE_USER_SESSION; addColumn tableName=OFFLINE_CLIENT_SESSION		\N	4.29.1	\N	\N	9690057994
25.0.0-28265-index-creation	keycloak	META-INF/jpa-changelog-25.0.0.xml	2025-10-05 18:47:41.498326	127	EXECUTED	9:3e96709818458ae49f3c679ae58d263a	createIndex indexName=IDX_OFFLINE_USS_BY_LAST_SESSION_REFRESH, tableName=OFFLINE_USER_SESSION		\N	4.29.1	\N	\N	9690057994
25.0.0-28265-index-cleanup	keycloak	META-INF/jpa-changelog-25.0.0.xml	2025-10-05 18:47:41.502161	128	EXECUTED	9:8c0cfa341a0474385b324f5c4b2dfcc1	dropIndex indexName=IDX_OFFLINE_USS_CREATEDON, tableName=OFFLINE_USER_SESSION; dropIndex indexName=IDX_OFFLINE_USS_PRELOAD, tableName=OFFLINE_USER_SESSION; dropIndex indexName=IDX_OFFLINE_USS_BY_USERSESS, tableName=OFFLINE_USER_SESSION; dropIndex ...		\N	4.29.1	\N	\N	9690057994
25.0.0-28265-index-2-mysql	keycloak	META-INF/jpa-changelog-25.0.0.xml	2025-10-05 18:47:41.503524	129	MARK_RAN	9:b7ef76036d3126bb83c2423bf4d449d6	createIndex indexName=IDX_OFFLINE_USS_BY_BROKER_SESSION_ID, tableName=OFFLINE_USER_SESSION		\N	4.29.1	\N	\N	9690057994
25.0.0-28265-index-2-not-mysql	keycloak	META-INF/jpa-changelog-25.0.0.xml	2025-10-05 18:47:41.519454	130	EXECUTED	9:23396cf51ab8bc1ae6f0cac7f9f6fcf7	createIndex indexName=IDX_OFFLINE_USS_BY_BROKER_SESSION_ID, tableName=OFFLINE_USER_SESSION		\N	4.29.1	\N	\N	9690057994
25.0.0-org	keycloak	META-INF/jpa-changelog-25.0.0.xml	2025-10-05 18:47:41.536697	131	EXECUTED	9:5c859965c2c9b9c72136c360649af157	createTable tableName=ORG; addUniqueConstraint constraintName=UK_ORG_NAME, tableName=ORG; addUniqueConstraint constraintName=UK_ORG_GROUP, tableName=ORG; createTable tableName=ORG_DOMAIN		\N	4.29.1	\N	\N	9690057994
unique-consentuser	keycloak	META-INF/jpa-changelog-25.0.0.xml	2025-10-05 18:47:41.543245	132	EXECUTED	9:5857626a2ea8767e9a6c66bf3a2cb32f	customChange; dropUniqueConstraint constraintName=UK_JKUWUVD56ONTGSUHOGM8UEWRT, tableName=USER_CONSENT; addUniqueConstraint constraintName=UK_LOCAL_CONSENT, tableName=USER_CONSENT; addUniqueConstraint constraintName=UK_EXTERNAL_CONSENT, tableName=...		\N	4.29.1	\N	\N	9690057994
unique-consentuser-mysql	keycloak	META-INF/jpa-changelog-25.0.0.xml	2025-10-05 18:47:41.545315	133	MARK_RAN	9:b79478aad5adaa1bc428e31563f55e8e	customChange; dropUniqueConstraint constraintName=UK_JKUWUVD56ONTGSUHOGM8UEWRT, tableName=USER_CONSENT; addUniqueConstraint constraintName=UK_LOCAL_CONSENT, tableName=USER_CONSENT; addUniqueConstraint constraintName=UK_EXTERNAL_CONSENT, tableName=...		\N	4.29.1	\N	\N	9690057994
25.0.0-28861-index-creation	keycloak	META-INF/jpa-changelog-25.0.0.xml	2025-10-05 18:47:41.57506	134	EXECUTED	9:b9acb58ac958d9ada0fe12a5d4794ab1	createIndex indexName=IDX_PERM_TICKET_REQUESTER, tableName=RESOURCE_SERVER_PERM_TICKET; createIndex indexName=IDX_PERM_TICKET_OWNER, tableName=RESOURCE_SERVER_PERM_TICKET		\N	4.29.1	\N	\N	9690057994
26.0.0-org-alias	keycloak	META-INF/jpa-changelog-26.0.0.xml	2025-10-05 18:47:41.579366	135	EXECUTED	9:6ef7d63e4412b3c2d66ed179159886a4	addColumn tableName=ORG; update tableName=ORG; addNotNullConstraint columnName=ALIAS, tableName=ORG; addUniqueConstraint constraintName=UK_ORG_ALIAS, tableName=ORG		\N	4.29.1	\N	\N	9690057994
26.0.0-org-group	keycloak	META-INF/jpa-changelog-26.0.0.xml	2025-10-05 18:47:41.583041	136	EXECUTED	9:da8e8087d80ef2ace4f89d8c5b9ca223	addColumn tableName=KEYCLOAK_GROUP; update tableName=KEYCLOAK_GROUP; addNotNullConstraint columnName=TYPE, tableName=KEYCLOAK_GROUP; customChange		\N	4.29.1	\N	\N	9690057994
26.0.0-org-indexes	keycloak	META-INF/jpa-changelog-26.0.0.xml	2025-10-05 18:47:41.59905	137	EXECUTED	9:79b05dcd610a8c7f25ec05135eec0857	createIndex indexName=IDX_ORG_DOMAIN_ORG_ID, tableName=ORG_DOMAIN		\N	4.29.1	\N	\N	9690057994
26.0.0-org-group-membership	keycloak	META-INF/jpa-changelog-26.0.0.xml	2025-10-05 18:47:41.602019	138	EXECUTED	9:a6ace2ce583a421d89b01ba2a28dc2d4	addColumn tableName=USER_GROUP_MEMBERSHIP; update tableName=USER_GROUP_MEMBERSHIP; addNotNullConstraint columnName=MEMBERSHIP_TYPE, tableName=USER_GROUP_MEMBERSHIP		\N	4.29.1	\N	\N	9690057994
31296-persist-revoked-access-tokens	keycloak	META-INF/jpa-changelog-26.0.0.xml	2025-10-05 18:47:41.607148	139	EXECUTED	9:64ef94489d42a358e8304b0e245f0ed4	createTable tableName=REVOKED_TOKEN; addPrimaryKey constraintName=CONSTRAINT_RT, tableName=REVOKED_TOKEN		\N	4.29.1	\N	\N	9690057994
31725-index-persist-revoked-access-tokens	keycloak	META-INF/jpa-changelog-26.0.0.xml	2025-10-05 18:47:41.623173	140	EXECUTED	9:b994246ec2bf7c94da881e1d28782c7b	createIndex indexName=IDX_REV_TOKEN_ON_EXPIRE, tableName=REVOKED_TOKEN		\N	4.29.1	\N	\N	9690057994
26.0.0-idps-for-login	keycloak	META-INF/jpa-changelog-26.0.0.xml	2025-10-05 18:47:41.654253	141	EXECUTED	9:51f5fffadf986983d4bd59582c6c1604	addColumn tableName=IDENTITY_PROVIDER; createIndex indexName=IDX_IDP_REALM_ORG, tableName=IDENTITY_PROVIDER; createIndex indexName=IDX_IDP_FOR_LOGIN, tableName=IDENTITY_PROVIDER; customChange		\N	4.29.1	\N	\N	9690057994
26.0.0-32583-drop-redundant-index-on-client-session	keycloak	META-INF/jpa-changelog-26.0.0.xml	2025-10-05 18:47:41.656467	142	EXECUTED	9:24972d83bf27317a055d234187bb4af9	dropIndex indexName=IDX_US_SESS_ID_ON_CL_SESS, tableName=OFFLINE_CLIENT_SESSION		\N	4.29.1	\N	\N	9690057994
26.0.0.32582-remove-tables-user-session-user-session-note-and-client-session	keycloak	META-INF/jpa-changelog-26.0.0.xml	2025-10-05 18:47:41.664505	143	EXECUTED	9:febdc0f47f2ed241c59e60f58c3ceea5	dropTable tableName=CLIENT_SESSION_ROLE; dropTable tableName=CLIENT_SESSION_NOTE; dropTable tableName=CLIENT_SESSION_PROT_MAPPER; dropTable tableName=CLIENT_SESSION_AUTH_STATUS; dropTable tableName=CLIENT_USER_SESSION_NOTE; dropTable tableName=CLI...		\N	4.29.1	\N	\N	9690057994
\.


--
-- Data for Name: databasechangeloglock; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.databasechangeloglock (id, locked, lockgranted, lockedby) FROM stdin;
1	f	\N	\N
1000	f	\N	\N
\.


--
-- Data for Name: default_client_scope; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.default_client_scope (realm_id, scope_id, default_scope) FROM stdin;
818f97fc-7b35-4bae-bdd7-76b5035dce69	30a1a6be-de4f-4589-9ef2-83e83833e01d	f
818f97fc-7b35-4bae-bdd7-76b5035dce69	ed16778f-75a9-49ca-8f43-08546fa6b7f9	t
818f97fc-7b35-4bae-bdd7-76b5035dce69	64bce3f2-8039-4e6e-8bba-8a09f5fa01f7	t
818f97fc-7b35-4bae-bdd7-76b5035dce69	ffce7315-b3dd-4dbd-bae9-2113339971e9	t
818f97fc-7b35-4bae-bdd7-76b5035dce69	b8b4a138-8568-40f9-ae77-640d689ee351	t
818f97fc-7b35-4bae-bdd7-76b5035dce69	88572d2f-b484-4e8a-a07c-5142e7c3d652	f
818f97fc-7b35-4bae-bdd7-76b5035dce69	08aeccde-4fee-4f6e-8c1d-de4b798be445	f
818f97fc-7b35-4bae-bdd7-76b5035dce69	10d2d5eb-ef10-4929-87c6-cbf2ba34dbf8	t
818f97fc-7b35-4bae-bdd7-76b5035dce69	170f72d8-f885-40a8-9a54-c2de31fb86a9	t
818f97fc-7b35-4bae-bdd7-76b5035dce69	491f228c-d4ff-4558-a930-a1827af3d97c	f
818f97fc-7b35-4bae-bdd7-76b5035dce69	3f3195c0-df74-4157-a3b3-4888e23686c4	t
818f97fc-7b35-4bae-bdd7-76b5035dce69	5aefe0fb-a5e0-4af2-8c61-4aced9ca12df	t
818f97fc-7b35-4bae-bdd7-76b5035dce69	648f874f-c49f-486c-9018-383f959ccb95	f
c43fd595-cebd-4062-ac8d-8943c15bd7de	e1c1fbc7-6160-473c-aaa2-9c3c48563692	f
c43fd595-cebd-4062-ac8d-8943c15bd7de	8344e632-4402-4372-9ad9-b84c5c71cf42	t
c43fd595-cebd-4062-ac8d-8943c15bd7de	df00c71c-a425-442d-bea1-d0572fa8c9a7	t
c43fd595-cebd-4062-ac8d-8943c15bd7de	41fba862-fab4-4ad5-b49e-9801130b762e	t
c43fd595-cebd-4062-ac8d-8943c15bd7de	50a5108d-53c8-4592-8720-28aecb224e47	t
c43fd595-cebd-4062-ac8d-8943c15bd7de	51d4c7f8-6f73-434b-b638-07809009cb75	f
c43fd595-cebd-4062-ac8d-8943c15bd7de	dba36531-8daf-4849-bf80-ff8ef431ebdb	f
c43fd595-cebd-4062-ac8d-8943c15bd7de	98aae9b8-0b25-4b90-a9dc-a0f157e2afe2	t
c43fd595-cebd-4062-ac8d-8943c15bd7de	a809a4eb-789f-4cdd-b800-2a2a2b1ee9c5	t
c43fd595-cebd-4062-ac8d-8943c15bd7de	91dd31ee-b71e-457d-a181-3cb748c0b729	f
c43fd595-cebd-4062-ac8d-8943c15bd7de	16e8f9e7-0b2b-457c-87f9-149e032aeaf7	t
c43fd595-cebd-4062-ac8d-8943c15bd7de	a1f423bd-7673-4235-8124-5460a1225ad3	t
c43fd595-cebd-4062-ac8d-8943c15bd7de	e3abe23c-a247-4e24-bcc6-c42a3d2b8da3	f
\.


--
-- Data for Name: event_entity; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.event_entity (id, client_id, details_json, error, ip_address, realm_id, session_id, event_time, type, user_id, details_json_long_value) FROM stdin;
\.


--
-- Data for Name: fed_user_attribute; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.fed_user_attribute (id, name, user_id, realm_id, storage_provider_id, value, long_value_hash, long_value_hash_lower_case, long_value) FROM stdin;
\.


--
-- Data for Name: fed_user_consent; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.fed_user_consent (id, client_id, user_id, realm_id, storage_provider_id, created_date, last_updated_date, client_storage_provider, external_client_id) FROM stdin;
\.


--
-- Data for Name: fed_user_consent_cl_scope; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.fed_user_consent_cl_scope (user_consent_id, scope_id) FROM stdin;
\.


--
-- Data for Name: fed_user_credential; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.fed_user_credential (id, salt, type, created_date, user_id, realm_id, storage_provider_id, user_label, secret_data, credential_data, priority) FROM stdin;
\.


--
-- Data for Name: fed_user_group_membership; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.fed_user_group_membership (group_id, user_id, realm_id, storage_provider_id) FROM stdin;
\.


--
-- Data for Name: fed_user_required_action; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.fed_user_required_action (required_action, user_id, realm_id, storage_provider_id) FROM stdin;
\.


--
-- Data for Name: fed_user_role_mapping; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.fed_user_role_mapping (role_id, user_id, realm_id, storage_provider_id) FROM stdin;
\.


--
-- Data for Name: federated_identity; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.federated_identity (identity_provider, realm_id, federated_user_id, federated_username, token, user_id) FROM stdin;
\.


--
-- Data for Name: federated_user; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.federated_user (id, storage_provider_id, realm_id) FROM stdin;
\.


--
-- Data for Name: group_attribute; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.group_attribute (id, name, value, group_id) FROM stdin;
\.


--
-- Data for Name: group_role_mapping; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.group_role_mapping (role_id, group_id) FROM stdin;
\.


--
-- Data for Name: identity_provider; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.identity_provider (internal_id, enabled, provider_alias, provider_id, store_token, authenticate_by_default, realm_id, add_token_role, trust_email, first_broker_login_flow_id, post_broker_login_flow_id, provider_display_name, link_only, organization_id, hide_on_login) FROM stdin;
\.


--
-- Data for Name: identity_provider_config; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.identity_provider_config (identity_provider_id, value, name) FROM stdin;
\.


--
-- Data for Name: identity_provider_mapper; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.identity_provider_mapper (id, name, idp_alias, idp_mapper_name, realm_id) FROM stdin;
\.


--
-- Data for Name: idp_mapper_config; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.idp_mapper_config (idp_mapper_id, value, name) FROM stdin;
\.


--
-- Data for Name: keycloak_group; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.keycloak_group (id, name, parent_group, realm_id, type) FROM stdin;
\.


--
-- Data for Name: keycloak_role; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.keycloak_role (id, client_realm_constraint, client_role, description, name, realm_id, client, realm) FROM stdin;
e32d865e-3691-40e1-97eb-0422b52e4e68	818f97fc-7b35-4bae-bdd7-76b5035dce69	f	${role_default-roles}	default-roles-master	818f97fc-7b35-4bae-bdd7-76b5035dce69	\N	\N
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	818f97fc-7b35-4bae-bdd7-76b5035dce69	f	${role_admin}	admin	818f97fc-7b35-4bae-bdd7-76b5035dce69	\N	\N
b5509541-1a2e-4f2b-b349-76da6dd28a01	818f97fc-7b35-4bae-bdd7-76b5035dce69	f	${role_create-realm}	create-realm	818f97fc-7b35-4bae-bdd7-76b5035dce69	\N	\N
44364503-acaa-4439-9f59-4e6301cea366	b62619cb-bb63-4412-a21c-5daadfdd4996	t	${role_create-client}	create-client	818f97fc-7b35-4bae-bdd7-76b5035dce69	b62619cb-bb63-4412-a21c-5daadfdd4996	\N
4afef63d-66c2-42c6-b2e1-66fba9242ecf	b62619cb-bb63-4412-a21c-5daadfdd4996	t	${role_view-realm}	view-realm	818f97fc-7b35-4bae-bdd7-76b5035dce69	b62619cb-bb63-4412-a21c-5daadfdd4996	\N
ed90c43d-fcc1-487d-a7c2-8a1a5555487a	b62619cb-bb63-4412-a21c-5daadfdd4996	t	${role_view-users}	view-users	818f97fc-7b35-4bae-bdd7-76b5035dce69	b62619cb-bb63-4412-a21c-5daadfdd4996	\N
64eb6e37-64d6-4a35-bd9d-7d9ff6eb2572	b62619cb-bb63-4412-a21c-5daadfdd4996	t	${role_view-clients}	view-clients	818f97fc-7b35-4bae-bdd7-76b5035dce69	b62619cb-bb63-4412-a21c-5daadfdd4996	\N
b7149392-4110-445a-b7be-2ac8bf76f59c	b62619cb-bb63-4412-a21c-5daadfdd4996	t	${role_view-events}	view-events	818f97fc-7b35-4bae-bdd7-76b5035dce69	b62619cb-bb63-4412-a21c-5daadfdd4996	\N
8d9cf821-967c-42a9-abfc-5b4fb62ee571	b62619cb-bb63-4412-a21c-5daadfdd4996	t	${role_view-identity-providers}	view-identity-providers	818f97fc-7b35-4bae-bdd7-76b5035dce69	b62619cb-bb63-4412-a21c-5daadfdd4996	\N
9e03630a-89a0-4db4-9def-744be7845aa3	b62619cb-bb63-4412-a21c-5daadfdd4996	t	${role_view-authorization}	view-authorization	818f97fc-7b35-4bae-bdd7-76b5035dce69	b62619cb-bb63-4412-a21c-5daadfdd4996	\N
b0112c42-db3b-473f-b638-aa851e42d1d3	b62619cb-bb63-4412-a21c-5daadfdd4996	t	${role_manage-realm}	manage-realm	818f97fc-7b35-4bae-bdd7-76b5035dce69	b62619cb-bb63-4412-a21c-5daadfdd4996	\N
232bcf85-a479-4608-a4d5-5f9902584220	b62619cb-bb63-4412-a21c-5daadfdd4996	t	${role_manage-users}	manage-users	818f97fc-7b35-4bae-bdd7-76b5035dce69	b62619cb-bb63-4412-a21c-5daadfdd4996	\N
d4aa5ba9-6d0e-4e81-bd14-3f4f7d1e4b54	b62619cb-bb63-4412-a21c-5daadfdd4996	t	${role_manage-clients}	manage-clients	818f97fc-7b35-4bae-bdd7-76b5035dce69	b62619cb-bb63-4412-a21c-5daadfdd4996	\N
a714ed78-1388-4ae2-a6ec-0e9f8e216650	b62619cb-bb63-4412-a21c-5daadfdd4996	t	${role_manage-events}	manage-events	818f97fc-7b35-4bae-bdd7-76b5035dce69	b62619cb-bb63-4412-a21c-5daadfdd4996	\N
b4ffe2a2-643e-4532-9fbc-acbc99612a6b	b62619cb-bb63-4412-a21c-5daadfdd4996	t	${role_manage-identity-providers}	manage-identity-providers	818f97fc-7b35-4bae-bdd7-76b5035dce69	b62619cb-bb63-4412-a21c-5daadfdd4996	\N
2fa6a297-eb34-4eb5-b302-9c5099fce8be	b62619cb-bb63-4412-a21c-5daadfdd4996	t	${role_manage-authorization}	manage-authorization	818f97fc-7b35-4bae-bdd7-76b5035dce69	b62619cb-bb63-4412-a21c-5daadfdd4996	\N
ef2d3534-915a-48ee-b63d-100c95569e96	b62619cb-bb63-4412-a21c-5daadfdd4996	t	${role_query-users}	query-users	818f97fc-7b35-4bae-bdd7-76b5035dce69	b62619cb-bb63-4412-a21c-5daadfdd4996	\N
388b4549-176e-4909-b870-5c2077c340a3	b62619cb-bb63-4412-a21c-5daadfdd4996	t	${role_query-clients}	query-clients	818f97fc-7b35-4bae-bdd7-76b5035dce69	b62619cb-bb63-4412-a21c-5daadfdd4996	\N
210a7e99-9e88-4c94-8ae0-cc3d59a094ac	b62619cb-bb63-4412-a21c-5daadfdd4996	t	${role_query-realms}	query-realms	818f97fc-7b35-4bae-bdd7-76b5035dce69	b62619cb-bb63-4412-a21c-5daadfdd4996	\N
fd061c98-f939-4566-a492-e501ce9a96e1	b62619cb-bb63-4412-a21c-5daadfdd4996	t	${role_query-groups}	query-groups	818f97fc-7b35-4bae-bdd7-76b5035dce69	b62619cb-bb63-4412-a21c-5daadfdd4996	\N
7a3f815c-74e2-40af-a67a-10909fc7f286	c98fef2f-a93f-4a7e-9aa9-2fdc9ac7f43b	t	${role_view-profile}	view-profile	818f97fc-7b35-4bae-bdd7-76b5035dce69	c98fef2f-a93f-4a7e-9aa9-2fdc9ac7f43b	\N
4f5e9358-636b-4d56-9e2e-d30c23f894d8	c98fef2f-a93f-4a7e-9aa9-2fdc9ac7f43b	t	${role_manage-account}	manage-account	818f97fc-7b35-4bae-bdd7-76b5035dce69	c98fef2f-a93f-4a7e-9aa9-2fdc9ac7f43b	\N
498b9c20-5d38-4cca-a3b1-de7d39afeb6e	c98fef2f-a93f-4a7e-9aa9-2fdc9ac7f43b	t	${role_manage-account-links}	manage-account-links	818f97fc-7b35-4bae-bdd7-76b5035dce69	c98fef2f-a93f-4a7e-9aa9-2fdc9ac7f43b	\N
a66f396d-3604-4a3b-82e7-4656c78bc0f0	c98fef2f-a93f-4a7e-9aa9-2fdc9ac7f43b	t	${role_view-applications}	view-applications	818f97fc-7b35-4bae-bdd7-76b5035dce69	c98fef2f-a93f-4a7e-9aa9-2fdc9ac7f43b	\N
c17bb60a-fa29-4035-918a-c28fdd4f3773	c98fef2f-a93f-4a7e-9aa9-2fdc9ac7f43b	t	${role_view-consent}	view-consent	818f97fc-7b35-4bae-bdd7-76b5035dce69	c98fef2f-a93f-4a7e-9aa9-2fdc9ac7f43b	\N
4cb3b9fe-bceb-4e0b-9bc1-88713087103d	c98fef2f-a93f-4a7e-9aa9-2fdc9ac7f43b	t	${role_manage-consent}	manage-consent	818f97fc-7b35-4bae-bdd7-76b5035dce69	c98fef2f-a93f-4a7e-9aa9-2fdc9ac7f43b	\N
27ce47a9-29c4-4780-8e2d-47de1220888e	c98fef2f-a93f-4a7e-9aa9-2fdc9ac7f43b	t	${role_view-groups}	view-groups	818f97fc-7b35-4bae-bdd7-76b5035dce69	c98fef2f-a93f-4a7e-9aa9-2fdc9ac7f43b	\N
2b524a0f-ac36-4ae0-a244-43f64903cecc	c98fef2f-a93f-4a7e-9aa9-2fdc9ac7f43b	t	${role_delete-account}	delete-account	818f97fc-7b35-4bae-bdd7-76b5035dce69	c98fef2f-a93f-4a7e-9aa9-2fdc9ac7f43b	\N
d994e4cc-3625-4821-9ead-0c1ded39354e	7ab4da4d-4c05-421f-95b8-b794e97f5393	t	${role_read-token}	read-token	818f97fc-7b35-4bae-bdd7-76b5035dce69	7ab4da4d-4c05-421f-95b8-b794e97f5393	\N
2288a5d7-165c-49ec-884c-8c44aa7217bd	b62619cb-bb63-4412-a21c-5daadfdd4996	t	${role_impersonation}	impersonation	818f97fc-7b35-4bae-bdd7-76b5035dce69	b62619cb-bb63-4412-a21c-5daadfdd4996	\N
b3961954-2961-4db0-889f-bb4abf624f81	818f97fc-7b35-4bae-bdd7-76b5035dce69	f	${role_offline-access}	offline_access	818f97fc-7b35-4bae-bdd7-76b5035dce69	\N	\N
f9d8dac5-880f-4397-8ee9-cb62413dcadc	818f97fc-7b35-4bae-bdd7-76b5035dce69	f	${role_uma_authorization}	uma_authorization	818f97fc-7b35-4bae-bdd7-76b5035dce69	\N	\N
824567dd-42a8-4317-93be-6ad83ed43903	c43fd595-cebd-4062-ac8d-8943c15bd7de	f	${role_default-roles}	default-roles-eaf-test	c43fd595-cebd-4062-ac8d-8943c15bd7de	\N	\N
caecac3a-b9fd-4f39-8a4c-af7f42dc73db	e16e81cd-d3d9-497a-af75-107cbf3c4a34	t	${role_create-client}	create-client	818f97fc-7b35-4bae-bdd7-76b5035dce69	e16e81cd-d3d9-497a-af75-107cbf3c4a34	\N
b373583d-3352-4e61-9b6c-b526f2be1d0f	e16e81cd-d3d9-497a-af75-107cbf3c4a34	t	${role_view-realm}	view-realm	818f97fc-7b35-4bae-bdd7-76b5035dce69	e16e81cd-d3d9-497a-af75-107cbf3c4a34	\N
574c5fa3-3708-4497-abd1-5ae05a6ba70b	e16e81cd-d3d9-497a-af75-107cbf3c4a34	t	${role_view-users}	view-users	818f97fc-7b35-4bae-bdd7-76b5035dce69	e16e81cd-d3d9-497a-af75-107cbf3c4a34	\N
7f5067e0-4d7a-416b-9a18-0f729ad25b5f	e16e81cd-d3d9-497a-af75-107cbf3c4a34	t	${role_view-clients}	view-clients	818f97fc-7b35-4bae-bdd7-76b5035dce69	e16e81cd-d3d9-497a-af75-107cbf3c4a34	\N
3eb2a5f5-de39-47ff-8d02-5711c37bb6df	e16e81cd-d3d9-497a-af75-107cbf3c4a34	t	${role_view-events}	view-events	818f97fc-7b35-4bae-bdd7-76b5035dce69	e16e81cd-d3d9-497a-af75-107cbf3c4a34	\N
5dc69cf5-2b03-46bb-b38e-7ab651888614	e16e81cd-d3d9-497a-af75-107cbf3c4a34	t	${role_view-identity-providers}	view-identity-providers	818f97fc-7b35-4bae-bdd7-76b5035dce69	e16e81cd-d3d9-497a-af75-107cbf3c4a34	\N
94004920-5b81-447f-89a0-2689105a02cd	e16e81cd-d3d9-497a-af75-107cbf3c4a34	t	${role_view-authorization}	view-authorization	818f97fc-7b35-4bae-bdd7-76b5035dce69	e16e81cd-d3d9-497a-af75-107cbf3c4a34	\N
b592917b-8de8-4559-a18d-f90245eb690f	e16e81cd-d3d9-497a-af75-107cbf3c4a34	t	${role_manage-realm}	manage-realm	818f97fc-7b35-4bae-bdd7-76b5035dce69	e16e81cd-d3d9-497a-af75-107cbf3c4a34	\N
659c8f92-8151-4df7-8e5d-3a2d64b871e2	e16e81cd-d3d9-497a-af75-107cbf3c4a34	t	${role_manage-users}	manage-users	818f97fc-7b35-4bae-bdd7-76b5035dce69	e16e81cd-d3d9-497a-af75-107cbf3c4a34	\N
6c7bca9f-1587-4fad-9791-9e191d0d4a63	e16e81cd-d3d9-497a-af75-107cbf3c4a34	t	${role_manage-clients}	manage-clients	818f97fc-7b35-4bae-bdd7-76b5035dce69	e16e81cd-d3d9-497a-af75-107cbf3c4a34	\N
ee4abf42-e67c-4c39-9804-4913931a20ec	e16e81cd-d3d9-497a-af75-107cbf3c4a34	t	${role_manage-events}	manage-events	818f97fc-7b35-4bae-bdd7-76b5035dce69	e16e81cd-d3d9-497a-af75-107cbf3c4a34	\N
878289c6-cfbf-428f-91f0-501860881138	e16e81cd-d3d9-497a-af75-107cbf3c4a34	t	${role_manage-identity-providers}	manage-identity-providers	818f97fc-7b35-4bae-bdd7-76b5035dce69	e16e81cd-d3d9-497a-af75-107cbf3c4a34	\N
dd3eee53-9e7d-47d6-b927-c31f38900598	e16e81cd-d3d9-497a-af75-107cbf3c4a34	t	${role_manage-authorization}	manage-authorization	818f97fc-7b35-4bae-bdd7-76b5035dce69	e16e81cd-d3d9-497a-af75-107cbf3c4a34	\N
6b577a88-49ed-4bd1-9156-22c4a76be406	e16e81cd-d3d9-497a-af75-107cbf3c4a34	t	${role_query-users}	query-users	818f97fc-7b35-4bae-bdd7-76b5035dce69	e16e81cd-d3d9-497a-af75-107cbf3c4a34	\N
f7e6ee35-77f4-4668-b1bd-290c02e550f1	e16e81cd-d3d9-497a-af75-107cbf3c4a34	t	${role_query-clients}	query-clients	818f97fc-7b35-4bae-bdd7-76b5035dce69	e16e81cd-d3d9-497a-af75-107cbf3c4a34	\N
173ac7d6-d034-44bc-80ba-7e913e8b1d88	e16e81cd-d3d9-497a-af75-107cbf3c4a34	t	${role_query-realms}	query-realms	818f97fc-7b35-4bae-bdd7-76b5035dce69	e16e81cd-d3d9-497a-af75-107cbf3c4a34	\N
bc65f015-d318-42be-9815-a74022d304fc	e16e81cd-d3d9-497a-af75-107cbf3c4a34	t	${role_query-groups}	query-groups	818f97fc-7b35-4bae-bdd7-76b5035dce69	e16e81cd-d3d9-497a-af75-107cbf3c4a34	\N
c1ffaa7d-8f2b-4cc5-8239-2b3bcc982d51	c3f69b94-df50-4fef-8e18-636bb984c0f4	t	${role_realm-admin}	realm-admin	c43fd595-cebd-4062-ac8d-8943c15bd7de	c3f69b94-df50-4fef-8e18-636bb984c0f4	\N
8ce0b23d-7559-4790-9055-00acb015b55c	c3f69b94-df50-4fef-8e18-636bb984c0f4	t	${role_create-client}	create-client	c43fd595-cebd-4062-ac8d-8943c15bd7de	c3f69b94-df50-4fef-8e18-636bb984c0f4	\N
36a86ce3-c59e-485b-8851-c53930fa1252	c3f69b94-df50-4fef-8e18-636bb984c0f4	t	${role_view-realm}	view-realm	c43fd595-cebd-4062-ac8d-8943c15bd7de	c3f69b94-df50-4fef-8e18-636bb984c0f4	\N
9445d466-4bdd-4462-810a-9aa67461e64a	c3f69b94-df50-4fef-8e18-636bb984c0f4	t	${role_view-users}	view-users	c43fd595-cebd-4062-ac8d-8943c15bd7de	c3f69b94-df50-4fef-8e18-636bb984c0f4	\N
d2ac2f03-f35b-4eb0-8ed0-8a3d88750c6e	c3f69b94-df50-4fef-8e18-636bb984c0f4	t	${role_view-clients}	view-clients	c43fd595-cebd-4062-ac8d-8943c15bd7de	c3f69b94-df50-4fef-8e18-636bb984c0f4	\N
124183ce-072f-4bdd-8628-6b114cddc710	c3f69b94-df50-4fef-8e18-636bb984c0f4	t	${role_view-events}	view-events	c43fd595-cebd-4062-ac8d-8943c15bd7de	c3f69b94-df50-4fef-8e18-636bb984c0f4	\N
3b7f1fae-70a9-4e60-b8d5-310dcbb63bc2	c3f69b94-df50-4fef-8e18-636bb984c0f4	t	${role_view-identity-providers}	view-identity-providers	c43fd595-cebd-4062-ac8d-8943c15bd7de	c3f69b94-df50-4fef-8e18-636bb984c0f4	\N
80ec7cdd-ae0d-4009-822f-f3c77e0157f6	c3f69b94-df50-4fef-8e18-636bb984c0f4	t	${role_view-authorization}	view-authorization	c43fd595-cebd-4062-ac8d-8943c15bd7de	c3f69b94-df50-4fef-8e18-636bb984c0f4	\N
1cd58d10-c6eb-4505-b396-c1619ae4f37f	c3f69b94-df50-4fef-8e18-636bb984c0f4	t	${role_manage-realm}	manage-realm	c43fd595-cebd-4062-ac8d-8943c15bd7de	c3f69b94-df50-4fef-8e18-636bb984c0f4	\N
4e576312-42c6-4eec-9ca8-62488235a9a6	c3f69b94-df50-4fef-8e18-636bb984c0f4	t	${role_manage-users}	manage-users	c43fd595-cebd-4062-ac8d-8943c15bd7de	c3f69b94-df50-4fef-8e18-636bb984c0f4	\N
d0121f54-d65d-4aa7-bbda-e4717f0b1dad	c3f69b94-df50-4fef-8e18-636bb984c0f4	t	${role_manage-clients}	manage-clients	c43fd595-cebd-4062-ac8d-8943c15bd7de	c3f69b94-df50-4fef-8e18-636bb984c0f4	\N
c522481e-c299-4219-b6c6-7ff2ea5b8c06	c3f69b94-df50-4fef-8e18-636bb984c0f4	t	${role_manage-events}	manage-events	c43fd595-cebd-4062-ac8d-8943c15bd7de	c3f69b94-df50-4fef-8e18-636bb984c0f4	\N
9b17afba-410e-4ca2-9c94-e66b6c5fef3e	c3f69b94-df50-4fef-8e18-636bb984c0f4	t	${role_manage-identity-providers}	manage-identity-providers	c43fd595-cebd-4062-ac8d-8943c15bd7de	c3f69b94-df50-4fef-8e18-636bb984c0f4	\N
c12793ca-e7db-44bc-a0f0-9127b61c3c74	c3f69b94-df50-4fef-8e18-636bb984c0f4	t	${role_manage-authorization}	manage-authorization	c43fd595-cebd-4062-ac8d-8943c15bd7de	c3f69b94-df50-4fef-8e18-636bb984c0f4	\N
7d92004d-37c2-483d-9846-c406c1ae5ff2	c3f69b94-df50-4fef-8e18-636bb984c0f4	t	${role_query-users}	query-users	c43fd595-cebd-4062-ac8d-8943c15bd7de	c3f69b94-df50-4fef-8e18-636bb984c0f4	\N
8fcf6c3a-2154-4c63-afd9-c8017dd103ed	c3f69b94-df50-4fef-8e18-636bb984c0f4	t	${role_query-clients}	query-clients	c43fd595-cebd-4062-ac8d-8943c15bd7de	c3f69b94-df50-4fef-8e18-636bb984c0f4	\N
337e4814-6657-4363-99fb-f40a1e7e0a74	c3f69b94-df50-4fef-8e18-636bb984c0f4	t	${role_query-realms}	query-realms	c43fd595-cebd-4062-ac8d-8943c15bd7de	c3f69b94-df50-4fef-8e18-636bb984c0f4	\N
e10a8be5-e246-4a26-ace2-56e0752d55a3	c3f69b94-df50-4fef-8e18-636bb984c0f4	t	${role_query-groups}	query-groups	c43fd595-cebd-4062-ac8d-8943c15bd7de	c3f69b94-df50-4fef-8e18-636bb984c0f4	\N
aa2f1228-bbee-4e22-ba4c-87be72192594	26103ba8-ed16-476a-be4f-1b15363631d0	t	${role_view-profile}	view-profile	c43fd595-cebd-4062-ac8d-8943c15bd7de	26103ba8-ed16-476a-be4f-1b15363631d0	\N
818fc192-bf1e-4443-bd28-8eb6a3883ac5	26103ba8-ed16-476a-be4f-1b15363631d0	t	${role_manage-account}	manage-account	c43fd595-cebd-4062-ac8d-8943c15bd7de	26103ba8-ed16-476a-be4f-1b15363631d0	\N
0fbbf9fe-79da-4bd0-ba91-6fa3a4b41105	26103ba8-ed16-476a-be4f-1b15363631d0	t	${role_manage-account-links}	manage-account-links	c43fd595-cebd-4062-ac8d-8943c15bd7de	26103ba8-ed16-476a-be4f-1b15363631d0	\N
23c0c3a5-5fb7-4ce5-88c9-8140ac790a3e	26103ba8-ed16-476a-be4f-1b15363631d0	t	${role_view-applications}	view-applications	c43fd595-cebd-4062-ac8d-8943c15bd7de	26103ba8-ed16-476a-be4f-1b15363631d0	\N
4bf696f6-e576-4ada-a884-ef91ee7a932c	26103ba8-ed16-476a-be4f-1b15363631d0	t	${role_view-consent}	view-consent	c43fd595-cebd-4062-ac8d-8943c15bd7de	26103ba8-ed16-476a-be4f-1b15363631d0	\N
f849a0fb-0a5e-45da-8d7a-5eead5c96973	26103ba8-ed16-476a-be4f-1b15363631d0	t	${role_manage-consent}	manage-consent	c43fd595-cebd-4062-ac8d-8943c15bd7de	26103ba8-ed16-476a-be4f-1b15363631d0	\N
3ca79fe0-cd89-4ab4-9960-403634a750b6	26103ba8-ed16-476a-be4f-1b15363631d0	t	${role_view-groups}	view-groups	c43fd595-cebd-4062-ac8d-8943c15bd7de	26103ba8-ed16-476a-be4f-1b15363631d0	\N
3c395d02-2183-421e-a1eb-9f3ade3b19e3	26103ba8-ed16-476a-be4f-1b15363631d0	t	${role_delete-account}	delete-account	c43fd595-cebd-4062-ac8d-8943c15bd7de	26103ba8-ed16-476a-be4f-1b15363631d0	\N
1eca9faa-b374-49da-850f-504562e9c36c	e16e81cd-d3d9-497a-af75-107cbf3c4a34	t	${role_impersonation}	impersonation	818f97fc-7b35-4bae-bdd7-76b5035dce69	e16e81cd-d3d9-497a-af75-107cbf3c4a34	\N
12bb4521-a50c-4718-8781-9f1cf8bcdf28	c3f69b94-df50-4fef-8e18-636bb984c0f4	t	${role_impersonation}	impersonation	c43fd595-cebd-4062-ac8d-8943c15bd7de	c3f69b94-df50-4fef-8e18-636bb984c0f4	\N
60436cb2-1a6e-4b20-aea0-175bcd85b5a8	b95ff58d-6af0-4fa2-a73e-5381a7533c37	t	${role_read-token}	read-token	c43fd595-cebd-4062-ac8d-8943c15bd7de	b95ff58d-6af0-4fa2-a73e-5381a7533c37	\N
f96fdd65-e423-4407-8593-5d72429e8616	c43fd595-cebd-4062-ac8d-8943c15bd7de	f	${role_offline-access}	offline_access	c43fd595-cebd-4062-ac8d-8943c15bd7de	\N	\N
9a6ca847-44ec-4656-9551-68c87b01ef78	c43fd595-cebd-4062-ac8d-8943c15bd7de	f	${role_uma_authorization}	uma_authorization	c43fd595-cebd-4062-ac8d-8943c15bd7de	\N	\N
\.


--
-- Data for Name: migration_model; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.migration_model (id, version, update_time) FROM stdin;
v3j38	26.0.0	1759690061
\.


--
-- Data for Name: offline_client_session; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.offline_client_session (user_session_id, client_id, offline_flag, "timestamp", data, client_storage_provider, external_client_id, version) FROM stdin;
2f3f4a30-fe88-49b8-bae8-9f57b896bb9b	074f0272-7808-4075-81e8-128c21528edb	0	1759690067	{"authMethod":"openid-connect","notes":{"clientId":"074f0272-7808-4075-81e8-128c21528edb","userSessionStartedAt":"1759690067","iss":"http://localhost:8080/realms/master","startedAt":"1759690067","level-of-authentication":"-1"}}	local	local	0
5511f036-8947-4dfd-bb59-14fd4b947399	074f0272-7808-4075-81e8-128c21528edb	0	1759690068	{"authMethod":"openid-connect","notes":{"clientId":"074f0272-7808-4075-81e8-128c21528edb","userSessionStartedAt":"1759690068","iss":"http://127.0.0.1:8080/realms/master","startedAt":"1759690068","level-of-authentication":"-1"}}	local	local	0
\.


--
-- Data for Name: offline_user_session; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.offline_user_session (user_session_id, user_id, realm_id, created_on, offline_flag, data, last_session_refresh, broker_session_id, version) FROM stdin;
2f3f4a30-fe88-49b8-bae8-9f57b896bb9b	3a9e8f9d-f2ec-4e97-812c-282abc3f7721	818f97fc-7b35-4bae-bdd7-76b5035dce69	1759690067	0	{"ipAddress":"127.0.0.1","authMethod":"openid-connect","rememberMe":false,"started":0,"notes":{"KC_DEVICE_NOTE":"eyJpcEFkZHJlc3MiOiIxMjcuMC4wLjEiLCJvcyI6Ik90aGVyIiwib3NWZXJzaW9uIjoiVW5rbm93biIsImJyb3dzZXIiOiJBcGFjaGUtSHR0cENsaWVudC80LjUuMTQiLCJkZXZpY2UiOiJPdGhlciIsImxhc3RBY2Nlc3MiOjAsIm1vYmlsZSI6ZmFsc2V9","authenticators-completed":"{\\"6048c800-d43e-47a0-a89c-c1aea32b0dec\\":1759690067,\\"f84451b8-fbee-4eee-9008-bbe13b654659\\":1759690067}"},"state":"LOGGED_IN"}	1759690067	\N	0
5511f036-8947-4dfd-bb59-14fd4b947399	3a9e8f9d-f2ec-4e97-812c-282abc3f7721	818f97fc-7b35-4bae-bdd7-76b5035dce69	1759690068	0	{"ipAddress":"127.0.0.1","authMethod":"openid-connect","rememberMe":false,"started":0,"notes":{"KC_DEVICE_NOTE":"eyJpcEFkZHJlc3MiOiIxMjcuMC4wLjEiLCJvcyI6Ik90aGVyIiwib3NWZXJzaW9uIjoiVW5rbm93biIsImJyb3dzZXIiOiJBcGFjaGUtSHR0cENsaWVudC80LjUuMTQiLCJkZXZpY2UiOiJPdGhlciIsImxhc3RBY2Nlc3MiOjAsIm1vYmlsZSI6ZmFsc2V9","authenticators-completed":"{\\"6048c800-d43e-47a0-a89c-c1aea32b0dec\\":1759690068,\\"f84451b8-fbee-4eee-9008-bbe13b654659\\":1759690068}"},"state":"LOGGED_IN"}	1759690068	\N	0
\.


--
-- Data for Name: org; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.org (id, enabled, realm_id, group_id, name, description, alias, redirect_url) FROM stdin;
\.


--
-- Data for Name: org_domain; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.org_domain (id, name, verified, org_id) FROM stdin;
\.


--
-- Data for Name: policy_config; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.policy_config (policy_id, name, value) FROM stdin;
\.


--
-- Data for Name: protocol_mapper; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.protocol_mapper (id, name, protocol, protocol_mapper_name, client_id, client_scope_id) FROM stdin;
cbc69b84-8605-403b-b0a6-86da12bcec13	audience resolve	openid-connect	oidc-audience-resolve-mapper	3ed9a267-16aa-4320-b9a3-2d38f6ad94fa	\N
b87253ab-9fae-417f-a9bc-2b6642e6d203	locale	openid-connect	oidc-usermodel-attribute-mapper	1e451ada-fbdd-4a7f-ad23-b5229b440909	\N
65fc0e43-4d84-452e-9b89-7f23444a86e0	role list	saml	saml-role-list-mapper	\N	ed16778f-75a9-49ca-8f43-08546fa6b7f9
1e0db20a-22d2-4ed7-8749-868a67269cc1	organization	saml	saml-organization-membership-mapper	\N	64bce3f2-8039-4e6e-8bba-8a09f5fa01f7
ed480b62-be93-4ce3-915a-96b1af18e2a4	full name	openid-connect	oidc-full-name-mapper	\N	ffce7315-b3dd-4dbd-bae9-2113339971e9
5b0053c5-8431-4345-8c0c-103a9cba8285	family name	openid-connect	oidc-usermodel-attribute-mapper	\N	ffce7315-b3dd-4dbd-bae9-2113339971e9
35103b46-7e2e-4e13-bda0-59f4d8e0455d	given name	openid-connect	oidc-usermodel-attribute-mapper	\N	ffce7315-b3dd-4dbd-bae9-2113339971e9
2b4af4e7-a63b-4c99-b178-7a2f90213553	middle name	openid-connect	oidc-usermodel-attribute-mapper	\N	ffce7315-b3dd-4dbd-bae9-2113339971e9
e132b378-76e0-485e-8b6c-e6104f4c233e	nickname	openid-connect	oidc-usermodel-attribute-mapper	\N	ffce7315-b3dd-4dbd-bae9-2113339971e9
bd3958a3-9129-46c2-898f-360855eaabc3	username	openid-connect	oidc-usermodel-attribute-mapper	\N	ffce7315-b3dd-4dbd-bae9-2113339971e9
09637c3b-8d7e-411d-ab8e-49e15c072e09	profile	openid-connect	oidc-usermodel-attribute-mapper	\N	ffce7315-b3dd-4dbd-bae9-2113339971e9
7b0763a6-ba9b-4894-a212-293d19bb3693	picture	openid-connect	oidc-usermodel-attribute-mapper	\N	ffce7315-b3dd-4dbd-bae9-2113339971e9
adfd261a-843a-4c65-b35c-2ddd246255e6	website	openid-connect	oidc-usermodel-attribute-mapper	\N	ffce7315-b3dd-4dbd-bae9-2113339971e9
46fd8bf1-85c2-40b4-9a46-f34b6e2a48b0	gender	openid-connect	oidc-usermodel-attribute-mapper	\N	ffce7315-b3dd-4dbd-bae9-2113339971e9
fb30a01d-ea52-476b-b3e3-7832082eee2a	birthdate	openid-connect	oidc-usermodel-attribute-mapper	\N	ffce7315-b3dd-4dbd-bae9-2113339971e9
893309e7-5cd0-400f-8e62-7bbee73bffd7	zoneinfo	openid-connect	oidc-usermodel-attribute-mapper	\N	ffce7315-b3dd-4dbd-bae9-2113339971e9
7bec668a-a962-4cd9-8ab7-73f16a74da9a	locale	openid-connect	oidc-usermodel-attribute-mapper	\N	ffce7315-b3dd-4dbd-bae9-2113339971e9
8f40075b-ff23-403d-8e33-b2eb69fc35db	updated at	openid-connect	oidc-usermodel-attribute-mapper	\N	ffce7315-b3dd-4dbd-bae9-2113339971e9
42a017e5-3231-4e13-a146-953c2a7cffe4	email	openid-connect	oidc-usermodel-attribute-mapper	\N	b8b4a138-8568-40f9-ae77-640d689ee351
7d5a537d-92e8-4b91-8948-505a2c3f9e8b	email verified	openid-connect	oidc-usermodel-property-mapper	\N	b8b4a138-8568-40f9-ae77-640d689ee351
1731ae34-ba25-473f-b549-c7e0fe299bf2	address	openid-connect	oidc-address-mapper	\N	88572d2f-b484-4e8a-a07c-5142e7c3d652
d0be0fdd-7633-4e78-b15a-25ea592bbd99	phone number	openid-connect	oidc-usermodel-attribute-mapper	\N	08aeccde-4fee-4f6e-8c1d-de4b798be445
80bee0e2-734a-40bc-a7f9-9aae0189ae20	phone number verified	openid-connect	oidc-usermodel-attribute-mapper	\N	08aeccde-4fee-4f6e-8c1d-de4b798be445
e057e8ea-c6fe-4809-b15f-26a631996bfd	realm roles	openid-connect	oidc-usermodel-realm-role-mapper	\N	10d2d5eb-ef10-4929-87c6-cbf2ba34dbf8
26f34f09-b0f4-42b3-9fb6-93855d0713c0	client roles	openid-connect	oidc-usermodel-client-role-mapper	\N	10d2d5eb-ef10-4929-87c6-cbf2ba34dbf8
2e0b1fdb-fb9a-4b01-816a-9f9c8445ebc4	audience resolve	openid-connect	oidc-audience-resolve-mapper	\N	10d2d5eb-ef10-4929-87c6-cbf2ba34dbf8
a827c983-77a8-40ec-8cf9-2511568c0e06	allowed web origins	openid-connect	oidc-allowed-origins-mapper	\N	170f72d8-f885-40a8-9a54-c2de31fb86a9
a4645ee6-7789-4e8f-8182-a70100066e09	upn	openid-connect	oidc-usermodel-attribute-mapper	\N	491f228c-d4ff-4558-a930-a1827af3d97c
77e361c2-0c85-4655-a262-6202e9c3c862	groups	openid-connect	oidc-usermodel-realm-role-mapper	\N	491f228c-d4ff-4558-a930-a1827af3d97c
4d2ccbb5-96c7-49ac-8382-977e90cf02aa	acr loa level	openid-connect	oidc-acr-mapper	\N	3f3195c0-df74-4157-a3b3-4888e23686c4
6555c12f-dc04-496d-ba4a-b1901c9af6c6	auth_time	openid-connect	oidc-usersessionmodel-note-mapper	\N	5aefe0fb-a5e0-4af2-8c61-4aced9ca12df
492b1e1d-93b8-4539-bc79-59f79b79a99d	sub	openid-connect	oidc-sub-mapper	\N	5aefe0fb-a5e0-4af2-8c61-4aced9ca12df
1bc22608-97a9-42d7-8b71-3ef2b9b87203	organization	openid-connect	oidc-organization-membership-mapper	\N	648f874f-c49f-486c-9018-383f959ccb95
cb0451bd-c25c-49bd-8fde-bcd4cff752c3	audience resolve	openid-connect	oidc-audience-resolve-mapper	73f93ba6-a168-4560-8a23-1522043206c0	\N
998ff42f-83d9-4fe5-b997-b382d7cdb4bf	role list	saml	saml-role-list-mapper	\N	8344e632-4402-4372-9ad9-b84c5c71cf42
a9b5231b-a973-47e2-932e-295a8d3d94d3	organization	saml	saml-organization-membership-mapper	\N	df00c71c-a425-442d-bea1-d0572fa8c9a7
ed2a92db-1a50-4620-ae94-d28ee3761c33	full name	openid-connect	oidc-full-name-mapper	\N	41fba862-fab4-4ad5-b49e-9801130b762e
349cb0cc-d502-4204-86ec-de4aec72ee5c	family name	openid-connect	oidc-usermodel-attribute-mapper	\N	41fba862-fab4-4ad5-b49e-9801130b762e
509ff692-b6b1-4400-8afc-607a9f55d669	given name	openid-connect	oidc-usermodel-attribute-mapper	\N	41fba862-fab4-4ad5-b49e-9801130b762e
4284c223-0628-49ce-8595-6389e0f5bf4c	middle name	openid-connect	oidc-usermodel-attribute-mapper	\N	41fba862-fab4-4ad5-b49e-9801130b762e
0385d1a3-37c8-40a1-87b8-931f3bd8da30	nickname	openid-connect	oidc-usermodel-attribute-mapper	\N	41fba862-fab4-4ad5-b49e-9801130b762e
c0d64ff2-1aee-4463-9c45-9f70e6acd874	username	openid-connect	oidc-usermodel-attribute-mapper	\N	41fba862-fab4-4ad5-b49e-9801130b762e
7dfdfd96-5d57-41ad-8841-2e9d9c98cdeb	profile	openid-connect	oidc-usermodel-attribute-mapper	\N	41fba862-fab4-4ad5-b49e-9801130b762e
a61967c9-574e-457b-9f3a-8706708ba48f	picture	openid-connect	oidc-usermodel-attribute-mapper	\N	41fba862-fab4-4ad5-b49e-9801130b762e
98060b08-a57f-45c1-9e50-68d7863d3928	website	openid-connect	oidc-usermodel-attribute-mapper	\N	41fba862-fab4-4ad5-b49e-9801130b762e
039bff35-a728-4127-9ef9-918a221baa87	gender	openid-connect	oidc-usermodel-attribute-mapper	\N	41fba862-fab4-4ad5-b49e-9801130b762e
01f4c13a-5e89-4e69-828f-77802c14ba79	birthdate	openid-connect	oidc-usermodel-attribute-mapper	\N	41fba862-fab4-4ad5-b49e-9801130b762e
917a49da-df78-49e6-a5b9-b93c21dba387	zoneinfo	openid-connect	oidc-usermodel-attribute-mapper	\N	41fba862-fab4-4ad5-b49e-9801130b762e
ba4afea0-ed0a-4fb4-9bfb-e2eb6decf20a	locale	openid-connect	oidc-usermodel-attribute-mapper	\N	41fba862-fab4-4ad5-b49e-9801130b762e
017d7162-3d7f-4585-ab00-ec4299660b67	updated at	openid-connect	oidc-usermodel-attribute-mapper	\N	41fba862-fab4-4ad5-b49e-9801130b762e
b81eeafa-267f-442f-8f45-7a87b1b93945	email	openid-connect	oidc-usermodel-attribute-mapper	\N	50a5108d-53c8-4592-8720-28aecb224e47
4a0631f1-f770-4b92-938b-b01983c0a381	email verified	openid-connect	oidc-usermodel-property-mapper	\N	50a5108d-53c8-4592-8720-28aecb224e47
fcf830d1-a38c-4021-a1b2-529d47de4af1	address	openid-connect	oidc-address-mapper	\N	51d4c7f8-6f73-434b-b638-07809009cb75
a2bfafe3-e959-462d-8b52-afd1631fc6a4	phone number	openid-connect	oidc-usermodel-attribute-mapper	\N	dba36531-8daf-4849-bf80-ff8ef431ebdb
52085523-1661-44cd-8d4e-0c89b336c5a3	phone number verified	openid-connect	oidc-usermodel-attribute-mapper	\N	dba36531-8daf-4849-bf80-ff8ef431ebdb
6d9ab36d-7903-43a0-a0ee-24a8ea4244c1	realm roles	openid-connect	oidc-usermodel-realm-role-mapper	\N	98aae9b8-0b25-4b90-a9dc-a0f157e2afe2
37469979-d0c0-41d5-807d-a8fd6e438ba3	client roles	openid-connect	oidc-usermodel-client-role-mapper	\N	98aae9b8-0b25-4b90-a9dc-a0f157e2afe2
a4721286-495e-4d83-8014-f3001fa6d3f1	audience resolve	openid-connect	oidc-audience-resolve-mapper	\N	98aae9b8-0b25-4b90-a9dc-a0f157e2afe2
cb818740-4b59-4f11-9736-ad3f640a7783	allowed web origins	openid-connect	oidc-allowed-origins-mapper	\N	a809a4eb-789f-4cdd-b800-2a2a2b1ee9c5
26980dc9-8374-4bf5-8cfc-79917f485ec3	upn	openid-connect	oidc-usermodel-attribute-mapper	\N	91dd31ee-b71e-457d-a181-3cb748c0b729
fb33aa85-5101-42c1-b092-1577dff4ce90	groups	openid-connect	oidc-usermodel-realm-role-mapper	\N	91dd31ee-b71e-457d-a181-3cb748c0b729
dfbbe1f3-ed4a-4af8-aa10-a9afa5ddc157	acr loa level	openid-connect	oidc-acr-mapper	\N	16e8f9e7-0b2b-457c-87f9-149e032aeaf7
e926edfd-0694-49b9-9ca2-2b4647e5ab02	auth_time	openid-connect	oidc-usersessionmodel-note-mapper	\N	a1f423bd-7673-4235-8124-5460a1225ad3
888bce78-72ec-4cb4-af69-7abc36ebdbb5	sub	openid-connect	oidc-sub-mapper	\N	a1f423bd-7673-4235-8124-5460a1225ad3
12d896bc-0c08-40fb-b435-4c9341eb1b12	organization	openid-connect	oidc-organization-membership-mapper	\N	e3abe23c-a247-4e24-bcc6-c42a3d2b8da3
7235e9a4-d926-48f2-b133-d7f71f54a9d5	locale	openid-connect	oidc-usermodel-attribute-mapper	9bb712ff-a545-4dbd-9df3-3ac5e67dd486	\N
\.


--
-- Data for Name: protocol_mapper_config; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.protocol_mapper_config (protocol_mapper_id, value, name) FROM stdin;
b87253ab-9fae-417f-a9bc-2b6642e6d203	true	introspection.token.claim
b87253ab-9fae-417f-a9bc-2b6642e6d203	true	userinfo.token.claim
b87253ab-9fae-417f-a9bc-2b6642e6d203	locale	user.attribute
b87253ab-9fae-417f-a9bc-2b6642e6d203	true	id.token.claim
b87253ab-9fae-417f-a9bc-2b6642e6d203	true	access.token.claim
b87253ab-9fae-417f-a9bc-2b6642e6d203	locale	claim.name
b87253ab-9fae-417f-a9bc-2b6642e6d203	String	jsonType.label
65fc0e43-4d84-452e-9b89-7f23444a86e0	false	single
65fc0e43-4d84-452e-9b89-7f23444a86e0	Basic	attribute.nameformat
65fc0e43-4d84-452e-9b89-7f23444a86e0	Role	attribute.name
09637c3b-8d7e-411d-ab8e-49e15c072e09	true	introspection.token.claim
09637c3b-8d7e-411d-ab8e-49e15c072e09	true	userinfo.token.claim
09637c3b-8d7e-411d-ab8e-49e15c072e09	profile	user.attribute
09637c3b-8d7e-411d-ab8e-49e15c072e09	true	id.token.claim
09637c3b-8d7e-411d-ab8e-49e15c072e09	true	access.token.claim
09637c3b-8d7e-411d-ab8e-49e15c072e09	profile	claim.name
09637c3b-8d7e-411d-ab8e-49e15c072e09	String	jsonType.label
2b4af4e7-a63b-4c99-b178-7a2f90213553	true	introspection.token.claim
2b4af4e7-a63b-4c99-b178-7a2f90213553	true	userinfo.token.claim
2b4af4e7-a63b-4c99-b178-7a2f90213553	middleName	user.attribute
2b4af4e7-a63b-4c99-b178-7a2f90213553	true	id.token.claim
2b4af4e7-a63b-4c99-b178-7a2f90213553	true	access.token.claim
2b4af4e7-a63b-4c99-b178-7a2f90213553	middle_name	claim.name
2b4af4e7-a63b-4c99-b178-7a2f90213553	String	jsonType.label
35103b46-7e2e-4e13-bda0-59f4d8e0455d	true	introspection.token.claim
35103b46-7e2e-4e13-bda0-59f4d8e0455d	true	userinfo.token.claim
35103b46-7e2e-4e13-bda0-59f4d8e0455d	firstName	user.attribute
35103b46-7e2e-4e13-bda0-59f4d8e0455d	true	id.token.claim
35103b46-7e2e-4e13-bda0-59f4d8e0455d	true	access.token.claim
35103b46-7e2e-4e13-bda0-59f4d8e0455d	given_name	claim.name
35103b46-7e2e-4e13-bda0-59f4d8e0455d	String	jsonType.label
46fd8bf1-85c2-40b4-9a46-f34b6e2a48b0	true	introspection.token.claim
46fd8bf1-85c2-40b4-9a46-f34b6e2a48b0	true	userinfo.token.claim
46fd8bf1-85c2-40b4-9a46-f34b6e2a48b0	gender	user.attribute
46fd8bf1-85c2-40b4-9a46-f34b6e2a48b0	true	id.token.claim
46fd8bf1-85c2-40b4-9a46-f34b6e2a48b0	true	access.token.claim
46fd8bf1-85c2-40b4-9a46-f34b6e2a48b0	gender	claim.name
46fd8bf1-85c2-40b4-9a46-f34b6e2a48b0	String	jsonType.label
5b0053c5-8431-4345-8c0c-103a9cba8285	true	introspection.token.claim
5b0053c5-8431-4345-8c0c-103a9cba8285	true	userinfo.token.claim
5b0053c5-8431-4345-8c0c-103a9cba8285	lastName	user.attribute
5b0053c5-8431-4345-8c0c-103a9cba8285	true	id.token.claim
5b0053c5-8431-4345-8c0c-103a9cba8285	true	access.token.claim
5b0053c5-8431-4345-8c0c-103a9cba8285	family_name	claim.name
5b0053c5-8431-4345-8c0c-103a9cba8285	String	jsonType.label
7b0763a6-ba9b-4894-a212-293d19bb3693	true	introspection.token.claim
7b0763a6-ba9b-4894-a212-293d19bb3693	true	userinfo.token.claim
7b0763a6-ba9b-4894-a212-293d19bb3693	picture	user.attribute
7b0763a6-ba9b-4894-a212-293d19bb3693	true	id.token.claim
7b0763a6-ba9b-4894-a212-293d19bb3693	true	access.token.claim
7b0763a6-ba9b-4894-a212-293d19bb3693	picture	claim.name
7b0763a6-ba9b-4894-a212-293d19bb3693	String	jsonType.label
7bec668a-a962-4cd9-8ab7-73f16a74da9a	true	introspection.token.claim
7bec668a-a962-4cd9-8ab7-73f16a74da9a	true	userinfo.token.claim
7bec668a-a962-4cd9-8ab7-73f16a74da9a	locale	user.attribute
7bec668a-a962-4cd9-8ab7-73f16a74da9a	true	id.token.claim
7bec668a-a962-4cd9-8ab7-73f16a74da9a	true	access.token.claim
7bec668a-a962-4cd9-8ab7-73f16a74da9a	locale	claim.name
7bec668a-a962-4cd9-8ab7-73f16a74da9a	String	jsonType.label
893309e7-5cd0-400f-8e62-7bbee73bffd7	true	introspection.token.claim
893309e7-5cd0-400f-8e62-7bbee73bffd7	true	userinfo.token.claim
893309e7-5cd0-400f-8e62-7bbee73bffd7	zoneinfo	user.attribute
893309e7-5cd0-400f-8e62-7bbee73bffd7	true	id.token.claim
893309e7-5cd0-400f-8e62-7bbee73bffd7	true	access.token.claim
893309e7-5cd0-400f-8e62-7bbee73bffd7	zoneinfo	claim.name
893309e7-5cd0-400f-8e62-7bbee73bffd7	String	jsonType.label
8f40075b-ff23-403d-8e33-b2eb69fc35db	true	introspection.token.claim
8f40075b-ff23-403d-8e33-b2eb69fc35db	true	userinfo.token.claim
8f40075b-ff23-403d-8e33-b2eb69fc35db	updatedAt	user.attribute
8f40075b-ff23-403d-8e33-b2eb69fc35db	true	id.token.claim
8f40075b-ff23-403d-8e33-b2eb69fc35db	true	access.token.claim
8f40075b-ff23-403d-8e33-b2eb69fc35db	updated_at	claim.name
8f40075b-ff23-403d-8e33-b2eb69fc35db	long	jsonType.label
adfd261a-843a-4c65-b35c-2ddd246255e6	true	introspection.token.claim
adfd261a-843a-4c65-b35c-2ddd246255e6	true	userinfo.token.claim
adfd261a-843a-4c65-b35c-2ddd246255e6	website	user.attribute
adfd261a-843a-4c65-b35c-2ddd246255e6	true	id.token.claim
adfd261a-843a-4c65-b35c-2ddd246255e6	true	access.token.claim
adfd261a-843a-4c65-b35c-2ddd246255e6	website	claim.name
adfd261a-843a-4c65-b35c-2ddd246255e6	String	jsonType.label
bd3958a3-9129-46c2-898f-360855eaabc3	true	introspection.token.claim
bd3958a3-9129-46c2-898f-360855eaabc3	true	userinfo.token.claim
bd3958a3-9129-46c2-898f-360855eaabc3	username	user.attribute
bd3958a3-9129-46c2-898f-360855eaabc3	true	id.token.claim
bd3958a3-9129-46c2-898f-360855eaabc3	true	access.token.claim
bd3958a3-9129-46c2-898f-360855eaabc3	preferred_username	claim.name
bd3958a3-9129-46c2-898f-360855eaabc3	String	jsonType.label
e132b378-76e0-485e-8b6c-e6104f4c233e	true	introspection.token.claim
e132b378-76e0-485e-8b6c-e6104f4c233e	true	userinfo.token.claim
e132b378-76e0-485e-8b6c-e6104f4c233e	nickname	user.attribute
e132b378-76e0-485e-8b6c-e6104f4c233e	true	id.token.claim
e132b378-76e0-485e-8b6c-e6104f4c233e	true	access.token.claim
e132b378-76e0-485e-8b6c-e6104f4c233e	nickname	claim.name
e132b378-76e0-485e-8b6c-e6104f4c233e	String	jsonType.label
ed480b62-be93-4ce3-915a-96b1af18e2a4	true	introspection.token.claim
ed480b62-be93-4ce3-915a-96b1af18e2a4	true	userinfo.token.claim
ed480b62-be93-4ce3-915a-96b1af18e2a4	true	id.token.claim
ed480b62-be93-4ce3-915a-96b1af18e2a4	true	access.token.claim
fb30a01d-ea52-476b-b3e3-7832082eee2a	true	introspection.token.claim
fb30a01d-ea52-476b-b3e3-7832082eee2a	true	userinfo.token.claim
fb30a01d-ea52-476b-b3e3-7832082eee2a	birthdate	user.attribute
fb30a01d-ea52-476b-b3e3-7832082eee2a	true	id.token.claim
fb30a01d-ea52-476b-b3e3-7832082eee2a	true	access.token.claim
fb30a01d-ea52-476b-b3e3-7832082eee2a	birthdate	claim.name
fb30a01d-ea52-476b-b3e3-7832082eee2a	String	jsonType.label
42a017e5-3231-4e13-a146-953c2a7cffe4	true	introspection.token.claim
42a017e5-3231-4e13-a146-953c2a7cffe4	true	userinfo.token.claim
42a017e5-3231-4e13-a146-953c2a7cffe4	email	user.attribute
42a017e5-3231-4e13-a146-953c2a7cffe4	true	id.token.claim
42a017e5-3231-4e13-a146-953c2a7cffe4	true	access.token.claim
42a017e5-3231-4e13-a146-953c2a7cffe4	email	claim.name
42a017e5-3231-4e13-a146-953c2a7cffe4	String	jsonType.label
7d5a537d-92e8-4b91-8948-505a2c3f9e8b	true	introspection.token.claim
7d5a537d-92e8-4b91-8948-505a2c3f9e8b	true	userinfo.token.claim
7d5a537d-92e8-4b91-8948-505a2c3f9e8b	emailVerified	user.attribute
7d5a537d-92e8-4b91-8948-505a2c3f9e8b	true	id.token.claim
7d5a537d-92e8-4b91-8948-505a2c3f9e8b	true	access.token.claim
7d5a537d-92e8-4b91-8948-505a2c3f9e8b	email_verified	claim.name
7d5a537d-92e8-4b91-8948-505a2c3f9e8b	boolean	jsonType.label
1731ae34-ba25-473f-b549-c7e0fe299bf2	formatted	user.attribute.formatted
1731ae34-ba25-473f-b549-c7e0fe299bf2	country	user.attribute.country
1731ae34-ba25-473f-b549-c7e0fe299bf2	true	introspection.token.claim
1731ae34-ba25-473f-b549-c7e0fe299bf2	postal_code	user.attribute.postal_code
1731ae34-ba25-473f-b549-c7e0fe299bf2	true	userinfo.token.claim
1731ae34-ba25-473f-b549-c7e0fe299bf2	street	user.attribute.street
1731ae34-ba25-473f-b549-c7e0fe299bf2	true	id.token.claim
1731ae34-ba25-473f-b549-c7e0fe299bf2	region	user.attribute.region
1731ae34-ba25-473f-b549-c7e0fe299bf2	true	access.token.claim
1731ae34-ba25-473f-b549-c7e0fe299bf2	locality	user.attribute.locality
80bee0e2-734a-40bc-a7f9-9aae0189ae20	true	introspection.token.claim
80bee0e2-734a-40bc-a7f9-9aae0189ae20	true	userinfo.token.claim
80bee0e2-734a-40bc-a7f9-9aae0189ae20	phoneNumberVerified	user.attribute
80bee0e2-734a-40bc-a7f9-9aae0189ae20	true	id.token.claim
80bee0e2-734a-40bc-a7f9-9aae0189ae20	true	access.token.claim
80bee0e2-734a-40bc-a7f9-9aae0189ae20	phone_number_verified	claim.name
80bee0e2-734a-40bc-a7f9-9aae0189ae20	boolean	jsonType.label
d0be0fdd-7633-4e78-b15a-25ea592bbd99	true	introspection.token.claim
d0be0fdd-7633-4e78-b15a-25ea592bbd99	true	userinfo.token.claim
d0be0fdd-7633-4e78-b15a-25ea592bbd99	phoneNumber	user.attribute
d0be0fdd-7633-4e78-b15a-25ea592bbd99	true	id.token.claim
d0be0fdd-7633-4e78-b15a-25ea592bbd99	true	access.token.claim
d0be0fdd-7633-4e78-b15a-25ea592bbd99	phone_number	claim.name
d0be0fdd-7633-4e78-b15a-25ea592bbd99	String	jsonType.label
26f34f09-b0f4-42b3-9fb6-93855d0713c0	true	introspection.token.claim
26f34f09-b0f4-42b3-9fb6-93855d0713c0	true	multivalued
26f34f09-b0f4-42b3-9fb6-93855d0713c0	foo	user.attribute
26f34f09-b0f4-42b3-9fb6-93855d0713c0	true	access.token.claim
26f34f09-b0f4-42b3-9fb6-93855d0713c0	resource_access.${client_id}.roles	claim.name
26f34f09-b0f4-42b3-9fb6-93855d0713c0	String	jsonType.label
2e0b1fdb-fb9a-4b01-816a-9f9c8445ebc4	true	introspection.token.claim
2e0b1fdb-fb9a-4b01-816a-9f9c8445ebc4	true	access.token.claim
e057e8ea-c6fe-4809-b15f-26a631996bfd	true	introspection.token.claim
e057e8ea-c6fe-4809-b15f-26a631996bfd	true	multivalued
e057e8ea-c6fe-4809-b15f-26a631996bfd	foo	user.attribute
e057e8ea-c6fe-4809-b15f-26a631996bfd	true	access.token.claim
e057e8ea-c6fe-4809-b15f-26a631996bfd	realm_access.roles	claim.name
e057e8ea-c6fe-4809-b15f-26a631996bfd	String	jsonType.label
a827c983-77a8-40ec-8cf9-2511568c0e06	true	introspection.token.claim
a827c983-77a8-40ec-8cf9-2511568c0e06	true	access.token.claim
77e361c2-0c85-4655-a262-6202e9c3c862	true	introspection.token.claim
77e361c2-0c85-4655-a262-6202e9c3c862	true	multivalued
77e361c2-0c85-4655-a262-6202e9c3c862	foo	user.attribute
77e361c2-0c85-4655-a262-6202e9c3c862	true	id.token.claim
77e361c2-0c85-4655-a262-6202e9c3c862	true	access.token.claim
77e361c2-0c85-4655-a262-6202e9c3c862	groups	claim.name
77e361c2-0c85-4655-a262-6202e9c3c862	String	jsonType.label
a4645ee6-7789-4e8f-8182-a70100066e09	true	introspection.token.claim
a4645ee6-7789-4e8f-8182-a70100066e09	true	userinfo.token.claim
a4645ee6-7789-4e8f-8182-a70100066e09	username	user.attribute
a4645ee6-7789-4e8f-8182-a70100066e09	true	id.token.claim
a4645ee6-7789-4e8f-8182-a70100066e09	true	access.token.claim
a4645ee6-7789-4e8f-8182-a70100066e09	upn	claim.name
a4645ee6-7789-4e8f-8182-a70100066e09	String	jsonType.label
4d2ccbb5-96c7-49ac-8382-977e90cf02aa	true	introspection.token.claim
4d2ccbb5-96c7-49ac-8382-977e90cf02aa	true	id.token.claim
4d2ccbb5-96c7-49ac-8382-977e90cf02aa	true	access.token.claim
492b1e1d-93b8-4539-bc79-59f79b79a99d	true	introspection.token.claim
492b1e1d-93b8-4539-bc79-59f79b79a99d	true	access.token.claim
6555c12f-dc04-496d-ba4a-b1901c9af6c6	AUTH_TIME	user.session.note
6555c12f-dc04-496d-ba4a-b1901c9af6c6	true	introspection.token.claim
6555c12f-dc04-496d-ba4a-b1901c9af6c6	true	id.token.claim
6555c12f-dc04-496d-ba4a-b1901c9af6c6	true	access.token.claim
6555c12f-dc04-496d-ba4a-b1901c9af6c6	auth_time	claim.name
6555c12f-dc04-496d-ba4a-b1901c9af6c6	long	jsonType.label
1bc22608-97a9-42d7-8b71-3ef2b9b87203	true	introspection.token.claim
1bc22608-97a9-42d7-8b71-3ef2b9b87203	true	multivalued
1bc22608-97a9-42d7-8b71-3ef2b9b87203	true	id.token.claim
1bc22608-97a9-42d7-8b71-3ef2b9b87203	true	access.token.claim
1bc22608-97a9-42d7-8b71-3ef2b9b87203	organization	claim.name
1bc22608-97a9-42d7-8b71-3ef2b9b87203	String	jsonType.label
998ff42f-83d9-4fe5-b997-b382d7cdb4bf	false	single
998ff42f-83d9-4fe5-b997-b382d7cdb4bf	Basic	attribute.nameformat
998ff42f-83d9-4fe5-b997-b382d7cdb4bf	Role	attribute.name
017d7162-3d7f-4585-ab00-ec4299660b67	true	introspection.token.claim
017d7162-3d7f-4585-ab00-ec4299660b67	true	userinfo.token.claim
017d7162-3d7f-4585-ab00-ec4299660b67	updatedAt	user.attribute
017d7162-3d7f-4585-ab00-ec4299660b67	true	id.token.claim
017d7162-3d7f-4585-ab00-ec4299660b67	true	access.token.claim
017d7162-3d7f-4585-ab00-ec4299660b67	updated_at	claim.name
017d7162-3d7f-4585-ab00-ec4299660b67	long	jsonType.label
01f4c13a-5e89-4e69-828f-77802c14ba79	true	introspection.token.claim
01f4c13a-5e89-4e69-828f-77802c14ba79	true	userinfo.token.claim
01f4c13a-5e89-4e69-828f-77802c14ba79	birthdate	user.attribute
01f4c13a-5e89-4e69-828f-77802c14ba79	true	id.token.claim
01f4c13a-5e89-4e69-828f-77802c14ba79	true	access.token.claim
01f4c13a-5e89-4e69-828f-77802c14ba79	birthdate	claim.name
01f4c13a-5e89-4e69-828f-77802c14ba79	String	jsonType.label
0385d1a3-37c8-40a1-87b8-931f3bd8da30	true	introspection.token.claim
0385d1a3-37c8-40a1-87b8-931f3bd8da30	true	userinfo.token.claim
0385d1a3-37c8-40a1-87b8-931f3bd8da30	nickname	user.attribute
0385d1a3-37c8-40a1-87b8-931f3bd8da30	true	id.token.claim
0385d1a3-37c8-40a1-87b8-931f3bd8da30	true	access.token.claim
0385d1a3-37c8-40a1-87b8-931f3bd8da30	nickname	claim.name
0385d1a3-37c8-40a1-87b8-931f3bd8da30	String	jsonType.label
039bff35-a728-4127-9ef9-918a221baa87	true	introspection.token.claim
039bff35-a728-4127-9ef9-918a221baa87	true	userinfo.token.claim
039bff35-a728-4127-9ef9-918a221baa87	gender	user.attribute
039bff35-a728-4127-9ef9-918a221baa87	true	id.token.claim
039bff35-a728-4127-9ef9-918a221baa87	true	access.token.claim
039bff35-a728-4127-9ef9-918a221baa87	gender	claim.name
039bff35-a728-4127-9ef9-918a221baa87	String	jsonType.label
349cb0cc-d502-4204-86ec-de4aec72ee5c	true	introspection.token.claim
349cb0cc-d502-4204-86ec-de4aec72ee5c	true	userinfo.token.claim
349cb0cc-d502-4204-86ec-de4aec72ee5c	lastName	user.attribute
349cb0cc-d502-4204-86ec-de4aec72ee5c	true	id.token.claim
349cb0cc-d502-4204-86ec-de4aec72ee5c	true	access.token.claim
349cb0cc-d502-4204-86ec-de4aec72ee5c	family_name	claim.name
349cb0cc-d502-4204-86ec-de4aec72ee5c	String	jsonType.label
4284c223-0628-49ce-8595-6389e0f5bf4c	true	introspection.token.claim
4284c223-0628-49ce-8595-6389e0f5bf4c	true	userinfo.token.claim
4284c223-0628-49ce-8595-6389e0f5bf4c	middleName	user.attribute
4284c223-0628-49ce-8595-6389e0f5bf4c	true	id.token.claim
4284c223-0628-49ce-8595-6389e0f5bf4c	true	access.token.claim
4284c223-0628-49ce-8595-6389e0f5bf4c	middle_name	claim.name
4284c223-0628-49ce-8595-6389e0f5bf4c	String	jsonType.label
509ff692-b6b1-4400-8afc-607a9f55d669	true	introspection.token.claim
509ff692-b6b1-4400-8afc-607a9f55d669	true	userinfo.token.claim
509ff692-b6b1-4400-8afc-607a9f55d669	firstName	user.attribute
509ff692-b6b1-4400-8afc-607a9f55d669	true	id.token.claim
509ff692-b6b1-4400-8afc-607a9f55d669	true	access.token.claim
509ff692-b6b1-4400-8afc-607a9f55d669	given_name	claim.name
509ff692-b6b1-4400-8afc-607a9f55d669	String	jsonType.label
7dfdfd96-5d57-41ad-8841-2e9d9c98cdeb	true	introspection.token.claim
7dfdfd96-5d57-41ad-8841-2e9d9c98cdeb	true	userinfo.token.claim
7dfdfd96-5d57-41ad-8841-2e9d9c98cdeb	profile	user.attribute
7dfdfd96-5d57-41ad-8841-2e9d9c98cdeb	true	id.token.claim
7dfdfd96-5d57-41ad-8841-2e9d9c98cdeb	true	access.token.claim
7dfdfd96-5d57-41ad-8841-2e9d9c98cdeb	profile	claim.name
7dfdfd96-5d57-41ad-8841-2e9d9c98cdeb	String	jsonType.label
917a49da-df78-49e6-a5b9-b93c21dba387	true	introspection.token.claim
917a49da-df78-49e6-a5b9-b93c21dba387	true	userinfo.token.claim
917a49da-df78-49e6-a5b9-b93c21dba387	zoneinfo	user.attribute
917a49da-df78-49e6-a5b9-b93c21dba387	true	id.token.claim
917a49da-df78-49e6-a5b9-b93c21dba387	true	access.token.claim
917a49da-df78-49e6-a5b9-b93c21dba387	zoneinfo	claim.name
917a49da-df78-49e6-a5b9-b93c21dba387	String	jsonType.label
98060b08-a57f-45c1-9e50-68d7863d3928	true	introspection.token.claim
98060b08-a57f-45c1-9e50-68d7863d3928	true	userinfo.token.claim
98060b08-a57f-45c1-9e50-68d7863d3928	website	user.attribute
98060b08-a57f-45c1-9e50-68d7863d3928	true	id.token.claim
98060b08-a57f-45c1-9e50-68d7863d3928	true	access.token.claim
98060b08-a57f-45c1-9e50-68d7863d3928	website	claim.name
98060b08-a57f-45c1-9e50-68d7863d3928	String	jsonType.label
a61967c9-574e-457b-9f3a-8706708ba48f	true	introspection.token.claim
a61967c9-574e-457b-9f3a-8706708ba48f	true	userinfo.token.claim
a61967c9-574e-457b-9f3a-8706708ba48f	picture	user.attribute
a61967c9-574e-457b-9f3a-8706708ba48f	true	id.token.claim
a61967c9-574e-457b-9f3a-8706708ba48f	true	access.token.claim
a61967c9-574e-457b-9f3a-8706708ba48f	picture	claim.name
a61967c9-574e-457b-9f3a-8706708ba48f	String	jsonType.label
ba4afea0-ed0a-4fb4-9bfb-e2eb6decf20a	true	introspection.token.claim
ba4afea0-ed0a-4fb4-9bfb-e2eb6decf20a	true	userinfo.token.claim
ba4afea0-ed0a-4fb4-9bfb-e2eb6decf20a	locale	user.attribute
ba4afea0-ed0a-4fb4-9bfb-e2eb6decf20a	true	id.token.claim
ba4afea0-ed0a-4fb4-9bfb-e2eb6decf20a	true	access.token.claim
ba4afea0-ed0a-4fb4-9bfb-e2eb6decf20a	locale	claim.name
ba4afea0-ed0a-4fb4-9bfb-e2eb6decf20a	String	jsonType.label
c0d64ff2-1aee-4463-9c45-9f70e6acd874	true	introspection.token.claim
c0d64ff2-1aee-4463-9c45-9f70e6acd874	true	userinfo.token.claim
c0d64ff2-1aee-4463-9c45-9f70e6acd874	username	user.attribute
c0d64ff2-1aee-4463-9c45-9f70e6acd874	true	id.token.claim
c0d64ff2-1aee-4463-9c45-9f70e6acd874	true	access.token.claim
c0d64ff2-1aee-4463-9c45-9f70e6acd874	preferred_username	claim.name
c0d64ff2-1aee-4463-9c45-9f70e6acd874	String	jsonType.label
ed2a92db-1a50-4620-ae94-d28ee3761c33	true	introspection.token.claim
ed2a92db-1a50-4620-ae94-d28ee3761c33	true	userinfo.token.claim
ed2a92db-1a50-4620-ae94-d28ee3761c33	true	id.token.claim
ed2a92db-1a50-4620-ae94-d28ee3761c33	true	access.token.claim
4a0631f1-f770-4b92-938b-b01983c0a381	true	introspection.token.claim
4a0631f1-f770-4b92-938b-b01983c0a381	true	userinfo.token.claim
4a0631f1-f770-4b92-938b-b01983c0a381	emailVerified	user.attribute
4a0631f1-f770-4b92-938b-b01983c0a381	true	id.token.claim
4a0631f1-f770-4b92-938b-b01983c0a381	true	access.token.claim
4a0631f1-f770-4b92-938b-b01983c0a381	email_verified	claim.name
4a0631f1-f770-4b92-938b-b01983c0a381	boolean	jsonType.label
b81eeafa-267f-442f-8f45-7a87b1b93945	true	introspection.token.claim
b81eeafa-267f-442f-8f45-7a87b1b93945	true	userinfo.token.claim
b81eeafa-267f-442f-8f45-7a87b1b93945	email	user.attribute
b81eeafa-267f-442f-8f45-7a87b1b93945	true	id.token.claim
b81eeafa-267f-442f-8f45-7a87b1b93945	true	access.token.claim
b81eeafa-267f-442f-8f45-7a87b1b93945	email	claim.name
b81eeafa-267f-442f-8f45-7a87b1b93945	String	jsonType.label
fcf830d1-a38c-4021-a1b2-529d47de4af1	formatted	user.attribute.formatted
fcf830d1-a38c-4021-a1b2-529d47de4af1	country	user.attribute.country
fcf830d1-a38c-4021-a1b2-529d47de4af1	true	introspection.token.claim
fcf830d1-a38c-4021-a1b2-529d47de4af1	postal_code	user.attribute.postal_code
fcf830d1-a38c-4021-a1b2-529d47de4af1	true	userinfo.token.claim
fcf830d1-a38c-4021-a1b2-529d47de4af1	street	user.attribute.street
fcf830d1-a38c-4021-a1b2-529d47de4af1	true	id.token.claim
fcf830d1-a38c-4021-a1b2-529d47de4af1	region	user.attribute.region
fcf830d1-a38c-4021-a1b2-529d47de4af1	true	access.token.claim
fcf830d1-a38c-4021-a1b2-529d47de4af1	locality	user.attribute.locality
52085523-1661-44cd-8d4e-0c89b336c5a3	true	introspection.token.claim
52085523-1661-44cd-8d4e-0c89b336c5a3	true	userinfo.token.claim
52085523-1661-44cd-8d4e-0c89b336c5a3	phoneNumberVerified	user.attribute
52085523-1661-44cd-8d4e-0c89b336c5a3	true	id.token.claim
52085523-1661-44cd-8d4e-0c89b336c5a3	true	access.token.claim
52085523-1661-44cd-8d4e-0c89b336c5a3	phone_number_verified	claim.name
52085523-1661-44cd-8d4e-0c89b336c5a3	boolean	jsonType.label
a2bfafe3-e959-462d-8b52-afd1631fc6a4	true	introspection.token.claim
a2bfafe3-e959-462d-8b52-afd1631fc6a4	true	userinfo.token.claim
a2bfafe3-e959-462d-8b52-afd1631fc6a4	phoneNumber	user.attribute
a2bfafe3-e959-462d-8b52-afd1631fc6a4	true	id.token.claim
a2bfafe3-e959-462d-8b52-afd1631fc6a4	true	access.token.claim
a2bfafe3-e959-462d-8b52-afd1631fc6a4	phone_number	claim.name
a2bfafe3-e959-462d-8b52-afd1631fc6a4	String	jsonType.label
37469979-d0c0-41d5-807d-a8fd6e438ba3	true	introspection.token.claim
37469979-d0c0-41d5-807d-a8fd6e438ba3	true	multivalued
37469979-d0c0-41d5-807d-a8fd6e438ba3	foo	user.attribute
37469979-d0c0-41d5-807d-a8fd6e438ba3	true	access.token.claim
37469979-d0c0-41d5-807d-a8fd6e438ba3	resource_access.${client_id}.roles	claim.name
37469979-d0c0-41d5-807d-a8fd6e438ba3	String	jsonType.label
6d9ab36d-7903-43a0-a0ee-24a8ea4244c1	true	introspection.token.claim
6d9ab36d-7903-43a0-a0ee-24a8ea4244c1	true	multivalued
6d9ab36d-7903-43a0-a0ee-24a8ea4244c1	foo	user.attribute
6d9ab36d-7903-43a0-a0ee-24a8ea4244c1	true	access.token.claim
6d9ab36d-7903-43a0-a0ee-24a8ea4244c1	realm_access.roles	claim.name
6d9ab36d-7903-43a0-a0ee-24a8ea4244c1	String	jsonType.label
a4721286-495e-4d83-8014-f3001fa6d3f1	true	introspection.token.claim
a4721286-495e-4d83-8014-f3001fa6d3f1	true	access.token.claim
cb818740-4b59-4f11-9736-ad3f640a7783	true	introspection.token.claim
cb818740-4b59-4f11-9736-ad3f640a7783	true	access.token.claim
26980dc9-8374-4bf5-8cfc-79917f485ec3	true	introspection.token.claim
26980dc9-8374-4bf5-8cfc-79917f485ec3	true	userinfo.token.claim
26980dc9-8374-4bf5-8cfc-79917f485ec3	username	user.attribute
26980dc9-8374-4bf5-8cfc-79917f485ec3	true	id.token.claim
26980dc9-8374-4bf5-8cfc-79917f485ec3	true	access.token.claim
26980dc9-8374-4bf5-8cfc-79917f485ec3	upn	claim.name
26980dc9-8374-4bf5-8cfc-79917f485ec3	String	jsonType.label
fb33aa85-5101-42c1-b092-1577dff4ce90	true	introspection.token.claim
fb33aa85-5101-42c1-b092-1577dff4ce90	true	multivalued
fb33aa85-5101-42c1-b092-1577dff4ce90	foo	user.attribute
fb33aa85-5101-42c1-b092-1577dff4ce90	true	id.token.claim
fb33aa85-5101-42c1-b092-1577dff4ce90	true	access.token.claim
fb33aa85-5101-42c1-b092-1577dff4ce90	groups	claim.name
fb33aa85-5101-42c1-b092-1577dff4ce90	String	jsonType.label
dfbbe1f3-ed4a-4af8-aa10-a9afa5ddc157	true	introspection.token.claim
dfbbe1f3-ed4a-4af8-aa10-a9afa5ddc157	true	id.token.claim
dfbbe1f3-ed4a-4af8-aa10-a9afa5ddc157	true	access.token.claim
888bce78-72ec-4cb4-af69-7abc36ebdbb5	true	introspection.token.claim
888bce78-72ec-4cb4-af69-7abc36ebdbb5	true	access.token.claim
e926edfd-0694-49b9-9ca2-2b4647e5ab02	AUTH_TIME	user.session.note
e926edfd-0694-49b9-9ca2-2b4647e5ab02	true	introspection.token.claim
e926edfd-0694-49b9-9ca2-2b4647e5ab02	true	id.token.claim
e926edfd-0694-49b9-9ca2-2b4647e5ab02	true	access.token.claim
e926edfd-0694-49b9-9ca2-2b4647e5ab02	auth_time	claim.name
e926edfd-0694-49b9-9ca2-2b4647e5ab02	long	jsonType.label
12d896bc-0c08-40fb-b435-4c9341eb1b12	true	introspection.token.claim
12d896bc-0c08-40fb-b435-4c9341eb1b12	true	multivalued
12d896bc-0c08-40fb-b435-4c9341eb1b12	true	id.token.claim
12d896bc-0c08-40fb-b435-4c9341eb1b12	true	access.token.claim
12d896bc-0c08-40fb-b435-4c9341eb1b12	organization	claim.name
12d896bc-0c08-40fb-b435-4c9341eb1b12	String	jsonType.label
7235e9a4-d926-48f2-b133-d7f71f54a9d5	true	introspection.token.claim
7235e9a4-d926-48f2-b133-d7f71f54a9d5	true	userinfo.token.claim
7235e9a4-d926-48f2-b133-d7f71f54a9d5	locale	user.attribute
7235e9a4-d926-48f2-b133-d7f71f54a9d5	true	id.token.claim
7235e9a4-d926-48f2-b133-d7f71f54a9d5	true	access.token.claim
7235e9a4-d926-48f2-b133-d7f71f54a9d5	locale	claim.name
7235e9a4-d926-48f2-b133-d7f71f54a9d5	String	jsonType.label
\.


--
-- Data for Name: realm; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.realm (id, access_code_lifespan, user_action_lifespan, access_token_lifespan, account_theme, admin_theme, email_theme, enabled, events_enabled, events_expiration, login_theme, name, not_before, password_policy, registration_allowed, remember_me, reset_password_allowed, social, ssl_required, sso_idle_timeout, sso_max_lifespan, update_profile_on_soc_login, verify_email, master_admin_client, login_lifespan, internationalization_enabled, default_locale, reg_email_as_username, admin_events_enabled, admin_events_details_enabled, edit_username_allowed, otp_policy_counter, otp_policy_window, otp_policy_period, otp_policy_digits, otp_policy_alg, otp_policy_type, browser_flow, registration_flow, direct_grant_flow, reset_credentials_flow, client_auth_flow, offline_session_idle_timeout, revoke_refresh_token, access_token_life_implicit, login_with_email_allowed, duplicate_emails_allowed, docker_auth_flow, refresh_token_max_reuse, allow_user_managed_access, sso_max_lifespan_remember_me, sso_idle_timeout_remember_me, default_role) FROM stdin;
818f97fc-7b35-4bae-bdd7-76b5035dce69	60	300	60	\N	\N	\N	t	f	0	\N	master	0	\N	f	f	f	f	EXTERNAL	1800	36000	f	f	b62619cb-bb63-4412-a21c-5daadfdd4996	1800	f	\N	f	f	f	f	0	1	30	6	HmacSHA1	totp	f2312c70-9614-4eb7-aabc-743a2aee10ea	3e041e39-2fdc-45b6-b800-5e01f588181a	8f281943-5ab4-431a-b40d-d01be9ffcb56	f7275f2d-27e4-4132-a896-9a07c914e15c	78e843e8-00a3-4ad6-a834-55492827b279	2592000	f	900	t	f	6dd75b98-03e9-4093-b9c1-35554173afeb	0	f	0	0	e32d865e-3691-40e1-97eb-0422b52e4e68
c43fd595-cebd-4062-ac8d-8943c15bd7de	60	300	300	\N	\N	\N	t	f	0	\N	eaf-test	0	\N	f	f	f	f	EXTERNAL	1800	36000	f	f	e16e81cd-d3d9-497a-af75-107cbf3c4a34	1800	f	\N	f	f	f	f	0	1	30	6	HmacSHA1	totp	56d6e51d-f268-4e22-8606-8c6ce35bdd8d	ff8c6b13-ca94-41cc-9bb5-d586330e110b	aad40e8a-8f47-4600-b2e1-403a3621dcb1	637683bb-96f5-4dd7-b144-befa65b3407c	b07f243e-78de-488b-bafa-aa450f0f13e7	2592000	f	900	t	f	b8b3f788-8f30-4a90-9042-4ae0cada4c34	0	f	0	0	824567dd-42a8-4317-93be-6ad83ed43903
\.


--
-- Data for Name: realm_attribute; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.realm_attribute (name, realm_id, value) FROM stdin;
_browser_header.contentSecurityPolicyReportOnly	818f97fc-7b35-4bae-bdd7-76b5035dce69	
_browser_header.xContentTypeOptions	818f97fc-7b35-4bae-bdd7-76b5035dce69	nosniff
_browser_header.referrerPolicy	818f97fc-7b35-4bae-bdd7-76b5035dce69	no-referrer
_browser_header.xRobotsTag	818f97fc-7b35-4bae-bdd7-76b5035dce69	none
_browser_header.xFrameOptions	818f97fc-7b35-4bae-bdd7-76b5035dce69	SAMEORIGIN
_browser_header.contentSecurityPolicy	818f97fc-7b35-4bae-bdd7-76b5035dce69	frame-src 'self'; frame-ancestors 'self'; object-src 'none';
_browser_header.xXSSProtection	818f97fc-7b35-4bae-bdd7-76b5035dce69	1; mode=block
_browser_header.strictTransportSecurity	818f97fc-7b35-4bae-bdd7-76b5035dce69	max-age=31536000; includeSubDomains
bruteForceProtected	818f97fc-7b35-4bae-bdd7-76b5035dce69	false
permanentLockout	818f97fc-7b35-4bae-bdd7-76b5035dce69	false
maxTemporaryLockouts	818f97fc-7b35-4bae-bdd7-76b5035dce69	0
maxFailureWaitSeconds	818f97fc-7b35-4bae-bdd7-76b5035dce69	900
minimumQuickLoginWaitSeconds	818f97fc-7b35-4bae-bdd7-76b5035dce69	60
waitIncrementSeconds	818f97fc-7b35-4bae-bdd7-76b5035dce69	60
quickLoginCheckMilliSeconds	818f97fc-7b35-4bae-bdd7-76b5035dce69	1000
maxDeltaTimeSeconds	818f97fc-7b35-4bae-bdd7-76b5035dce69	43200
failureFactor	818f97fc-7b35-4bae-bdd7-76b5035dce69	30
realmReusableOtpCode	818f97fc-7b35-4bae-bdd7-76b5035dce69	false
firstBrokerLoginFlowId	818f97fc-7b35-4bae-bdd7-76b5035dce69	7f97d87f-3f8d-4f78-ac23-467c123140b6
displayName	818f97fc-7b35-4bae-bdd7-76b5035dce69	Keycloak
displayNameHtml	818f97fc-7b35-4bae-bdd7-76b5035dce69	<div class="kc-logo-text"><span>Keycloak</span></div>
defaultSignatureAlgorithm	818f97fc-7b35-4bae-bdd7-76b5035dce69	RS256
offlineSessionMaxLifespanEnabled	818f97fc-7b35-4bae-bdd7-76b5035dce69	false
offlineSessionMaxLifespan	818f97fc-7b35-4bae-bdd7-76b5035dce69	5184000
_browser_header.contentSecurityPolicyReportOnly	c43fd595-cebd-4062-ac8d-8943c15bd7de	
_browser_header.xContentTypeOptions	c43fd595-cebd-4062-ac8d-8943c15bd7de	nosniff
_browser_header.referrerPolicy	c43fd595-cebd-4062-ac8d-8943c15bd7de	no-referrer
_browser_header.xRobotsTag	c43fd595-cebd-4062-ac8d-8943c15bd7de	none
_browser_header.xFrameOptions	c43fd595-cebd-4062-ac8d-8943c15bd7de	SAMEORIGIN
_browser_header.contentSecurityPolicy	c43fd595-cebd-4062-ac8d-8943c15bd7de	frame-src 'self'; frame-ancestors 'self'; object-src 'none';
_browser_header.xXSSProtection	c43fd595-cebd-4062-ac8d-8943c15bd7de	1; mode=block
_browser_header.strictTransportSecurity	c43fd595-cebd-4062-ac8d-8943c15bd7de	max-age=31536000; includeSubDomains
bruteForceProtected	c43fd595-cebd-4062-ac8d-8943c15bd7de	false
permanentLockout	c43fd595-cebd-4062-ac8d-8943c15bd7de	false
maxTemporaryLockouts	c43fd595-cebd-4062-ac8d-8943c15bd7de	0
maxFailureWaitSeconds	c43fd595-cebd-4062-ac8d-8943c15bd7de	900
minimumQuickLoginWaitSeconds	c43fd595-cebd-4062-ac8d-8943c15bd7de	60
waitIncrementSeconds	c43fd595-cebd-4062-ac8d-8943c15bd7de	60
quickLoginCheckMilliSeconds	c43fd595-cebd-4062-ac8d-8943c15bd7de	1000
maxDeltaTimeSeconds	c43fd595-cebd-4062-ac8d-8943c15bd7de	43200
failureFactor	c43fd595-cebd-4062-ac8d-8943c15bd7de	30
realmReusableOtpCode	c43fd595-cebd-4062-ac8d-8943c15bd7de	false
defaultSignatureAlgorithm	c43fd595-cebd-4062-ac8d-8943c15bd7de	RS256
offlineSessionMaxLifespanEnabled	c43fd595-cebd-4062-ac8d-8943c15bd7de	false
offlineSessionMaxLifespan	c43fd595-cebd-4062-ac8d-8943c15bd7de	5184000
actionTokenGeneratedByAdminLifespan	c43fd595-cebd-4062-ac8d-8943c15bd7de	43200
actionTokenGeneratedByUserLifespan	c43fd595-cebd-4062-ac8d-8943c15bd7de	300
oauth2DeviceCodeLifespan	c43fd595-cebd-4062-ac8d-8943c15bd7de	600
oauth2DevicePollingInterval	c43fd595-cebd-4062-ac8d-8943c15bd7de	5
webAuthnPolicyRpEntityName	c43fd595-cebd-4062-ac8d-8943c15bd7de	keycloak
webAuthnPolicySignatureAlgorithms	c43fd595-cebd-4062-ac8d-8943c15bd7de	ES256,RS256
webAuthnPolicyRpId	c43fd595-cebd-4062-ac8d-8943c15bd7de	
webAuthnPolicyAttestationConveyancePreference	c43fd595-cebd-4062-ac8d-8943c15bd7de	not specified
webAuthnPolicyAuthenticatorAttachment	c43fd595-cebd-4062-ac8d-8943c15bd7de	not specified
webAuthnPolicyRequireResidentKey	c43fd595-cebd-4062-ac8d-8943c15bd7de	not specified
webAuthnPolicyUserVerificationRequirement	c43fd595-cebd-4062-ac8d-8943c15bd7de	not specified
webAuthnPolicyCreateTimeout	c43fd595-cebd-4062-ac8d-8943c15bd7de	0
webAuthnPolicyAvoidSameAuthenticatorRegister	c43fd595-cebd-4062-ac8d-8943c15bd7de	false
webAuthnPolicyRpEntityNamePasswordless	c43fd595-cebd-4062-ac8d-8943c15bd7de	keycloak
webAuthnPolicySignatureAlgorithmsPasswordless	c43fd595-cebd-4062-ac8d-8943c15bd7de	ES256,RS256
webAuthnPolicyRpIdPasswordless	c43fd595-cebd-4062-ac8d-8943c15bd7de	
webAuthnPolicyAttestationConveyancePreferencePasswordless	c43fd595-cebd-4062-ac8d-8943c15bd7de	not specified
webAuthnPolicyAuthenticatorAttachmentPasswordless	c43fd595-cebd-4062-ac8d-8943c15bd7de	not specified
webAuthnPolicyRequireResidentKeyPasswordless	c43fd595-cebd-4062-ac8d-8943c15bd7de	not specified
webAuthnPolicyUserVerificationRequirementPasswordless	c43fd595-cebd-4062-ac8d-8943c15bd7de	not specified
webAuthnPolicyCreateTimeoutPasswordless	c43fd595-cebd-4062-ac8d-8943c15bd7de	0
webAuthnPolicyAvoidSameAuthenticatorRegisterPasswordless	c43fd595-cebd-4062-ac8d-8943c15bd7de	false
cibaBackchannelTokenDeliveryMode	c43fd595-cebd-4062-ac8d-8943c15bd7de	poll
cibaExpiresIn	c43fd595-cebd-4062-ac8d-8943c15bd7de	120
cibaInterval	c43fd595-cebd-4062-ac8d-8943c15bd7de	5
cibaAuthRequestedUserHint	c43fd595-cebd-4062-ac8d-8943c15bd7de	login_hint
parRequestUriLifespan	c43fd595-cebd-4062-ac8d-8943c15bd7de	60
firstBrokerLoginFlowId	c43fd595-cebd-4062-ac8d-8943c15bd7de	6bda4140-643b-4da5-9a8f-c13095bd3054
\.


--
-- Data for Name: realm_default_groups; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.realm_default_groups (realm_id, group_id) FROM stdin;
\.


--
-- Data for Name: realm_enabled_event_types; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.realm_enabled_event_types (realm_id, value) FROM stdin;
\.


--
-- Data for Name: realm_events_listeners; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.realm_events_listeners (realm_id, value) FROM stdin;
818f97fc-7b35-4bae-bdd7-76b5035dce69	jboss-logging
c43fd595-cebd-4062-ac8d-8943c15bd7de	jboss-logging
\.


--
-- Data for Name: realm_localizations; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.realm_localizations (realm_id, locale, texts) FROM stdin;
\.


--
-- Data for Name: realm_required_credential; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.realm_required_credential (type, form_label, input, secret, realm_id) FROM stdin;
password	password	t	t	818f97fc-7b35-4bae-bdd7-76b5035dce69
password	password	t	t	c43fd595-cebd-4062-ac8d-8943c15bd7de
\.


--
-- Data for Name: realm_smtp_config; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.realm_smtp_config (realm_id, value, name) FROM stdin;
\.


--
-- Data for Name: realm_supported_locales; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.realm_supported_locales (realm_id, value) FROM stdin;
\.


--
-- Data for Name: redirect_uris; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.redirect_uris (client_id, value) FROM stdin;
c98fef2f-a93f-4a7e-9aa9-2fdc9ac7f43b	/realms/master/account/*
3ed9a267-16aa-4320-b9a3-2d38f6ad94fa	/realms/master/account/*
1e451ada-fbdd-4a7f-ad23-b5229b440909	/admin/master/console/*
26103ba8-ed16-476a-be4f-1b15363631d0	/realms/eaf-test/account/*
73f93ba6-a168-4560-8a23-1522043206c0	/realms/eaf-test/account/*
9bb712ff-a545-4dbd-9df3-3ac5e67dd486	/admin/eaf-test/console/*
\.


--
-- Data for Name: required_action_config; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.required_action_config (required_action_id, value, name) FROM stdin;
\.


--
-- Data for Name: required_action_provider; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.required_action_provider (id, alias, name, realm_id, enabled, default_action, provider_id, priority) FROM stdin;
9dcadefa-2b70-426c-ba17-da88de98e378	VERIFY_EMAIL	Verify Email	818f97fc-7b35-4bae-bdd7-76b5035dce69	t	f	VERIFY_EMAIL	50
53590316-63f7-44e2-954c-781420f12846	UPDATE_PROFILE	Update Profile	818f97fc-7b35-4bae-bdd7-76b5035dce69	t	f	UPDATE_PROFILE	40
7f354179-4eaf-4774-92a3-d30437e88da7	CONFIGURE_TOTP	Configure OTP	818f97fc-7b35-4bae-bdd7-76b5035dce69	t	f	CONFIGURE_TOTP	10
49489593-6835-431e-b7b6-dc7ef1fbcab4	UPDATE_PASSWORD	Update Password	818f97fc-7b35-4bae-bdd7-76b5035dce69	t	f	UPDATE_PASSWORD	30
072cce2d-0c73-471f-afb2-6f9135d32a26	TERMS_AND_CONDITIONS	Terms and Conditions	818f97fc-7b35-4bae-bdd7-76b5035dce69	f	f	TERMS_AND_CONDITIONS	20
63b2be2e-cc0b-4e15-a55f-db46f908c76b	delete_account	Delete Account	818f97fc-7b35-4bae-bdd7-76b5035dce69	f	f	delete_account	60
06eb5f31-b0e5-489a-bfbf-89e29bdf4165	delete_credential	Delete Credential	818f97fc-7b35-4bae-bdd7-76b5035dce69	t	f	delete_credential	100
3c39e8af-02f8-4e7d-bbb8-26fb729d8ff7	update_user_locale	Update User Locale	818f97fc-7b35-4bae-bdd7-76b5035dce69	t	f	update_user_locale	1000
9b591932-e271-41c2-bbc5-8f4f00ebb314	webauthn-register	Webauthn Register	818f97fc-7b35-4bae-bdd7-76b5035dce69	t	f	webauthn-register	70
62fa2786-4f00-4007-8b16-db8fd6bcb894	webauthn-register-passwordless	Webauthn Register Passwordless	818f97fc-7b35-4bae-bdd7-76b5035dce69	t	f	webauthn-register-passwordless	80
91a82846-1c10-47a8-bb42-bd561fb1bb74	VERIFY_PROFILE	Verify Profile	818f97fc-7b35-4bae-bdd7-76b5035dce69	t	f	VERIFY_PROFILE	90
a963e4e2-8012-4c58-a085-ab4cb9d1182b	VERIFY_EMAIL	Verify Email	c43fd595-cebd-4062-ac8d-8943c15bd7de	t	f	VERIFY_EMAIL	50
6f8ea1fc-3170-4ebf-ac58-651fdcd2409f	UPDATE_PROFILE	Update Profile	c43fd595-cebd-4062-ac8d-8943c15bd7de	t	f	UPDATE_PROFILE	40
db7ed7a6-0b1f-47cd-843a-3d12564653cc	CONFIGURE_TOTP	Configure OTP	c43fd595-cebd-4062-ac8d-8943c15bd7de	t	f	CONFIGURE_TOTP	10
b6630105-39e1-4e3b-8082-326a969893db	UPDATE_PASSWORD	Update Password	c43fd595-cebd-4062-ac8d-8943c15bd7de	t	f	UPDATE_PASSWORD	30
2141add2-a2b2-4ae4-ba6e-001d50186e55	TERMS_AND_CONDITIONS	Terms and Conditions	c43fd595-cebd-4062-ac8d-8943c15bd7de	f	f	TERMS_AND_CONDITIONS	20
ca7f0927-fa1e-4423-af9c-a3d0916c0229	delete_account	Delete Account	c43fd595-cebd-4062-ac8d-8943c15bd7de	f	f	delete_account	60
dadec11a-c9c3-4333-8a9a-172b4364f5c5	delete_credential	Delete Credential	c43fd595-cebd-4062-ac8d-8943c15bd7de	t	f	delete_credential	100
eb81fbd3-1917-49d3-ba25-98ed53ba1190	update_user_locale	Update User Locale	c43fd595-cebd-4062-ac8d-8943c15bd7de	t	f	update_user_locale	1000
e33af286-9b4a-4df7-bb64-727813a3276e	webauthn-register	Webauthn Register	c43fd595-cebd-4062-ac8d-8943c15bd7de	t	f	webauthn-register	70
4349182b-0fe7-422e-a301-2349d8fb337d	webauthn-register-passwordless	Webauthn Register Passwordless	c43fd595-cebd-4062-ac8d-8943c15bd7de	t	f	webauthn-register-passwordless	80
c6a3414f-e7d3-455f-a718-f82213a9b927	VERIFY_PROFILE	Verify Profile	c43fd595-cebd-4062-ac8d-8943c15bd7de	t	f	VERIFY_PROFILE	90
\.


--
-- Data for Name: resource_attribute; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.resource_attribute (id, name, value, resource_id) FROM stdin;
\.


--
-- Data for Name: resource_policy; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.resource_policy (resource_id, policy_id) FROM stdin;
\.


--
-- Data for Name: resource_scope; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.resource_scope (resource_id, scope_id) FROM stdin;
\.


--
-- Data for Name: resource_server; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.resource_server (id, allow_rs_remote_mgmt, policy_enforce_mode, decision_strategy) FROM stdin;
\.


--
-- Data for Name: resource_server_perm_ticket; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.resource_server_perm_ticket (id, owner, requester, created_timestamp, granted_timestamp, resource_id, scope_id, resource_server_id, policy_id) FROM stdin;
\.


--
-- Data for Name: resource_server_policy; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.resource_server_policy (id, name, description, type, decision_strategy, logic, resource_server_id, owner) FROM stdin;
\.


--
-- Data for Name: resource_server_resource; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.resource_server_resource (id, name, type, icon_uri, owner, resource_server_id, owner_managed_access, display_name) FROM stdin;
\.


--
-- Data for Name: resource_server_scope; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.resource_server_scope (id, name, icon_uri, resource_server_id, display_name) FROM stdin;
\.


--
-- Data for Name: resource_uris; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.resource_uris (resource_id, value) FROM stdin;
\.


--
-- Data for Name: revoked_token; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.revoked_token (id, expire) FROM stdin;
\.


--
-- Data for Name: role_attribute; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.role_attribute (id, role_id, name, value) FROM stdin;
\.


--
-- Data for Name: scope_mapping; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.scope_mapping (client_id, role_id) FROM stdin;
3ed9a267-16aa-4320-b9a3-2d38f6ad94fa	4f5e9358-636b-4d56-9e2e-d30c23f894d8
3ed9a267-16aa-4320-b9a3-2d38f6ad94fa	27ce47a9-29c4-4780-8e2d-47de1220888e
73f93ba6-a168-4560-8a23-1522043206c0	818fc192-bf1e-4443-bd28-8eb6a3883ac5
73f93ba6-a168-4560-8a23-1522043206c0	3ca79fe0-cd89-4ab4-9960-403634a750b6
\.


--
-- Data for Name: scope_policy; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.scope_policy (scope_id, policy_id) FROM stdin;
\.


--
-- Data for Name: user_attribute; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.user_attribute (name, value, user_id, id, long_value_hash, long_value_hash_lower_case, long_value) FROM stdin;
is_temporary_admin	true	3a9e8f9d-f2ec-4e97-812c-282abc3f7721	4f6c2be1-9a30-463b-bf81-a8e59f19daaf	\N	\N	\N
\.


--
-- Data for Name: user_consent; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.user_consent (id, client_id, user_id, created_date, last_updated_date, client_storage_provider, external_client_id) FROM stdin;
\.


--
-- Data for Name: user_consent_client_scope; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.user_consent_client_scope (user_consent_id, scope_id) FROM stdin;
\.


--
-- Data for Name: user_entity; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.user_entity (id, email, email_constraint, email_verified, enabled, federation_link, first_name, last_name, realm_id, username, created_timestamp, service_account_client_link, not_before) FROM stdin;
3a9e8f9d-f2ec-4e97-812c-282abc3f7721	\N	535cef84-3967-493a-9f2f-4fc9d44d21e5	f	t	\N	\N	\N	818f97fc-7b35-4bae-bdd7-76b5035dce69	admin	1759690064118	\N	0
\.


--
-- Data for Name: user_federation_config; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.user_federation_config (user_federation_provider_id, value, name) FROM stdin;
\.


--
-- Data for Name: user_federation_mapper; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.user_federation_mapper (id, name, federation_provider_id, federation_mapper_type, realm_id) FROM stdin;
\.


--
-- Data for Name: user_federation_mapper_config; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.user_federation_mapper_config (user_federation_mapper_id, value, name) FROM stdin;
\.


--
-- Data for Name: user_federation_provider; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.user_federation_provider (id, changed_sync_period, display_name, full_sync_period, last_sync, priority, provider_name, realm_id) FROM stdin;
\.


--
-- Data for Name: user_group_membership; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.user_group_membership (group_id, user_id, membership_type) FROM stdin;
\.


--
-- Data for Name: user_required_action; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.user_required_action (user_id, required_action) FROM stdin;
\.


--
-- Data for Name: user_role_mapping; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.user_role_mapping (role_id, user_id) FROM stdin;
e32d865e-3691-40e1-97eb-0422b52e4e68	3a9e8f9d-f2ec-4e97-812c-282abc3f7721
2cad98e1-f10f-4d55-8fdd-32ed93cf5111	3a9e8f9d-f2ec-4e97-812c-282abc3f7721
\.


--
-- Data for Name: username_login_failure; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.username_login_failure (realm_id, username, failed_login_not_before, last_failure, last_ip_failure, num_failures) FROM stdin;
\.


--
-- Data for Name: web_origins; Type: TABLE DATA; Schema: public; Owner: eaf
--

COPY public.web_origins (client_id, value) FROM stdin;
1e451ada-fbdd-4a7f-ad23-b5229b440909	+
9bb712ff-a545-4dbd-9df3-3ac5e67dd486	+
\.


--
-- Name: outbox outbox_pkey; Type: CONSTRAINT; Schema: eaf_event; Owner: eaf
--

ALTER TABLE ONLY eaf_event.outbox
    ADD CONSTRAINT outbox_pkey PRIMARY KEY (id);


--
-- Name: username_login_failure CONSTRAINT_17-2; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.username_login_failure
    ADD CONSTRAINT "CONSTRAINT_17-2" PRIMARY KEY (realm_id, username);


--
-- Name: org_domain ORG_DOMAIN_pkey; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.org_domain
    ADD CONSTRAINT "ORG_DOMAIN_pkey" PRIMARY KEY (id, name);


--
-- Name: org ORG_pkey; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.org
    ADD CONSTRAINT "ORG_pkey" PRIMARY KEY (id);


--
-- Name: keycloak_role UK_J3RWUVD56ONTGSUHOGM184WW2-2; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.keycloak_role
    ADD CONSTRAINT "UK_J3RWUVD56ONTGSUHOGM184WW2-2" UNIQUE (name, client_realm_constraint);


--
-- Name: client_auth_flow_bindings c_cli_flow_bind; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.client_auth_flow_bindings
    ADD CONSTRAINT c_cli_flow_bind PRIMARY KEY (client_id, binding_name);


--
-- Name: client_scope_client c_cli_scope_bind; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.client_scope_client
    ADD CONSTRAINT c_cli_scope_bind PRIMARY KEY (client_id, scope_id);


--
-- Name: client_initial_access cnstr_client_init_acc_pk; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.client_initial_access
    ADD CONSTRAINT cnstr_client_init_acc_pk PRIMARY KEY (id);


--
-- Name: realm_default_groups con_group_id_def_groups; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.realm_default_groups
    ADD CONSTRAINT con_group_id_def_groups UNIQUE (group_id);


--
-- Name: broker_link constr_broker_link_pk; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.broker_link
    ADD CONSTRAINT constr_broker_link_pk PRIMARY KEY (identity_provider, user_id);


--
-- Name: component_config constr_component_config_pk; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.component_config
    ADD CONSTRAINT constr_component_config_pk PRIMARY KEY (id);


--
-- Name: component constr_component_pk; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.component
    ADD CONSTRAINT constr_component_pk PRIMARY KEY (id);


--
-- Name: fed_user_required_action constr_fed_required_action; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.fed_user_required_action
    ADD CONSTRAINT constr_fed_required_action PRIMARY KEY (required_action, user_id);


--
-- Name: fed_user_attribute constr_fed_user_attr_pk; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.fed_user_attribute
    ADD CONSTRAINT constr_fed_user_attr_pk PRIMARY KEY (id);


--
-- Name: fed_user_consent constr_fed_user_consent_pk; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.fed_user_consent
    ADD CONSTRAINT constr_fed_user_consent_pk PRIMARY KEY (id);


--
-- Name: fed_user_credential constr_fed_user_cred_pk; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.fed_user_credential
    ADD CONSTRAINT constr_fed_user_cred_pk PRIMARY KEY (id);


--
-- Name: fed_user_group_membership constr_fed_user_group; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.fed_user_group_membership
    ADD CONSTRAINT constr_fed_user_group PRIMARY KEY (group_id, user_id);


--
-- Name: fed_user_role_mapping constr_fed_user_role; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.fed_user_role_mapping
    ADD CONSTRAINT constr_fed_user_role PRIMARY KEY (role_id, user_id);


--
-- Name: federated_user constr_federated_user; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.federated_user
    ADD CONSTRAINT constr_federated_user PRIMARY KEY (id);


--
-- Name: realm_default_groups constr_realm_default_groups; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.realm_default_groups
    ADD CONSTRAINT constr_realm_default_groups PRIMARY KEY (realm_id, group_id);


--
-- Name: realm_enabled_event_types constr_realm_enabl_event_types; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.realm_enabled_event_types
    ADD CONSTRAINT constr_realm_enabl_event_types PRIMARY KEY (realm_id, value);


--
-- Name: realm_events_listeners constr_realm_events_listeners; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.realm_events_listeners
    ADD CONSTRAINT constr_realm_events_listeners PRIMARY KEY (realm_id, value);


--
-- Name: realm_supported_locales constr_realm_supported_locales; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.realm_supported_locales
    ADD CONSTRAINT constr_realm_supported_locales PRIMARY KEY (realm_id, value);


--
-- Name: identity_provider constraint_2b; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.identity_provider
    ADD CONSTRAINT constraint_2b PRIMARY KEY (internal_id);


--
-- Name: client_attributes constraint_3c; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.client_attributes
    ADD CONSTRAINT constraint_3c PRIMARY KEY (client_id, name);


--
-- Name: event_entity constraint_4; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.event_entity
    ADD CONSTRAINT constraint_4 PRIMARY KEY (id);


--
-- Name: federated_identity constraint_40; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.federated_identity
    ADD CONSTRAINT constraint_40 PRIMARY KEY (identity_provider, user_id);


--
-- Name: realm constraint_4a; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.realm
    ADD CONSTRAINT constraint_4a PRIMARY KEY (id);


--
-- Name: user_federation_provider constraint_5c; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.user_federation_provider
    ADD CONSTRAINT constraint_5c PRIMARY KEY (id);


--
-- Name: client constraint_7; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.client
    ADD CONSTRAINT constraint_7 PRIMARY KEY (id);


--
-- Name: scope_mapping constraint_81; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.scope_mapping
    ADD CONSTRAINT constraint_81 PRIMARY KEY (client_id, role_id);


--
-- Name: client_node_registrations constraint_84; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.client_node_registrations
    ADD CONSTRAINT constraint_84 PRIMARY KEY (client_id, name);


--
-- Name: realm_attribute constraint_9; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.realm_attribute
    ADD CONSTRAINT constraint_9 PRIMARY KEY (name, realm_id);


--
-- Name: realm_required_credential constraint_92; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.realm_required_credential
    ADD CONSTRAINT constraint_92 PRIMARY KEY (realm_id, type);


--
-- Name: keycloak_role constraint_a; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.keycloak_role
    ADD CONSTRAINT constraint_a PRIMARY KEY (id);


--
-- Name: admin_event_entity constraint_admin_event_entity; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.admin_event_entity
    ADD CONSTRAINT constraint_admin_event_entity PRIMARY KEY (id);


--
-- Name: authenticator_config_entry constraint_auth_cfg_pk; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.authenticator_config_entry
    ADD CONSTRAINT constraint_auth_cfg_pk PRIMARY KEY (authenticator_id, name);


--
-- Name: authentication_execution constraint_auth_exec_pk; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.authentication_execution
    ADD CONSTRAINT constraint_auth_exec_pk PRIMARY KEY (id);


--
-- Name: authentication_flow constraint_auth_flow_pk; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.authentication_flow
    ADD CONSTRAINT constraint_auth_flow_pk PRIMARY KEY (id);


--
-- Name: authenticator_config constraint_auth_pk; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.authenticator_config
    ADD CONSTRAINT constraint_auth_pk PRIMARY KEY (id);


--
-- Name: user_role_mapping constraint_c; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.user_role_mapping
    ADD CONSTRAINT constraint_c PRIMARY KEY (role_id, user_id);


--
-- Name: composite_role constraint_composite_role; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.composite_role
    ADD CONSTRAINT constraint_composite_role PRIMARY KEY (composite, child_role);


--
-- Name: identity_provider_config constraint_d; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.identity_provider_config
    ADD CONSTRAINT constraint_d PRIMARY KEY (identity_provider_id, name);


--
-- Name: policy_config constraint_dpc; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.policy_config
    ADD CONSTRAINT constraint_dpc PRIMARY KEY (policy_id, name);


--
-- Name: realm_smtp_config constraint_e; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.realm_smtp_config
    ADD CONSTRAINT constraint_e PRIMARY KEY (realm_id, name);


--
-- Name: credential constraint_f; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.credential
    ADD CONSTRAINT constraint_f PRIMARY KEY (id);


--
-- Name: user_federation_config constraint_f9; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.user_federation_config
    ADD CONSTRAINT constraint_f9 PRIMARY KEY (user_federation_provider_id, name);


--
-- Name: resource_server_perm_ticket constraint_fapmt; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.resource_server_perm_ticket
    ADD CONSTRAINT constraint_fapmt PRIMARY KEY (id);


--
-- Name: resource_server_resource constraint_farsr; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.resource_server_resource
    ADD CONSTRAINT constraint_farsr PRIMARY KEY (id);


--
-- Name: resource_server_policy constraint_farsrp; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.resource_server_policy
    ADD CONSTRAINT constraint_farsrp PRIMARY KEY (id);


--
-- Name: associated_policy constraint_farsrpap; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.associated_policy
    ADD CONSTRAINT constraint_farsrpap PRIMARY KEY (policy_id, associated_policy_id);


--
-- Name: resource_policy constraint_farsrpp; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.resource_policy
    ADD CONSTRAINT constraint_farsrpp PRIMARY KEY (resource_id, policy_id);


--
-- Name: resource_server_scope constraint_farsrs; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.resource_server_scope
    ADD CONSTRAINT constraint_farsrs PRIMARY KEY (id);


--
-- Name: resource_scope constraint_farsrsp; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.resource_scope
    ADD CONSTRAINT constraint_farsrsp PRIMARY KEY (resource_id, scope_id);


--
-- Name: scope_policy constraint_farsrsps; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.scope_policy
    ADD CONSTRAINT constraint_farsrsps PRIMARY KEY (scope_id, policy_id);


--
-- Name: user_entity constraint_fb; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.user_entity
    ADD CONSTRAINT constraint_fb PRIMARY KEY (id);


--
-- Name: user_federation_mapper_config constraint_fedmapper_cfg_pm; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.user_federation_mapper_config
    ADD CONSTRAINT constraint_fedmapper_cfg_pm PRIMARY KEY (user_federation_mapper_id, name);


--
-- Name: user_federation_mapper constraint_fedmapperpm; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.user_federation_mapper
    ADD CONSTRAINT constraint_fedmapperpm PRIMARY KEY (id);


--
-- Name: fed_user_consent_cl_scope constraint_fgrntcsnt_clsc_pm; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.fed_user_consent_cl_scope
    ADD CONSTRAINT constraint_fgrntcsnt_clsc_pm PRIMARY KEY (user_consent_id, scope_id);


--
-- Name: user_consent_client_scope constraint_grntcsnt_clsc_pm; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.user_consent_client_scope
    ADD CONSTRAINT constraint_grntcsnt_clsc_pm PRIMARY KEY (user_consent_id, scope_id);


--
-- Name: user_consent constraint_grntcsnt_pm; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.user_consent
    ADD CONSTRAINT constraint_grntcsnt_pm PRIMARY KEY (id);


--
-- Name: keycloak_group constraint_group; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.keycloak_group
    ADD CONSTRAINT constraint_group PRIMARY KEY (id);


--
-- Name: group_attribute constraint_group_attribute_pk; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.group_attribute
    ADD CONSTRAINT constraint_group_attribute_pk PRIMARY KEY (id);


--
-- Name: group_role_mapping constraint_group_role; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.group_role_mapping
    ADD CONSTRAINT constraint_group_role PRIMARY KEY (role_id, group_id);


--
-- Name: identity_provider_mapper constraint_idpm; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.identity_provider_mapper
    ADD CONSTRAINT constraint_idpm PRIMARY KEY (id);


--
-- Name: idp_mapper_config constraint_idpmconfig; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.idp_mapper_config
    ADD CONSTRAINT constraint_idpmconfig PRIMARY KEY (idp_mapper_id, name);


--
-- Name: migration_model constraint_migmod; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.migration_model
    ADD CONSTRAINT constraint_migmod PRIMARY KEY (id);


--
-- Name: offline_client_session constraint_offl_cl_ses_pk3; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.offline_client_session
    ADD CONSTRAINT constraint_offl_cl_ses_pk3 PRIMARY KEY (user_session_id, client_id, client_storage_provider, external_client_id, offline_flag);


--
-- Name: offline_user_session constraint_offl_us_ses_pk2; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.offline_user_session
    ADD CONSTRAINT constraint_offl_us_ses_pk2 PRIMARY KEY (user_session_id, offline_flag);


--
-- Name: protocol_mapper constraint_pcm; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.protocol_mapper
    ADD CONSTRAINT constraint_pcm PRIMARY KEY (id);


--
-- Name: protocol_mapper_config constraint_pmconfig; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.protocol_mapper_config
    ADD CONSTRAINT constraint_pmconfig PRIMARY KEY (protocol_mapper_id, name);


--
-- Name: redirect_uris constraint_redirect_uris; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.redirect_uris
    ADD CONSTRAINT constraint_redirect_uris PRIMARY KEY (client_id, value);


--
-- Name: required_action_config constraint_req_act_cfg_pk; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.required_action_config
    ADD CONSTRAINT constraint_req_act_cfg_pk PRIMARY KEY (required_action_id, name);


--
-- Name: required_action_provider constraint_req_act_prv_pk; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.required_action_provider
    ADD CONSTRAINT constraint_req_act_prv_pk PRIMARY KEY (id);


--
-- Name: user_required_action constraint_required_action; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.user_required_action
    ADD CONSTRAINT constraint_required_action PRIMARY KEY (required_action, user_id);


--
-- Name: resource_uris constraint_resour_uris_pk; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.resource_uris
    ADD CONSTRAINT constraint_resour_uris_pk PRIMARY KEY (resource_id, value);


--
-- Name: role_attribute constraint_role_attribute_pk; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.role_attribute
    ADD CONSTRAINT constraint_role_attribute_pk PRIMARY KEY (id);


--
-- Name: revoked_token constraint_rt; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.revoked_token
    ADD CONSTRAINT constraint_rt PRIMARY KEY (id);


--
-- Name: user_attribute constraint_user_attribute_pk; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.user_attribute
    ADD CONSTRAINT constraint_user_attribute_pk PRIMARY KEY (id);


--
-- Name: user_group_membership constraint_user_group; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.user_group_membership
    ADD CONSTRAINT constraint_user_group PRIMARY KEY (group_id, user_id);


--
-- Name: web_origins constraint_web_origins; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.web_origins
    ADD CONSTRAINT constraint_web_origins PRIMARY KEY (client_id, value);


--
-- Name: databasechangeloglock databasechangeloglock_pkey; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.databasechangeloglock
    ADD CONSTRAINT databasechangeloglock_pkey PRIMARY KEY (id);


--
-- Name: client_scope_attributes pk_cl_tmpl_attr; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.client_scope_attributes
    ADD CONSTRAINT pk_cl_tmpl_attr PRIMARY KEY (scope_id, name);


--
-- Name: client_scope pk_cli_template; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.client_scope
    ADD CONSTRAINT pk_cli_template PRIMARY KEY (id);


--
-- Name: resource_server pk_resource_server; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.resource_server
    ADD CONSTRAINT pk_resource_server PRIMARY KEY (id);


--
-- Name: client_scope_role_mapping pk_template_scope; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.client_scope_role_mapping
    ADD CONSTRAINT pk_template_scope PRIMARY KEY (scope_id, role_id);


--
-- Name: default_client_scope r_def_cli_scope_bind; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.default_client_scope
    ADD CONSTRAINT r_def_cli_scope_bind PRIMARY KEY (realm_id, scope_id);


--
-- Name: realm_localizations realm_localizations_pkey; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.realm_localizations
    ADD CONSTRAINT realm_localizations_pkey PRIMARY KEY (realm_id, locale);


--
-- Name: resource_attribute res_attr_pk; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.resource_attribute
    ADD CONSTRAINT res_attr_pk PRIMARY KEY (id);


--
-- Name: keycloak_group sibling_names; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.keycloak_group
    ADD CONSTRAINT sibling_names UNIQUE (realm_id, parent_group, name);


--
-- Name: identity_provider uk_2daelwnibji49avxsrtuf6xj33; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.identity_provider
    ADD CONSTRAINT uk_2daelwnibji49avxsrtuf6xj33 UNIQUE (provider_alias, realm_id);


--
-- Name: client uk_b71cjlbenv945rb6gcon438at; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.client
    ADD CONSTRAINT uk_b71cjlbenv945rb6gcon438at UNIQUE (realm_id, client_id);


--
-- Name: client_scope uk_cli_scope; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.client_scope
    ADD CONSTRAINT uk_cli_scope UNIQUE (realm_id, name);


--
-- Name: user_entity uk_dykn684sl8up1crfei6eckhd7; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.user_entity
    ADD CONSTRAINT uk_dykn684sl8up1crfei6eckhd7 UNIQUE (realm_id, email_constraint);


--
-- Name: user_consent uk_external_consent; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.user_consent
    ADD CONSTRAINT uk_external_consent UNIQUE (client_storage_provider, external_client_id, user_id);


--
-- Name: resource_server_resource uk_frsr6t700s9v50bu18ws5ha6; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.resource_server_resource
    ADD CONSTRAINT uk_frsr6t700s9v50bu18ws5ha6 UNIQUE (name, owner, resource_server_id);


--
-- Name: resource_server_perm_ticket uk_frsr6t700s9v50bu18ws5pmt; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.resource_server_perm_ticket
    ADD CONSTRAINT uk_frsr6t700s9v50bu18ws5pmt UNIQUE (owner, requester, resource_server_id, resource_id, scope_id);


--
-- Name: resource_server_policy uk_frsrpt700s9v50bu18ws5ha6; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.resource_server_policy
    ADD CONSTRAINT uk_frsrpt700s9v50bu18ws5ha6 UNIQUE (name, resource_server_id);


--
-- Name: resource_server_scope uk_frsrst700s9v50bu18ws5ha6; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.resource_server_scope
    ADD CONSTRAINT uk_frsrst700s9v50bu18ws5ha6 UNIQUE (name, resource_server_id);


--
-- Name: user_consent uk_local_consent; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.user_consent
    ADD CONSTRAINT uk_local_consent UNIQUE (client_id, user_id);


--
-- Name: org uk_org_alias; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.org
    ADD CONSTRAINT uk_org_alias UNIQUE (realm_id, alias);


--
-- Name: org uk_org_group; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.org
    ADD CONSTRAINT uk_org_group UNIQUE (group_id);


--
-- Name: org uk_org_name; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.org
    ADD CONSTRAINT uk_org_name UNIQUE (realm_id, name);


--
-- Name: realm uk_orvsdmla56612eaefiq6wl5oi; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.realm
    ADD CONSTRAINT uk_orvsdmla56612eaefiq6wl5oi UNIQUE (name);


--
-- Name: user_entity uk_ru8tt6t700s9v50bu18ws5ha6; Type: CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.user_entity
    ADD CONSTRAINT uk_ru8tt6t700s9v50bu18ws5ha6 UNIQUE (realm_id, username);


--
-- Name: fed_user_attr_long_values; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX fed_user_attr_long_values ON public.fed_user_attribute USING btree (long_value_hash, name);


--
-- Name: fed_user_attr_long_values_lower_case; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX fed_user_attr_long_values_lower_case ON public.fed_user_attribute USING btree (long_value_hash_lower_case, name);


--
-- Name: idx_admin_event_time; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_admin_event_time ON public.admin_event_entity USING btree (realm_id, admin_event_time);


--
-- Name: idx_assoc_pol_assoc_pol_id; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_assoc_pol_assoc_pol_id ON public.associated_policy USING btree (associated_policy_id);


--
-- Name: idx_auth_config_realm; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_auth_config_realm ON public.authenticator_config USING btree (realm_id);


--
-- Name: idx_auth_exec_flow; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_auth_exec_flow ON public.authentication_execution USING btree (flow_id);


--
-- Name: idx_auth_exec_realm_flow; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_auth_exec_realm_flow ON public.authentication_execution USING btree (realm_id, flow_id);


--
-- Name: idx_auth_flow_realm; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_auth_flow_realm ON public.authentication_flow USING btree (realm_id);


--
-- Name: idx_cl_clscope; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_cl_clscope ON public.client_scope_client USING btree (scope_id);


--
-- Name: idx_client_att_by_name_value; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_client_att_by_name_value ON public.client_attributes USING btree (name, substr(value, 1, 255));


--
-- Name: idx_client_id; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_client_id ON public.client USING btree (client_id);


--
-- Name: idx_client_init_acc_realm; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_client_init_acc_realm ON public.client_initial_access USING btree (realm_id);


--
-- Name: idx_clscope_attrs; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_clscope_attrs ON public.client_scope_attributes USING btree (scope_id);


--
-- Name: idx_clscope_cl; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_clscope_cl ON public.client_scope_client USING btree (client_id);


--
-- Name: idx_clscope_protmap; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_clscope_protmap ON public.protocol_mapper USING btree (client_scope_id);


--
-- Name: idx_clscope_role; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_clscope_role ON public.client_scope_role_mapping USING btree (scope_id);


--
-- Name: idx_compo_config_compo; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_compo_config_compo ON public.component_config USING btree (component_id);


--
-- Name: idx_component_provider_type; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_component_provider_type ON public.component USING btree (provider_type);


--
-- Name: idx_component_realm; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_component_realm ON public.component USING btree (realm_id);


--
-- Name: idx_composite; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_composite ON public.composite_role USING btree (composite);


--
-- Name: idx_composite_child; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_composite_child ON public.composite_role USING btree (child_role);


--
-- Name: idx_defcls_realm; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_defcls_realm ON public.default_client_scope USING btree (realm_id);


--
-- Name: idx_defcls_scope; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_defcls_scope ON public.default_client_scope USING btree (scope_id);


--
-- Name: idx_event_time; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_event_time ON public.event_entity USING btree (realm_id, event_time);


--
-- Name: idx_fedidentity_feduser; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_fedidentity_feduser ON public.federated_identity USING btree (federated_user_id);


--
-- Name: idx_fedidentity_user; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_fedidentity_user ON public.federated_identity USING btree (user_id);


--
-- Name: idx_fu_attribute; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_fu_attribute ON public.fed_user_attribute USING btree (user_id, realm_id, name);


--
-- Name: idx_fu_cnsnt_ext; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_fu_cnsnt_ext ON public.fed_user_consent USING btree (user_id, client_storage_provider, external_client_id);


--
-- Name: idx_fu_consent; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_fu_consent ON public.fed_user_consent USING btree (user_id, client_id);


--
-- Name: idx_fu_consent_ru; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_fu_consent_ru ON public.fed_user_consent USING btree (realm_id, user_id);


--
-- Name: idx_fu_credential; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_fu_credential ON public.fed_user_credential USING btree (user_id, type);


--
-- Name: idx_fu_credential_ru; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_fu_credential_ru ON public.fed_user_credential USING btree (realm_id, user_id);


--
-- Name: idx_fu_group_membership; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_fu_group_membership ON public.fed_user_group_membership USING btree (user_id, group_id);


--
-- Name: idx_fu_group_membership_ru; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_fu_group_membership_ru ON public.fed_user_group_membership USING btree (realm_id, user_id);


--
-- Name: idx_fu_required_action; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_fu_required_action ON public.fed_user_required_action USING btree (user_id, required_action);


--
-- Name: idx_fu_required_action_ru; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_fu_required_action_ru ON public.fed_user_required_action USING btree (realm_id, user_id);


--
-- Name: idx_fu_role_mapping; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_fu_role_mapping ON public.fed_user_role_mapping USING btree (user_id, role_id);


--
-- Name: idx_fu_role_mapping_ru; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_fu_role_mapping_ru ON public.fed_user_role_mapping USING btree (realm_id, user_id);


--
-- Name: idx_group_att_by_name_value; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_group_att_by_name_value ON public.group_attribute USING btree (name, ((value)::character varying(250)));


--
-- Name: idx_group_attr_group; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_group_attr_group ON public.group_attribute USING btree (group_id);


--
-- Name: idx_group_role_mapp_group; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_group_role_mapp_group ON public.group_role_mapping USING btree (group_id);


--
-- Name: idx_id_prov_mapp_realm; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_id_prov_mapp_realm ON public.identity_provider_mapper USING btree (realm_id);


--
-- Name: idx_ident_prov_realm; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_ident_prov_realm ON public.identity_provider USING btree (realm_id);


--
-- Name: idx_idp_for_login; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_idp_for_login ON public.identity_provider USING btree (realm_id, enabled, link_only, hide_on_login, organization_id);


--
-- Name: idx_idp_realm_org; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_idp_realm_org ON public.identity_provider USING btree (realm_id, organization_id);


--
-- Name: idx_keycloak_role_client; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_keycloak_role_client ON public.keycloak_role USING btree (client);


--
-- Name: idx_keycloak_role_realm; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_keycloak_role_realm ON public.keycloak_role USING btree (realm);


--
-- Name: idx_offline_uss_by_broker_session_id; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_offline_uss_by_broker_session_id ON public.offline_user_session USING btree (broker_session_id, realm_id);


--
-- Name: idx_offline_uss_by_last_session_refresh; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_offline_uss_by_last_session_refresh ON public.offline_user_session USING btree (realm_id, offline_flag, last_session_refresh);


--
-- Name: idx_offline_uss_by_user; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_offline_uss_by_user ON public.offline_user_session USING btree (user_id, realm_id, offline_flag);


--
-- Name: idx_org_domain_org_id; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_org_domain_org_id ON public.org_domain USING btree (org_id);


--
-- Name: idx_perm_ticket_owner; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_perm_ticket_owner ON public.resource_server_perm_ticket USING btree (owner);


--
-- Name: idx_perm_ticket_requester; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_perm_ticket_requester ON public.resource_server_perm_ticket USING btree (requester);


--
-- Name: idx_protocol_mapper_client; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_protocol_mapper_client ON public.protocol_mapper USING btree (client_id);


--
-- Name: idx_realm_attr_realm; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_realm_attr_realm ON public.realm_attribute USING btree (realm_id);


--
-- Name: idx_realm_clscope; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_realm_clscope ON public.client_scope USING btree (realm_id);


--
-- Name: idx_realm_def_grp_realm; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_realm_def_grp_realm ON public.realm_default_groups USING btree (realm_id);


--
-- Name: idx_realm_evt_list_realm; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_realm_evt_list_realm ON public.realm_events_listeners USING btree (realm_id);


--
-- Name: idx_realm_evt_types_realm; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_realm_evt_types_realm ON public.realm_enabled_event_types USING btree (realm_id);


--
-- Name: idx_realm_master_adm_cli; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_realm_master_adm_cli ON public.realm USING btree (master_admin_client);


--
-- Name: idx_realm_supp_local_realm; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_realm_supp_local_realm ON public.realm_supported_locales USING btree (realm_id);


--
-- Name: idx_redir_uri_client; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_redir_uri_client ON public.redirect_uris USING btree (client_id);


--
-- Name: idx_req_act_prov_realm; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_req_act_prov_realm ON public.required_action_provider USING btree (realm_id);


--
-- Name: idx_res_policy_policy; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_res_policy_policy ON public.resource_policy USING btree (policy_id);


--
-- Name: idx_res_scope_scope; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_res_scope_scope ON public.resource_scope USING btree (scope_id);


--
-- Name: idx_res_serv_pol_res_serv; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_res_serv_pol_res_serv ON public.resource_server_policy USING btree (resource_server_id);


--
-- Name: idx_res_srv_res_res_srv; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_res_srv_res_res_srv ON public.resource_server_resource USING btree (resource_server_id);


--
-- Name: idx_res_srv_scope_res_srv; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_res_srv_scope_res_srv ON public.resource_server_scope USING btree (resource_server_id);


--
-- Name: idx_rev_token_on_expire; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_rev_token_on_expire ON public.revoked_token USING btree (expire);


--
-- Name: idx_role_attribute; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_role_attribute ON public.role_attribute USING btree (role_id);


--
-- Name: idx_role_clscope; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_role_clscope ON public.client_scope_role_mapping USING btree (role_id);


--
-- Name: idx_scope_mapping_role; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_scope_mapping_role ON public.scope_mapping USING btree (role_id);


--
-- Name: idx_scope_policy_policy; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_scope_policy_policy ON public.scope_policy USING btree (policy_id);


--
-- Name: idx_update_time; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_update_time ON public.migration_model USING btree (update_time);


--
-- Name: idx_usconsent_clscope; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_usconsent_clscope ON public.user_consent_client_scope USING btree (user_consent_id);


--
-- Name: idx_usconsent_scope_id; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_usconsent_scope_id ON public.user_consent_client_scope USING btree (scope_id);


--
-- Name: idx_user_attribute; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_user_attribute ON public.user_attribute USING btree (user_id);


--
-- Name: idx_user_attribute_name; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_user_attribute_name ON public.user_attribute USING btree (name, value);


--
-- Name: idx_user_consent; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_user_consent ON public.user_consent USING btree (user_id);


--
-- Name: idx_user_credential; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_user_credential ON public.credential USING btree (user_id);


--
-- Name: idx_user_email; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_user_email ON public.user_entity USING btree (email);


--
-- Name: idx_user_group_mapping; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_user_group_mapping ON public.user_group_membership USING btree (user_id);


--
-- Name: idx_user_reqactions; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_user_reqactions ON public.user_required_action USING btree (user_id);


--
-- Name: idx_user_role_mapping; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_user_role_mapping ON public.user_role_mapping USING btree (user_id);


--
-- Name: idx_user_service_account; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_user_service_account ON public.user_entity USING btree (realm_id, service_account_client_link);


--
-- Name: idx_usr_fed_map_fed_prv; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_usr_fed_map_fed_prv ON public.user_federation_mapper USING btree (federation_provider_id);


--
-- Name: idx_usr_fed_map_realm; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_usr_fed_map_realm ON public.user_federation_mapper USING btree (realm_id);


--
-- Name: idx_usr_fed_prv_realm; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_usr_fed_prv_realm ON public.user_federation_provider USING btree (realm_id);


--
-- Name: idx_web_orig_client; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX idx_web_orig_client ON public.web_origins USING btree (client_id);


--
-- Name: user_attr_long_values; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX user_attr_long_values ON public.user_attribute USING btree (long_value_hash, name);


--
-- Name: user_attr_long_values_lower_case; Type: INDEX; Schema: public; Owner: eaf
--

CREATE INDEX user_attr_long_values_lower_case ON public.user_attribute USING btree (long_value_hash_lower_case, name);


--
-- Name: identity_provider fk2b4ebc52ae5c3b34; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.identity_provider
    ADD CONSTRAINT fk2b4ebc52ae5c3b34 FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: client_attributes fk3c47c64beacca966; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.client_attributes
    ADD CONSTRAINT fk3c47c64beacca966 FOREIGN KEY (client_id) REFERENCES public.client(id);


--
-- Name: federated_identity fk404288b92ef007a6; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.federated_identity
    ADD CONSTRAINT fk404288b92ef007a6 FOREIGN KEY (user_id) REFERENCES public.user_entity(id);


--
-- Name: client_node_registrations fk4129723ba992f594; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.client_node_registrations
    ADD CONSTRAINT fk4129723ba992f594 FOREIGN KEY (client_id) REFERENCES public.client(id);


--
-- Name: redirect_uris fk_1burs8pb4ouj97h5wuppahv9f; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.redirect_uris
    ADD CONSTRAINT fk_1burs8pb4ouj97h5wuppahv9f FOREIGN KEY (client_id) REFERENCES public.client(id);


--
-- Name: user_federation_provider fk_1fj32f6ptolw2qy60cd8n01e8; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.user_federation_provider
    ADD CONSTRAINT fk_1fj32f6ptolw2qy60cd8n01e8 FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: realm_required_credential fk_5hg65lybevavkqfki3kponh9v; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.realm_required_credential
    ADD CONSTRAINT fk_5hg65lybevavkqfki3kponh9v FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: resource_attribute fk_5hrm2vlf9ql5fu022kqepovbr; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.resource_attribute
    ADD CONSTRAINT fk_5hrm2vlf9ql5fu022kqepovbr FOREIGN KEY (resource_id) REFERENCES public.resource_server_resource(id);


--
-- Name: user_attribute fk_5hrm2vlf9ql5fu043kqepovbr; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.user_attribute
    ADD CONSTRAINT fk_5hrm2vlf9ql5fu043kqepovbr FOREIGN KEY (user_id) REFERENCES public.user_entity(id);


--
-- Name: user_required_action fk_6qj3w1jw9cvafhe19bwsiuvmd; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.user_required_action
    ADD CONSTRAINT fk_6qj3w1jw9cvafhe19bwsiuvmd FOREIGN KEY (user_id) REFERENCES public.user_entity(id);


--
-- Name: keycloak_role fk_6vyqfe4cn4wlq8r6kt5vdsj5c; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.keycloak_role
    ADD CONSTRAINT fk_6vyqfe4cn4wlq8r6kt5vdsj5c FOREIGN KEY (realm) REFERENCES public.realm(id);


--
-- Name: realm_smtp_config fk_70ej8xdxgxd0b9hh6180irr0o; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.realm_smtp_config
    ADD CONSTRAINT fk_70ej8xdxgxd0b9hh6180irr0o FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: realm_attribute fk_8shxd6l3e9atqukacxgpffptw; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.realm_attribute
    ADD CONSTRAINT fk_8shxd6l3e9atqukacxgpffptw FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: composite_role fk_a63wvekftu8jo1pnj81e7mce2; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.composite_role
    ADD CONSTRAINT fk_a63wvekftu8jo1pnj81e7mce2 FOREIGN KEY (composite) REFERENCES public.keycloak_role(id);


--
-- Name: authentication_execution fk_auth_exec_flow; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.authentication_execution
    ADD CONSTRAINT fk_auth_exec_flow FOREIGN KEY (flow_id) REFERENCES public.authentication_flow(id);


--
-- Name: authentication_execution fk_auth_exec_realm; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.authentication_execution
    ADD CONSTRAINT fk_auth_exec_realm FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: authentication_flow fk_auth_flow_realm; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.authentication_flow
    ADD CONSTRAINT fk_auth_flow_realm FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: authenticator_config fk_auth_realm; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.authenticator_config
    ADD CONSTRAINT fk_auth_realm FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: user_role_mapping fk_c4fqv34p1mbylloxang7b1q3l; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.user_role_mapping
    ADD CONSTRAINT fk_c4fqv34p1mbylloxang7b1q3l FOREIGN KEY (user_id) REFERENCES public.user_entity(id);


--
-- Name: client_scope_attributes fk_cl_scope_attr_scope; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.client_scope_attributes
    ADD CONSTRAINT fk_cl_scope_attr_scope FOREIGN KEY (scope_id) REFERENCES public.client_scope(id);


--
-- Name: client_scope_role_mapping fk_cl_scope_rm_scope; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.client_scope_role_mapping
    ADD CONSTRAINT fk_cl_scope_rm_scope FOREIGN KEY (scope_id) REFERENCES public.client_scope(id);


--
-- Name: protocol_mapper fk_cli_scope_mapper; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.protocol_mapper
    ADD CONSTRAINT fk_cli_scope_mapper FOREIGN KEY (client_scope_id) REFERENCES public.client_scope(id);


--
-- Name: client_initial_access fk_client_init_acc_realm; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.client_initial_access
    ADD CONSTRAINT fk_client_init_acc_realm FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: component_config fk_component_config; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.component_config
    ADD CONSTRAINT fk_component_config FOREIGN KEY (component_id) REFERENCES public.component(id);


--
-- Name: component fk_component_realm; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.component
    ADD CONSTRAINT fk_component_realm FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: realm_default_groups fk_def_groups_realm; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.realm_default_groups
    ADD CONSTRAINT fk_def_groups_realm FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: user_federation_mapper_config fk_fedmapper_cfg; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.user_federation_mapper_config
    ADD CONSTRAINT fk_fedmapper_cfg FOREIGN KEY (user_federation_mapper_id) REFERENCES public.user_federation_mapper(id);


--
-- Name: user_federation_mapper fk_fedmapperpm_fedprv; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.user_federation_mapper
    ADD CONSTRAINT fk_fedmapperpm_fedprv FOREIGN KEY (federation_provider_id) REFERENCES public.user_federation_provider(id);


--
-- Name: user_federation_mapper fk_fedmapperpm_realm; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.user_federation_mapper
    ADD CONSTRAINT fk_fedmapperpm_realm FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: associated_policy fk_frsr5s213xcx4wnkog82ssrfy; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.associated_policy
    ADD CONSTRAINT fk_frsr5s213xcx4wnkog82ssrfy FOREIGN KEY (associated_policy_id) REFERENCES public.resource_server_policy(id);


--
-- Name: scope_policy fk_frsrasp13xcx4wnkog82ssrfy; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.scope_policy
    ADD CONSTRAINT fk_frsrasp13xcx4wnkog82ssrfy FOREIGN KEY (policy_id) REFERENCES public.resource_server_policy(id);


--
-- Name: resource_server_perm_ticket fk_frsrho213xcx4wnkog82sspmt; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.resource_server_perm_ticket
    ADD CONSTRAINT fk_frsrho213xcx4wnkog82sspmt FOREIGN KEY (resource_server_id) REFERENCES public.resource_server(id);


--
-- Name: resource_server_resource fk_frsrho213xcx4wnkog82ssrfy; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.resource_server_resource
    ADD CONSTRAINT fk_frsrho213xcx4wnkog82ssrfy FOREIGN KEY (resource_server_id) REFERENCES public.resource_server(id);


--
-- Name: resource_server_perm_ticket fk_frsrho213xcx4wnkog83sspmt; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.resource_server_perm_ticket
    ADD CONSTRAINT fk_frsrho213xcx4wnkog83sspmt FOREIGN KEY (resource_id) REFERENCES public.resource_server_resource(id);


--
-- Name: resource_server_perm_ticket fk_frsrho213xcx4wnkog84sspmt; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.resource_server_perm_ticket
    ADD CONSTRAINT fk_frsrho213xcx4wnkog84sspmt FOREIGN KEY (scope_id) REFERENCES public.resource_server_scope(id);


--
-- Name: associated_policy fk_frsrpas14xcx4wnkog82ssrfy; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.associated_policy
    ADD CONSTRAINT fk_frsrpas14xcx4wnkog82ssrfy FOREIGN KEY (policy_id) REFERENCES public.resource_server_policy(id);


--
-- Name: scope_policy fk_frsrpass3xcx4wnkog82ssrfy; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.scope_policy
    ADD CONSTRAINT fk_frsrpass3xcx4wnkog82ssrfy FOREIGN KEY (scope_id) REFERENCES public.resource_server_scope(id);


--
-- Name: resource_server_perm_ticket fk_frsrpo2128cx4wnkog82ssrfy; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.resource_server_perm_ticket
    ADD CONSTRAINT fk_frsrpo2128cx4wnkog82ssrfy FOREIGN KEY (policy_id) REFERENCES public.resource_server_policy(id);


--
-- Name: resource_server_policy fk_frsrpo213xcx4wnkog82ssrfy; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.resource_server_policy
    ADD CONSTRAINT fk_frsrpo213xcx4wnkog82ssrfy FOREIGN KEY (resource_server_id) REFERENCES public.resource_server(id);


--
-- Name: resource_scope fk_frsrpos13xcx4wnkog82ssrfy; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.resource_scope
    ADD CONSTRAINT fk_frsrpos13xcx4wnkog82ssrfy FOREIGN KEY (resource_id) REFERENCES public.resource_server_resource(id);


--
-- Name: resource_policy fk_frsrpos53xcx4wnkog82ssrfy; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.resource_policy
    ADD CONSTRAINT fk_frsrpos53xcx4wnkog82ssrfy FOREIGN KEY (resource_id) REFERENCES public.resource_server_resource(id);


--
-- Name: resource_policy fk_frsrpp213xcx4wnkog82ssrfy; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.resource_policy
    ADD CONSTRAINT fk_frsrpp213xcx4wnkog82ssrfy FOREIGN KEY (policy_id) REFERENCES public.resource_server_policy(id);


--
-- Name: resource_scope fk_frsrps213xcx4wnkog82ssrfy; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.resource_scope
    ADD CONSTRAINT fk_frsrps213xcx4wnkog82ssrfy FOREIGN KEY (scope_id) REFERENCES public.resource_server_scope(id);


--
-- Name: resource_server_scope fk_frsrso213xcx4wnkog82ssrfy; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.resource_server_scope
    ADD CONSTRAINT fk_frsrso213xcx4wnkog82ssrfy FOREIGN KEY (resource_server_id) REFERENCES public.resource_server(id);


--
-- Name: composite_role fk_gr7thllb9lu8q4vqa4524jjy8; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.composite_role
    ADD CONSTRAINT fk_gr7thllb9lu8q4vqa4524jjy8 FOREIGN KEY (child_role) REFERENCES public.keycloak_role(id);


--
-- Name: user_consent_client_scope fk_grntcsnt_clsc_usc; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.user_consent_client_scope
    ADD CONSTRAINT fk_grntcsnt_clsc_usc FOREIGN KEY (user_consent_id) REFERENCES public.user_consent(id);


--
-- Name: user_consent fk_grntcsnt_user; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.user_consent
    ADD CONSTRAINT fk_grntcsnt_user FOREIGN KEY (user_id) REFERENCES public.user_entity(id);


--
-- Name: group_attribute fk_group_attribute_group; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.group_attribute
    ADD CONSTRAINT fk_group_attribute_group FOREIGN KEY (group_id) REFERENCES public.keycloak_group(id);


--
-- Name: group_role_mapping fk_group_role_group; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.group_role_mapping
    ADD CONSTRAINT fk_group_role_group FOREIGN KEY (group_id) REFERENCES public.keycloak_group(id);


--
-- Name: realm_enabled_event_types fk_h846o4h0w8epx5nwedrf5y69j; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.realm_enabled_event_types
    ADD CONSTRAINT fk_h846o4h0w8epx5nwedrf5y69j FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: realm_events_listeners fk_h846o4h0w8epx5nxev9f5y69j; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.realm_events_listeners
    ADD CONSTRAINT fk_h846o4h0w8epx5nxev9f5y69j FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: identity_provider_mapper fk_idpm_realm; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.identity_provider_mapper
    ADD CONSTRAINT fk_idpm_realm FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: idp_mapper_config fk_idpmconfig; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.idp_mapper_config
    ADD CONSTRAINT fk_idpmconfig FOREIGN KEY (idp_mapper_id) REFERENCES public.identity_provider_mapper(id);


--
-- Name: web_origins fk_lojpho213xcx4wnkog82ssrfy; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.web_origins
    ADD CONSTRAINT fk_lojpho213xcx4wnkog82ssrfy FOREIGN KEY (client_id) REFERENCES public.client(id);


--
-- Name: scope_mapping fk_ouse064plmlr732lxjcn1q5f1; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.scope_mapping
    ADD CONSTRAINT fk_ouse064plmlr732lxjcn1q5f1 FOREIGN KEY (client_id) REFERENCES public.client(id);


--
-- Name: protocol_mapper fk_pcm_realm; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.protocol_mapper
    ADD CONSTRAINT fk_pcm_realm FOREIGN KEY (client_id) REFERENCES public.client(id);


--
-- Name: credential fk_pfyr0glasqyl0dei3kl69r6v0; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.credential
    ADD CONSTRAINT fk_pfyr0glasqyl0dei3kl69r6v0 FOREIGN KEY (user_id) REFERENCES public.user_entity(id);


--
-- Name: protocol_mapper_config fk_pmconfig; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.protocol_mapper_config
    ADD CONSTRAINT fk_pmconfig FOREIGN KEY (protocol_mapper_id) REFERENCES public.protocol_mapper(id);


--
-- Name: default_client_scope fk_r_def_cli_scope_realm; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.default_client_scope
    ADD CONSTRAINT fk_r_def_cli_scope_realm FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: required_action_provider fk_req_act_realm; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.required_action_provider
    ADD CONSTRAINT fk_req_act_realm FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: resource_uris fk_resource_server_uris; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.resource_uris
    ADD CONSTRAINT fk_resource_server_uris FOREIGN KEY (resource_id) REFERENCES public.resource_server_resource(id);


--
-- Name: role_attribute fk_role_attribute_id; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.role_attribute
    ADD CONSTRAINT fk_role_attribute_id FOREIGN KEY (role_id) REFERENCES public.keycloak_role(id);


--
-- Name: realm_supported_locales fk_supported_locales_realm; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.realm_supported_locales
    ADD CONSTRAINT fk_supported_locales_realm FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: user_federation_config fk_t13hpu1j94r2ebpekr39x5eu5; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.user_federation_config
    ADD CONSTRAINT fk_t13hpu1j94r2ebpekr39x5eu5 FOREIGN KEY (user_federation_provider_id) REFERENCES public.user_federation_provider(id);


--
-- Name: user_group_membership fk_user_group_user; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.user_group_membership
    ADD CONSTRAINT fk_user_group_user FOREIGN KEY (user_id) REFERENCES public.user_entity(id);


--
-- Name: policy_config fkdc34197cf864c4e43; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.policy_config
    ADD CONSTRAINT fkdc34197cf864c4e43 FOREIGN KEY (policy_id) REFERENCES public.resource_server_policy(id);


--
-- Name: identity_provider_config fkdc4897cf864c4e43; Type: FK CONSTRAINT; Schema: public; Owner: eaf
--

ALTER TABLE ONLY public.identity_provider_config
    ADD CONSTRAINT fkdc4897cf864c4e43 FOREIGN KEY (identity_provider_id) REFERENCES public.identity_provider(internal_id);


--
-- PostgreSQL database dump complete
--

\unrestrict 2lxNCZdgQ6A29A647dlEmLkb9gnBiZifbkfvWOUDmIOk6VaWeQeORHM3Z8sqLYR

