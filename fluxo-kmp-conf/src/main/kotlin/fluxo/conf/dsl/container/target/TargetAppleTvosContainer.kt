package fluxo.conf.dsl.container.target

import fluxo.conf.dsl.FluxoKmpConfDsl
import fluxo.conf.dsl.container.ContainerContext
import fluxo.conf.impl.EMPTY_FUN
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithSimulatorTests

public sealed class TargetAppleTvosContainer<T : KotlinNativeTarget>
private constructor(
    context: ContainerContext,
    targetName: String,
) : KmpTarget.NonJvm.Native.Unix.Apple.Tvos<T>(context, targetName) {

    public sealed interface Configure : ContainerHolderAware {

        /**
         *
         * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts.tvos
         * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts.createIntermediateSourceSet
         * @see tvosCompat
         */
        public fun tvosAll() {
            tvosArm64()
            tvosSimulatorArm64()
            tvosX64()
        }


        public fun tvosArm64(
            targetName: String = "tvosArm64",
            action: Arm64.() -> Unit = EMPTY_FUN,
        ) {
            holder.configure(targetName, ::Arm64, action)
        }

        public fun tvosSimulatorArm64(
            targetName: String = "tvosSimulatorArm64",
            action: SimulatorArm64.() -> Unit = EMPTY_FUN,
        ) {
            holder.configure(targetName, ::SimulatorArm64, action)
        }

        public fun tvosX64(
            targetName: String = "tvosX64",
            action: X64.() -> Unit = EMPTY_FUN,
        ) {
            holder.configure(targetName, ::X64, action)
        }
    }


    @FluxoKmpConfDsl
    public class Arm64 internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetAppleTvosContainer<KotlinNativeTarget>(context, targetName)

    @FluxoKmpConfDsl
    public class SimulatorArm64 internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetAppleTvosContainer<KotlinNativeTargetWithSimulatorTests>(context, targetName)

    @FluxoKmpConfDsl
    public class X64 internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetAppleTvosContainer<KotlinNativeTargetWithSimulatorTests>(context, targetName)


    override fun KotlinMultiplatformExtension.setup() {
        val target = when (this@TargetAppleTvosContainer) {
            is Arm64 -> tvosArm64(name, lazyTargetConf)
            is SimulatorArm64 -> tvosSimulatorArm64(name, lazyTargetConf)
            is X64 -> tvosX64(name, lazyTargetConf)
        }

        applyPlugins(target.project)

        with(sourceSets) {
            getByName("${name}Main") {
                dependsOn(getByName("${TVOS}Main"))
                lazySourceSetMainConf()
            }
            getByName("${name}Test") {
                dependsOn(getByName("${TVOS}Test"))
                lazySourceSetTestConf()
            }
        }
    }


    final override val sortOrder: Byte = 33

    private companion object {
        private const val TVOS = "tvos"
    }
}
