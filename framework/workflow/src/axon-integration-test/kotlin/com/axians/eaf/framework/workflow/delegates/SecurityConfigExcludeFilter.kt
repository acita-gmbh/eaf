package com.axians.eaf.framework.workflow.delegates

import org.springframework.boot.context.TypeExcludeFilter
import org.springframework.core.type.classreading.MetadataReader
import org.springframework.core.type.classreading.MetadataReaderFactory

/**
 * TypeExcludeFilter that prevents Spring from loading security @Configuration classes
 * from framework/security module during test context initialization.
 *
 * This filter runs BEFORE component scanning and bean creation, preventing security
 * configurations from loading even when framework/security JAR is on test classpath.
 *
 * ## Why This Is Needed (Story 6.2)
 *
 * The test requires:
 * - Widget aggregate from products/widget-demo
 * - CommandGateway from framework/cqrs
 * - TenantContext (provided by AxonIntegrationTestConfig)
 *
 * But products/widget-demo → framework/security (compile dependency)
 * And framework/cqrs → framework/security (compile dependency)
 *
 * This puts framework/security.jar on test classpath, and Spring's component scanning
 * discovers SecurityConfiguration, SecurityFilterChainConfiguration which require
 * JwtDecoder → connects to Keycloak → test fails.
 *
 * Standard `exclude = [SecurityAutoConfiguration::class]` doesn't work because these
 * are custom @Configuration classes, not auto-configurations.
 *
 * TypeExcludeFilter operates at component scanning time (before bean creation) and
 * can filter ANY class from ANY JAR.
 *
 * ## Research Sources
 * - Spring Boot TypeExcludeFilter docs (official extension point)
 * - External AI research (4 sources, unanimous recommendation)
 * - Story 6.2 deep research: .ai/story-6.2-spring-boot-test-spike-blocker.md
 *
 * @see org.springframework.boot.context.TypeExcludeFilter
 */
class SecurityConfigExcludeFilter : TypeExcludeFilter() {
    /**
     * Determines if a class should be excluded from component scanning.
     *
     * Returns true if the class is a @Configuration from framework/security module.
     */
    override fun match(
        metadataReader: MetadataReader,
        metadataReaderFactory: MetadataReaderFactory,
    ): Boolean {
        val className = metadataReader.classMetadata.className
        val isConfiguration =
            metadataReader.annotationMetadata
                .hasAnnotation("org.springframework.context.annotation.Configuration")

        // Exclude ALL @Configuration classes from framework.security module
        return className.startsWith("com.axians.eaf.framework.security") && isConfiguration
    }

    /**
     * CRITICAL: Must implement equals/hashCode for Spring Test Context caching.
     * Without this, each test gets a new context instead of reusing cached one.
     */
    override fun equals(other: Any?): Boolean = other is SecurityConfigExcludeFilter

    override fun hashCode(): Int = SecurityConfigExcludeFilter::class.hashCode()
}
