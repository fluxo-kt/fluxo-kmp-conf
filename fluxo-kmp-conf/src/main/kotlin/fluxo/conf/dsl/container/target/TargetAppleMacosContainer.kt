package fluxo.conf.dsl.container.target

import fluxo.conf.dsl.FluxoKmpConfDsl
import fluxo.conf.dsl.container.ContainerContext
import fluxo.conf.impl.EMPTY_FUN
import macosCompat
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

public sealed class TargetAppleMacosContainer
private constructor(
    context: ContainerContext,
    targetName: String,
) : KmpTarget.NonJvm.Native.Unix.Apple.Macos(context, targetName) {

    public sealed interface Configure : ContainerHolderAware {

        /**
         *
         * @see macosCompat
         */
        public fun macos(action: TargetAppleMacosContainer.() -> Unit = EMPTY_FUN) {
            macosArm64(action = action)
            macosX64(action = action)
        }


        public fun macosArm64(
            targetName: String = "macosArm64",
            action: Arm64.() -> Unit = EMPTY_FUN,
        ) {
            holder.configure(targetName, ::Arm64, action)
        }

        public fun macosX64(
            targetName: String = "macosX64",
            action: X64.() -> Unit = EMPTY_FUN,
        ) {
            holder.configure(targetName, ::X64, action)
        }
    }


    @FluxoKmpConfDsl
    public class Arm64 internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetAppleMacosContainer(context, targetName)

    @FluxoKmpConfDsl
    public class X64 internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetAppleMacosContainer(context, targetName)


    final override fun setup(k: KotlinMultiplatformExtension) {
        val target = when (this) {
            is Arm64 -> k.macosArm64(name, lazyTargetConf)
            is X64 -> k.macosX64(name, lazyTargetConf)
        }

        applyPlugins(target.project)

        with(k.sourceSets) {
            getByName("${name}Main") {
                dependsOn(getByName("${MACOS}Main"))
                lazySourceSetMainConf()
            }
            getByName("${name}Test") {
                dependsOn(getByName("${MACOS}Test"))
                lazySourceSetTestConf()
            }
        }
    }

    final override val sortOrder: Byte = 32

    internal companion object {
        internal const val MACOS = "macos"
    }
}
