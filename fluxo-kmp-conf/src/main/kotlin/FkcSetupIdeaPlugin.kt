@file:Suppress("LongParameterList", "KDocUnresolvedReference")
@file:JvmName("Fkc")
@file:JvmMultifileClass

import fluxo.conf.dsl.FluxoConfigurationExtension
import fluxo.conf.dsl.fluxoConfiguration
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.kotlin.INTELLIJ_PLUGIN_ID
import fluxo.log.w
import org.gradle.api.Project
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * Configures an IntelliJ IDEA Plugin module (Gradle [Project]).
 *
 * The [org.jetbrains.intellij.platform][INTELLIJ_PLUGIN_ID] plugin is applied automatically.
 * The IntelliJ Platform dependency must be added by the consumer:
 * ```kotlin
 * dependencies {
 *     intellijPlatform { intellijIdeaCommunity("2025.1") }
 * }
 * ```
 *
 * @receiver The [Project] to configure.
 *
 * @param config Configuration block for the [FluxoConfigurationExtension].
 * @param group The group name of the plugin.
 * @param version The version of the plugin.
 * @param sinceBuild The minimum IDE build this plugin supports (e.g. `"251"` for IDEA 2025.1).
 *   Configures [IntelliJPlatformExtension.PluginConfiguration.IdeaVersion.sinceBuild].
 * @param intellijVersion **Deprecated** (no-op; emits a warning at configuration time).
 *   In IntelliJ Platform Gradle Plugin v2, add the IntelliJ dependency explicitly:
 *   `dependencies { intellijPlatform { intellijIdeaCommunity(version) } }`.
 * @param kotlin Configuration block for the [KotlinJvmProjectExtension].
 * @param plugin **Deprecated.** Use the typed [extension] parameter for [IntelliJPlatformExtension]
 *   configuration. When non-null, the lambda is invoked with the extension as `this`.
 * @param extension Configuration block for the [IntelliJPlatformExtension].
 *
 * @see IntelliJPlatformExtension
 * @see IntelliJPlatformExtension.PluginConfiguration.IdeaVersion.sinceBuild
 */
@JvmName("setupIdeaPlugin")
public fun Project.fkcSetupIdeaPlugin(
    config: (FluxoConfigurationExtension.() -> Unit)? = null,
    group: String? = null,
    version: String? = null,
    sinceBuild: String? = null,
    intellijVersion: String = "",
    kotlin: (KotlinJvmProjectExtension.() -> Unit)? = null,
    plugin: (Any.() -> Unit)? = null,
    extension: (IntelliJPlatformExtension.() -> Unit)? = null,
) {
    val project = this
    project.fluxoConfiguration {
        group?.let { this.group = it }
        version?.let { this.version = it }

        config?.invoke(this)

        asIdeaPlugin {
            kotlin?.let { this.kotlin(it) }

            // Wire sinceBuild, plugin, and extension to IntelliJPlatformExtension.
            // The IJ Platform plugin is applied by configureContainers (after this action lambda
            // runs), so withPlugin defers the callback until the extension is actually registered.
            if (sinceBuild != null || plugin != null || extension != null) {
                project.pluginManager.withPlugin(INTELLIJ_PLUGIN_ID) {
                    project.configureExtension<IntelliJPlatformExtension>("intellijPlatform") {
                        sinceBuild?.let {
                            pluginConfiguration {
                                ideaVersion { this.sinceBuild.set(it) }
                            }
                        }
                        plugin?.invoke(this)
                        extension?.invoke(this)
                    }
                }
            }

            if (intellijVersion.isNotBlank()) {
                project.logger.w(
                    "fkcSetupIdeaPlugin: `intellijVersion` is deprecated in IJ Platform v2." +
                        " Configure via: dependencies { intellijPlatform {" +
                        " intellijIdeaCommunity(\"$intellijVersion\") } }",
                )
            }
        }
    }
}
