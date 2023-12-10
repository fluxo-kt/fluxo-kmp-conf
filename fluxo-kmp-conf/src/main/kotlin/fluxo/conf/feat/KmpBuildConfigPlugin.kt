package fluxo.conf.feat

import com.github.gmazzo.gradle.plugins.BuildConfigTask
import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.FluxoKmpConfContext.Companion.KOTLIN_IDEA_BSM_TASK
import fluxo.conf.FluxoKmpConfContext.Companion.KOTLIN_IDEA_IMPORT_TASK
import fluxo.conf.data.BuildConstants.BUILD_CONFIG_PLUGIN_ID
import fluxo.conf.impl.maybeRegister
import fluxo.conf.impl.withType
import org.gradle.api.Project
import org.gradle.api.Task

// A plugin for generating BuildConstants for any kind of Gradle projects: Java, Kotlin, Android, Groovy, etc.
// Designed for KTS scripts, with experimental support for Kotlin's multi-platform plugin
// https://github.com/gmazzo/gradle-buildconfig-plugin/releases
// https://plugins.gradle.org/plugin/com.github.gmazzo.buildconfig
internal fun FluxoKmpConfContext.prepareKmpBuildConfigPlugin(project: Project) {
    plugins.withId(BUILD_CONFIG_PLUGIN_ID) {
        // Automatically build configs during Gradle sync in IDEA
        onProjectInSyncRun {
            val buildConfigTasks = project.tasks.withType<BuildConfigTask>()
            val configureSyncTasks: Task.() -> Unit = {
                dependsOn(buildConfigTasks)
            }
            project.tasks.maybeRegister(KOTLIN_IDEA_IMPORT_TASK, configureSyncTasks)
            project.tasks.maybeRegister(KOTLIN_IDEA_BSM_TASK, configureSyncTasks)
        }
    }
}
