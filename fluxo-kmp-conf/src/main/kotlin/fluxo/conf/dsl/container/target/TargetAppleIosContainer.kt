@file:Suppress("DeprecatedCallableAddReplaceWith")

package fluxo.conf.dsl.container.target

import fluxo.conf.dsl.FluxoKmpConfDsl
import fluxo.conf.dsl.container.ContainerContext
import fluxo.conf.impl.EMPTY_FUN
import fluxo.conf.target.KmpTargetCode.Companion.DEPRECATED_TARGET_MSG
import iosCompat
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget as KNT
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithSimulatorTests

public sealed class TargetAppleIosContainer<out T : KNT>
private constructor(
    context: ContainerContext,
    targetName: String,
) : KmpTarget.NonJvm.Native.Unix.Apple.Ios<T>(context, targetName) {

    public sealed interface Configure : ContainerHolderAware {

        /**
         *
         * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts.ios
         * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts.createIntermediateSourceSet
         * @see iosCompat
         */
        public fun ios(action: TargetAppleIosContainer<KNT>.() -> Unit = EMPTY_FUN) {
            iosArm64(action = action)
            iosX64(action = action)
            iosSimulatorArm64(action = action)
        }


        @Suppress("DEPRECATION")
        @Deprecated(message = DEPRECATED_TARGET_MSG)
        public fun iosArm32(targetName: String = "iosArm32", action: Arm32.() -> Unit = EMPTY_FUN) {
            holder.configure(targetName, ::Arm32, action)
        }

        public fun iosArm64(targetName: String = "iosArm64", action: Arm64.() -> Unit = EMPTY_FUN) {
            holder.configure(targetName, ::Arm64, action)
        }

        public fun iosSimulatorArm64(
            targetName: String = "iosSimulatorArm64",
            action: SimulatorArm64.() -> Unit = EMPTY_FUN,
        ) {
            holder.configure(targetName, ::SimulatorArm64, action)
        }

        public fun iosX64(targetName: String = "iosX64", action: X64.() -> Unit = EMPTY_FUN) {
            holder.configure(targetName, ::X64, action)
        }
    }

    @FluxoKmpConfDsl
    @Deprecated(message = DEPRECATED_TARGET_MSG)
    public class Arm32 internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetAppleIosContainer<KNT>(context, targetName)

    @FluxoKmpConfDsl
    public class Arm64 internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetAppleIosContainer<KNT>(context, targetName)

    @FluxoKmpConfDsl
    public class SimulatorArm64 internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetAppleIosContainer<KotlinNativeTargetWithSimulatorTests>(context, targetName)

    @FluxoKmpConfDsl
    public class X64 internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetAppleIosContainer<KotlinNativeTargetWithSimulatorTests>(context, targetName)

    final override fun KotlinMultiplatformExtension.setup() {
        @Suppress("DEPRECATION")
        val target = when (this@TargetAppleIosContainer) {
            is Arm32 -> iosArm32(name, lazyTargetConf)
            is Arm64 -> iosArm64(name, lazyTargetConf)
            is SimulatorArm64 -> iosSimulatorArm64(name, lazyTargetConf)
            is X64 -> iosX64(name, lazyTargetConf)
        }

        applyPlugins(target.project)

        with(sourceSets) {
            getByName("${name}Main") {
                dependsOn(getByName("${IOS}Main"))
                lazySourceSetMainConf()
            }
            getByName("${name}Test") {
                dependsOn(getByName("${IOS}Test"))
                lazySourceSetTestConf()
            }
        }
    }

    final override val sortOrder: Byte = 31

    private companion object {
        private const val IOS = "ios"
    }
}
