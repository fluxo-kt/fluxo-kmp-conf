@file:Suppress("ktPropBy", "UnstableApiUsage")

import com.android.build.api.dsl.CommonExtension
import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.lint.AndroidLintTask
import com.android.build.gradle.internal.lint.AndroidLintTextOutputTask
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.diffplug.gradle.spotless.FormatExtension
import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.gradle.spotless.SpotlessPlugin
import com.diffplug.spotless.kotlin.DiktatStep
import com.diffplug.spotless.kotlin.KtLintStep
import impl.MergeDetektBaselinesTask
import impl.checkIsRootProject
import impl.isDetektTaskAllowed
import impl.isRootProject
import impl.libsCatalogOptional
import impl.onLibrary
import impl.optionalVersion
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import io.gitlab.arturbosch.detekt.report.ReportMergeTask
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty

/**
 *
 * @param ignoredBuildTypes List of Android build types for which to create no detekt task.
 * @param ignoredFlavors List of Android build flavors for which to create no detekt task.
 */
fun Project.setupVerification(
    ignoredBuildTypes: List<String> = listOf(),
    ignoredFlavors: List<String> = listOf(),
    kotlinConfig: KotlinConfigSetup? = getDefaults(),
    spotless: Boolean = false,
) {
    checkIsRootProject("setupVerification")

    if (spotless) {
        setupSpotless()
    }

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
    val rootBasePath = rootProject.projectDir.absolutePath

    allprojects {
        plugins.apply(DETEKT_PLUGIN_ID)

        val detektBaselineFile = file("detekt-baseline.xml")
        val taskNames = gradle.startParameter.taskNames
        val mergeDetektBaselinesTask = when {
            !taskNames.any { it == MergeDetektBaselinesTask.TASK_NAME } -> null
            else -> tasks.register(
                MergeDetektBaselinesTask.TASK_NAME,
                MergeDetektBaselinesTask::class.java,
            ) {
                outputFile.set(detektBaselineFile)
            }
        }
        val detektMergeStarted = mergeDetektBaselinesTask != null
        val testStarted = taskNames.any { name ->
            arrayOf("check", "test").any { name.startsWith(it) }
        }

        val detektBaselineIntermediate = "$buildDir/intermediates/detekt/baseline"
        extensions.configure<DetektExtension> {
            val isCI by isCI()

            parallel = true
            buildUponDefaultConfig = true
            ignoreFailures = true
            autoCorrect = !isCI && !testStarted && !detektMergeStarted
            basePath = rootBasePath

            this.ignoredBuildTypes = ignoredBuildTypes
            this.ignoredFlavors = ignoredFlavors

            val files = arrayOf(
                file("detekt.yml"),
                rootProject.file("detekt.yml"),
                detektCompose,
            ).filter { it.exists() && it.canRead() }.toTypedArray()
            if (files.isNotEmpty()) {
                @Suppress("SpreadOperator")
                config.from(*files)
            }

            baseline = if (detektMergeStarted) {
                file("$detektBaselineIntermediate.xml")
            } else {
                file(detektBaselineFile)
            }
        }

        if (mergeDetektBaselinesTask != null) {
            val baselineTasks = tasks.withType<DetektCreateBaselineTask> {
                baseline.set(file("$detektBaselineIntermediate-$name.xml"))
            }
            mergeDetektBaselinesTask.configure {
                dependsOn(baselineTasks)
                baselineFiles.from(baselineTasks.map { it.baseline })
            }
        }

        val disableTests by disableTests()
        val libs = rootProject.libsCatalogOptional
        val javaLangTarget = libs?.getJavaLangTarget(kotlinConfig)
        val detektTasks = tasks.withType<Detekt> {
            if (disableTests || !isDetektTaskAllowed()) {
                enabled = false
            }
            if (javaLangTarget != null) {
                jvmTarget = javaLangTarget
            }
            reports {
                sarif.required.set(true)
                html.required.set(true)
                txt.required.set(false)
                xml.required.set(false)
            }
        }
        val detektAll = tasks.register("detektAll") {
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            description = "Calls all available Detekt tasks for this project"
            dependsOn(detektTasks)
        }
        tasks.matching { it.name == "check" }.configureEach {
            dependsOn(detektAll)
        }
        mergeDetekt.configure {
            dependsOn(detektTasks)
            input.from(detektTasks.map { it.sarifReportFile })
        }

        dependencies {
            libs?.onLibrary("detekt-formatting") { detektPlugins(it) }
            libs?.onLibrary("detekt-compose") { detektPlugins(it) }
        }

        val function = Action<Any> {
            setupLint(mergeLint, ignoredBuildTypes, ignoredFlavors)
        }
        plugins.withId("com.android.library", function)
        plugins.withId("com.android.application", function)
    }

    setupTestsReport()
}

internal fun DependencyHandler.detektPlugins(dependencyNotation: Any) =
    add("detektPlugins", dependencyNotation)

