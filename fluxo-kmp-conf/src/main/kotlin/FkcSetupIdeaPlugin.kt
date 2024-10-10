@file:Suppress("LongParameterList", "KDocUnresolvedReference")
@file:JvmName("Fkc")
@file:JvmMultifileClass

import fluxo.conf.dsl.FluxoConfigurationExtension
import fluxo.conf.dsl.fluxoConfiguration
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
// import org.jetbrains.intellij.IntelliJPluginExtension
// import org.jetbrains.intellij.tasks.PatchPluginXmlTask

/**
 * Configures an IntelliJ IDEA Plugin module (Gradle [Project]).
 *
 * @receiver The [Project] to configure.
 *
 * @param config Configuration block for the [FluxoConfigurationExtension].
 *
 * @param group The group name of the plugin.
 * @param version The version of the plugin.
 * @param sinceBuild The 'since build' of the plugin.
 * @param intellijVersion The IntelliJ version of the plugin.
 *
 * @param kotlin Configuration block for the [KotlinJvmProjectExtension].
 * @param plugin Configuration block for the [IntelliJPluginExtension].
 *
 * @see org.jetbrains.intellij.IntelliJPluginExtension
 * @see org.jetbrains.intellij.IntelliJPluginExtension.version
 * @see org.jetbrains.intellij.IntelliJPluginExtension.updateSinceUntilBuild
 * @see org.jetbrains.intellij.tasks.PatchPluginXmlTask.sinceBuild
 */
@Suppress("UNUSED_PARAMETER")
@JvmName("setupIdeaPlugin")
public fun Project.fkcSetupIdeaPlugin(
    config: (FluxoConfigurationExtension.() -> Unit)? = null,
    group: String? = null,
    version: String? = null,
    sinceBuild: String? = null,
    intellijVersion: String,
    kotlin: (KotlinJvmProjectExtension.() -> Unit)? = null,
    plugin: (Any.() -> Unit)? = null, // IntelliJPluginExtension
) {
    val project = this
    project.fluxoConfiguration {
        group?.let { this.group = it }
        version?.let { this.version = it }

        config?.invoke(this)

        asIdeaPlugin {
            kotlin?.let { this.kotlin(it) }

//            if (sinceBuild != null) {
//                project.tasks.withType<PatchPluginXmlTask> {
//                    this.sinceBuild.set(sinceBuild)
//                }
//            }

//            project.configureExtension<IntelliJPluginExtension> {
//                this.version.set(intellijVersion)
//                if (sinceBuild != null) {
//                    updateSinceUntilBuild.set(false)
//                }
//                plugin?.invoke(this)
//            }
        }
    }
}
