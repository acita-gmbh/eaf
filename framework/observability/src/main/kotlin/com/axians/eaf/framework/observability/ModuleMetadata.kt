package com.axians.eaf.framework.observability

import org.springframework.modulith.ApplicationModule

/**
 * Spring Modulith metadata for EAF Observability module.
 * Defines module boundaries and allowed dependencies.
 */
@ApplicationModule(
    displayName = "EAF Observability Module",
    allowedDependencies = ["core", "security"],
)
class ModuleMetadata
