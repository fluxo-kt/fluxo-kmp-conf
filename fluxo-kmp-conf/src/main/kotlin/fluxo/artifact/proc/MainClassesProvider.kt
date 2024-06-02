package fluxo.artifact.proc

import fluxo.conf.impl.kotlin.KMP_COMPOSE_PLUGIN_ID
import fluxo.conf.impl.memoizeSafe
import fluxo.log.w
import org.gradle.api.Project
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.provider.Provider
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.desktop.DesktopExtension
import org.jetbrains.compose.desktop.application.dsl.JvmApplication

/**
 * Provides the main class of the JVM project if detected.
 *
 * [JavaApplication] and [ComposeExtension] are checked.
 */
internal fun Project.getMainClassesProvider(): Provider<out Iterable<String>> {
    val extensions = extensions
    return provider p@{
        // Java Application plugin
        val app = extensions.findByName("application")
        if (app != null) {
            if (app is JavaApplication) {
                val mainClass = app.mainClass.orNull
                if (!mainClass.isNullOrBlank()) {
                    return@p listOf(mainClass)
                }
                logger.w("Main class is not set in the 'application' extension!")
            } else {
                logger.w("Unknown application plugin: $app (${app.javaClass.name})")
            }
        }

        // Compose Desktop application
        val jvmApp = composeDesktopApplication()
        if (jvmApp != null && jvmApp is JvmApplication) {
            val mainClass = jvmApp.mainClass
            if (!mainClass.isNullOrBlank()) {
                return@p listOf(mainClass)
            }
            logger.w("Main class is not set in the 'compose.desktop.application'!")
        }

        return@p emptyList()
    }.memoizeSafe()
}

/**
 *
 * WARN: can be misleading if the plugin is not applied but will be later.
 *
 * @return the [JvmApplication] or null.
 */
internal fun Project.composeDesktopApplication(): Any? {
    val compose = extensions.findByName("compose")
    if (compose != null) {
        try {
            val desktop = (compose as ComposeExtension).extensions
                .findByName("desktop") as DesktopExtension

            if (desktop.isJvmApplication0) {
                return desktop.application
            }
        } catch (e: Throwable) {
            logger.w("JB Compose plugin error: $compose (${compose.javaClass.name})", e)
        }
    }
    return null
}

internal fun Project.onComposeDesktopApplication(
    action: (JvmApplication) -> Unit,
) {
    pluginManager.withPlugin(KMP_COMPOSE_PLUGIN_ID) {
        extensions.configure<ComposeExtension>("compose") c@{
            this.extensions.configure<DesktopExtension>("desktop") {
                // No lazy API here, wait for the evaluation to be sure.
                afterEvaluate {
                    if (isJvmApplication0) {
                        action(application)
                    }
                }
            }
        }
    }
}

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
private val DesktopExtension.isJvmApplication0: Boolean
    get() = _isJvmApplicationInitialized
