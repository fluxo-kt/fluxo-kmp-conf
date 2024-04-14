@file:Suppress(
    "CyclomaticComplexMethod",
    "LongMethod",
    "NestedBlockDepth",
    "LoopWithTooManyJumpStatements",
)

package fluxo.vc

import fluxo.log.d
import fluxo.log.e
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultMinimalDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.logging.Logger
import org.tomlj.TomlParseResult
import org.tomlj.TomlTable

/**
 * Fallback version catalog from bundled toml file.
 *
 * @see org.gradle.api.internal.catalog.parser.TomlCatalogFileParser
 * @see org.gradle.api.internal.catalog.parser.TomlCatalogFileParser.parseLibraries
 * @see org.gradle.api.internal.catalog.parser.TomlCatalogFileParser.parseVersions
 * @see org.gradle.api.internal.catalog.DefaultVersionCatalogBuilder
 */
internal class TomlResultCatalog(
    toml: TomlParseResult,
    project: Project,
) {
    internal val versions: Map<String, String>
    internal val libraries: Map<String, MinimalExternalModuleDependency>

    init {
        val logger = project.logger
        versions = parseVersions(toml.getTable(VERSIONS_KEY), logger)
        libraries = parseLibraries(toml.getTable(LIBRARIES_KEY), logger)

        // TODO: Parse bundles and plugins

        // val bundles = toml.getTable(BUNDLES_KEY)
        // val plugins = toml.getTable(PLUGINS_KEY)

        logger.d(
            "Loaded bundled toml version catalog" +
                " (${versions.size} versions, ${libraries.size} libraries)",
        )
    }

    /** @see org.gradle.api.internal.catalog.parser.TomlCatalogFileParser.parseLibrary */
    private fun parseLibraries(
        table: TomlTable?,
        logger: Logger,
    ): Map<String, MinimalExternalModuleDependency> {
        if (table == null || table.isEmpty) {
            return emptyMap()
        }

        val libraries = HashMap<String, MinimalExternalModuleDependency>(table.size())
        for (alias in table.keySet()) {
            libraries[alias] = when (val entry = table.get(alias)) {
                is String -> {
                    val (group, name, version) = entry.split(':', limit = 3)
                    val module = DefaultModuleIdentifier.newId(group, name)
                    val v = DefaultMutableVersionConstraint.withVersion(version)
                    DefaultMinimalDependency(module, v)
                }

                is TomlTable -> {
                    val entryTable: TomlTable = entry
                    val moduleString = entryTable.getString("module")
                    val module = if (moduleString != null) {
                        val (group, name) = moduleString.split(':', limit = 2)
                        DefaultModuleIdentifier.newId(group, name)
                    } else {
                        val group = entryTable.getString("group")
                        val name = entryTable.getString("name")
                        if (name == null) {
                            logger.e("Missing 'name' for library alias '$alias': $entry")
                            continue
                        }
                        DefaultModuleIdentifier.newId(group, name)
                    }

                    val version = when (val v = entryTable.get("version")) {
                        is String -> {
                            DefaultMutableVersionConstraint.withVersion(v)
                        }

                        is TomlTable -> {
                            val ref = v.getString("ref")
                            if (ref == null) {
                                logger.e("Missing version 'ref' for library alias '$alias': $v")
                                continue
                            }
                            val version = versions[ref]
                            if (version == null) {
                                logger.e(
                                    "Missing referenced version for library alias '$alias': $ref",
                                )
                                continue
                            }
                            DefaultMutableVersionConstraint.withVersion(version)
                        }

                        else -> {
                            logger.e("Invalid version entry for library alias '$alias': $v")
                            continue
                        }
                    }

                    DefaultMinimalDependency(module, version)
                }

                else -> {
                    logger.e("Invalid library entry for alias '$alias': $entry")
                    continue
                }
            }
        }
        return libraries
    }

    /** @see org.gradle.api.internal.catalog.parser.TomlCatalogFileParser.parseVersion */
    private fun parseVersions(table: TomlTable?, logger: Logger): Map<String, String> {
        if (table == null || table.isEmpty) {
            return emptyMap()
        }

        val versions = HashMap<String, String>(table.size())
        for (alias in table.keySet()) {
            when (val version = table.get(alias)) {
                is String -> if (version.isNotEmpty()) {
                    versions[alias] = version
                }

                else -> logger.e("Invalid version entry for bundled TOML alias '$alias': $version")
            }
        }
        return versions
    }

    private companion object {
        private const val VERSIONS_KEY = "versions"
        private const val LIBRARIES_KEY = "libraries"
//        private const val BUNDLES_KEY = "bundles"
//        private const val PLUGINS_KEY = "plugins"
    }
}
