package fluxo.conf

import allKmpTargetsEnabled
import areComposeMetricsEnabled
import disableTests
import fluxo.conf.deps.GradleProvisioner
import fluxo.conf.deps.Provisioner
import fluxo.conf.impl.SHOW_DEBUG_LOGS
import fluxo.conf.impl.e
import fluxo.conf.impl.l
import fluxo.conf.impl.libsCatalogOptional
import fluxo.conf.impl.tryAsBoolean
import fluxo.conf.impl.v
import fluxo.conf.impl.w
import fluxo.conf.target.KmpTargetCode
import fluxo.conf.target.KmpTargetCode.Companion.getSetOfRequestedKmpTargets
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
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
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


    val libs: VersionCatalog? = rootProject.libsCatalogOptional

    val kotlinPluginVersion: KotlinVersion = kotlinPluginVersion()

    val requestedKmpTargets: Set<KmpTargetCode> = rootProject.getSetOfRequestedKmpTargets()

    val allKmpTargetsEnabled: Boolean =
        rootProject.allKmpTargetsEnabled() || requestedKmpTargets.isEmpty()

    val testsDisabled: Boolean


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
        startTaskNames = start.taskNames.map {
            it.substringAfterLast(":")
        }.toSet()

        val isInIde = start.systemPropertiesArgs["idea.active"].tryAsBoolean()
        if (isInIde && startTaskNames.isEmpty()) {
            markProjectInSync()
        }

        val isCI by project.isCI()
        if (isCI) logger.l("CI mode is enabled!")
        if (project.isRelease().get()) logger.l("RELEASE mode is enabled!")
        if (project.areComposeMetricsEnabled().get()) logger.l("COMPOSE_METRICS mode is enabled!")

        if (project.useKotlinDebug().get()) logger.w("USE_KOTLIN_DEBUG mode is enabled!")
        if (project.isMaxDebugEnabled().get()) logger.w("MAX_DEBUG mode is enabled!")
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


    private fun kotlinPluginVersion(): KotlinVersion {
        val logger = rootProject.logger
        try {
            getKotlinPluginVersion(logger).let { versionString ->
                val baseVersion = versionString.split("-", limit = 2)[0]
                val parts = baseVersion.split(".")
                return KotlinVersion(
                    major = parts[0].toInt(),
                    minor = parts[1].toInt(),
                    patch = parts.getOrNull(2)?.toInt() ?: 0,
                )
            }
        } catch (e: Throwable) {
            logger.e("Failed to get Kotlin plugin version: $e", e)
        }
        return KotlinVersion.CURRENT
    }


    internal companion object {
        internal fun getFor(target: Project): FluxoKmpConfContext {
            return target.rootProject.extensions
                .create(NAME, FluxoKmpConfContext::class.java, target)
        }

        private const val NAME = "fluxoInternalConfigurationContext"
    }

    private object ProjectInSyncMarker
}
