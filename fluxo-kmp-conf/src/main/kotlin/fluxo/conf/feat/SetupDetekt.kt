package fluxo.conf.feat

import dev.detekt.gradle.Detekt as Detekt2
import dev.detekt.gradle.DetektCreateBaselineTask as Detekt2CreateBaselineTask
import dev.detekt.gradle.extensions.DetektExtension as Detekt2Extension
import dev.detekt.gradle.plugin.DetektKotlinCompilerPlugin as Detekt2KotlinCompilerPlugin
import dev.detekt.gradle.plugin.DetektPlugin as Detekt2Plugin
import dev.detekt.gradle.plugin.getSupportedKotlinVersion as getDetekt2CompilerVersion
import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.MergeDetektBaselinesTask
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.addAndLog
import fluxo.conf.impl.android.ANDROID_KMP_LIB_PLUGIN_ID
import fluxo.conf.impl.android.AgpVersion
import fluxo.conf.impl.configureExtensionIfAvailable
import fluxo.conf.impl.dependencies
import fluxo.conf.impl.disableTask
import fluxo.conf.impl.kotlin.mppExtOrNull
import fluxo.conf.impl.kotlin.setupTargets
import fluxo.conf.impl.namedCompat
import fluxo.conf.impl.registerCompat
import fluxo.conf.impl.withType
import fluxo.log.l
import fluxo.log.logDecision
import fluxo.vc.onLibrary
import io.github.detekt.gradle.DetektKotlinCompilerPlugin
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.DetektPlugin
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import java.io.File
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin.TEST_TASK_NAME
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

private const val DEBUG_DETEKT_LOGS = false

private const val MERGE_DETEKT_TASK_NAME = "mergeDetektSarif"

internal const val CONFIG_DIR_NAME = "config"

// https://detekt.dev/docs/introduction/reporting/#merging-reports
internal fun FluxoKmpConfContext.registerDetektMergeRootTask(): TaskProvider<MergeSarifTask>? {
    if (testsDisabled) return null
    return rootProject.tasks.registerCompat<MergeSarifTask>(MERGE_DETEKT_TASK_NAME) {
        group = JavaBasePlugin.VERIFICATION_GROUP
        description = "Merges all Detekt reports from all modules to the root one"
        output.set(project.layout.buildDirectory.file("detekt-merged.sarif"))
        failOnNewApi.set(false)
    }
}

// TODO: Add option to ignore baselines completely and fail on anything,
//  to help working on reducing baselines.

// FIXME: Setup the "InvalidPackageDeclaration" rule for each module,
//  set the 'rootPackage' automatically from module group/package.
//  https://github.com/detekt/detekt/issues/4936#issue-1265233509

// FIXME: Setup checks for the non source set kotlin files (e.g., *.kts scripts).
//  See orbit-mvi setup for an example.

// FIXME: Setup the light-weight mode for the git hooks, to run only on the changed files.
//  And probably without types resolution.

