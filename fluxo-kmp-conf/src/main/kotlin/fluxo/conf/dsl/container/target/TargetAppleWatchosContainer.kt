package fluxo.conf.dsl.container.target

import fluxo.conf.dsl.FluxoKmpConfDsl
import fluxo.conf.dsl.container.ContainerContext
import fluxo.conf.impl.EMPTY_FUN
import fluxo.conf.impl.KOTLIN_1_8
import fluxo.conf.target.KmpTargetCode.Companion.TARGET_DEPRECTION_MSG
import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithSimulatorTests

public sealed class TargetAppleWatchosContainer<T : KotlinNativeTarget>
private constructor(
    context: ContainerContext,
    targetName: String,
) : KmpTarget.NonJvm.Native.Unix.Apple.Watchos<T>(context, targetName) {

    public sealed interface Configure : ContainerHolderAware {

        public fun watchosAll() {
            watchosArm32()
            watchosArm64()
            if (holder.kotlinPluginVersion >= KOTLIN_1_8) {
                watchosDeviceArm64()
            }
            watchosX64()
            watchosSimulatorArm64()
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
        @Deprecated(message = TARGET_DEPRECTION_MSG)
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
    ) : TargetAppleWatchosContainer<KotlinNativeTarget>(context, targetName)

    @FluxoKmpConfDsl
    public class Arm64 internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetAppleWatchosContainer<KotlinNativeTarget>(context, targetName)

    @FluxoKmpConfDsl
    public class DeviceArm64 internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetAppleWatchosContainer<KotlinNativeTarget>(context, targetName)

    @FluxoKmpConfDsl
    public class X64 internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetAppleWatchosContainer<KotlinNativeTarget>(context, targetName)

    @FluxoKmpConfDsl
    @Deprecated(message = TARGET_DEPRECTION_MSG)
    public class X86 internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetAppleWatchosContainer<KotlinNativeTarget>(context, targetName)

    @FluxoKmpConfDsl
    public class SimulatorArm64 internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetAppleWatchosContainer<KotlinNativeTargetWithSimulatorTests>(context, targetName)


    override fun KotlinMultiplatformExtension.setup() {
        @Suppress("DEPRECATION")
        val target = when (this@TargetAppleWatchosContainer) {
            is Arm32 -> watchosArm32(name, lazyTargetConf)
            is Arm64 -> watchosArm64(name, lazyTargetConf)
            is DeviceArm64 -> watchosDeviceArm64(name, lazyTargetConf)
            is SimulatorArm64 -> watchosSimulatorArm64(name, lazyTargetConf)
            is X86 -> watchosX86(name, lazyTargetConf)
            is X64 -> watchosX64(name, lazyTargetConf)
        }

        applyPlugins(target.project)

        with(sourceSets) {
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

    private companion object {
        private const val WATCHOS = "watchos"
    }
}
