package fluxo.conf.dsl.container.impl.target

import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerHolderAware
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl
import fluxo.conf.dsl.container.target.AndroidNativeTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

internal abstract class TargetAndroidNativeContainer(
    context: ContainerContext, name: String,
) : KmpTargetContainerImpl<KotlinNativeTarget>(context, name, ANDROID_NATIVE_SORT_ORDER),
    KmpTargetContainerImpl.NonJvm.Native.AndroidNative, AndroidNativeTarget {

    interface Configure : AndroidNativeTarget.Configure, ContainerHolderAware {

        override fun androidNativeArm64(
            targetName: String,
            configure: AndroidNativeTarget.() -> Unit,
        ) {
            holder.configure(targetName, ::Arm64, KmpTargetCode.ANDROID_ARM64, configure)
        }

        override fun androidNativeArm32(
            targetName: String,
            configure: AndroidNativeTarget.() -> Unit,
        ) {
            holder.configure(targetName, ::Arm32, KmpTargetCode.ANDROID_ARM32, configure)
        }

        override fun androidNativeX64(
            targetName: String,
            configure: AndroidNativeTarget.() -> Unit,
        ) {
            holder.configure(targetName, ::X64, KmpTargetCode.ANDROID_X64, configure)
        }

        override fun androidNativeX86(
            targetName: String,
            configure: AndroidNativeTarget.() -> Unit,
        ) {
            holder.configure(targetName, ::X86, KmpTargetCode.ANDROID_X86, configure)
        }
    }


    class Arm32(context: ContainerContext, targetName: String) :
        TargetAndroidNativeContainer(context, targetName) {

        override fun KotlinMultiplatformExtension.createTarget() =
            createTarget(::androidNativeArm32)
    }

    class Arm64(context: ContainerContext, targetName: String) :
        TargetAndroidNativeContainer(context, targetName) {

        override fun KotlinMultiplatformExtension.createTarget() =
            createTarget(::androidNativeArm64)
    }

    class X64(context: ContainerContext, targetName: String) :
        TargetAndroidNativeContainer(context, targetName) {

        override fun KotlinMultiplatformExtension.createTarget() =
            createTarget(::androidNativeX64)
    }

    class X86(context: ContainerContext, targetName: String) :
        TargetAndroidNativeContainer(context, targetName) {

        override fun KotlinMultiplatformExtension.createTarget() =
            createTarget(::androidNativeX86)
    }
}
