@file:Suppress("MatchingDeclarationName")

package com.axians.eaf.products.widgetdemo

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    displayName = "Widget Demo Product Module",
    allowedDependencies = ["core", "security", "cqrs", "persistence", "web", "shared::api"],
)
class WidgetModule
