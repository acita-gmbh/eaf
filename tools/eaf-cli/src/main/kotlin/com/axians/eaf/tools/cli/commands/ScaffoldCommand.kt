package com.axians.eaf.tools.cli.commands

import picocli.CommandLine.Command

/**
 * Scaffold command for generating EAF code structures.
 *
 * This is a placeholder command in Story 7.1. Actual generator subcommands
 * (module, aggregate, ra-resource) will be added in Stories 7.2-7.4.
 */
@Command(
    name = "scaffold",
    description = ["Generate EAF code scaffolds"],
    mixinStandardHelpOptions = true,
)
class ScaffoldCommand : Runnable {
    override fun run() {
        println("Scaffold command - generators will be added in Stories 7.2-7.4")
        println("Available generators:")
        println("  - module (Story 7.2)")
        println("  - aggregate (Story 7.3)")
        println("  - ra-resource (Story 7.4)")
    }
}
