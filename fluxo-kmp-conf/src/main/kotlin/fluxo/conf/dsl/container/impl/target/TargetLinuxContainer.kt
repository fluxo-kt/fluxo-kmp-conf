package fluxo.conf.dsl.container.impl.target

import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerHolderAware
import fluxo.conf.dsl.container.impl.KotlinTargetContainerImpl
import fluxo.conf.dsl.container.target.TargetLinux
import fluxo.conf.target.KmpTargetCode
import fluxo.conf.target.KmpTargetCode.Companion.DEPRECATED_TARGET_MSG
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget as KNT
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithHostTests as KNTHT

internal abstract class TargetLinuxContainer<T : KNT>(
    context: ContainerContext, name: String, code: KmpTargetCode,
) : KotlinTargetContainerImpl<T>(
    context, name, code, LINUX_SORT_ORDER,
), KotlinTargetContainerImpl.NonJvm.Native.Unix.Linux<T>, TargetLinux<T> {

    interface Configure : TargetLinux.Configure, ContainerHolderAware {

        override fun linuxX64(targetName: String, action: TargetLinux<KNTHT>.() -> Unit) {
            holder.configure(targetName, ::X64, action)
        }

        override fun linuxArm64(targetName: String, action: TargetLinux<KNT>.() -> Unit) {
            holder.configure(targetName, ::Arm64, action)
        }


        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun linuxArm32Hfp(targetName: String, action: TargetLinux<KNT>.() -> Unit) {
            holder.configure(targetName, ::Arm32Hfp, action)
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun linuxMips32(targetName: String, action: TargetLinux<KNT>.() -> Unit) {
            holder.configure(targetName, ::Mips32, action)
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun linuxMipsel32(targetName: String, action: TargetLinux<KNT>.() -> Unit) {
            holder.configure(targetName, ::Mipsel32, action)
        }
    }


    class X64(context: ContainerContext, targetName: String) :
        TargetLinuxContainer<KNTHT>(context, targetName, KmpTargetCode.LINUX_X64) {

        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::linuxX64)
    }

    class Arm64(context: ContainerContext, targetName: String) :
        TargetLinuxContainer<KNT>(context, targetName, KmpTargetCode.LINUX_ARM64) {

        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::linuxArm64)
    }

    @Deprecated(DEPRECATED_TARGET_MSG)
    class Arm32Hfp(context: ContainerContext, targetName: String) :
        TargetLinuxContainer<KNT>(context, targetName, KmpTargetCode.LINUX_ARM32HFP) {

        @Suppress("DEPRECATION")
        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::linuxArm32Hfp)
    }

    @Deprecated(DEPRECATED_TARGET_MSG)
    class Mips32(context: ContainerContext, targetName: String) :
        TargetLinuxContainer<KNT>(context, targetName, KmpTargetCode.LINUX_MIPS32) {

        @Suppress("DEPRECATION")
        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::linuxMips32)
    }

    @Deprecated(DEPRECATED_TARGET_MSG)
    class Mipsel32(context: ContainerContext, targetName: String) :
        TargetLinuxContainer<KNT>(context, targetName, KmpTargetCode.LINUX_MIPSEL32) {

        @Suppress("DEPRECATION")
        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::linuxMipsel32)
    }


    internal companion object {
        internal const val LINUX = "linux"
    }
}
