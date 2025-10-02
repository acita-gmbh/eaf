package com.axians.eaf.tools.cli

import com.axians.eaf.tools.cli.commands.ScaffoldCommand
import picocli.CommandLine
import picocli.CommandLine.Command

/**
 * Main entry point for the EAF Scaffolding CLI.
 *
 * This CLI provides code generation and scaffolding capabilities for the
 * Enterprise Application Framework, automating the creation of boilerplate
 * code required by the Hexagonal/CQRS/Modulith/Flowable stack.
 */
@Command(
    name = "eaf",
    version = ["EAF CLI 0.1.0"],
    description = ["Enterprise Application Framework Scaffolding CLI"],
    mixinStandardHelpOptions = true,
    subcommands = [ScaffoldCommand::class],
)
class EafCli : Runnable {
    override fun run() {
        // Default behavior: show help if no subcommand provided
        CommandLine(this).usage(System.out)
    }
}

@Suppress("SpreadOperator") // Standard Kotlin varargs pattern for main() function
fun main(args: Array<String>) {
    val exitCode = CommandLine(EafCli()).execute(*args)
    kotlin.system.exitProcess(exitCode)
}
