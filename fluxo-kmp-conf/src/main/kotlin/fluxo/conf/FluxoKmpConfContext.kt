package fluxo.conf

import allKmpTargetsEnabled
import areComposeMetricsEnabled
import disableTests
import fluxo.conf.deps.GradleProvisioner
import fluxo.conf.deps.Provisioner
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.container.impl.getSetOfRequestedKmpTargets
import fluxo.conf.feat.registerDetektMergeRootTask
import fluxo.conf.feat.registerLintMergeRootTask
import fluxo.conf.impl.CPUs
import fluxo.conf.impl.TOTAL_OS_MEMORY
import fluxo.conf.impl.XMX
import fluxo.conf.impl.android.AgpVersion
import fluxo.conf.impl.kotlin.DeprecatedKotlinVersions
import fluxo.conf.impl.kotlin.JRE_VERSION_STRING
import fluxo.conf.impl.kotlin.kotlinPluginVersion
import fluxo.conf.impl.tryAsBoolean
import fluxo.log.FluxoProblem
import fluxo.log.SHOW_DEBUG_LOGS
import fluxo.log.d
import fluxo.log.i
import fluxo.log.reportProblem
import fluxo.log.v
import fluxo.shrink.BUNDLED_PROGUARD_VERSION
import fluxo.shrink.BUNDLED_R8_VERSION
import fluxo.util.readableByteSize
import fluxo.vc.FluxoVersionCatalog
import getValue
import isCI
import isDesugaringEnabled
import isFluxoExplain
import isFluxoVerbose
import isMaxDebugEnabled
import isRelease
import isShrinkerDisabled
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import org.gradle.api.DomainObjectSet
import org.gradle.api.Project
import org.gradle.api.flow.FlowProviders
import org.gradle.api.flow.FlowScope
import org.gradle.api.plugins.JavaPlugin.TEST_TASK_NAME
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME
import org.gradle.util.GradleVersion
import scmTag
import useKotlinDebug

/**
 * Internal configuration context for the Fluxo KMP plugin.
 * It's a root-project-based singleton.
 *
 * @see FluxoKmpConfPlugin
 */
