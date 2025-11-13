package com.axians.eaf.framework.security.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Enables KeycloakAdminProperties for all profiles.
 *
 * Story 3.8: KeycloakUserDirectory (@Component) requires KeycloakAdminProperties to be available
 * globally, not just when SecurityConfiguration is active.
 */
@Configuration
@EnableConfigurationProperties(KeycloakAdminProperties::class)
open class KeycloakConfigurationProperties