private fun Project.setupSpotless(
    enableDiktat: Boolean = false,
) {
    // TODO: Git pre-commit hook
    //  https://medium.com/@mmessell/apply-spotless-formatting-with-git-pre-commit-hook-1c484ea68c34

    checkIsRootProject("setupSpotless")

    val libs = libsCatalogOptional
    allprojects {
        pluginManager.apply(SpotlessPlugin::class.java)

        @Suppress("SpreadOperator", "MagicNumber")
        extensions.configure<SpotlessExtension>("spotless") {
            // https://github.com/search?l=Kotlin&q=spotless+language%3AKotlin&type=Code
            // https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/c60d0a2/conventions/src/main/kotlin/otel.spotless-conventions.gradle.kts

            // optional: limit format enforcement to just the files changed by this feature branch
            // ratchetFrom = "origin/dev"

            // TODO: read base settings from .editorconfig ?

            if (isRootProject) {
                predeclareDeps()
            }

            val excludePaths = arrayOf(
                "**/.gradle-cache/",
                "**/.gradle/",
                "**/.idea/",
                "**/.run/",
                "**/_/",
                "**/build/",
                "**/generated/",
                "**/node_modules/",
                "**/resources/",
            )

            fun FormatExtension.defaultFormatSettings(
                numSpacesPerTab: Int = 4,
            ) {
                targetExclude(*excludePaths)
                trimTrailingWhitespace()
                indentWithSpaces(numSpacesPerTab)
                endWithNewline()
            }

            // TODO: Only if kotlin plugin enabled?
            val editorConfigPath = rootProject.file(".editorconfig")
            kotlin {
                target("**/*.kt", "**/*.kts")

                // https://github.com/search?q=setEditorConfigPath+path%3A*.kt&type=code
                try {
                    ktlint(libs.optionalVersion("ktlint") ?: KtLintStep.defaultVersion())
                } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
                    logger.warn("ktlint version error: $e", e)
                    ktlint()
                }.setEditorConfigPath(editorConfigPath)

                if (enableDiktat) {
                    try {
                        diktat(libs.optionalVersion("diktat") ?: DiktatStep.defaultVersionDiktat())
                    } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
                        logger.warn("diktat version error: $e", e)
                        diktat()
                    }
                }

                targetExclude(*excludePaths)

                // TODO: Licenses
                // licenseHeader("/* (C)$YEAR */")
            }
            kotlinGradle {
                try {
                    ktlint(libs.optionalVersion("ktlint") ?: KtLintStep.defaultVersion())
                } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
                    logger.warn("ktlint version error: $e", e)
                    ktlint()
                }.setEditorConfigPath(editorConfigPath)

                if (enableDiktat) {
                    try {
                        diktat(libs.optionalVersion("diktat") ?: DiktatStep.defaultVersionDiktat())
                    } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
                        logger.warn("diktat version error: $e", e)
                        diktat()
                    }
                }
            }

            // TODO: Only if java plugin enabled
            java {
                target("**/*.java")
                googleJavaFormat().aosp()
                defaultFormatSettings()
            }

            json {
                target("**/*.json")
                targetExclude(*excludePaths)
                gson().indentWithSpaces(2).sortByKeys()
            }
            format("misc") {
                target(
                    "**/*.css",
                    "**/*.dockerfile",
                    "**/*.gradle",
                    "**/*.htm",
                    "**/*.html",
                    "**/*.md",
                    "**/*.pro",
                    "**/*.sh",
                    "**/*.xml",
                    "**/*.yml",
                    "**/gradle.properties",
                    "*.md",
                    "*.yml",
                    ".dockerignore",
                    ".editorconfig",
                    ".gitattributes",
                    ".gitconfig",
                    ".gitignore",
                )
                defaultFormatSettings(numSpacesPerTab = 2)
            }

            // TODO: Freshmark
            // https://github.com/diffplug/spotless/tree/main/plugin-gradle#freshmark
            // https://github.com/diffplug/freshmark
        }

        // `spotlessPredeclare` is only declared after spotless.predeclareDeps call
        if (isRootProject) {
            extensions.configure<SpotlessExtension>("spotlessPredeclare") {
                kotlin {
                    ktlint()
                    if (enableDiktat) {
                        diktat()
                    }
                }
                kotlinGradle {
                    ktlint()
                    if (enableDiktat) {
                        diktat()
                    }
                }
                java {
                    googleJavaFormat()
                }
                json {
                    gson()
                }
                yaml {
                    jackson()
                }
                format("markdown") {
                    prettier()
                }
            }
        }
    }
}

private fun Project.setupLint(
    mergeLint: TaskProvider<ReportMergeTask>?,
    ignoredBuildTypes: List<String>,
    ignoredFlavors: List<String>,
) {
    val disableLint = !isGenericCompilationEnabled || disableTests().get()
    extensions.configure<CommonExtension<*, *, *, *>>("android") {
        lint {
            sarifReport = !disableLint
            htmlReport = !disableLint
            textReport = !disableLint
            xmlReport = false

            // Use baseline only for CI checks, show all problems in local development.
            val isCI by project.isCI()
            val isRelease by project.isRelease()
            if (isCI || isRelease) {
                baseline = file("lint-baseline.xml")
            }

            abortOnError = false
            absolutePaths = false
            checkAllWarnings = !disableLint
            checkDependencies = false
            checkReleaseBuilds = !disableLint
            explainIssues = false
            noLines = true
            warningsAsErrors = !disableLint
        }
    }

    if (!disableLint && mergeLint != null) {
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

    val variants = (ignoredBuildTypes + ignoredFlavors).ifNotEmpty { toHashSet().toTypedArray() }
    if (!variants.isNullOrEmpty()) {
        val disableIgnoredVariants: AndroidVariantTask.() -> Unit = {
            if (enabled) {
                for (v in variants) {
                    if (name.contains(v, ignoreCase = true)) {
                        enabled = false
                        logger.lifecycle("Task disabled: $path")
                        break
                    }
                }
            }
        }
        tasks.withType<AndroidLintTextOutputTask>(disableIgnoredVariants)
        tasks.withType<AndroidLintAnalysisTask>(disableIgnoredVariants)
        tasks.withType<AndroidLintTask>(disableIgnoredVariants)
    }
}

private const val DETEKT_PLUGIN_ID = "io.gitlab.arturbosch.detekt"
private const val MERGE_LINT_TASK_NAME = "mergeLintSarif"
private const val MERGE_DETEKT_TASK_NAME = "mergeDetektSarif"
