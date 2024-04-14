package fluxo.vc

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.impl.getOrNull
import fluxo.log.e
import fluxo.log.w
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.tomlj.Toml

internal class FluxoVersionCatalog(project: Project, context: FluxoKmpConfContext) {
    internal val gradle: VersionCatalog? by lazy { project.libsCatalogOrNull }
    internal val fallback: TomlResultCatalog? by lazy { loadFluxoCatalogOrNull(project, context) }
}

private const val DEFAULT_VERSION_CATALOG = "libs"
private const val FLUXO_BUNDLED_VERSION_CATALOG = "fluxo.versions.toml"

/**
 * Default Gradle version catalog.
 *
 * @see org.gradle.api.artifacts.VersionCatalogsExtension
 */
private val Project.libsCatalogOrNull: VersionCatalog?
    get() {
        val name = DEFAULT_VERSION_CATALOG
        return try {
            extensions.findByType(VersionCatalogsExtension::class.java)
                ?.find(name)
                ?.getOrNull()
        } catch (e: Throwable) {
            logger.e("Failed to get '$name' version catalog: $e", e)
            null
        }
    }

/**
 * Pre-packaged/bundled version catalog.
 *
 * @see libsCatalogOrNull
 * @see org.gradle.api.internal.catalog.parser.TomlCatalogFileParser
 * @see org.gradle.api.internal.catalog.DefaultVersionCatalogBuilder
 */
private fun loadFluxoCatalogOrNull(
    project: Project,
    context: FluxoKmpConfContext,
): TomlResultCatalog? {
    val name = FLUXO_BUNDLED_VERSION_CATALOG
    return try {
        val stream = context.javaClass.classLoader.getResourceAsStream(name)
        if (stream == null) {
            project.logger.w("No bundled '$name' version catalog found")
            return null
        }
        TomlResultCatalog(stream.use(Toml::parse), project)
    } catch (e: Throwable) {
        project.logger.e("Failed to load bundled '$name' version catalog: $e", e)
        null
    }
}
