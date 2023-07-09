package fluxo.conf

import allKmpTargetsEnabled
import areComposeMetricsEnabled
import disableTests
import fluxo.conf.deps.GradleProvisioner
import fluxo.conf.deps.Provisioner
import fluxo.conf.impl.SHOW_DEBUG_LOGS
import fluxo.conf.impl.d
import fluxo.conf.impl.e
import fluxo.conf.impl.kotlin.kotlinPluginVersion
import fluxo.conf.impl.kotlin.mppAndroidSourceSetLayoutVersion
import fluxo.conf.impl.l
import fluxo.conf.impl.libsCatalogOptional
import fluxo.conf.impl.tryAsBoolean
import fluxo.conf.impl.v
import fluxo.conf.impl.w
import fluxo.conf.kmp.KmpTargetCode
import fluxo.conf.kmp.KmpTargetCode.Companion.getSetOfRequestedKmpTargets
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

    /**
     * [Kotlin 1.8](https://kotlinlang.org/docs/whatsnew18.html#kotlinsourceset-naming-schema)
     * added a new source set layout for Android projects.
     * It's the default since
     * [Kotlin 1.9](https://kotlinlang.org/docs/whatsnew19.html#new-android-source-set-layout-enabled-by-default)
     */
    val androidLayoutV2: Boolean = rootProject.mppAndroidSourceSetLayoutVersion(kotlinPluginVersion)

    val testsDisabled: Boolean


    val isInCompositeBuild get() = rootProject.gradle.includedBuilds.size > 1

    val isCI by rootProject.isCI()
    val isRelease by rootProject.isRelease()
    val isMaxDebug by rootProject.isMaxDebugEnabled()
    val useKotlinDebug by rootProject.useKotlinDebug()
    val composeMetricsEnabled by rootProject.areComposeMetricsEnabled()

    val requestedKmpTargets: Set<KmpTargetCode> = getSetOfRequestedKmpTargets()

    val allKmpTargetsEnabled: Boolean =
        rootProject.allKmpTargetsEnabled() || requestedKmpTargets.isEmpty()


    val startTaskNames: Set<String>

    fun hasAnyTaskCalled(name: String) = name in startTaskNames

    @JvmName("hasAnyTaskCalledVararg")
    fun hasAnyTaskCalled(vararg name: String) = hasAnyTaskCalled(name)

    fun hasAnyTaskCalled(names: Array<out String>) = names.any { it in startTaskNames }


    init {
        val project = rootProject
        val gradle = project.gradle
        val logger = project.logger

        // Log environment
        run {
            val gradleVersion = gradle.gradleVersion
            val java = System.getProperty("java.version")
            val cpus = Runtime.getRuntime().availableProcessors()
            logger.l("Gradle $gradleVersion, JRE $java, $cpus CPUs")
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
        if (composeMetricsEnabled) logger.l("COMPOSE_METRICS mode is enabled!")

        if (useKotlinDebug) logger.w("USE_KOTLIN_DEBUG mode is enabled!")
        if (isMaxDebug) logger.w("MAX_DEBUG mode is enabled!")
        if (project.isDesugaringEnabled().get()) logger.w("DESUGARING mode is enabled!")
        if (project.isR8Disabled().get()) logger.w("DISABLE_R8 mode is enabled!")

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
            if (!reported) logger.w("DISABLE_TESTS mode is enabled!")
        }

        if (SHOW_DEBUG_LOGS) logger.v("Cleaned start task names: $startTaskNames")
        logger.v("kotlinPluginVersion: $kotlinPluginVersion")
        logger.v("isInIde: $isInIde")
    }


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
