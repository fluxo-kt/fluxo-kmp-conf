package fluxo.conf.dsl.container.impl

import bundleFor
import commonApple
import commonIos
import commonJs
import commonJvm
import commonLinux
import commonMacos
import commonMingw
import commonNative
import commonNonJvm
import commonTvos
import commonUnix
import commonWasm
import commonWasmNative
import commonWatchos
import dependsOn
import fluxo.conf.impl.set
import fluxo.conf.kmp.SourceSetBundle
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.AbstractKotlinTarget
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

    internal val lazyTargetConf: T.() -> Unit = { lazyTarget.all { this() } }

    override fun target(action: T.() -> Unit) {
        lazyTarget.add(action)
    }


    protected abstract fun KotlinMultiplatformExtension.createTarget(): T

    protected fun createTarget(
        factory: (name: String, configure: T.() -> Unit) -> T,
        configure: T.() -> Unit = lazyTargetConf,
    ): T = factory(name, configure)


    override fun setup(k: KotlinMultiplatformExtension) {
        val target = k.createTarget()
        setupParentSourceSet(k, k.bundleFor(target))
    }


    interface CommonJvm<T : AbstractKotlinTarget> : KmpTargetContainer<T> {

        companion object {
            const val COMMON_JVM = "commonJvm"
            const val ANDROID = "android"
        }

        override fun setupParentSourceSet(k: KotlinMultiplatformExtension, child: SourceSetBundle) {
            val bundle = k.commonJvm
            child dependsOn bundle
            super.setupParentSourceSet(k, bundle)
        }
    }


    interface NonJvm<T : KotlinTarget> : KmpTargetContainer<T> {

        companion object {
            const val NON_JVM = "nonJvm"
        }

        override fun setupParentSourceSet(k: KotlinMultiplatformExtension, child: SourceSetBundle) {
            val bundle = k.commonNonJvm
            child dependsOn bundle
            super.setupParentSourceSet(k, bundle)
        }


        interface CommonJs<T : KotlinTarget> : NonJvm<T> {

            companion object {
                const val COMMON_JS = "commonJs"
            }

            override fun setupParentSourceSet(
                k: KotlinMultiplatformExtension,
                child: SourceSetBundle,
            ) {
                val bundle = k.commonJs
                child dependsOn bundle
                super.setupParentSourceSet(k, bundle)
            }


            interface CommonWasm<T : KotlinWasmTargetDsl> : CommonJs<T> {

                companion object {
                    const val COMMON_WASM = "commonWasm"
                }

                override fun setupParentSourceSet(
                    k: KotlinMultiplatformExtension,
                    child: SourceSetBundle,
                ) {
                    val bundle = k.commonWasm
                    child dependsOn bundle
                    super.setupParentSourceSet(k, bundle)
                }
            }
        }

        @Suppress("MemberNameEqualsClassName")
        interface Native<T : KotlinNativeTarget> : NonJvm<T> {

            companion object {
                const val NATIVE = "native"
            }

            override fun setupParentSourceSet(
                k: KotlinMultiplatformExtension,
                child: SourceSetBundle,
            ) {
                val bundle = k.commonNative
                child dependsOn bundle
                super.setupParentSourceSet(k, bundle)
            }


            interface AndroidNative : Native<KotlinNativeTarget>

            interface Unix<T : KotlinNativeTarget> : Native<T> {

                companion object {
                    const val UNIX = "unix"
                }

                override fun setupParentSourceSet(
                    k: KotlinMultiplatformExtension,
                    child: SourceSetBundle,
                ) {
                    val bundle = k.commonUnix
                    child dependsOn bundle
                    super.setupParentSourceSet(k, bundle)
                }


                interface Apple<T : KotlinNativeTarget> : Unix<T> {

                    companion object {
                        const val APPLE = "apple"
                    }

                    override fun setupParentSourceSet(
                        k: KotlinMultiplatformExtension,
                        child: SourceSetBundle,
                    ) {
                        val bundle = k.commonApple
                        child dependsOn bundle
                        super.setupParentSourceSet(k, bundle)
                    }


                    interface Ios<T : KotlinNativeTarget> : Apple<T> {
                        companion object {
                            const val IOS = "ios"
                        }

                        override fun setupParentSourceSet(
                            k: KotlinMultiplatformExtension,
                            child: SourceSetBundle,
                        ) {
                            val bundle = k.commonIos
                            child dependsOn bundle
                            super.setupParentSourceSet(k, bundle)
                        }
                    }

                    interface Macos : Apple<KotlinNativeTargetWithHostTests> {
                        companion object {
                            const val MACOS = "macos"
                        }

                        override fun setupParentSourceSet(
                            k: KotlinMultiplatformExtension,
                            child: SourceSetBundle,
                        ) {
                            val bundle = k.commonMacos
                            child dependsOn bundle
                            super.setupParentSourceSet(k, bundle)
                        }
                    }

                    interface Tvos<T : KotlinNativeTarget> : Apple<T> {
                        companion object {
                            const val TVOS = "tvos"
                        }

                        override fun setupParentSourceSet(
                            k: KotlinMultiplatformExtension,
                            child: SourceSetBundle,
                        ) {
                            val bundle = k.commonTvos
                            child dependsOn bundle
                            super.setupParentSourceSet(k, bundle)
                        }
                    }

                    interface Watchos<T : KotlinNativeTarget> : Apple<T> {
                        companion object {
                            const val WATCHOS = "watchos"
                        }

                        override fun setupParentSourceSet(
                            k: KotlinMultiplatformExtension,
                            child: SourceSetBundle,
                        ) {
                            val bundle = k.commonWatchos
                            child dependsOn bundle
                            super.setupParentSourceSet(k, bundle)
                        }
                    }
                }

                interface Linux<T : KotlinNativeTarget> : Unix<T> {
                    companion object {
                        const val LINUX = "linux"
                    }

                    override fun setupParentSourceSet(
                        k: KotlinMultiplatformExtension,
                        child: SourceSetBundle,
                    ) {
                        val bundle = k.commonLinux
                        child dependsOn bundle
                        super.setupParentSourceSet(k, bundle)
                    }
                }
            }

            interface Mingw<T : KotlinNativeTarget> : Native<T> {
                companion object {
                    const val MINGW = "mingw"
                }

                override fun setupParentSourceSet(
                    k: KotlinMultiplatformExtension,
                    child: SourceSetBundle,
                ) {
                    val bundle = k.commonMingw
                    child dependsOn bundle
                    super.setupParentSourceSet(k, bundle)
                }
            }

            interface WasmNative : Native<KotlinNativeTarget> {
                companion object {
                    const val WASM_NATIVE = "wasmNative"
                }

                override fun setupParentSourceSet(
                    k: KotlinMultiplatformExtension,
                    child: SourceSetBundle,
                ) {
                    @Suppress("DEPRECATION")
                    val bundle = k.commonWasmNative
                    child dependsOn bundle
                    super.setupParentSourceSet(k, bundle)
                }
            }
        }
    }
}
