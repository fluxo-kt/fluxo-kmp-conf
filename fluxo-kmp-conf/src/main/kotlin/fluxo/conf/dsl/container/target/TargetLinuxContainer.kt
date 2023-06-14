package fluxo.conf.dsl.container.target

import fluxo.conf.dsl.FluxoKmpConfDsl
import fluxo.conf.dsl.container.ContainerContext
import fluxo.conf.impl.EMPTY_FUN
import fluxo.conf.target.KmpTargetCode.Companion.TARGET_DEPRECTION_MSG
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithHostTests

public sealed class TargetLinuxContainer<T : KotlinNativeTarget>
private constructor(
    context: ContainerContext,
    targetName: String,
) : KmpTarget.NonJvm.Native.Unix.Linux<T>(context, targetName) {

    public sealed interface Configure : ContainerHolderAware {

        public fun linuxAll() {
            linuxX64()
            linuxArm64()
        }


        public fun linuxX64(targetName: String = "linuxX64", action: X64.() -> Unit = EMPTY_FUN) {
            holder.configure(targetName, ::X64, action)
        }

        public fun linuxArm64(
            targetName: String = "linuxArm64",
            action: Arm64.() -> Unit = EMPTY_FUN,
        ) {
            holder.configure(targetName, ::Arm64, action)
        }


        @Suppress("DEPRECATION", "DeprecatedCallableAddReplaceWith")
        @Deprecated(message = TARGET_DEPRECTION_MSG)
        public fun linuxArm32Hfp(
            targetName: String = "linuxArm32Hfp",
            action: Arm32Hfp.() -> Unit = EMPTY_FUN,
        ) {
            holder.configure(targetName, ::Arm32Hfp, action)
        }

        @Suppress("DEPRECATION", "DeprecatedCallableAddReplaceWith")
        @Deprecated(message = TARGET_DEPRECTION_MSG)
        public fun linuxMips32(
            targetName: String = "linuxMips32",
            action: Mips32.() -> Unit = EMPTY_FUN,
        ) {
            holder.configure(targetName, ::Mips32, action)
        }

        @Suppress("DEPRECATION", "DeprecatedCallableAddReplaceWith")
        @Deprecated(message = TARGET_DEPRECTION_MSG)
        public fun linuxMipsel32(
            targetName: String = "linuxMipsel32",
            action: Mipsel32.() -> Unit = EMPTY_FUN,
        ) {
            holder.configure(targetName, ::Mipsel32, action)
        }
    }


    @FluxoKmpConfDsl
    public class X64 internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetLinuxContainer<KotlinNativeTargetWithHostTests>(context, targetName)

    @FluxoKmpConfDsl
    public class Arm64 internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetLinuxContainer<KotlinNativeTarget>(context, targetName)

    @FluxoKmpConfDsl
    @Deprecated(message = TARGET_DEPRECTION_MSG)
    public class Arm32Hfp internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetLinuxContainer<KotlinNativeTarget>(context, targetName)

    @FluxoKmpConfDsl
    @Deprecated(message = TARGET_DEPRECTION_MSG)
    public class Mips32 internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetLinuxContainer<KotlinNativeTarget>(context, targetName)

    @FluxoKmpConfDsl
    @Deprecated(message = TARGET_DEPRECTION_MSG)
    public class Mipsel32 internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetLinuxContainer<KotlinNativeTarget>(context, targetName)


    final override fun KotlinMultiplatformExtension.setup() {
        @Suppress("DEPRECATION")
        val target = when (this@TargetLinuxContainer) {
            is X64 -> linuxX64(name, lazyTargetConf)
            is Arm64 -> linuxArm64(name, lazyTargetConf)
            is Arm32Hfp -> linuxArm32Hfp(name, lazyTargetConf)
            is Mips32 -> linuxMips32(name, lazyTargetConf)
            is Mipsel32 -> linuxMipsel32(name, lazyTargetConf)
        }

        applyPlugins(target.project)

        with(sourceSets) {
            getByName("${name}Main") {
                dependsOn(getByName("${LINUX}Main"))
                lazySourceSetMainConf()
            }
            getByName("${name}Test") {
                dependsOn(getByName("${LINUX}Test"))
                lazySourceSetTestConf()
            }
        }
    }

    final override val sortOrder: Byte = 41

    private companion object {
        private const val LINUX = "linux"
    }
}
