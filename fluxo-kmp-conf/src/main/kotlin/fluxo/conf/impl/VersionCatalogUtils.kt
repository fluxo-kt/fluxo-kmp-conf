@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "TooManyFunctions")

package fluxo.conf.impl

import kotlin.internal.LowPriorityInOverloadResolution
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependencyBundle
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.plugin.use.PluginDependency


private const val DEFAULT_VERSION_CATALOG = "libs"

@Deprecated("")
internal val Project.libsCatalog: VersionCatalog
    get() = the<VersionCatalogsExtension>().named(DEFAULT_VERSION_CATALOG)

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

internal inline fun VersionCatalog.onPlugin(
    alias: String,
    body: (PluginDependency) -> Unit,
): Boolean {
    val opt = findPlugin(alias)
    if (opt.isPresent) {
        body(opt.get().get())
        return true
    }
    return false
}


@JvmName("vIntVararg")
@LowPriorityInOverloadResolution
internal fun VersionCatalog?.vInt(vararg aliases: String, force: Boolean = true): Int? =
    vInt(aliases, force = force)

internal fun VersionCatalog?.vInt(aliases: Array<out String>?, force: Boolean = true): Int? {
    if (this != null && aliases != null) {
        for (alias in aliases) {
            return vInt(alias, force = force) ?: continue
        }
    }
    return null
}

internal fun VersionCatalog?.vInt(alias: String?, force: Boolean = true): Int? {
    if (!alias.isNullOrEmpty()) {
        val v = this?.findVersion(alias)?.getOrNull()?.toString()
        try {
            return v?.toInt()
        } catch (e: Throwable) {
            if (force) {
                val msg = "Invalid '$alias' declared in libs.versions.toml, expected int: '$v'"
                throw GradleException(msg, e)
            }
        }
    }
    return null
}

@JvmName("vVararg")
@LowPriorityInOverloadResolution
internal fun VersionCatalog?.v(vararg aliases: String) = v(aliases)

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

internal inline fun VersionCatalog.onVersion(alias: String, body: (String) -> Unit): Boolean {
    val opt = findVersion(alias)
    if (opt.isPresent) {
        body(opt.get().toString())
        return true
    }
    return false
}