@Suppress("LongMethod")
internal fun Project.setupDetekt(
    conf: FluxoConfigurationExtensionImpl,
    ignoredBuildTypes: List<String>,
    ignoredFlavors: List<String>,
    testsDisabled: Boolean,
) {
    val context = conf.ctx
    val detekt2 = usesDetekt2(conf)
    if (!testsDisabled) {
        val asCompilerPlugin = conf.enableDetektCompilerPlugin == true
        logger.l("setup Detekt" + if (asCompilerPlugin) " as COMPILER PLUGIN" else "")

        // Both Detekt lines are always available in the classpath as implementation
        // dependencies.
        pluginManager.apply(
            when {
                detekt2 && asCompilerPlugin -> Detekt2KotlinCompilerPlugin::class.java
                detekt2 -> Detekt2Plugin::class.java
                asCompilerPlugin -> DetektKotlinCompilerPlugin::class.java
                else -> DetektPlugin::class.java
            },
        )
        if (!detekt2) {
            pluginManager.withPlugin(ANDROID_KMP_LIB_PLUGIN_ID) {
                useCompilerClasspathInKmpAndroidDetekt()
            }
        }
    }

    val detektBaselineFile = layout.projectDirectory.file(DETEKT_BASELINE_FILE_NAME)
    val mergeDetektBaselinesTask = when {
        testsDisabled || !context.hasStartTaskCalled(MergeDetektBaselinesTask.TASK_NAME) -> null
        else -> tasks.registerCompat<MergeDetektBaselinesTask>(MergeDetektBaselinesTask.TASK_NAME) {
            outputFile.set(detektBaselineFile)
        }
    }
    val detektMergeStarted = mergeDetektBaselinesTask != null
    val testStarted = context.startTaskNames.any { name ->
        TEST_TASK_PREFIXES.any { name.startsWith(it) }
    }

    val baselineIntermediateDir = project.layout.buildDirectory.dir("intermediates/detekt")
    val rootProjectDir = rootProject.layout.projectDirectory
    val settings = DetektSettings(
        ignoreFailures = detektMergeStarted,
        autoCorrect = conf.enableDetektAutoCorrect == true &&
            !context.isCI && !testStarted && !detektMergeStarted,
        // For GitHub or another report consumers to know
        // where the file with issue is to place annotations correctly.
        basePath = rootProjectDir,
        config = detektConfigFiles(rootProjectDir, setupCompose = conf.kotlinConfig.setupCompose),
        baseline = when {
            !detektMergeStarted -> detektBaselineFile
            else -> baselineIntermediateDir.get().file("$BASELINE.$EXT")
        }.asFile,
        intermediateBaseline = { name ->
            baselineIntermediateDir.map { it.file("$BASELINE-$name.$EXT") }
        },
        mergeBaselines = mergeDetektBaselinesTask,
        ignoredBuildTypes = ignoredBuildTypes,
        ignoredFlavors = ignoredFlavors,
        testsDisabled = testsDisabled,
    )
    val detektTasks: DomainObjectCollection<out Task> = when {
        detekt2 -> setupDetekt2(settings, context)
        else -> setupDetekt1(settings, conf)
    }

    if (!testsDisabled) {
        val analysisTasks: Provider<List<Task>> = when {
            detekt2 -> detekt2AnalysisTasks().map<List<Task>> { it }
            else -> provider { detektTasks.toList() }
        }
        val detektAll = tasks.registerCompat<Task>("detektAll") {
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            description = "Calls all available Detekt tasks for this project"
            dependsOn(analysisTasks)
        }
        tasks.namedCompat { it == CHECK_TASK_NAME }
            .configureEach { dependsOn(detektAll) }
        // Detekt's own `detekt` task analyses its `source` without types. By default that is the
        // `main`/`test` directories the type-resolved tasks (`detektMain`, `detektTest`, per
        // variant or target) analyse already, and in KMP modules nothing, so the command people
        // type passed on zero files while `check` failed; Detekt's plugin still wires it into
        // `check`, where its own pass doubled the analysis time. So it runs every other Detekt
        // task (not `detektAll`, which includes it) and skips its own pass when they cover every
        // file it would analyse; a consumer-set `source` elsewhere still gets analysed.
        tasks.namedCompat { it == DetektPlugin.DETEKT_TASK_NAME }.configureEach {
            val others = analysisTasks.map { all ->
                all.filter { it.name != DetektPlugin.DETEKT_TASK_NAME }
            }
            dependsOn(others)
            val covered = files(
                others.map { all -> all.filter { it.enabled }.map { (it as SourceTask).source } },
            )
            val own = (this as SourceTask).source
            onlyIf("it has files the type-resolved Detekt tasks don't analyse") {
                !covered.files.containsAll(own.files)
            }
        }

        context.libs.run {
            dependencies {
                val packs = if (detekt2) DETEKT2_RULE_PACKS else DETEKT1_RULE_PACKS
                packs.forEach { id -> onLibrary(id) { detektPlugins(dh = this, it) } }

                val prefix = if (detekt2) "detekt2-" else "detekt-"
                if (conf.kotlinConfig.setupCompose) {
                    onLibrary(prefix + "compose") { detektPlugins(dh = this, it) }
                }
                if (!conf.isApplication) {
                    onLibrary(prefix + "libraries") { detektPlugins(dh = this, it) }
                }
            }
        }
    }
}

