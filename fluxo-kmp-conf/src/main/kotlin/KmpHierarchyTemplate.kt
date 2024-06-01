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

/**
 * Extends the Kotlin hierarchy template with the `commonJvm`,
 * `nonJvm`, `commonJs` and other groups.
 */
@OptIn(ExperimentalKotlinGradlePluginApi::class)
public val KotlinHierarchyTemplate.Templates.fluxoKmpConf: KotlinHierarchyBuilder.Root.() -> Unit
    get() = {
        group("common") {
            group(COMMON_JVM) {
                withJvm()
                try {
                    withAndroidTarget()
                } catch (_: Throwable) {
                    // Fallback for old Kotlin
                    @Suppress("DEPRECATION")
                    withAndroid()
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
                    group(NIX) {
                        group(APPLE) {
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
