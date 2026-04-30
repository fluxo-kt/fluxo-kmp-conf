package fluxo.conf.dsl.container.impl

import fluxo.conf.impl.set
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithHostTests
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmTargetDsl

internal abstract class KmpTargetContainerImpl<T : KotlinTarget>(
    context: ContainerContext,
    private val name: String,
    final override val sortOrder: Byte,
) : ContainerImpl(context), KmpTargetContainer<T> {

    final override fun getName(): String = name

    private val lazyTarget = context.objects.set<T.() -> Unit>()

    internal val lazyTargetConf: T.() -> Unit = { lazyTarget.configureEach { this() } }

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


    interface CommonJvm<T : KotlinTarget> : KmpTargetContainer<T> {

        companion object {
            const val COMMON_JVM = "commonJvm"
            const val ANDROID = "android"
        }
    }


    interface NonJvm<T : KotlinTarget> : KmpTargetContainer<T> {

        companion object {
            const val NON_JVM = "nonJvm"
        }


        interface CommonJs<T : KotlinTarget> : NonJvm<T> {

            companion object {
                const val COMMON_JS = "commonJs"
            }


            interface CommonWasm<T : KotlinWasmTargetDsl> : CommonJs<T> {

                companion object {
                    const val COMMON_WASM = "commonWasm"
                }
            }
        }

        @Suppress("MemberNameEqualsClassName")
        interface Native<T : KotlinNativeTarget> : NonJvm<T> {

            companion object {
                const val NATIVE = "native"
            }

            interface Nix<T : KotlinNativeTarget> : Native<T> {

                companion object {
                    const val NIX = "nix"
                }

                interface Apple<T : KotlinNativeTarget> : Nix<T> {

                    companion object {
                        const val APPLE = "apple"
                    }

                    interface Ios<T : KotlinNativeTarget> : Apple<T> {
                        companion object {
                            const val IOS = "ios"
                        }
                    }

                    interface Macos : Apple<KotlinNativeTargetWithHostTests> {
                        companion object {
                            const val MACOS = "macos"
                        }
                    }

                    interface Tvos<T : KotlinNativeTarget> : Apple<T> {
                        companion object {
                            const val TVOS = "tvos"
                        }
                    }

                    interface Watchos<T : KotlinNativeTarget> : Apple<T> {
                        companion object {
                            const val WATCHOS = "watchos"
                        }
                    }
                }

                interface Linux<T : KotlinNativeTarget> : Nix<T> {
                    companion object {
                        const val LINUX = "linux"
                    }
                }

                interface AndroidNative : Nix<KotlinNativeTarget> {
                    companion object {
                        const val ANDROID_NATIVE = "androidNative"
                    }
                }
            }

            interface Mingw<T : KotlinNativeTarget> : Native<T> {
                companion object {
                    const val MINGW = "mingw"
                }
            }
        }
    }
}