/** What fluxo sets on either Detekt line; each line's adapter applies it in its own types. */
private class DetektSettings(
    val ignoreFailures: Boolean,
    val autoCorrect: Boolean,
    val basePath: Directory,
    val config: List<RegularFile>,
    val baseline: File,
    /** Where a baseline task writes its part when `detektBaselineMerge` merges them. */
    val intermediateBaseline: (taskName: String) -> Provider<RegularFile>,
    val mergeBaselines: TaskProvider<MergeDetektBaselinesTask>?,
    val ignoredBuildTypes: List<String>,
    val ignoredFlavors: List<String>,
    val testsDisabled: Boolean,
)

/**
 * Which Detekt line analyses this module.
 *
 * The line the module applied itself wins: both lines register the extension `detekt`, so one
 * project can hold only one. Otherwise Detekt 2 when the module's stdlib is 2.2 or newer:
 * Detekt 1.23 embeds Kotlin 2.0, which can't read that stdlib's metadata, so its type-resolved
 * rules go silent with no warning (Detekt 1.23.8 CLI, `RedundantHigherOrderMapUsage` on
 * `List.map`: found with stdlib 2.1.21, missing with 2.4.20; Detekt 2 finds it). Below 2.2
 * Detekt 1 keeps working, and stays: Detekt 2 is a pre-release that renames rules and config.
 *
 * Detekt 2 needs AGP 8.8.2 or newer (its migration guide); with an older AGP on the build
 * classpath every module stays on Detekt 1.
 */
private fun Project.usesDetekt2(conf: FluxoConfigurationExtensionImpl): Boolean {
    val (detekt2, reason) = when {
        DETEKT2_IDS.any(pluginManager::hasPlugin) -> true to "applied by the module"
        DETEKT1_IDS.any(pluginManager::hasPlugin) -> false to "applied by the module"
        AgpVersion.current(this)?.let { it < DETEKT2_MIN_AGP } == true ->
            false to "Detekt 2 needs AGP $DETEKT2_MIN_AGP or newer"

        parseDetektLangVersion(conf.kotlinConfig.coreLibs) >= DETEKT2_MIN_STDLIB -> {
            conf.ctx.buildEndReport.hintOnDetekt2Failure(this)
            true to "stdlib ${conf.kotlinConfig.coreLibs}: Detekt 1 can't read stdlib " +
                "$DETEKT2_MIN_STDLIB_TEXT+, so its type-resolved rules would go silent"
        }

        else -> false to "stdlib ${conf.kotlinConfig.coreLibs} is readable by Detekt 1"
    }
    conf.ctx.logDecision(
        project = this,
        setting = "Detekt",
        value = if (detekt2) "2 (dev.detekt)" else "1 (io.gitlab.arturbosch.detekt)",
        reason = reason,
        howToChange = "apply the other line's plugin id in the module's plugins block",
    )
    return detekt2
}

/**
 * Moving to Detekt 2 can fail a build that passed on Detekt 1: its baselines match nothing,
 * rules and config keys were renamed, and its type resolution finds what Detekt 1 missed.
 * Only for modules fluxo moved: a module that applied Detekt 2 itself chose it knowingly.
 * Printed only when a Detekt 2 task fails, since a line on every build would be noise long
 * after the upgrade.
 */
private fun fluxo.conf.BuildEndReport.hintOnDetekt2Failure(project: Project) {
    val module = project.path
    project.tasks.withType<Detekt2>().configureEach {
        hintOnFailure(
            taskPath = path,
            hint = "w: Module '$module' runs Detekt 2, chosen by fluxo-kmp-conf because its " +
                "Kotlin stdlib is $DETEKT2_MIN_STDLIB_TEXT or newer, which Detekt 1 can't " +
                "read. Detekt 1 baselines match nothing in Detekt 2 (regenerate: " +
                "./gradlew detektBaselineMerge), the `formatting` config section is now " +
                "`ktlint`, some rules were renamed, and type resolution finds issues Detekt 1 " +
                "missed: https://detekt.dev/docs/next/introduction/migration " +
                "To stay on Detekt 1, apply id(\"io.gitlab.arturbosch.detekt\") in the module.",
        )
    }
}

