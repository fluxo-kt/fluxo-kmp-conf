package fluxo.conf.dsl.container.impl.target

import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerHolderAware
import fluxo.conf.dsl.container.impl.KotlinTargetContainerImpl
import fluxo.conf.dsl.container.target.TargetAndroidNative
import fluxo.conf.target.KmpTargetCode
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

internal abstract class TargetAndroidNativeContainer(
    context: ContainerContext, name: String, code: KmpTargetCode,
) : KotlinTargetContainerImpl<KotlinNativeTarget>(
    context, name, code, ANDROID_NATIVE_SORT_ORDER,
), KotlinTargetContainerImpl.NonJvm.Native.AndroidNative, TargetAndroidNative {

    interface Configure : TargetAndroidNative.Configure, ContainerHolderAware {

        override fun androidNativeArm64(
            targetName: String,
            action: TargetAndroidNative.() -> Unit,
        ) {
            holder.configure(targetName, ::Arm64, action)
        }

        override fun androidNativeArm32(
            targetName: String,
            action: TargetAndroidNative.() -> Unit,
        ) {
            holder.configure(targetName, ::Arm32, action)
        }

        override fun androidNativeX64(
            targetName: String,
            action: TargetAndroidNative.() -> Unit,
        ) {
            holder.configure(targetName, ::X64, action)
        }

        override fun androidNativeX86(
            targetName: String,
            action: TargetAndroidNative.() -> Unit,
        ) {
            holder.configure(targetName, ::X86, action)
        }
    }


    class Arm32(context: ContainerContext, targetName: String) :
        TargetAndroidNativeContainer(context, targetName, KmpTargetCode.ANDROID_ARM32) {

        override fun KotlinMultiplatformExtension.createTarget() =
            createTarget(::androidNativeArm32)
    }

    class Arm64(context: ContainerContext, targetName: String) :
        TargetAndroidNativeContainer(context, targetName, KmpTargetCode.ANDROID_ARM64) {

        override fun KotlinMultiplatformExtension.createTarget() =
            createTarget(::androidNativeArm64)
    }

    class X64(context: ContainerContext, targetName: String) :
        TargetAndroidNativeContainer(context, targetName, KmpTargetCode.ANDROID_X64) {

        override fun KotlinMultiplatformExtension.createTarget() =
            createTarget(::androidNativeX64)
    }

    class X86(context: ContainerContext, targetName: String) :
        TargetAndroidNativeContainer(context, targetName, KmpTargetCode.ANDROID_X86) {

        override fun KotlinMultiplatformExtension.createTarget() =
            createTarget(::androidNativeX86)
    }


    internal companion object {
        internal const val ANDROID_NATIVE = "androidNative"
    }
}
