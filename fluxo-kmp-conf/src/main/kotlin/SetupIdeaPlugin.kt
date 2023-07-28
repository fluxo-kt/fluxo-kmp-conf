import fluxo.conf.dsl.FluxoConfigurationExtension
import fluxo.conf.dsl.fluxoConfiguration
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.withType
import org.gradle.api.Project
import org.jetbrains.intellij.IntelliJPluginExtension
import org.jetbrains.intellij.tasks.PatchPluginXmlTask

@Suppress("LongParameterList")
public fun Project.setupIdeaPlugin(
    config: (FluxoConfigurationExtension.() -> Unit)? = null,
    group: String,
    version: String,
    sinceBuild: String,
    intellijVersion: String,
    body: (KotlinSingleTarget.() -> Unit)? = null,
): Unit = fluxoConfiguration {
    if (group.isNotEmpty()) this.group = group
    if (version.isNotEmpty()) this.version = version
    config?.invoke(this)

    configureAsIdeaPlugin {
        kotlin {
            @Suppress("UNCHECKED_CAST")
            body?.invoke(this as KotlinSingleTarget)

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
