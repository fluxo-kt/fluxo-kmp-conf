import fluxo.conf.dsl.FluxoConfigurationExtension
import fluxo.conf.dsl.fluxoConfiguration
import fluxo.conf.impl.kotlin.hasKsp
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

// TODO: Support JVM application with `application` plugin

public fun Project.setupKotlin(
    setupKsp: Boolean? = if (hasKsp) true else null,
    optIns: List<String>? = null,
    kotlin: (KotlinJvmProjectExtension.() -> Unit)? = null,
    config: (FluxoConfigurationExtension.() -> Unit)? = null,
): Unit = fluxoConfiguration {
    if (setupKsp != null) {
        this.setupKsp = setupKsp
    }
    if (!optIns.isNullOrEmpty()) {
        this.optIns += optIns
    }
    config?.invoke(this)

    asJvm {
        kotlin?.let { kotlin(action = it) }
    }
}
