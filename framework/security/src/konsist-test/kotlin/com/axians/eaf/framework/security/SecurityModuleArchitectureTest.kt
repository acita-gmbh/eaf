package com.axians.eaf.framework.security

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import io.kotest.core.spec.style.FreeSpec

/**
 * Konsist architecture test for Security Module boundary enforcement.
 *
 * Validates:
 * - AC2: SecurityModule has @ApplicationModule annotation
 * - AC2: Security module only depends on core module
 * - AC2: No circular dependencies (core does not depend on security)
 *
 * Story 3.1: Spring Security OAuth2 Resource Server Foundation
 */
class SecurityModuleArchitectureTest :
    FreeSpec({
        val scope = Konsist.scopeFromProject()

        "Security Module Boundary Enforcement" - {
            "SecurityModule class should have @ApplicationModule annotation" {
                scope
                    .classes()
                    .withNameEndingWith("SecurityModule")
                    .assertTrue {
                        it.hasAnnotation { annotation ->
                            annotation.name == "ApplicationModule"
                        }
                    }
            }

            "Security module classes should not import from other framework modules except core" {
                scope
                    .files
                    .withPackage("..framework.security..")
                    .assertFalse {
                        it.hasImport { import ->
                            import.name.startsWith("com.axians.eaf.framework.") &&
                                !import.name.startsWith("com.axians.eaf.framework.core.") &&
                                !import.name.startsWith("com.axians.eaf.framework.security.")
                        }
                    }
            }

            "Core module should not depend on security module (no circular dependencies)" {
                scope
                    .files
                    .withPackage("..framework.core..")
                    .assertFalse {
                        it.hasImport { import ->
                            import.name.startsWith("com.axians.eaf.framework.security.")
                        }
                    }
            }
        }
    })
