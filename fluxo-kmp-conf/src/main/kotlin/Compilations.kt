@file:Suppress("DeprecatedCallableAddReplaceWith")

import fluxo.conf.impl.EnvParams
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.isTaskAllowedBasedByName
import fluxo.conf.impl.isTestRelated
import fluxo.conf.impl.kotlin.disableCompilation
import fluxo.conf.impl.splitCamelCase
import fluxo.conf.impl.withType
import org.gradle.api.Project
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.targets
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import org.jetbrains.kotlin.konan.target.Family

@Deprecated("Use FluxoKmpConfContext.requestedKmpTargets instead")
public object Compilations {

    public val isGenericEnabled: Boolean get() = isValidOs { it.isLinux }
    public val isDarwinEnabled: Boolean get() = isValidOs { it.isMacOsX }
    public val isWindowsEnabled: Boolean get() = isValidOs { it.isWindows }

    public fun isGenericEnabledForProject(project: Project): Boolean = when {
        project.isCI().get() -> isDarwinEnabled
        else -> isGenericEnabled
    }

    private fun isValidOs(predicate: (OperatingSystem) -> Boolean): Boolean =
        !EnvParams.splitTargets || predicate(OperatingSystem.current())
}

// FIXME: replace implementation with FluxoKmpConfContext usage
public val Project.isGenericCompilationEnabled: Boolean
    get() = Compilations.isGenericEnabledForProject(this)

// FIXME: update implementation with FluxoKmpConfContext usage
internal fun KotlinProjectExtension.disableCompilationsOfNeeded(project: Project) {
    val disableTests by project.disableTests()
    targets.forEach {
        it.disableCompilationsOfNeeded(disableTests)
    }

    if (!EnvParams.splitTargets) {
        if (disableTests) {
            // yarn.lock calculated differently without tests, ignore mismatch
            val rootProject = project.rootProject
            rootProject.plugins.withType<YarnPlugin> {
                rootProject.configureExtension<YarnRootExtension> {
                    yarnLockMismatchReport = YarnLockMismatchReport.NONE
                    yarnLockAutoReplace = false
                    reportNewYarnLock = false
                }
            }
        }
        return
    }
    project.afterEvaluate {
        tasks.withType<org.gradle.jvm.tasks.Jar> {
            if (enabled && !isTaskAllowedBasedByName()) {
                logger.info("{}, {}, jar disabled", this.project, this)
                enabled = false
            }
        }

        if (!isGenericCompilationEnabled) {
            /**
             * @see org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsSetupTask
             * @see org.jetbrains.kotlin.gradle.targets.js.npm.PublicPackageJsonTask
             * @see org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinNpmInstallTask
             * @see org.jetbrains.kotlin.gradle.targets.js.typescript.TypeScriptValidationTask
             * @see org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockStoreTask
             */
            tasks
                .matching { t ->
                    t::class.java.name.startsWith("org.jetbrains.kotlin.gradle.targets.js.")
                }
                .configureEach {
                    if (enabled) {
                        logger.info("{}, {}, task disabled", project, this)
                        enabled = false
                    }
                }
        }
    }
}

private fun KotlinTarget.disableCompilationsOfNeeded(disableTests: Boolean) {
    val logger = project.logger
    if (!isCompilationAllowed()) {
        logger.info("{}, {}, target compilations disabled", project, this)
        disableCompilations(testOnly = false)
    } else if (disableTests) {
        logger.info("{}, {}, target test compilations disabled", project, this)
        disableCompilations(testOnly = true)
    }
}

private fun KotlinTarget.disableCompilations(testOnly: Boolean = false) {
    compilations.configureEach {
        if (!testOnly || isTestRelated()) {
            disableCompilation()
        }
    }
}

// FIXME: update implementation with FluxoKmpConfContext usage
private fun KotlinTarget.isCompilationAllowed(): Boolean = when (platformType) {
    KotlinPlatformType.common -> true

    KotlinPlatformType.jvm,
    KotlinPlatformType.js,
    KotlinPlatformType.androidJvm,
    KotlinPlatformType.wasm,
    -> project.isGenericCompilationEnabled

    KotlinPlatformType.native ->
        (this as KotlinNativeTarget).konanTarget.family.isCompilationAllowed(project)
}

// FIXME: update implementation with FluxoKmpConfContext usage
private fun Family.isCompilationAllowed(project: Project): Boolean = when (this) {
    Family.OSX,
    Family.IOS,
    Family.TVOS,
    Family.WATCHOS,
    -> Compilations.isDarwinEnabled

    Family.LINUX -> Compilations.isGenericEnabled

    Family.ANDROID,
    Family.WASM,
    -> project.isGenericCompilationEnabled

    Family.MINGW -> Compilations.isWindowsEnabled

    Family.ZEPHYR -> error("Unsupported family: $this")
}

// FIXME: update implementation with FluxoKmpConfContext usage
internal fun AbstractTestTask.isTestTaskAllowed(): Boolean {
    return when (this) {
        is KotlinJsTest ->
            project.isGenericCompilationEnabled

        is KotlinNativeTest ->
            nativeFamilyFromString(platformFromTaskName(name)).isCompilationAllowed(project)

        // JVM/Android tests
        else -> project.isGenericCompilationEnabled
    }
}

private fun platformFromTaskName(name: String): String? =
    name.splitCamelCase(limit = 2).firstOrNull()

@Suppress("CyclomaticComplexMethod")
private fun nativeFamilyFromString(platform: String?): Family = when {
    platform.equals("watchos", ignoreCase = true) -> Family.WATCHOS
    platform.equals("tvos", ignoreCase = true) -> Family.TVOS
    platform.equals("ios", ignoreCase = true) -> Family.IOS

    platform.equals("darwin", ignoreCase = true) ||
        platform.equals("apple", ignoreCase = true) ||
        platform.equals("macos", ignoreCase = true)
    -> Family.OSX

    platform.equals("android", ignoreCase = true) -> Family.ANDROID
    platform.equals("linux", ignoreCase = true) -> Family.LINUX
    platform.equals("wasm", ignoreCase = true) -> Family.WASM

    platform.equals("mingw", ignoreCase = true) ||
        platform.equals("win", ignoreCase = true) ||
        platform.equals("windows", ignoreCase = true)
    -> Family.MINGW

    else -> throw IllegalArgumentException("Unsupported family: $platform")
}
