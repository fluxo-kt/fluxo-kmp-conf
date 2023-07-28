@file:Suppress("CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth")

package fluxo.conf.feat

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.KotlinSourceSetsReportTask
import fluxo.conf.impl.ifNotEmpty
import fluxo.conf.impl.register
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
    onProjectInSyncRun(forceIf = hasStartTaskCalled(DIAGNOSTIC_TASKS)) {
        rootProject.allprojects {
            plugins.withType<KotlinBasePlugin> {
                // TODO: @DisableCachingByDefault(because = "Not worth caching")

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

                tasks.register<KotlinSourceSetsReportTask>(SOURCES_GRAPH_TASK) {
                    group = TASK_GROUP
                    description = printTaskDescriptionFor("source sets", "graph")
                }
            }
        }
    }
}

private fun Project.printKotlinTargetsInfo() {
    println("Project '$path' Kotlin targets:")
    kotlinExtension.targets.forEach { target ->
        println("Target: $target (${target.name}, ${target.platformType})")
        target.attributes.let {
            it.keySet().forEach { attr ->
                println("$T $attr = ${it.getAttribute(attr)}")
            }
        }
        if (target is KotlinJsSubTargetContainerDsl) {
            println("$T nodejs: ${target.isNodejsConfigured}")
            println("$T browser: ${target.isBrowserConfigured}")
        }
        if (target is KotlinWasmSubTargetContainerDsl) {
            println("$T d8: ${target.isD8Configured}")
        }
        if (target is KotlinJsTargetDsl) {
            println("$T moduleName: ${target.moduleName}")
        }
        if (target is KotlinJsTarget) {
            println(
                "$T disambiguationClassifierInPlatform:" +
                        " ${target.disambiguationClassifierInPlatform}",
            )
        }
        if (target is KotlinJvmTarget) {
            println("$T javaEnabled: ${target.withJavaEnabled}")
        }
        if (target is KotlinTargetWithBinaries<*, *>) {
            if (target is KotlinNativeTarget) {
                target.binaries.forEach {
                    println(
                        "$P binary ${it.name}(${it.outputKind}" +
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
                    println("$P binary ${it.name}(${it.mode})")
                }
            }
        }
        target.compilations.forEach { compilation ->
            println("$P $compilation ${compilation.name}")

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
    val projectDir = project.projectDir
    kotlinExtension.sourceSets.forEach { sourceSet ->
        println("SourceSet: ${sourceSet.name}")

        sourceSet.kotlin.srcDirs.ifNotEmpty {
            println("$P kotlin.srcDirs: [${joinToString { it.relativeTo(projectDir).path }}]")
        }
        sourceSet.resources.srcDirs.ifNotEmpty {
            println("$P resources.srcDirs: [${joinToString { it.relativeTo(projectDir).path }}]")
        }
        sourceSet.dependsOn.ifNotEmpty {
            println("$P dependsOn: [${joinToString { it.name }}]")
        }

        try {
            // Scheduled for removal with Kotlin 2.0
            @Suppress("DEPRECATION")
            sourceSet.requiresVisibilityOf.ifNotEmpty {
                println("$P requiresVisibilityOf: [${joinToString { it.name }}]")
            }
        } catch (_: Throwable) {
        }

        sourceSet.languageSettings.run {
            println("$P languageVersion: $languageVersion")
            println("$P apiVersion: $apiVersion")
            println("$P progressiveMode: $progressiveMode")
            println("$P optIns: $optInAnnotationsInUse")
        }
    }
}


private fun Project.printTaskDescriptionFor(obj: String, type: String = "information"): String =
    "Prints detailed $type for Kotlin $obj in the '$path' project"

private const val T = "   "
private const val P = "$T ->"

private const val TASK_GROUP = "group"
private const val TARGETS_TASK = "printKotlinTargetsInfo"
private const val SOURCES_TASK = "printKotlinSourceSetsInfo"
private const val SOURCES_GRAPH_TASK = "printKotlinSourceSetsGraph"
private val DIAGNOSTIC_TASKS = arrayOf(TARGETS_TASK, SOURCES_TASK, SOURCES_GRAPH_TASK)
