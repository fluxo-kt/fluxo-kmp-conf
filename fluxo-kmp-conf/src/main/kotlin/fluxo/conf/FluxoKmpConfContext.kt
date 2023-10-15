package fluxo.conf

import allKmpTargetsEnabled
import areComposeMetricsEnabled
import disableTests
import fluxo.conf.deps.GradleProvisioner
import fluxo.conf.deps.Provisioner
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.container.impl.KmpTargetCode.Companion.getSetOfRequestedKmpTargets
import fluxo.conf.feat.registerDetektMergeRootTask
import fluxo.conf.feat.registerLintMergeRootTask
import fluxo.conf.impl.CPUs
import fluxo.conf.impl.SHOW_DEBUG_LOGS
import fluxo.conf.impl.d
import fluxo.conf.impl.e
import fluxo.conf.impl.kotlin.JRE_VERSION_STRING
import fluxo.conf.impl.kotlin.KotlinConfig
import fluxo.conf.impl.kotlin.kotlinPluginVersion
import fluxo.conf.impl.kotlin.mppAndroidSourceSetLayoutVersion
import fluxo.conf.impl.l
import fluxo.conf.impl.libsCatalogOptional
import fluxo.conf.impl.tryAsBoolean
import fluxo.conf.impl.v
import fluxo.conf.impl.w
import getValue
import isCI
import isDesugaringEnabled
import isMaxDebugEnabled
import isR8Disabled
import isRelease
import javax.inject.Inject
import org.gradle.api.DomainObjectSet
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.internal.tasks.JvmConstants.TEST_TASK_NAME
import org.gradle.api.plugins.PluginAware
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME
import useKotlinDebug

