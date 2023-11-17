import fluxo.conf.dsl.FluxoConfigurationExtension
import fluxo.conf.dsl.fluxoConfiguration
import fluxo.conf.impl.kotlin.hasKsp
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.AbstractKotlinTarget

public typealias KotlinSingleTarget = KotlinSingleTargetExtension<out AbstractKotlinTarget>

public fun Project.setupKotlin(
    config: (FluxoConfigurationExtension.() -> Unit)? = null,
    setupKsp: Boolean? = if (hasKsp) true else null,
    optIns: List<String>? = null,
    body: (KotlinSingleTarget.() -> Unit)? = null,
): Unit = fluxoConfiguration {
    if (setupKsp != null) this.setupKsp = setupKsp
    if (!optIns.isNullOrEmpty()) this.optIns += optIns
    config?.invoke(this)

    configureAsKotlinJvm {
        if (body != null) {
            @Suppress("UNCHECKED_CAST")
            kotlin { body(this as KotlinSingleTarget) }
        }
    }
}
