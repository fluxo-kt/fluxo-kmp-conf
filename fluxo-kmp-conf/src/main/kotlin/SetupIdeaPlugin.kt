import fluxo.conf.dsl.FluxoConfigurationExtension
import fluxo.conf.dsl.fluxoConfiguration
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.withType
import org.gradle.api.Project
import org.jetbrains.intellij.IntelliJPluginExtension
import org.jetbrains.intellij.tasks.PatchPluginXmlTask
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

@Suppress("LongParameterList")
public fun Project.setupIdeaPlugin(
    config: (FluxoConfigurationExtension.() -> Unit)? = null,
    group: String? = null,
    version: String? = null,
    sinceBuild: String,
    intellijVersion: String,
    body: (KotlinJvmProjectExtension.() -> Unit)? = null,
): Unit = fluxoConfiguration {
    if (!group.isNullOrEmpty()) this.group = group
    if (!version.isNullOrEmpty()) this.version = version
    config?.invoke(this)

    asIdeaPlugin {
        kotlin {
            body?.let { kotlin(action = it) }

            tasks.withType<PatchPluginXmlTask> {
                this.sinceBuild.set(sinceBuild)
            }

            configureExtension<IntelliJPluginExtension> {
                this.version.set(intellijVersion)
                this.updateSinceUntilBuild.set(false)
            }
        }
    }
}
