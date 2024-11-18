package fluxo.conf.dsl.container.impl

import bundleFor
import commonAndroidNative
import commonApple
import commonIos
import commonJs
import commonJvm
import commonLinux
import commonMacos
import commonMingw
import commonNative
import commonNix
import commonNonJvm
import commonTvos
import commonWasm
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

    override val allowManualHierarchy: Boolean
        get() = context.conf.kotlinConfig.allowManualHierarchy

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
        val target = k.createTarget()
        setupParentSourceSet(k, k.bundleFor(target))
    }


    interface CommonJvm<T : AbstractKotlinTarget> : KmpTargetContainer<T> {

        companion object {
            const val COMMON_JVM = "commonJvm"
            const val ANDROID = "android"
        }

        override fun setupParentSourceSet(k: KotlinMultiplatformExtension, child: SourceSetBundle) {
            if (!allowManualHierarchy) return
            val bundle = k.commonJvm

            // Fix the broken test-main dependencies in the intermediate KMP source sets.
            bundle.test.dependsOn(bundle.main)

            @Suppress("DEPRECATION")
            child dependsOn bundle
            super.setupParentSourceSet(k, bundle)
        }
    }


    interface NonJvm<T : KotlinTarget> : KmpTargetContainer<T> {

        companion object {
            const val NON_JVM = "nonJvm"
        }

        override fun setupParentSourceSet(k: KotlinMultiplatformExtension, child: SourceSetBundle) {
            if (!allowManualHierarchy) return
            val bundle = k.commonNonJvm
            @Suppress("DEPRECATION")
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
                if (!allowManualHierarchy) return
                val bundle = k.commonJs
                @Suppress("DEPRECATION")
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
                    if (!allowManualHierarchy) return
                    val bundle = k.commonWasm
                    @Suppress("DEPRECATION")
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
                if (!allowManualHierarchy) return
                val bundle = k.commonNative
                @Suppress("DEPRECATION")
                child dependsOn bundle
                super.setupParentSourceSet(k, bundle)
            }


            interface Nix<T : KotlinNativeTarget> : Native<T> {

                companion object {
                    const val NIX = "nix"
                }

                override fun setupParentSourceSet(
                    k: KotlinMultiplatformExtension,
                    child: SourceSetBundle,
                ) {
                    if (!allowManualHierarchy) return
                    val bundle = k.commonNix
                    @Suppress("DEPRECATION")
                    child dependsOn bundle
                    super.setupParentSourceSet(k, bundle)
                }


                interface Apple<T : KotlinNativeTarget> : Nix<T> {

                    companion object {
                        const val APPLE = "apple"
                    }

                    override fun setupParentSourceSet(
                        k: KotlinMultiplatformExtension,
                        child: SourceSetBundle,
                    ) {
                        if (!allowManualHierarchy) return
                        val bundle = k.commonApple
                        @Suppress("DEPRECATION")
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
                            if (!allowManualHierarchy) return
                            val bundle = k.commonIos
                            @Suppress("DEPRECATION")
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
                            if (!allowManualHierarchy) return
                            val bundle = k.commonMacos
                            @Suppress("DEPRECATION")
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
                            if (!allowManualHierarchy) return
                            val bundle = k.commonTvos
                            @Suppress("DEPRECATION")
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
                            if (!allowManualHierarchy) return
                            val bundle = k.commonWatchos
                            @Suppress("DEPRECATION")
                            child dependsOn bundle
                            super.setupParentSourceSet(k, bundle)
                        }
                    }
                }

                interface Linux<T : KotlinNativeTarget> : Nix<T> {
                    companion object {
                        const val LINUX = "linux"
                    }

                    override fun setupParentSourceSet(
                        k: KotlinMultiplatformExtension,
                        child: SourceSetBundle,
                    ) {
                        if (!allowManualHierarchy) return
                        val bundle = k.commonLinux
                        @Suppress("DEPRECATION")
                        child dependsOn bundle
                        super.setupParentSourceSet(k, bundle)
                    }
                }

                interface AndroidNative : Nix<KotlinNativeTarget> {
                    companion object {
                        const val ANDROID_NATIVE = "androidNative"
                    }

                    override fun setupParentSourceSet(
                        k: KotlinMultiplatformExtension,
                        child: SourceSetBundle,
                    ) {
                        if (!allowManualHierarchy) return
                        val bundle = k.commonAndroidNative
                        @Suppress("DEPRECATION")
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
                    if (!allowManualHierarchy) return
                    val bundle = k.commonMingw
                    @Suppress("DEPRECATION")
                    child dependsOn bundle
                    super.setupParentSourceSet(k, bundle)
                }
            }
        }
    }
}
