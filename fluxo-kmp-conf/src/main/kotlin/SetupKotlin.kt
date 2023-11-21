import fluxo.conf.dsl.FluxoConfigurationExtension
import fluxo.conf.dsl.fluxoConfiguration
import fluxo.conf.impl.kotlin.hasKsp
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

public fun Project.setupKotlin(
    config: (FluxoConfigurationExtension.() -> Unit)? = null,
    setupKsp: Boolean? = if (hasKsp) true else null,
    optIns: List<String>? = null,
    body: (KotlinJvmProjectExtension.() -> Unit)? = null,
): Unit = fluxoConfiguration {
    if (setupKsp != null) this.setupKsp = setupKsp
    if (!optIns.isNullOrEmpty()) this.optIns += optIns
    config?.invoke(this)

    asJvm {
        body?.let { kotlin(action = it) }
    }
}