internal abstract class FluxoKmpConfContext
@Inject constructor(
    val rootProject: Project,
) : PluginAware by rootProject {

    private val projectInSyncSet: DomainObjectSet<ProjectInSyncMarker> =
        rootProject.objects.domainObjectSet(ProjectInSyncMarker::class.java)

    internal val provisioner: Provisioner = GradleProvisioner.DedupingProvisioner(
        GradleProvisioner.forRootProjectBuildscript(rootProject),
    )

    @get:Inject
    internal abstract val eventsListenerRegistry: BuildEventsListenerRegistry


    val libs: VersionCatalog? = rootProject.libsCatalogOptional

    val kotlinPluginVersion: KotlinVersion = rootProject.logger.kotlinPluginVersion()

    lateinit var kotlinConfig: KotlinConfig
        internal set

    /**
     * [Kotlin 1.8](https://kotlinlang.org/docs/whatsnew18.html#kotlinsourceset-naming-schema)
     * added a new source set layout for Android projects.
     * It's the default since
     * [Kotlin 1.9](https://kotlinlang.org/docs/whatsnew19.html#new-android-source-set-layout-enabled-by-default)
     */
    val androidLayoutV2: Boolean = rootProject.mppAndroidSourceSetLayoutVersion(kotlinPluginVersion)

    val testsDisabled: Boolean


    val isInCompositeBuild get() = rootProject.gradle.includedBuilds.size > 1

    val isCI: Boolean = rootProject.isCI().get()
    val isRelease: Boolean = rootProject.isRelease().get()
    val isMaxDebug: Boolean = rootProject.isMaxDebugEnabled().get()
    val isDesugaringEnabled by rootProject.isDesugaringEnabled()
    val useKotlinDebug by rootProject.useKotlinDebug()
    val composeMetricsEnabled by rootProject.areComposeMetricsEnabled()

    private val kmpTargets: Set<KmpTargetCode> = getSetOfRequestedKmpTargets()
    private val allTargetsEnabled = rootProject.allKmpTargetsEnabled() || kmpTargets.isEmpty()
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

        if (isMaxDebug) SHOW_DEBUG_LOGS = true

        // Log environment
        run {
            var m = "Gradle ${gradle.gradleVersion}, " +
                "JRE $JRE_VERSION_STRING, " +
                "Kotlin $kotlinPluginVersion, " +
                "$CPUs CPUs"
            try {
                // https://r8.googlesource.com/r8/+refs
                // https://issuetracker.google.com/issues/193543616#comment4
                // https://mvnrepository.com/artifact/com.android.tools/r8
                val r8 = com.android.tools.r8.Version.getVersionString().substringBefore(" (")
                m += ", R8 $r8"
            } catch (_: Throwable) {
            }
            logger.l(m)
        }

        if (SHOW_DEBUG_LOGS) onProjectInSyncRun { logger.v("onProjectInSyncRun") }

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

        val isInIde = start.systemPropertiesArgs["idea.active"].tryAsBoolean()
        if (isInIde && startTaskNames.isEmpty()) {
            markProjectInSync()
        } else {
            taskGraphBasedProjectSyncDetection()
        }

        if (isCI) logger.l("CI mode is enabled!")
        if (isRelease) logger.l("RELEASE mode is enabled!")
        if (composeMetricsEnabled) logger.l("COMPOSE_METRICS are enabled!")

        if (useKotlinDebug) logger.w("USE_KOTLIN_DEBUG is enabled!")
        if (isMaxDebug) logger.w("MAX_DEBUG is enabled!")
        if (isDesugaringEnabled) logger.w("DESUGARING is enabled!")
        if (project.isR8Disabled().get()) logger.w("R8 is disabled! (DISABLE_R8)")

        testsDisabled = project.disableTests().get() || start.excludedTaskNames.let {
            CHECK_TASK_NAME in it || TEST_TASK_NAME in it
        }
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
        logger.v("kotlinPluginVersion: $kotlinPluginVersion")
        logger.v("isInIde: $isInIde")
    }


    val mergeLintTask = registerLintMergeRootTask()
    val mergeDetektTask = registerDetektMergeRootTask()


    // region Project IDE synchronization detection

    internal val isProjectInSyncRun: Boolean
        get() = projectInSyncSet.isNotEmpty()

    /**
     * Configures the project to apply everything that can be applied.
     * It's a special mode for IDE synchronization and other similar processes.
     *
     * @return `false` if was already done earlier.
     */
    fun markProjectInSync(): Boolean = projectInSyncSet.add(ProjectInSyncMarker)

    /**
     * Runs provided `action` if the project is in sync mode now or will be marked for it later.
     *
     * @see markProjectInSync
     * @FIXME Allow to use it from any build scripts
     */
    fun onProjectInSyncRun(forceIf: Boolean = false, action: FluxoKmpConfContext.() -> Unit) {
        val context = this
        when {
            forceIf || isProjectInSyncRun -> context.action()
            else -> projectInSyncSet.all {
                context.action()
            }
        }
    }

    private fun taskGraphBasedProjectSyncDetection() {
        // TODO: Better integration with `gradle-idea-ext-plugin` or `idea` plugins.
        //  https://github.com/JetBrains/gradle-idea-ext-plugin
        val logger = rootProject.logger
        rootProject.gradle.taskGraph.whenReady {
            if (!isProjectInSyncRun) {
                val hasImportTasksInGraph = allTasks.any {
                    it.path.let { p ->
                        p.endsWith(KOTLIN_IDEA_IMPORT_TASK) || p.endsWith(KOTLIN_IDEA_BSM_TASK)
                    }
                }
                if (hasImportTasksInGraph && !isProjectInSyncRun) {
                    logger.d("IDE project sync")
                    markProjectInSync()
                }
            }
        }
    }

    // endregion


    internal companion object {
        internal fun getFor(target: Project): FluxoKmpConfContext {
            return target.rootProject.extensions
                .create(NAME, FluxoKmpConfContext::class.java, target)
        }

        private const val NAME = "fluxoInternalConfigurationContext"

        // https://twitter.com/Sellmair/status/1619308362881187840
        private const val KOTLIN_IDEA_IMPORT_TASK = "prepareKotlinIdeaImport"
        private const val KOTLIN_IDEA_BSM_TASK = "prepareKotlinBuildScriptModel"
    }

    private object ProjectInSyncMarker
}