internal abstract class FluxoKmpConfContext
@Inject constructor(
    val rootProject: Project,
) {
    /** @see org.gradle.api.plugins.PluginAware.getPlugins */
    internal val plugins get() = rootProject.plugins

    private val projectInSyncFlag: DomainObjectSet<String> =
        rootProject.objects.domainObjectSet(String::class.java)

    /** Keys already reported; this context lives for one build, a static for the whole daemon. */
    private val reportedInBuild: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** `true` only the first time [key] is seen in this build: for warnings due once per build. */
    fun firstInBuild(key: String): Boolean = reportedInBuild.add(key)

    internal val provisioner: Provisioner = GradleProvisioner.DedupingProvisioner(
        GradleProvisioner.forProject(rootProject),
    )

    @get:Inject
    internal abstract val flowScope: FlowScope

    @get:Inject
    internal abstract val flowProviders: FlowProviders

    internal val buildEndReport = BuildEndReport(
        flowScope,
        flowProviders,
        rootProject,
        explain = rootProject.isFluxoExplain().get(),
    )


    @Suppress("LeakingThis")
    val libs = FluxoVersionCatalog(rootProject, context = this)

    val kotlinPluginVersion: KotlinVersion = rootProject.logger.kotlinPluginVersion()

    internal val deprecatedKotlinVersions =
        DeprecatedKotlinVersions(buildEndReport, kotlinPluginVersion)

    val testsDisabled: Boolean


    /**
     * Whether the project is part of a composite build.
     *
     * `true` when the project has "included" builds.
     */
    private val isInCompositeBuild: Boolean

    /**
     * Whether the project is a child of a composite build and has no startup tasks.
     *
     * `true` when the project has a parent build.
     */
    val isIncludedBuild: Boolean

    val isCI: Boolean = rootProject.isCI().get()
    val isRelease: Boolean = rootProject.isRelease().get()
    val isMaxDebug: Boolean = rootProject.isMaxDebugEnabled().get()
    val isDesugaringEnabled by rootProject.isDesugaringEnabled()
    val useKotlinDebug by rootProject.useKotlinDebug()
    val composeMetricsEnabled by rootProject.areComposeMetricsEnabled()
    val scmTag by rootProject.scmTag(allowBranch = false)

    private val kmpTargets: Set<KmpTargetCode> = getSetOfRequestedKmpTargets()
    val allTargetsEnabled: Boolean = rootProject.allKmpTargetsEnabled() || kmpTargets.isEmpty()
    fun isTargetEnabled(code: KmpTargetCode): Boolean = allTargetsEnabled || code in kmpTargets


    val startTaskNames: Set<String>

    fun hasStartTaskCalled(name: String) = name in startTaskNames

    @JvmName("hasStartTaskCalledVararg")
    fun hasStartTaskCalled(vararg name: String) = hasStartTaskCalled(name)

    fun hasStartTaskCalled(names: Array<out String>) = names.any { it in startTaskNames }


    init {
        val project = rootProject
        val gradle = project.gradle
        val logger = project.logger

        // FIXME: Detekt and use here if CI (GitHub) debug logs are enabled.
        val isVerbose = isMaxDebug || logger.isInfoEnabled || project.isFluxoVerbose().get()
        // Assigned on every build, never only raised: the flag is a static in a class the
        // daemon reuses across builds, so one verbose build would leave every later one verbose.
        SHOW_DEBUG_LOGS = isVerbose

        // Machine figures and bundled shrinkers go to info; the banner follows once the modes
        // it lists are known.
        run {
            var m = "$CPUs CPUs,  ${readableByteSize(XMX)} XMX"
            val ram = TOTAL_OS_MEMORY
            if (ram > 0) {
                m += " from ${readableByteSize(ram)} RAM"
            }
            BUNDLED_R8_VERSION?.let { r8 -> m += ", Bundled R8 $r8" }
            BUNDLED_PROGUARD_VERSION?.let { pg -> m += ", Bundled ProGuard $pg" }
            logger.i(m)
        }

        if (SHOW_DEBUG_LOGS) {
            onProjectInSyncRun {
                val reason = projectInSyncFlag.firstOrNull()
                logger.d("onProjectInSyncRun, because $reason")
                if (testsDisabled) {
                    logger.i("Tests are off, so their tasks are not configured for IDE sync")
                }
            }
        }

        val start = gradle.startParameter
        startTaskNames = start.taskNames.let { taskNames ->
            LinkedHashSet<String>(taskNames.size).apply {
                for (name in taskNames) {
                    if (name.isNotEmpty() && name[0] != '-') {
                        add(name.substringAfterLast(':'))
                    }
                }
            }
        }

        taskGraphBasedProjectSyncDetection()

        val includedBuilds = gradle.includedBuilds.size
        val includedBuilds2 = start.includedBuilds.size
        isInCompositeBuild = includedBuilds > 0 || includedBuilds2 > 0
        val compositeMsg =
            "$includedBuilds gradle.includedBuilds, $includedBuilds2 start.includedBuilds"

        if (isInCompositeBuild) {
            logger.i("COMPOSITE BUILD USED! ($compositeMsg)")
        } else if (isVerbose) {
            logger.i("NOT in a COMPOSITE build! ($compositeMsg)")
        }

        // Detect when the project is a child of a composite build
        // and has no startup tasks.
        // https://github.com/JetBrains/intellij-community/blob/ccb1ede/plugins/kotlin/gradle/gradle-tooling/impl/src/org/jetbrains/kotlin/idea/gradleTooling/PrepareKotlinIdeaImportTaskModelBuilder.kt#L84
        isIncludedBuild = when (val parent = gradle.parent) {
            null -> false

            else -> {
                val noTasks = startTaskNames.isEmpty()
                if (noTasks) logger.i("INCLUDED BUILD!")

                val pDir = parent.startParameter.run { projectDir ?: currentDir }
                val dir = pDir.run {
                    val relDir = relativeTo(start.projectDir ?: project.projectDir)
                    if (relDir.path.startsWith("..")) pDir else relDir
                }
                logger.i("Parent Gradle build: $dir")

                noTasks
            }
        }

        if (start.isDryRun) logger.i("DryRun mode is enabled!")
        if (start.isContinueOnFailure) logger.i("ContinueOnFailure mode is enabled!")
        if (composeMetricsEnabled) logger.i("COMPOSE_METRICS are enabled!")

        // Disable all tests if:
        //  - `DISABLE_TESTS` is enabled;
        //  - `check` or `test` tasks are excluded from the build;
        //  - is included build with no tasks (so it's a child of a composite build);
        val testsDisabledReason = when {
            isIncludedBuild -> "INCLUDED BUILD"
            project.disableTests().get() -> "DISABLE_TESTS flag"
            start.excludedTaskNames.let { CHECK_TASK_NAME in it || TEST_TASK_NAME in it } -> {
                "EXCLUDED_TASKS${start.excludedTaskNames}"
            }

            else -> null
        }
        testsDisabled = testsDisabledReason != null

        // The one lifecycle line per build: the toolchain fluxo adapts to and the modes that
        // change what it sets up.
        logger.lifecycle(environmentBanner(project, testsDisabledReason))

        if (testsDisabled) {
            val name = startTaskNames.firstOrNull { CHECK_TASK_NAME in it || TEST_TASK_NAME in it }
            if (name != null) {
                project.reportProblem(
                    FluxoProblem.TESTS_OFF,
                    "`$name` runs no tests: fluxo turns them off because of $testsDisabledReason.",
                    fix = "Unset DISABLE_TESTS, and don't exclude `check` or `test`, to run them.",
                )
            }
        }

        logger.v("Cleaned start task names: $startTaskNames")

        val isInIde = start.systemPropertiesArgs["idea.active"].tryAsBoolean()
        logger.v("isInIde: $isInIde")
    }


    val mergeLintTask = registerLintMergeRootTask()
    val mergeDetektTask = registerDetektMergeRootTask()


    // region Project IDE synchronization detection

    internal val isProjectInSyncRun: Boolean
        get() = projectInSyncFlag.isNotEmpty()

    private fun environmentBanner(project: Project, testsOffReason: String?): String = buildString {
        append("fluxo-kmp-conf: Gradle ${project.gradle.gradleVersion}, JDK $JRE_VERSION_STRING, ")
        append("Kotlin $kotlinPluginVersion")
        AgpVersion.current(project)?.let { append(", AGP $it") }
        composePluginVersion(project)?.let { append(", Compose $it") }
        if (isCI) append(", CI")
        if (isRelease) append(", RELEASE")
        if (!allTargetsEnabled) append(", KMP_TARGETS=${kmpTargets.joinToString(",")}")
        when {
            isMaxDebug -> append(", MAX_DEBUG")
            project.isFluxoVerbose().get() -> append(", FLUXO_VERBOSE")
        }
        if (useKotlinDebug) append(", USE_KOTLIN_DEBUG")
        if (isDesugaringEnabled) append(", DESUGARING")
        if (project.isShrinkerDisabled().get()) append(", DISABLE_R8")
        testsOffReason?.let { append(", tests off ($it)") }
    }

    /**
     * Configures the project to apply everything that can be applied.
     * It's a special mode for IDE synchronization and other similar processes.
     *
     * @return `false` if was already done earlier.
     */
    fun markProjectInSync(reason: String): Boolean =
        projectInSyncFlag.let { set ->
            if (set.isEmpty()) set.add(reason) else false
        }

    /**
     * Runs provided `action` if the project is in sync mode now or will be marked for it later.
     *
     * @see markProjectInSync
     * @FIXME Allow to use it from any build scripts
     */
    fun onProjectInSyncRun(
        forceIf: Boolean = false,
        rethrow: Boolean = false,
        action: FluxoKmpConfContext.() -> Unit,
    ) {
        val context = this
        // A failing action is logged and skipped, so one broken optional setup doesn't fail
        // every build; with `rethrow` (the build asked for what the action sets up) it fails it.
        val run = {
            try {
                context.action()
            } catch (e: Throwable) {
                if (rethrow) throw e
                // A warning: the build goes on, and the message names the fix. The trace is
                // only for debugging fluxo itself.
                rootProject.reportProblem(
                    FluxoProblem.SETUP_STEP_SKIPPED,
                    "Setup step skipped: ${e.message ?: e}",
                )
                rootProject.logger.v("Setup step failure", e)
            }
        }
        when {
            forceIf || isProjectInSyncRun -> run()
            else -> projectInSyncFlag.configureEach { run() }
        }
    }

    private fun taskGraphBasedProjectSyncDetection() {
        // TODO: Better integration with `gradle-idea-ext-plugin` or `idea` plugins.
        //  https://github.com/JetBrains/gradle-idea-ext-plugin
        // Gradle 8.11+ limits lazy plugin configuration, so `onProjectInSyncRun` cannot be
        // relied on for IDE-import detection at config time. Mark eagerly. Floor is now
        // Gradle 9.x via the wrapper, so the version gate is unconditional.
        // Keep it unconditional even though `idea.sync.active` and the requested import tasks
        // would allow detecting a sync before configuration: since 0.14.0 every plain build
        // has applied dependency-guard and set up publication, and has registered
        // `dependencyUpdates`, `allDeps`, `resolveDependencies` and the `printKotlin*` tasks.
        // Consumers reach those tasks by name abbreviation and `tasks.named`, and this repo's
        // own `compatibilityTest` depends on the local publication. Gating any of them is a
        // breaking change, and on `checks/kmp` sync work measured no configuration-time cost.
        markProjectInSync(reason = "${GradleVersion.current()}")
    }

    // endregion


    internal companion object {
        internal fun getFor(target: Project): FluxoKmpConfContext {
            return target.extensions.create(NAME, FluxoKmpConfContext::class.java, target)
        }

        private const val NAME = "fluxoInternalConfigurationContext"

        // https://twitter.com/Sellmair/status/1619308362881187840
        internal const val KOTLIN_IDEA_IMPORT_TASK = "prepareKotlinIdeaImport"
        internal const val KOTLIN_IDEA_BSM_TASK = "prepareKotlinBuildScriptModel"
    }
}

/**
 * Compose Multiplatform's plugin version when it is on the root build classpath. Read by name: the
 * plugin is optional, and its version is a `const` that a typed read would inline at our compile.
 */
private fun composePluginVersion(project: Project): String? = try {
    val cl = project.buildscript.classLoader
    Class.forName("org.jetbrains.compose.ComposeBuildConfig", false, cl)
        .getField("composeVersion").get(null) as? String
} catch (_: ReflectiveOperationException) {
    null
} catch (_: LinkageError) {
    null
}