private fun Project.setupDetekt1(
    s: DetektSettings,
    conf: FluxoConfigurationExtensionImpl,
): DomainObjectCollection<Detekt> {
    configureExtensionIfAvailable<DetektExtension>(DetektPlugin.DETEKT_EXTENSION) {
        parallel = true
        buildUponDefaultConfig = true
        ignoreFailures = s.ignoreFailures
        autoCorrect = s.autoCorrect
        basePath = s.basePath.asFile.absolutePath
        this.ignoredBuildTypes = s.ignoredBuildTypes
        this.ignoredFlavors = s.ignoredFlavors
        if (s.config.isNotEmpty()) {
            @Suppress("SpreadOperator")
            config.from(*s.config.toTypedArray())
        }
        baseline = s.baseline
        if (s.testsDisabled) {
            enableCompilerPlugin.set(false)
        }
    }

    // Detekt 1 analyses with the language version and JVM target fluxo passes, clamped to
    // what its embedded Kotlin 2.0 knows.
    val kc = conf.kotlinConfig
    val firstInBuild = conf.ctx::firstInBuild
    val (lang) = kc.langAndApiVersions(isTest = false)
    val baselineTasks = tasks.withType<DetektCreateBaselineTask> {
        // FIXME: Use kotlin settings directly from the linked kotlin compilation task?
        jvmTarget = clampJvmTargetForDetekt(kc.jvmTarget, logger, firstInBuild)
        lang?.let {
            languageVersion.set(clampKotlinLangVersionForDetekt(it.version, logger, firstInBuild))
        }
        s.mergeBaselines?.let {
            baseline.set(s.intermediateBaseline(name))
            finalizedBy(it)
        }
    }
    s.mergeBaselines?.configure {
        mustRunAfter(baselineTasks)
        baselineFiles.from(baselineTasks.map { it.baseline })
    }

    val detektTasks = tasks.withType<Detekt> {
        if (s.testsDisabled) {
            disableTask("tests are disabled")
        }
        // FIXME: Use kotlin settings directly from the linked kotlin compilation task?
        jvmTarget = clampJvmTargetForDetekt(kc.jvmTarget, logger, firstInBuild)
        lang?.let {
            languageVersion = clampKotlinLangVersionForDetekt(it.version, logger, firstInBuild)
        }
        if (DEBUG_DETEKT_LOGS) {
            debug = true
        }
        reports.apply {
            sarif.required.set(true)
            html.required.set(true)
            txt.required.set(false)
            md.required.set(false)
            xml.required.set(false)
        }
    }

    if (!s.testsDisabled) {
        conf.ctx.mergeDetektTask?.configure {
            // Detekt's report property is empty when a consumer's `sarif.required = false`,
            // and an empty one fails `check` while Gradle builds the task graph.
            for (task in detektTasks) {
                val sarif = task.sarifReportFile
                if (task.enabled && sarif.isPresent) {
                    dependsOn(task)
                    input.from(sarif)
                }
            }
        }
    }
    return detektTasks
}

private fun Project.setupDetekt2(
    s: DetektSettings,
    ctx: FluxoKmpConfContext,
): DomainObjectCollection<Detekt2> {
    configureExtensionIfAvailable<Detekt2Extension>(DetektPlugin.DETEKT_EXTENSION) {
        parallel.set(true)
        buildUponDefaultConfig.set(true)
        ignoreFailures.set(s.ignoreFailures)
        autoCorrect.set(s.autoCorrect)
        basePath.set(s.basePath)
        ignoredBuildTypes.set(s.ignoredBuildTypes)
        ignoredFlavors.set(s.ignoredFlavors)
        // `setFrom`, not `from`: Detekt 2 presets `config/detekt/detekt.yml`, which fluxo's
        // list already holds, and a file given twice is applied twice.
        if (s.config.isNotEmpty()) config.setFrom(s.config)
        baseline.set(s.baseline)
        if (s.testsDisabled) {
            enableCompilerPlugin.set(false)
        }
    }

    // Detekt 2 takes the language version, API version and JVM target from each task's compile
    // task, and its own compiler reads every stdlib fluxo supports; only versions newer than
    // that compiler are capped.
    capDetekt2KotlinVersions()
    val baselineTasks = tasks.withType<Detekt2CreateBaselineTask> {
        s.mergeBaselines?.let {
            baseline.set(s.intermediateBaseline(name))
            finalizedBy(it)
        }
    }
    s.mergeBaselines?.configure {
        mustRunAfter(baselineTasks)
        baselineFiles.from(baselineTasks.map { it.baseline })
    }

    val detektTasks = tasks.withType<Detekt2> {
        if (s.testsDisabled) {
            disableTask("tests are disabled")
        }
        if (DEBUG_DETEKT_LOGS) {
            debug.set(true)
        }
        reports.sarif.required.set(true)
        reports.html.required.set(true)
        reports.markdown.required.set(false)
        reports.checkstyle.required.set(false)
    }

    if (!s.testsDisabled) {
        val analysisTasks = detekt2AnalysisTasks()
        ctx.mergeDetektTask?.configure {
            for (task in analysisTasks.get()) {
                val sarif = task.reports.sarif
                if (task.enabled && sarif.required.get()) {
                    dependsOn(task)
                    input.from(sarif.outputLocation)
                }
            }
        }
    }
    return detektTasks
}

