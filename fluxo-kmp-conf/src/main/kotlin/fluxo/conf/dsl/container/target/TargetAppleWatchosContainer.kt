package fluxo.conf.dsl.container.target

import fluxo.conf.dsl.FluxoKmpConfDsl
import fluxo.conf.dsl.container.ContainerContext
import fluxo.conf.impl.EMPTY_FUN
import fluxo.conf.impl.KOTLIN_1_8
import fluxo.conf.target.KmpTargetCode.Companion.DEPRECATED_TARGET_MSG
import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget as KNT
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithSimulatorTests
import watchosCompat

public sealed class TargetAppleWatchosContainer<out T : KNT>
private constructor(
    context: ContainerContext,
    targetName: String,
) : KmpTarget.NonJvm.Native.Unix.Apple.Watchos<T>(context, targetName) {

    public sealed interface Configure : ContainerHolderAware {

        /**
         *
         * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts.watchos
         * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts.createIntermediateSourceSet
         * @see watchosCompat
         */
        public fun watchos(action: TargetAppleWatchosContainer<KNT>.() -> Unit = EMPTY_FUN) {
            watchosArm32(action = action)
            watchosArm64(action = action)
            if (holder.kotlinPluginVersion >= KOTLIN_1_8) {
                watchosDeviceArm64(action = action)
            }
            watchosX64(action = action)
            watchosSimulatorArm64(action = action)
        }


        public fun watchosArm32(
            targetName: String = "watchosArm32",
            action: Arm32.() -> Unit = EMPTY_FUN,
        ) {
            holder.configure(targetName, ::Arm32, action)
        }

        public fun watchosArm64(
            targetName: String = "watchosArm64",
            action: Arm64.() -> Unit = EMPTY_FUN,
        ) {
            holder.configure(targetName, ::Arm64, action)
        }

        //@SinceKotlin("1.8.0")
        //@Suppress("NEWER_VERSION_IN_SINCE_KOTLIN")
        public fun watchosDeviceArm64(
            targetName: String = "watchosDeviceArm64",
            action: DeviceArm64.() -> Unit = EMPTY_FUN,
        ) {
            if (holder.kotlinPluginVersion < KOTLIN_1_8) {
                throw GradleException("watchosDeviceArm64 requires Kotlin 1.8 or greater")
            }
            holder.configure(targetName, ::DeviceArm64, action)
        }

        public fun watchosX64(
            targetName: String = "watchosX64",
            action: X64.() -> Unit = EMPTY_FUN,
        ) {
            holder.configure(targetName, ::X64, action)
        }

        @Suppress("DEPRECATION", "DeprecatedCallableAddReplaceWith")
        @Deprecated(message = DEPRECATED_TARGET_MSG)
        public fun watchosX86(
            targetName: String = "watchosX86",
            action: X86.() -> Unit = EMPTY_FUN,
        ) {
            holder.configure(targetName, ::X86, action)
        }

        public fun watchosSimulatorArm64(
            targetName: String = "watchosSimulatorArm64",
            action: SimulatorArm64.() -> Unit = EMPTY_FUN,
        ) {
            holder.configure(targetName, ::SimulatorArm64, action)
        }
    }


    @FluxoKmpConfDsl
    public class Arm32 internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetAppleWatchosContainer<KNT>(context, targetName)

    @FluxoKmpConfDsl
    public class Arm64 internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetAppleWatchosContainer<KNT>(context, targetName)

    @FluxoKmpConfDsl
    public class DeviceArm64 internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetAppleWatchosContainer<KNT>(context, targetName)

    @FluxoKmpConfDsl
    public class X64 internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetAppleWatchosContainer<KNT>(context, targetName)

    @FluxoKmpConfDsl
    @Deprecated(message = DEPRECATED_TARGET_MSG)
    public class X86 internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetAppleWatchosContainer<KNT>(context, targetName)

    @FluxoKmpConfDsl
    public class SimulatorArm64 internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetAppleWatchosContainer<KotlinNativeTargetWithSimulatorTests>(context, targetName)


    override fun setup(k: KotlinMultiplatformExtension) {
        @Suppress("DEPRECATION")
        val target = when (this) {
            is Arm32 -> k.watchosArm32(name, lazyTargetConf)
            is Arm64 -> k.watchosArm64(name, lazyTargetConf)
            is DeviceArm64 -> k.watchosDeviceArm64(name, lazyTargetConf)
            is SimulatorArm64 -> k.watchosSimulatorArm64(name, lazyTargetConf)
            is X86 -> k.watchosX86(name, lazyTargetConf)
            is X64 -> k.watchosX64(name, lazyTargetConf)
        }

        applyPlugins(target.project)

        with(k.sourceSets) {
            getByName("${name}Main") {
                dependsOn(getByName("${WATCHOS}Main"))
                lazySourceSetMainConf()
            }
            getByName("${name}Test") {
                dependsOn(getByName("${WATCHOS}Test"))
                lazySourceSetTestConf()
            }
        }
    }

    final override val sortOrder: Byte = 34

    internal companion object {
        internal const val WATCHOS = "watchos"
    }
}
