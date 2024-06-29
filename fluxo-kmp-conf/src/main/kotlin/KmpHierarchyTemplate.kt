@file:JvmName("Fkc")
@file:JvmMultifileClass

import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl.CommonJvm.Companion.COMMON_JVM
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl.NonJvm.CommonJs.CommonWasm.Companion.COMMON_WASM
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl.NonJvm.CommonJs.Companion.COMMON_JS
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl.NonJvm.Companion.NON_JVM
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl.NonJvm.Native.Companion.NATIVE
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl.NonJvm.Native.Mingw.Companion.MINGW
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl.NonJvm.Native.Nix.AndroidNative.Companion.ANDROID_NATIVE
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl.NonJvm.Native.Nix.Apple.Companion.APPLE
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl.NonJvm.Native.Nix.Companion.NIX
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl.NonJvm.Native.Nix.Linux.Companion.LINUX
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyBuilder
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

/**
 * Extends the Kotlin hierarchy template with the `commonJvm`,
 * `nonJvm`, `commonJs` and other groups.
 *
 * @see org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate.Templates.default
 * @see org.jetbrains.kotlin.gradle.plugin.defaultKotlinHierarchyTemplate
 */
@OptIn(ExperimentalKotlinGradlePluginApi::class)
public val KotlinHierarchyTemplate.Templates.fluxoKmpConf: KotlinHierarchyBuilder.Root.() -> Unit
    get() = {
        // References:
        // https://github.com/coil-kt/coil/blob/5d8bbca/buildSrc/src/main/kotlin/coil3/hierarchyTemplate.kt#L11
        // https://github.com/touchlab/KaMPKit/blob/3af2a9f/shared/build.gradle.kts#L52
        // https://github.com/proto-at-block/bitkey/blob/15b7c44/app/gradle/build-logic/src/main/kotlin/build/wallet/gradle/logic/extensions/KotlinMultiplatformExtension.kt#L47
        // https://github.com/pdvrieze/xmlutil/blob/663c419/project-plugins/src/main/kotlin/net/devrieze/gradle/ext/nativeTargets.kt#L72

        /* natural hierarchy is only applied to default 'main'/'test' compilations (by default) */
        withSourceSetTree(KotlinSourceSetTree.main, KotlinSourceSetTree.test)

        /** @see org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyBuilder.common */
        group("common") {
            /* All compilations shall be added to the common group by default. */
            withCompilations { true }

            group(COMMON_JVM) {
                withJvm()
                try {
                    withAndroidTarget()
                } catch (_: Throwable) {
                    // Fallback for old Kotlin
                    @Suppress("DEPRECATION")
                    withAndroid()
                }

                excludeCompilations {
                    when (it.platformType) {
                        KotlinPlatformType.jvm,
                        KotlinPlatformType.common,
                        KotlinPlatformType.androidJvm -> false
                        else -> true
                    }
                }
            }
            group(NON_JVM) {
                group(COMMON_JS) {
                    withJs()
                    try {
                        group(COMMON_WASM) {
                            withWasmJs()
                            withWasmWasi()
                        }
                    } catch (_: Throwable) {
                        // Fallback for Kotlin before v2.0
                        @Suppress("DEPRECATION")
                        withWasm()
                    }
                }
                group(NATIVE) {
                    withNative()

                    group(NIX) {
                        group(APPLE) {
                            withApple()

                            withIos()
                            withMacos()
                            withTvos()
                            withWatchos()
                        }
                        group(LINUX) {
                            withLinux()
                        }
                        group(ANDROID_NATIVE) {
                            withAndroidNative()
                        }
                    }
                    group(MINGW) {
                        withMingw()
                    }
                }
            }
        }
    }
