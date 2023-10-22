package fluxo.conf.dsl.container.target

import fluxo.conf.dsl.container.KotlinTargetContainer
import fluxo.conf.impl.EMPTY_FUN
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

public interface JvmTarget : KotlinTargetContainer<KotlinJvmTarget> {

    public interface Configure {

        public fun jvm(
            targetName: String = "jvm",
            configure: JvmTarget.() -> Unit = EMPTY_FUN,
        )
    }
}
