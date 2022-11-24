package fluxo

import com.android.build.api.dsl.CommonExtension
import com.android.build.gradle.internal.lint.AndroidLintTask
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import io.gitlab.arturbosch.detekt.report.ReportMergeTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.gradle.language.base.plugins.LifecycleBasePlugin

fun Project.setupVerification() {
    checkIsRootProject()

    val mergeLint = tasks.register(MERGE_LINT_TASK_NAME, ReportMergeTask::class.java) {
        group = JavaBasePlugin.VERIFICATION_GROUP
        description = "Merges all Lint reports from all modules to the root one"
        output.set(project.layout.buildDirectory.file("lint-merged.sarif"))
    }
    val mergeDetekt = tasks.register(MERGE_DETEKT_TASK_NAME, ReportMergeTask::class.java) {
        group = JavaBasePlugin.VERIFICATION_GROUP
        description = "Merges all Detekt reports from all modules to the root one"
        output.set(project.layout.buildDirectory.file("detekt-merged.sarif"))
    }

    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(mergeDetekt, mergeLint)
    }

    val detektCompose = rootProject.file("detekt-compose.yml")
    val hasDetektCompose = detektCompose.exists()
    val rootBasePath = rootProject.projectDir.absolutePath

    allprojects {
        plugins.apply(DETEKT_PLUGIN_ID)

        val detektBaselineFile = file("detekt-baseline.xml")
        val taskNames = gradle.startParameter.taskNames
        val mergeDetektBaselinesTask = when {
            !taskNames.any { it == MergeDetektBaselinesTask.TASK_NAME } -> null
            else -> tasks.register(MergeDetektBaselinesTask.TASK_NAME, MergeDetektBaselinesTask::class.java) {
                outputFile.set(detektBaselineFile)
            }
        }

        val detektBaselineIntermediate = "$buildDir/intermediates/detekt/baseline"
        extensions.configure<DetektExtension> {
            val isCi by isCI()

            parallel = true
            buildUponDefaultConfig = true
            ignoreFailures = mergeDetektBaselinesTask != null || taskNames.any { it.startsWith("detekt") }
            autoCorrect = !isCi
            basePath = rootBasePath

            val files = arrayOf(
                file("detekt.yml"),
                rootProject.file("detekt.yml"),
                detektCompose,
            ).filter { it.exists() }.toTypedArray()
            if (files.isNotEmpty()) {
                @Suppress("SpreadOperator")
                config.from(*files)
            }

            baseline = if (mergeDetektBaselinesTask != null) {
                file("$detektBaselineIntermediate.xml")
            } else {
                file(detektBaselineFile)
            }
        }

        val irLoweringErrorAvoidance: (element: Task) -> Boolean = {
            // Avoid Exception during IR lowering (detektAndroidDebugAndroidTest)
            // Unhandled intrinsic in ExpressionCodegen: FUN FUNCTION_FOR_DEFAULT_PARAMETER name:runBlocking$default [expect]
            // https://youtrack.jetbrains.com/issue/KT-52829#focus=Comments-27-6658039.0-0
            !it.name.contains("DebugAndroidTest")
        }
        if (mergeDetektBaselinesTask != null) {
            val baselineTasks = tasks.withType<DetektCreateBaselineTask>() {
                baseline.set(file("$detektBaselineIntermediate-$name.xml"))
            }.matching(irLoweringErrorAvoidance)
            mergeDetektBaselinesTask.configure {
                dependsOn(baselineTasks)
                baselineFiles.from(baselineTasks.map { it.baseline })
            }
        }

        val detektTasks = tasks.withType<Detekt> {
            reports {
                sarif.required.set(true)
                html.required.set(true)
                txt.required.set(false)
                xml.required.set(false)
            }
        }.matching(irLoweringErrorAvoidance)
        tasks.register("detektAll") {
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            description = "Calls all available Detekt tasks for this project"
            dependsOn(detektTasks)
        }
        mergeDetekt.configure {
            dependsOn(detektTasks)
            input.from(detektTasks.map { it.sarifReportFile })
        }

        dependencies {
            val libsCatalog = rootProject.libsCatalog

            add("detektPlugins", libsCatalog.library("detekt-formatting"))
            if (hasDetektCompose) {
                // Add to all modules (even ones that don't use Compose) as detecting Compose can be messy.
                add("detektPlugins", libsCatalog.library("detekt-compose"))
            }
        }


        plugins.withId("com.android.library") {
            setupLint(mergeLint)
        }
        plugins.withId("com.android.application") {
            setupLint(mergeLint)
        }
    }
}

private fun Project.setupLint(mergeLint: TaskProvider<ReportMergeTask>) {
    extensions.configure<CommonExtension<*, *, *, *>>("android") {
        lint {
            sarifReport = true
            htmlReport = true
            textReport = false
            xmlReport = false

            baseline = file("lint-baseline.xml")
            // absolutePaths = true
            warningsAsErrors = true
            checkAllWarnings = true
            abortOnError = false
            checkDependencies = true
        }
    }

    tasks.withType<AndroidLintTask> {
        val lintTask = this
        if (name.startsWith("lintReport")) {
            mergeLint.configure {
                input.from(lintTask.sarifReportOutputFile)
                dependsOn(lintTask)
            }
        }
    }
}

private const val DETEKT_PLUGIN_ID = "io.gitlab.arturbosch.detekt"
private const val MERGE_LINT_TASK_NAME: String = "mergeLintSarif"
private const val MERGE_DETEKT_TASK_NAME: String = "mergeDetektSarif"
