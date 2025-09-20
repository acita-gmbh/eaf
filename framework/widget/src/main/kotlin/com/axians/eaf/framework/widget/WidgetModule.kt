@file:Suppress("MatchingDeclarationName")

package com.axians.eaf.framework.widget

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    displayName = "EAF Widget Module",
    allowedDependencies = ["core", "shared::api", "shared::testing"],
)
class WidgetModule
