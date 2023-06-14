package fluxo.conf.dsl.container.target

import fluxo.conf.dsl.FluxoKmpConfDsl
import fluxo.conf.dsl.container.ContainerContext
import fluxo.conf.impl.EMPTY_FUN
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

public sealed class TargetAndroidNativeContainer
private constructor(
    context: ContainerContext,
    name: String,
) : KmpTarget.NonJvm.Native.Android<KotlinNativeTarget>(context, name) {

    @Suppress("TooManyFunctions")
    public sealed interface Configure : ContainerHolderAware {

        public fun androidNative64() {
            androidNativeArm64()
            androidNativeX64()
        }

        public fun androidNativeAll() {
            androidNativeArm32()
            androidNativeArm64()
            androidNativeX86()
            androidNativeX64()
        }


        public fun androidNativeArm64(
            targetName: String = "androidNativeArm64",
            action: Arm64.() -> Unit = EMPTY_FUN,
        ) {
            holder.configure(targetName, ::Arm64, action)
        }

        public fun androidNativeArm32(
            targetName: String = "androidNativeArm32",
            action: Arm32.() -> Unit = EMPTY_FUN,
        ) {
            holder.configure(targetName, ::Arm32, action)
        }

        public fun androidNativeX64(
            targetName: String = "androidNativeX64",
            action: X64.() -> Unit = EMPTY_FUN,
        ) {
            holder.configure(targetName, ::X64, action)
        }

        public fun androidNativeX86(
            targetName: String = "androidNativeX86",
            action: X86.() -> Unit = EMPTY_FUN,
        ) {
            holder.configure(targetName, ::X86, action)
        }
    }


    @FluxoKmpConfDsl
    public class Arm32 internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetAndroidNativeContainer(context, targetName)

    @FluxoKmpConfDsl
    public class Arm64 internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetAndroidNativeContainer(context, targetName)

    @FluxoKmpConfDsl
    public class X64 internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetAndroidNativeContainer(context, targetName)

    @FluxoKmpConfDsl
    public class X86 internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetAndroidNativeContainer(context, targetName)


    final override fun KotlinMultiplatformExtension.setup() {
        val target = when (this@TargetAndroidNativeContainer) {
            is Arm32 -> androidNativeArm32(name, lazyTargetConf)
            is Arm64 -> androidNativeArm64(name, lazyTargetConf)
            is X64 -> androidNativeX64(name, lazyTargetConf)
            is X86 -> androidNativeX86(name, lazyTargetConf)
        }

        applyPlugins(target.project)

        with(sourceSets) {
            getByName("${name}Main") {
                dependsOn(getByName("${ANDROID_NATIVE}Main"))
                lazySourceSetMainConf()
            }
            getByName("${name}Test") {
                dependsOn(getByName("${ANDROID_NATIVE}Test"))
                lazySourceSetTestConf()
            }
        }
    }

    final override val sortOrder: Byte = 21

    private companion object {
        private const val ANDROID_NATIVE = "androidNative"
    }
}
