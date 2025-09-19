package com.axians.eaf.testing.stack

import java.nio.file.Files
import java.nio.file.Path

internal fun resolveRepoFile(fileName: String): Path {
    var current = Path.of("").toAbsolutePath()
    repeat(8) {
        val candidate = current.resolve(fileName)
        if (Files.exists(candidate)) {
            return candidate
        }
        current = current.parent ?: return@repeat
    }
    error("Unable to locate $fileName starting from ${Path.of("").toAbsolutePath()}")
}
