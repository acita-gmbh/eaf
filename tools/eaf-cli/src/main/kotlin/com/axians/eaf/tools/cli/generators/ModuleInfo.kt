package com.axians.eaf.tools.cli.generators

import java.nio.file.Path

/**
 * Information about a successfully generated module.
 */
data class ModuleInfo(
    val moduleName: String,
    val modulePath: Path,
    val filesCreated: List<Path> = emptyList(),
)