/**
 * The Detekt 2 tasks that analyse this module once each.
 *
 * Detekt 2 registers a type-resolved task per JVM/Android compilation and, beside them, a plain
 * task per source set (`detekt<SourceSet>SourceSet`), so a source set a JVM/Android compilation
 * covers would be analysed twice: double the time and duplicate findings in the merged report.
 * Only the source sets no such compilation covers (native, JS, Wasm, shared non-JVM ones) keep
 * their plain task. The plain `detekt` task is left out too: its default sources are what the
 * `main`/`test` compilations already analyse with types.
 */
private fun Project.detekt2AnalysisTasks(): Provider<List<Detekt2>> = provider {
    val kmp = mppExtOrNull
    val covered: Set<String>? = kmp?.targets
        ?.filter { it.platformType == KotlinPlatformType.jvm || it.platformType == KotlinPlatformType.androidJvm }
        ?.flatMap { it.compilations }
        ?.flatMapTo(HashSet()) { c -> c.allKotlinSourceSets.map { it.name.capitalized() } }
    tasks.withType(Detekt2::class.java).filter { task ->
        val sourceSet = task.name.removePrefix(DETEKT_TASK_NAME).removeSuffix(SOURCE_SET_SUFFIX)
        when {
            task.name == DETEKT_TASK_NAME -> false
            !task.name.endsWith(SOURCE_SET_SUFFIX) -> true
            // In a single-target module every source set belongs to a JVM/Android compilation.
            covered == null -> false
            else -> sourceSet !in covered
        }
    }
}

/**
 * Detekt tasks over a target `KMP_TARGETS` filters out must not run: their classpath is that
 * target's disabled compilation, which may not even resolve (AGP 9's consumer-applied `android`
 * target). Matched by name from the Kotlin model, so custom target names work: Detekt 1 names a
 * task after the target then the compilation, Detekt 2 after the compilation then the target,
 * and in a single-target module both use the compilation alone.
 */
internal fun KotlinTarget.disableDetektTasks(project: Project) {
    val target = this
    compilations.configureEach {
        disableDetektTasks(project, "target '${target.name}' is not in KMP_TARGETS")
    }
}

/** Disables both Detekt lines' analysis and baseline tasks over this compilation. */
internal fun KotlinCompilation<*>.disableDetektTasks(project: Project, reason: String) {
    val names = detektTaskNames(target, compilation = this, project)
    project.tasks.namedCompat { it in names }.configureEach { disableTask(reason) }
}

/**
 * Names of the analysis and baseline tasks either Detekt line registers for [compilation]:
 * Detekt 1 names them after the target then the compilation, Detekt 2 after the compilation
 * then the target, and in a single-target module both use the compilation alone.
 */
private fun detektTaskNames(
    target: KotlinTarget,
    compilation: KotlinCompilation<*>,
    project: Project,
): Set<String> {
    val c = compilation.name.capitalized()
    val t = target.name.capitalized()
    val suffixes = if (project.mppExtOrNull == null) listOf(c) else listOf(t + c, c + t)
    return suffixes.flatMapTo(HashSet()) {
        listOf(DETEKT_TASK_NAME + it, DETEKT_BASELINE_TASK_NAME + it)
    }
}

/**
 * Detekt 2 analyses each compilation with that compilation's language and API version, but its
 * own compiler rejects any version newer than one minor past itself (Detekt 2.0.0-alpha.6,
 * compiler 2.4.10: `"2.6" passed to --api-version, expected one of [2.0, …, 2.5]`). fluxo's
 * experimental compilations, and any Kotlin newer than Detekt's, would fail every Detekt task;
 * they are analysed at that newest version instead. The bound comes from Detekt itself, so a
 * newer Detekt lifts it with no change here; a Detekt 2 without that function gets no cap.
 */
