@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "TooManyFunctions", "ReturnCount")

package fluxo.vc

import fluxo.conf.impl.getOrNull
import java.util.Optional
import kotlin.internal.LowPriorityInOverloadResolution
import org.gradle.api.GradleException
import org.gradle.api.artifacts.ExternalModuleDependencyBundle
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.provider.Provider
import org.gradle.plugin.use.PluginDependency


// region Libraries

internal fun FluxoVersionCatalog.onLibrary(
    alias: String,
    body: (Provider<MinimalExternalModuleDependency>) -> Unit,
): Boolean = on({ findLibrary(alias) }, body)

@JvmName("lVararg")
@LowPriorityInOverloadResolution
internal fun FluxoVersionCatalog.l(vararg aliases: String) = l(aliases)

internal fun FluxoVersionCatalog.l(
    aliases: Array<out String>?,
    allowFallback: Boolean = true,
): MinimalExternalModuleDependency? {
    @Suppress("DuplicatedCode")
    if (aliases != null) {
        for (alias in aliases) {
            return l(alias, allowFallback = false) ?: continue
        }
        if (allowFallback) {
            for (alias in aliases) {
                return fallback?.libraries?.get(alias) ?: continue
            }
        }
    }
    return null
}

internal fun FluxoVersionCatalog.l(
    alias: String,
    allowFallback: Boolean = true,
): MinimalExternalModuleDependency? {
    var l = gradle?.findLibrary(alias)?.getOrNull()?.orNull
    if (l == null && allowFallback) {
        l = fallback?.libraries?.get(alias)
    }
    return l
}

// endregion


// region Bundles

internal fun FluxoVersionCatalog.b(alias: String?): Provider<ExternalModuleDependencyBundle>? =
    if (!alias.isNullOrEmpty()) gradle?.findBundle(alias)?.getOrNull() else null

internal fun FluxoVersionCatalog.onBundle(
    alias: String,
    body: (Provider<ExternalModuleDependencyBundle>) -> Unit,
): Boolean {
    fallback
    return on({ findBundle(alias) }, body)
}

// endregion


// region Plugins

internal fun FluxoVersionCatalog.p(
    alias: String?,
    allowFallback: Boolean = true,
): PluginDependency? {
    if (!alias.isNullOrEmpty()) {
        var p = gradle?.findPlugin(alias)?.getOrNull()?.orNull
        if (p == null && allowFallback) {
            p = fallback?.plugins?.get(alias)
        }
        return p
    }
    return null
}

internal fun FluxoVersionCatalog.p(
    aliases: Array<out String>?,
    allowFallback: Boolean = true,
): Pair<PluginDependency, String>? {
    if (aliases != null) {
        for (alias in aliases) {
            val pluginDep = p(alias, allowFallback = allowFallback)
            if (pluginDep != null) {
                return pluginDep to alias
            }
        }
    }
    return null
}

// endregion


// region Versions

@JvmName("vIntVararg")
@LowPriorityInOverloadResolution
internal fun FluxoVersionCatalog.vInt(
    vararg aliases: String,
    force: Boolean = true,
    allowFallback: Boolean = true,
): Int? = vInt(aliases, force = force, allowFallback = allowFallback)

internal fun FluxoVersionCatalog.vInt(
    aliases: Array<out String>?,
    force: Boolean = true,
    allowFallback: Boolean = true,
): Int? {
    if (aliases != null) {
        for (alias in aliases) {
            return vInt(alias, force = force, allowFallback = false)
                ?: continue
        }
        if (allowFallback) {
            for (alias in aliases) {
                return vInt(alias, force = force, allowFallback = true)
                    ?: continue
            }
        }
    }
    return null
}

internal fun FluxoVersionCatalog.vInt(
    alias: String?,
    force: Boolean = false,
    allowFallback: Boolean = true,
): Int? {
    if (!alias.isNullOrEmpty()) {
        var v = gradle?.findVersion(alias)?.getOrNull()?.toString()
        try {
            if (v == null && allowFallback) {
                v = fallback?.versions?.get(alias)
            }
            return v?.toInt()
        } catch (e: Throwable) {
            if (force) {
                val msg = "Invalid '$alias' declared in version catalog, expected int: '$v'"
                throw GradleException(msg, e)
            }
        }
    }
    return null
}

@JvmName("vVararg")
@LowPriorityInOverloadResolution
internal fun FluxoVersionCatalog.v(
    vararg aliases: String,
    allowFallback: Boolean = true,
) = v(aliases, allowFallback = allowFallback)

internal fun FluxoVersionCatalog.v(
    aliases: Array<out String>?,
    allowFallback: Boolean = true,
): String? {
    if (aliases != null) {
        for (alias in aliases) {
            return v(alias, allowFallback = false) ?: continue
        }
        if (allowFallback) {
            for (alias in aliases) {
                return fallback?.versions?.get(alias) ?: continue
            }
        }
    }
    return null
}

internal fun FluxoVersionCatalog.v(
    alias: String?,
    allowFallback: Boolean = true,
): String? {
    if (alias.isNullOrEmpty()) {
        return null
    }
    var v = gradle?.findVersion(alias)?.getOrNull()?.toString()
    if (v.isNullOrEmpty() && allowFallback) {
        v = fallback?.versions?.get(alias)
    }
    return v
}

internal fun FluxoVersionCatalog.onVersion(
    alias: String,
    allowFallback: Boolean = true,
    body: (String) -> Unit,
): Boolean {
    val opt = gradle?.findVersion(alias)
    if (opt?.isPresent == true) {
        body(opt.get().toString())
        return true
    }
    if (allowFallback) {
        fallback?.versions?.get(alias)?.let {
            body(it)
            return true
        }
    }
    return false
}

// endregion


private inline fun <T> FluxoVersionCatalog.on(
    lookup: VersionCatalog.() -> Optional<Provider<T>>,
    noinline body: (Provider<T>) -> Unit,
): Boolean {
    val opt = gradle?.lookup()
    if (opt?.isPresent == true) {
        body(opt.get())
        return true
    }
    return false
}
