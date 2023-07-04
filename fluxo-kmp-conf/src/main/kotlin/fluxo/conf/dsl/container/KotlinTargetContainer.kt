package fluxo.conf.dsl.container

import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

/**
 * Lazy configuration container for [KotlinTarget].
 */
public interface KotlinTargetContainer<out T : KotlinTarget> : Container {

    public fun target(action: T.() -> Unit)
}