private fun Project.capDetekt2KotlinVersions() {
    val max = try {
        parseDetektLangVersion(getDetekt2CompilerVersion())
            .let { KotlinVersion(it.major, it.minor + 1) }
    } catch (_: LinkageError) {
        return
    }
    val capped = { v: String ->
        if (parseDetektLangVersion(v) <= max) v else "${max.major}.${max.minor}"
    }
    kotlinExtension.setupTargets {
        val target = this
        target.compilations.configureEach {
            val names = detektTaskNames(target, compilation = this, project)
            val options = compileTaskProvider.map { it.compilerOptions }
            val lang = options.flatMap { it.languageVersion }.map { capped(it.version) }
            val api = options.flatMap { it.apiVersion }.map { capped(it.version) }
            tasks.withType(Detekt2::class.java).named { it in names }.configureEach {
                languageVersion.set(lang)
                apiVersion.set(api)
            }
            tasks.withType(Detekt2CreateBaselineTask::class.java).named { it in names }
                .configureEach {
                    languageVersion.set(lang)
                    apiVersion.set(api)
                }
        }
    }
}

private fun Project.detektConfigFiles(
    rootProjectDir: Directory,
    setupCompose: Boolean,
): List<RegularFile> {
    var configDir = rootProjectDir.dir(CONFIG_DIR_NAME)
        .takeIf { it.asFile.exists() } ?: rootProjectDir
    configDir = configDir.let {
        val detektDir = it.dir("detekt")
        if (detektDir.asFile.exists()) detektDir else it
    }
    val files = arrayListOf(
        layout.projectDirectory.file("detekt.yml"),
        configDir.file("detekt.yml"),
        configDir.file("detekt-formatting.yml"),
    )
    if (setupCompose) {
        files += configDir.file("detekt-compose.yml")
    }
    files.retainAll {
        val f = it.asFile
        f.exists() && f.canRead()
    }
    return files
}

internal fun String.capitalized() = replaceFirstChar { it.uppercase() }

private fun Project.detektPlugins(dh: DependencyHandler, dependencyNotation: Any) =
    addAndLog(dh, "detektPlugins", dependencyNotation)

/** Catalog keys of the rule packs every module gets; `compose`/`libraries` are conditional. */
private val DETEKT1_RULE_PACKS = arrayOf(
    "detekt-arrow",
    "detekt-compiler",
    "detekt-explicit",
    "detekt-faire",
    "detekt-formatting",
    "detekt-hbmartin",
    "detekt-ruleauthors",
    "detekt-verify-implementation",
)

/** A pack built for Detekt 1 can't load in Detekt 2; see the `detekt2-` catalog keys. */
private val DETEKT2_RULE_PACKS = arrayOf(
    "detekt2-compiler",
    "detekt2-faire",
    "detekt2-formatting",
    "detekt2-ruleauthors",
)

private val DETEKT1_IDS = arrayOf(
    "io.gitlab.arturbosch.detekt",
    "io.github.detekt.gradle.compiler-plugin",
)

private val DETEKT2_IDS = arrayOf("dev.detekt", "dev.detekt.gradle.compiler-plugin")

/** Detekt 2's documented AGP floor (its 2.0 migration guide). */
private val DETEKT2_MIN_AGP = KotlinVersion(8, 8, 2)

private const val DETEKT2_MIN_STDLIB_TEXT = "2.2"

private val DETEKT2_MIN_STDLIB = parseDetektLangVersion(DETEKT2_MIN_STDLIB_TEXT)

internal const val DETEKT_TASK_NAME = "detekt"

internal const val DETEKT_BASELINE_TASK_NAME = "detektBaseline"

private const val SOURCE_SET_SUFFIX = "SourceSet"

private const val BASELINE = "baseline"

private const val EXT = "xml"

private const val DETEKT_BASELINE_FILE_NAME = "detekt-$BASELINE.$EXT"

private val TEST_TASK_PREFIXES = arrayOf(CHECK_TASK_NAME, TEST_TASK_NAME)
