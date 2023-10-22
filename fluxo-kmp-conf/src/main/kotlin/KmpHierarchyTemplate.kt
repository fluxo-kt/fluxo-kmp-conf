import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyBuilder
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate

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
                    withWasm()
                    // TODO: commonWasm
                }
                group("native")
            }
        }
    }
