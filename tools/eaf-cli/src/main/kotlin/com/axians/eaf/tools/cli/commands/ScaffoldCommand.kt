package com.axians.eaf.tools.cli.commands

import picocli.CommandLine
import picocli.CommandLine.Command

/**
 * Scaffold command for generating EAF code structures.
 *
 * Subcommands:
 * - module: Generate new Spring Modulith product module (Story 7.2)
 * - aggregate: Generate CQRS/ES aggregate (Story 7.3 - planned)
 * - ra-resource: Generate React-Admin resource (Story 7.4 - planned)
 */
@Command(
    name = "scaffold",
    description = ["Generate EAF code scaffolds"],
    mixinStandardHelpOptions = true,
    subcommands = [ModuleCommand::class],
)
class ScaffoldCommand : Runnable {
    override fun run() {
        // Show help if no subcommand specified
        CommandLine(this).usage(System.out)
    }
}
