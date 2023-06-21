@file:Suppress("DeprecatedCallableAddReplaceWith", "DEPRECATION")

package fluxo.conf.dsl.container.target

import fluxo.conf.dsl.FluxoKmpConfDsl
import fluxo.conf.dsl.container.ContainerContext
import fluxo.conf.impl.EMPTY_FUN
import fluxo.conf.target.KmpTargetCode.Companion.DEPRECATED_TARGET_MSG
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithHostTests

public sealed class TargetMingwContainer<T : KotlinNativeTarget>
private constructor(
    context: ContainerContext,
    targetName: String,
) : KmpTarget.NonJvm.Native.Mingw<T>(context, targetName) {

    public sealed interface Configure : ContainerHolderAware {

        public fun mingwAll() {
            mingwX64()
        }

        public fun mingwX64(
            targetName: String = "mingwX64",
            action: X64.() -> Unit = EMPTY_FUN,
        ) {
            holder.configure(targetName, ::X64, action)
        }

        @Deprecated(message = DEPRECATED_TARGET_MSG)
        public fun mingwX86(targetName: String = "mingwX86", action: X86.() -> Unit = EMPTY_FUN) {
            holder.configure(targetName, ::X86, action)
        }
    }

    @FluxoKmpConfDsl
    public class X64 internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetMingwContainer<KotlinNativeTargetWithHostTests>(context, targetName)

    @FluxoKmpConfDsl
    @Deprecated(message = DEPRECATED_TARGET_MSG)
    public class X86 internal constructor(
        context: ContainerContext,
        targetName: String,
    ) : TargetMingwContainer<KotlinNativeTarget>(context, targetName)


    override fun KotlinMultiplatformExtension.setup() {
        val target = when (this@TargetMingwContainer) {
            is X64 -> mingwX64(name, lazyTargetConf)
            is X86 -> mingwX86(name, lazyTargetConf)
        }

        applyPlugins(target.project)

        with(sourceSets) {
            getByName("${name}Main") {
                dependsOn(getByName("${MINGW}Main"))
                lazySourceSetMainConf()
            }
            getByName("${name}Test") {
                dependsOn(getByName("${MINGW}Test"))
                lazySourceSetTestConf()
            }
        }
    }

    final override val sortOrder: Byte = 51

    private companion object {
        private const val MINGW = "mingw"
    }
}
