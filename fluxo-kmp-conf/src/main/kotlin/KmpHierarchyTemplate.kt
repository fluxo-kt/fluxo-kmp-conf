@file:JvmName("Fkc")
@file:JvmMultifileClass

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyBuilder
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate

/**
 * Extends the Kotlin hierarchy template with the `commonJvm`, `nonJvm`, and `commonJs` groups.
 */
@OptIn(ExperimentalKotlinGradlePluginApi::class)
public val KotlinHierarchyTemplate.Templates.fluxoKmpConf: KotlinHierarchyBuilder.Root.() -> Unit
    get() = {
        group("common") {
            group("commonJvm") {
                withJvm()
                withAndroidTarget()
            }
            group("nonJvm") {
                group("commonJs") {
                    withJs()
                    try {
                        group("commonWasm") {
                            withWasmJs()
                            withWasmWasi()
                        }
                    } catch (_: Throwable) {
                        // Fallback for Kotlin before v2.0
                        @Suppress("DEPRECATION")
                        withWasm()
                    }
                }
                group("native")
            }
        }
    }
