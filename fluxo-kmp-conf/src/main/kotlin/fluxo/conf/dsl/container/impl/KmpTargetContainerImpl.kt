package fluxo.conf.dsl.container.impl

import bundle
import bundleFor
import dependsOn
import fluxo.conf.impl.set
import fluxo.conf.kmp.KmpTargetCode
import fluxo.conf.kmp.SourceSetBundle
import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.AbstractKotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithHostTests
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl

internal abstract class KmpTargetContainerImpl<T : KotlinTarget>(
    context: ContainerContext,
    private val name: String,
    val code: KmpTargetCode,
    final override val sortOrder: Byte,
) : ContainerImpl(context), KmpTargetContainer<T> {

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
        val target = k.createTarget()
        setupParentSourceSet(k, k.bundleFor(target))
    }


    interface CommonJvm<T : AbstractKotlinTarget> : KmpTargetContainer<T> {

        companion object {
            const val COMMON_JVM = "commonJvm"
        }

        override fun setupParentSourceSet(k: KotlinMultiplatformExtension, child: SourceSetBundle) {
            // TODO: Create bundle once and reuse
            val bundle = k.sourceSets.bundle(COMMON_JVM)
            child dependsOn bundle
            super.setupParentSourceSet(k, bundle)
        }
    }


    interface NonJvm<T : KotlinTarget> : KmpTargetContainer<T> {

        companion object {
            const val NON_JVM = "nonJvm"
        }

        override fun setupParentSourceSet(k: KotlinMultiplatformExtension, child: SourceSetBundle) {
            // TODO: Create bundle once and reuse
            val bundle = k.sourceSets.bundle(NON_JVM)
            child dependsOn bundle
            super.setupParentSourceSet(k, bundle)
        }


        interface CommonJs<T : KotlinJsTargetDsl> : NonJvm<T> {

            companion object {
                const val COMMON_JS = "commonJs"
            }

            override fun setupParentSourceSet(
                k: KotlinMultiplatformExtension,
                child: SourceSetBundle,
            ) {
                // TODO: Create bundle once and reuse
                val bundle = k.sourceSets.bundle(COMMON_JS)
                child dependsOn bundle
                super.setupParentSourceSet(k, bundle)
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
                // TODO: Create bundle once and reuse
                val bundle = k.sourceSets.bundle(NATIVE)
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
                    // TODO: Create bundle once and reuse
                    val bundle = k.sourceSets.bundle(UNIX)
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
                        // TODO: Create bundle once and reuse
                        val bundle = k.sourceSets.bundle(APPLE)
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
                            // TODO: Create bundle once and reuse
                            val bundle = k.sourceSets.bundle(IOS)
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
                            // TODO: Create bundle once and reuse
                            val bundle = k.sourceSets.bundle(MACOS)
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
                            // TODO: Create bundle once and reuse
                            val bundle = k.sourceSets.bundle(TVOS)
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
                            // TODO: Create bundle once and reuse
                            val bundle = k.sourceSets.bundle(WATCHOS)
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
                        // TODO: Create bundle once and reuse
                        val bundle = k.sourceSets.bundle(LINUX)
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
                    // TODO: Create bundle once and reuse
                    val bundle = k.sourceSets.bundle(MINGW)
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
                    // TODO: Create bundle once and reuse
                    val bundle = k.sourceSets.bundle(WASM_NATIVE)
                    child dependsOn bundle
                    super.setupParentSourceSet(k, bundle)
                }
            }
        }
    }
}
