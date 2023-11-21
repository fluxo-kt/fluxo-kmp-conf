package fluxo.conf.dsl.container

import fluxo.conf.dsl.container.target.AndroidTarget
import fluxo.conf.dsl.container.target.JvmTarget
import org.gradle.api.Action
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

public interface KotlinConfigurationContainerDsl<out KPE : KotlinProjectExtension> :
    JvmTarget.Configure,
    AndroidTarget.Configure {

    /**
     * Executes the given [action] for all [KotlinTarget]s of the given [type].
     */
    public fun <T : KotlinTargetContainer<KotlinTarget>> onTarget(
        type: Class<T>,
        action: Action<in T>,
    )

    /**
     * Executes the given [action] for all [AndroidTarget]s.
     */
    public fun onAndroidTarget(action: Action<in AndroidTarget<*>>): Unit =
        onTarget(AndroidTarget::class.java, action)


    /**
     * Executes the given [action] for the Kotlin module with any target enabled.
     * [action] can apply plugins via [Container.applyPlugins] and configure common settings.
     */
    public fun common(action: Container.() -> Unit)

    /**
     * Executes the given [action] for the Kotlin module with any target enabled.
     */
    public fun kotlin(action: KPE.() -> Unit)
}
