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
import fluxo.conf.impl.SHOW_DEBUG_LOGS
import fluxo.conf.impl.TOTAL_OS_MEMORY
import fluxo.conf.impl.XMX
import fluxo.conf.impl.d
import fluxo.conf.impl.e
import fluxo.conf.impl.kotlin.JRE_VERSION_STRING
import fluxo.conf.impl.kotlin.kotlinPluginVersion
import fluxo.conf.impl.kotlin.mppAndroidSourceSetLayoutVersion
import fluxo.conf.impl.l
import fluxo.conf.impl.tryAsBoolean
import fluxo.conf.impl.v
import fluxo.conf.impl.w
import fluxo.util.readableByteSize
import fluxo.vc.FluxoVersionCatalog
import getValue
import isCI
import isDesugaringEnabled
import isFluxoVerbose
import isMaxDebugEnabled
import isR8Disabled
import isRelease
import javax.inject.Inject
import org.gradle.api.DomainObjectSet
import org.gradle.api.Project
import org.gradle.api.internal.tasks.JvmConstants.TEST_TASK_NAME
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME
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

    internal val provisioner: Provisioner = GradleProvisioner.DedupingProvisioner(
        GradleProvisioner.forRootProjectBuildscript(rootProject),
    )

    @get:Inject
    internal abstract val eventsListenerRegistry: BuildEventsListenerRegistry


    @Suppress("LeakingThis")
    val libs = FluxoVersionCatalog(rootProject, context = this)

    val kotlinPluginVersion: KotlinVersion = rootProject.logger.kotlinPluginVersion()

    /**
     * [Kotlin 1.8](https://kotlinlang.org/docs/whatsnew18.html#kotlinsourceset-naming-schema)
     * added a new source set layout for Android projects.
     * It's the default since
     * [Kotlin 1.9](https://kotlinlang.org/docs/whatsnew19.html#new-android-source-set-layout-enabled-by-default)
     */
    val androidLayoutV2: Boolean = rootProject.mppAndroidSourceSetLayoutVersion(kotlinPluginVersion)

    val testsDisabled: Boolean


    /**
     * Whether the project is part of a composite build.
     *
     * `true` when the project has "included" builds.
     */
    private val isInCompositeBuild: Boolean

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

        val isVerbose = isMaxDebug || logger.isInfoEnabled || project.isFluxoVerbose().get()
        if (isVerbose) {
            SHOW_DEBUG_LOGS = true
        }

        // Log environment
        run {
            var m = "Gradle ${gradle.gradleVersion},  " +
                "JRE $JRE_VERSION_STRING,  " +
                "Kotlin $kotlinPluginVersion,  " +
                "$CPUs CPUs,  " +
                "${readableByteSize(XMX)} XMX"

            // TODO: GC stats
            //  https://github.com/gradle/gradle/blob/3eda2dd/platforms/core-runtime/launcher/src/main/java/org/gradle/launcher/daemon/server/health/DaemonHealthStats.java#L87
            val ram = TOTAL_OS_MEMORY
            if (ram > 0) {
                m += " from ${readableByteSize(ram)} RAM"
            }

            // Bundled/Classpath R8 version
            try {
                // https://r8.googlesource.com/r8/+refs
                // https://issuetracker.google.com/issues/193543616#comment4
                // https://mvnrepository.com/artifact/com.android.tools/r8
                /** Cannot use [com.android.tools.r8.Version.LABEL] directly here
                 *  as it will be inlined during compilation. */
                val r8 = com.android.tools.r8.Version.getVersionString().substringBefore(" (")
                m += ", Bundled R8 $r8"
            } catch (_: Throwable) {
            }

            // Bundled/Classpath ProGuard version
            try {
                val pg = proguard.ProGuard.getVersion()
                m += ", Bundled ProGuard $pg"
            } catch (_: Throwable) {
            }

            logger.l(m)
        }

        if (SHOW_DEBUG_LOGS) {
            onProjectInSyncRun {
                val reason = projectInSyncFlag.firstOrNull()
                logger.d("onProjectInSyncRun, because $reason")
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
            logger.l("COMPOSITE BUILD! ($compositeMsg)")
        } else if (isVerbose) {
            logger.l("NOT in COMPOSITE build! ($compositeMsg)")
        }

        if (start.isDryRun) logger.l("DryRun mode is enabled!")
        if (start.isContinueOnFailure) logger.l("ContinueOnFailure mode is enabled!")
        if (isCI) logger.l("CI mode is enabled!")
        if (isRelease) logger.l("RELEASE mode is enabled!")
        if (composeMetricsEnabled) logger.l("COMPOSE_METRICS are enabled!")

        if (useKotlinDebug) logger.w("USE_KOTLIN_DEBUG is enabled!")
        when {
            isMaxDebug -> logger.w("MAX_DEBUG is enabled!")
            isVerbose -> logger.w("FLUXO_VERBOSE is enabled!")
        }
        if (isDesugaringEnabled) logger.w("DESUGARING is enabled!")
        if (project.isR8Disabled().get()) {
            logger.w("SHRINKING (R8/ProGuard) is disabled! (DISABLE_R8)")
        }

        // Disable all tests if:
        //  - `DISABLE_TESTS` is enabled;
        //  - `check` or `test` tasks are excluded from the build;
        testsDisabled = project.disableTests().get() ||
            start.excludedTaskNames.let { CHECK_TASK_NAME in it || TEST_TASK_NAME in it }

        if (testsDisabled) {
            var reported = false
            for (name in startTaskNames) {
                if (CHECK_TASK_NAME in name || TEST_TASK_NAME in name) {
                    reported = true
                    logger.e("DISABLE_TESTS mode is enabled while calling $name task!")
                    break
                }
            }
            if (!reported) logger.w("Tests are disabled! (DISABLE_TESTS)")
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
    fun onProjectInSyncRun(forceIf: Boolean = false, action: FluxoKmpConfContext.() -> Unit) {
        val context = this
        when {
            forceIf || isProjectInSyncRun -> {
                try {
                    context.action()
                } catch (e: Throwable) {
                    rootProject.logger.e("Failed to run onProjectInSyncRun action: $e", e)
                }
            }

            else -> projectInSyncFlag.all {
                try {
                    context.action()
                } catch (e: Throwable) {
                    rootProject.logger.e("Failed to run onProjectInSyncRun action: $e", e)
                }
            }
        }
    }

    private fun taskGraphBasedProjectSyncDetection() {
        // TODO: Better integration with `gradle-idea-ext-plugin` or `idea` plugins.
        //  https://github.com/JetBrains/gradle-idea-ext-plugin
        rootProject.gradle.taskGraph.whenReady {
            if (!isProjectInSyncRun) {
                val hasImportTaskInGraph = allTasks.any {
                    it.path.let { p ->
                        p.endsWith(KOTLIN_IDEA_IMPORT_TASK) || p.endsWith(KOTLIN_IDEA_BSM_TASK)
                    }
                }
                if (hasImportTaskInGraph && !isProjectInSyncRun) {
                    markProjectInSync(reason = "project has import task in graph")
                }
            }
        }
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
