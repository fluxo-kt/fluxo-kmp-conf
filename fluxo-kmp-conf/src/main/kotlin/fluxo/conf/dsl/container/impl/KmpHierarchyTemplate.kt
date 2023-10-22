package fluxo.conf.dsl.container.impl

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyBuilder
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate

@OptIn(ExperimentalKotlinGradlePluginApi::class)
internal typealias Template = KotlinHierarchyBuilder.Root.() -> Unit

// TODO: Make it public

@OptIn(ExperimentalKotlinGradlePluginApi::class)
internal val KotlinHierarchyTemplate.Templates.fluxoKmpConf: Template
    get() = {
        group("common") {
            group("commonJvm") {
                withJvm()
                withAndroidTarget()
            }
            group("nonJvm") {
                group("commonJs") {
                    withJs()
                    withWasm()
                    // TODO: commonWasm
                }
                group("native")
            }
        }
    }
