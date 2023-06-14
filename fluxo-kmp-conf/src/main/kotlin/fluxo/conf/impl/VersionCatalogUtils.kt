package fluxo.conf.impl

import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependencyBundle
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.plugin.use.PluginDependency


private const val DEFAULT_VERSION_CATALOG = "libs"

internal val Project.catalogs: VersionCatalogsExtension
    get() = the<VersionCatalogsExtension>()

internal val Project.libsCatalog: VersionCatalog
    get() = catalogs.named(DEFAULT_VERSION_CATALOG)

internal val Project.libsCatalogOptional: VersionCatalog?
    get() = try {
        extensions.findByType(VersionCatalogsExtension::class.java)
            ?.find(DEFAULT_VERSION_CATALOG)
            ?.getOrNull()
    } catch (_: Throwable) {
        null
    }


internal fun VersionCatalog.onLibrary(
    alias: String,
    body: (Provider<MinimalExternalModuleDependency>) -> Unit,
): Boolean {
    val opt = findLibrary(alias)
    if (opt.isPresent) {
        body(opt.get())
        return true
    }
    return false
}

internal fun VersionCatalog.lib(alias: String): Provider<MinimalExternalModuleDependency> {
    return findLibrary(alias).get()
}


internal fun VersionCatalog?.b(alias: String?): Provider<ExternalModuleDependencyBundle>? {
    return if (!alias.isNullOrEmpty()) this?.findBundle(alias)?.getOrNull() else null
}

internal fun VersionCatalog.bundle(alias: String): Provider<ExternalModuleDependencyBundle> {
    return findBundle(alias).get()
}

internal fun VersionCatalog.onBundle(
    alias: String,
    body: (Provider<ExternalModuleDependencyBundle>) -> Unit,
): Boolean {
    val opt = findBundle(alias)
    if (opt.isPresent) {
        body(opt.get())
        return true
    }
    return false
}


internal fun VersionCatalog?.p(alias: String?): Provider<PluginDependency>? {
    return if (!alias.isNullOrEmpty()) this?.findPlugin(alias)?.getOrNull() else null
}

internal fun VersionCatalog?.p(aliases: Array<out String>?): Provider<PluginDependency>? {
    if (this != null && aliases != null) {
        for (alias in aliases) {
            return p(alias) ?: continue
        }
    }
    return null
}

internal fun VersionCatalog.plugin(alias: String): Provider<PluginDependency> {
    return findPlugin(alias).get()
}

internal fun VersionCatalog.onPlugin(alias: String, body: (PluginDependency) -> Unit): Boolean {
    val opt = findPlugin(alias)
    if (opt.isPresent) {
        body(opt.get().get())
        return true
    }
    return false
}


internal fun VersionCatalog?.v(aliases: Array<out String>?): String? {
    if (this != null && aliases != null) {
        for (alias in aliases) {
            return v(alias) ?: continue
        }
    }
    return null
}

internal fun VersionCatalog?.v(alias: String?): String? {
    return if (!alias.isNullOrEmpty()) this?.findVersion(alias)?.getOrNull()?.toString() else null
}

internal fun VersionCatalog.version(alias: String): String {
    return findVersion(alias).get().toString()
}

internal fun VersionCatalog.onVersion(alias: String, body: (String) -> Unit): Boolean {
    val opt = findVersion(alias)
    if (opt.isPresent) {
        body(opt.get().toString())
        return true
    }
    return false
}
