package fluxo.conf.impl.kotlin

import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.log.logDecision

/**
 * Sets the Kotlin language version of a Gradle-plugin module that sets none.
 *
 * A Gradle plugin is loaded by the Gradle that runs it, whose embedded Kotlin reads class metadata
 * only up to its own version: a plugin built at the consumer's newer Kotlin default (Kotlin 2.4
 * on Gradle 9.0, which embeds 2.2) cannot load even on the Gradle that built it. Gradle's own
 * `kotlin-dsl` plugin applies the same rule. `KotlinVersion.CURRENT` is Gradle's embedded stdlib
 * here, as this plugin ships none. Capped by the consumer's Kotlin, which cannot compile a newer
 * language version than its own. The API version follows the language version when unset.
 */
internal fun FluxoConfigurationExtensionImpl.deriveGradlePluginKotlinVersion() {
    if (kotlinLangVersion != null) return
    val embedded = KotlinVersion.CURRENT
    val kotlin = ctx.kotlinPluginVersion
    val version = minOf(
        KotlinVersion(embedded.major, embedded.minor),
        KotlinVersion(kotlin.major, kotlin.minor),
    ).let { "${it.major}.${it.minor}" }
    kotlinLangVersion = version
    ctx.logDecision(
        project,
        setting = "kotlinLangVersion",
        value = version,
        reason = "Gradle plugin: this Gradle loads plugins with its embedded Kotlin $embedded, " +
            "so the plugin loads here and on newer Gradle",
        howToChange = "to support an older Gradle, set kotlinLangVersion to its embedded Kotlin",
    )
}
