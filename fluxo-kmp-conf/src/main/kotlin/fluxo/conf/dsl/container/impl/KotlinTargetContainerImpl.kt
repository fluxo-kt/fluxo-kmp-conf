package fluxo.conf.dsl.container.impl

import fluxo.conf.dsl.container.KotlinTargetContainer
import fluxo.conf.impl.set
import fluxo.conf.target.KmpTargetCode
import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.AbstractKotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithHostTests
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl

internal abstract class KotlinTargetContainerImpl<T : KotlinTarget>(
    context: ContainerContext,
    private val name: String,
    val code: KmpTargetCode,
    final override val sortOrder: Byte,
) : ContainerImpl(context), KotlinTargetContainer<T>, ContainerKotlinMultiplatformAware {

    final override fun getName(): String = name


    private val lazyTarget = context.objects.set<T.() -> Unit>()

    protected val lazyTargetConf: T.() -> Unit = { lazyTarget.all { this() } }

    override fun target(action: T.() -> Unit) {
        lazyTarget.add(action)
    }


    protected abstract fun KotlinMultiplatformExtension.createTarget(): T

    protected fun createTarget(
        factory: (name: String, configure: T.() -> Unit) -> T,
        configure: T.() -> Unit = lazyTargetConf,
    ): T = factory(name, configure)


    override fun setup(k: KotlinMultiplatformExtension) {
        k.createTarget()
    }


    interface CommonJvm<T : AbstractKotlinTarget> : KotlinTargetContainer<T> {

        companion object {
            const val COMMON_JVM = "commonJvm"
        }

        @Deprecated("Replace with full-fledged context-based target configuration")
        var kotlinJvmTarget: JavaVersion? get() = null; set(value) {}

        @Deprecated("Replace with full-fledged context-based target configuration")
        var compileSourceCompatibility: JavaVersion? get() = null; set(value) {}

        @Deprecated("Replace with full-fledged context-based target configuration")
        var compileTargetCompatibility: JavaVersion? get() = null; set(value) {}
    }


    interface NonJvm<T : KotlinTarget> : KotlinTargetContainer<T> {

        companion object {
            const val NON_JVM = "nonJvm"
        }

        interface CommonJs<T : KotlinJsTargetDsl> : NonJvm<T> {

            companion object {
                const val COMMON_JS = "commonJs"
            }
        }

        @Suppress("MemberNameEqualsClassName")
        interface Native<T : KotlinNativeTarget> : NonJvm<T> {

            companion object {
                const val NATIVE = "native"
            }

            interface AndroidNative : Native<KotlinNativeTarget>

            interface Unix<T : KotlinNativeTarget> : Native<T> {

                companion object {
                    const val UNIX = "unix"
                }

                interface Apple<T : KotlinNativeTarget> : Unix<T> {

                    companion object {
                        const val APPLE = "apple"
                    }

                    interface Ios<T : KotlinNativeTarget> : Apple<T>

                    interface Macos : Apple<KotlinNativeTargetWithHostTests>

                    interface Tvos<T : KotlinNativeTarget> : Apple<T>

                    interface Watchos<T : KotlinNativeTarget> : Apple<T>
                }

                interface Linux<T : KotlinNativeTarget> : Unix<T>
            }

            interface Mingw<T : KotlinNativeTarget> : Native<T>

            interface WasmNative : Native<KotlinNativeTarget>
        }
    }
}
