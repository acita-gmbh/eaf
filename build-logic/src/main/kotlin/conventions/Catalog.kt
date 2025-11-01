package conventions

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

internal data class CatalogLibrary(val module: String, val version: String) {
    /**
     * Get Gradle dependency notation string
     * Usage: add("implementation", library.toDependencyNotation())
     */
    fun toDependencyNotation(): String = "$module:$version"
}

internal class Catalog(private val versions: Map<String, String>, private val libraries: Map<String, CatalogLibrary>) {
    fun version(alias: String): String = versions[alias]
        ?: error("Version alias '$alias' not found in libs.versions.toml")

    fun library(alias: String): CatalogLibrary = libraries[alias]
        ?: error("Library alias '$alias' not found in libs.versions.toml")
}

private object CatalogCache {
    private val cache = ConcurrentHashMap<Pair<Path, Long>, Catalog>()

    fun get(path: Path): Catalog {
        val normalized = path.normalize()
        val lastModified = Files.getLastModifiedTime(normalized).toMillis()
        return cache.computeIfAbsent(normalized to lastModified) { parse(normalized) }
    }

    private fun parse(path: Path): Catalog {
        val versions = mutableMapOf<String, String>()
        val libraries = mutableMapOf<String, CatalogLibrary>()

        var section: String? = null
        Files.readAllLines(path).forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            if (line.startsWith("[")) {
                section = line.trim('[', ']')
                return@forEach
            }

            when (section) {
                "versions" -> {
                    val alias = line.substringBefore('=').trim()
                    val rawValue = line.substringAfter('=').trim()
                    val cleaned = rawValue.substringBefore('#').trim().trim('"')
                    if (alias.isNotEmpty() && cleaned.isNotEmpty()) {
                        versions[alias] = cleaned
                    }
                }
                "libraries" -> {
                    if (!line.contains('{')) return@forEach
                    val alias = line.substringBefore('=').trim()
                    val properties = line.substringAfter('{').substringBeforeLast('}').trim()
                    var module: String? = null
                    var version: String? = null

                    // Parse library properties
                    for (entry in properties.split(',')) {
                        val parts = entry.split('=', limit = 2)
                        if (parts.size != 2) continue
                        val key = parts[0].trim()
                        val value = parts[1].trim().substringBefore('#').trim().trim('"')
                        when (key) {
                            "module" -> module = value
                            "version" -> version = value
                            "version.ref" -> version = versions[value]
                        }
                    }

                    val resolvedModule = module ?: error("Library '$alias' missing module definition")
                    val resolvedVersion = version ?: error("Library '$alias' missing version information")

                    libraries[alias] = CatalogLibrary(resolvedModule, resolvedVersion)
                }
            }
        }

        return Catalog(versions, libraries)
    }

}

internal fun loadCatalog(path: Path): Catalog = CatalogCache.get(path)
