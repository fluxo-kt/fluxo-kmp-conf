package fluxo.artifact.proc

import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.lc
import fluxo.conf.impl.named
import fluxo.log.e
import fluxo.log.l
import fluxo.log.w
import fluxo.vc.l
import fluxo.vc.v
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.jetbrains.compose.desktop.application.dsl.JvmApplication
import org.jetbrains.compose.desktop.application.tasks.AbstractProguardTask

/**
 *
 * @see org.jetbrains.compose.desktop.application.internal.configureProguardTask
 * @see org.jetbrains.compose.desktop.application.internal.configurePackagingTasks
 * @see org.jetbrains.compose.desktop.application.internal.configurePackageTask
 * @see org.jetbrains.compose.desktop.application.tasks.AbstractProguardTask
 * @see proguard.gradle.ProGuardTask
 */
internal fun Project.processComposeDesktopArtifact(
    conf: FluxoConfigurationExtensionImpl,
    jvmApp: JvmApplication,
    jarProvider: Provider<out RegularFile>,
    destinationDir: Provider<out Directory>,
    builtBy: Any? = null,
) {
    // References:
    // https://github.com/JetBrains/compose-multiplatform/issues/1174#issuecomment-1200122370

    logger.l("Setup jar processing for Compose Desktop app..")

    val composeProguard = jvmApp.buildTypes.release.proguard
    if (composeProguard.isEnabled.orNull != true) {
        logger.e(
            "ProGuard is disabled for Compose Desktop app release build! " +
                "Please enable it first!",
        )
        return
    }
    composeProguard.isEnabled.set(true)

    // Set ProGuard version
    val libs = conf.ctx.libs
    val toolLc = JvmShrinker.ProGuard.name.lc()
    val version = libs.v(toolLc) ?: libs.l(toolLc)?.version
    if (!version.isNullOrEmpty()) {
        composeProguard.version.set(version)
    }

    tasks.named<AbstractProguardTask>(JB_COMPOSE_PROGUARD_TASK).configure {
        builtBy?.let { dependsOn(it) }
        actions.clear()
        this.destinationDir.set(destinationDir)
        this.mainJar.set(jarProvider)
        doFirst {}
    }
    logger.w("Default Compose Desktop app ProGuard IS NOW REPLACED with Fluxo processing!")
}

private const val JB_COMPOSE_PROGUARD_TASK = "proguardReleaseJars"
