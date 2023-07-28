import fluxo.conf.dsl.container.KmpConfigurationContainerDsl
import fluxo.conf.dsl.container.KotlinTargetContainer
import org.gradle.api.Action
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

public typealias KTC = KotlinTargetContainer<KotlinTarget>

public inline fun <reified T : KTC> KmpConfigurationContainerDsl.onTarget(
    action: Action<in T>,
): Unit = onTarget(T::class.java, action)
