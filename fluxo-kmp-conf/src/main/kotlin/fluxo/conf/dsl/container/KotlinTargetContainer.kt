package fluxo.conf.dsl.container

import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

/**
 * Lazy configuration container for [KotlinTarget].
 *
 * @see org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
 */
public interface KotlinTargetContainer<out T : KotlinTarget> : Container {

    public fun target(action: T.() -> Unit)
}
