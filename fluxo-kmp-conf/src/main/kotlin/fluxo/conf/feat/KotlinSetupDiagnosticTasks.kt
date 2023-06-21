@file:Suppress("CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth")

package fluxo.conf.feat

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.impl.ifNotEmpty
import fluxo.conf.impl.withType
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinTargetWithBinaries
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.targets
import org.jetbrains.kotlin.gradle.targets.js.KotlinJsTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsSubTargetContainerDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmSubTargetContainerDsl
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

internal fun FluxoKmpConfContext.prepareKotlinSetupDiagnosticTasks() {
    onProjectInSyncRun(forceIf = hasAnyTaskCalled(TARGETS_TASK, SOURCES_TASK)) {
        rootProject.allprojects {
            plugins.withType<KotlinBasePlugin> {
                tasks.register(TARGETS_TASK) {
                    group = TASK_GROUP
                    description = printTaskDescriptionFor("targets")
                    doLast { printKotlinTargetsInfo() }
                }
                tasks.register(SOURCES_TASK) {
                    group = TASK_GROUP
                    description = printTaskDescriptionFor("source sets")
                    doLast { printKotlinSourceSetsInfo() }
                }
            }
        }
    }
}

private fun Project.printKotlinTargetsInfo() {
    println("Project '$path' Kotlin targets:")
    val p = "    ->"
    kotlinExtension.targets.forEach { target ->
        println("Target: $target (${target.name}, ${target.platformType})")
        target.attributes.let {
            it.keySet().forEach { attr ->
                println("    $attr = ${it.getAttribute(attr)}")
            }
        }
        if (target is KotlinJsSubTargetContainerDsl) {
            println("    nodejs: ${target.isNodejsConfigured}")
            println("    browser: ${target.isBrowserConfigured}")
        }
        if (target is KotlinWasmSubTargetContainerDsl) {
            println("    d8: ${target.isD8Configured}")
        }
        if (target is KotlinJsTargetDsl) {
            println("    moduleName: ${target.moduleName}")
        }
        if (target is KotlinJsTarget) {
            println(
                "    disambiguationClassifierInPlatform:" +
                    " ${target.disambiguationClassifierInPlatform}",
            )
        }
        if (target is KotlinJvmTarget) {
            println("    javaEnabled: ${target.withJavaEnabled}")
        }
        if (target is KotlinTargetWithBinaries<*, *>) {
            if (target is KotlinNativeTarget) {
                target.binaries.forEach {
                    println(
                        "$p binary ${it.name}(${it.outputKind}" +
                            ", debuggable=${it.debuggable}, optimized=${it.optimized})",
                    )
                }
            } else {
                val binaries = when (target) {
                    is KotlinJsTarget -> target.binaries
                    is KotlinJsIrTarget -> target.binaries
                    else -> null
                }
                binaries?.forEach {
                    println("$p binary ${it.name}(${it.mode})")
                }
            }
        }
        target.compilations.forEach { compilation ->
            println("$p $compilation ${compilation.name}")

            try {
                // primary source set used to compile this compilation
                compilation.defaultSourceSet
                // all source sets used to compile this compilation
                compilation.allKotlinSourceSets
                // source set specific to this compilation
                compilation.kotlinSourceSets

                // List of compilation, which compiled outputs are used.
                // Associating compilations establishes internal visibility between them.
                compilation.associateWith

                compilation.compilerOptions.options
                compilation.kotlinOptions
            } catch (_: Throwable) {
            }
        }
    }
}

private fun Project.printKotlinSourceSetsInfo() {
    println("Project '$path' Kotlin source sets:")
    val p = "    ->"
    val projectDir = project.projectDir
    kotlinExtension.sourceSets.forEach { sourceSet ->
        println("SourceSet: $sourceSet (${sourceSet.name})")

        sourceSet.kotlin.srcDirs.ifNotEmpty {
            println("$p kotlin.srcDirs: [${joinToString { it.relativeTo(projectDir).path }}]")
        }
        sourceSet.resources.srcDirs.ifNotEmpty {
            println("$p resources.srcDirs: [${joinToString { it.relativeTo(projectDir).path }}]")
        }
        sourceSet.dependsOn.ifNotEmpty {
            println("$p dependsOn: [${joinToString { it.name }}]")
        }
        sourceSet.requiresVisibilityOf.ifNotEmpty {
            println("$p requiresVisibilityOf: [${joinToString { it.name }}]")
        }

        sourceSet.languageSettings.run {
            println("$p languageVersion: $languageVersion")
            println("$p apiVersion: $apiVersion")
            println("$p progressiveMode: $progressiveMode")
            println("$p optIns: $optInAnnotationsInUse")
        }
    }
}


private fun Project.printTaskDescriptionFor(type: String): String =
    "Prints detailed information for Kotlin $type in the '$path' project"

private const val TASK_GROUP = "group"
private const val TARGETS_TASK = "printKotlinTargetsInfo"
private const val SOURCES_TASK = "printKotlinSourceSetsInfo"
