package fluxo.conf.feat

import com.github.gmazzo.buildconfig.BuildConfigTask
import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.FluxoKmpConfContext.Companion.KOTLIN_IDEA_BSM_TASK
import fluxo.conf.data.BuildConstants.BUILD_CONFIG_PLUGIN_ID
import fluxo.conf.impl.maybeRegister
import fluxo.conf.impl.namedCompat
import fluxo.conf.impl.withType
import fluxo.log.l
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.Task

// A plugin for generating BuildConstants for any kind of Gradle projects:
//  Java, Kotlin, Android, Groovy, etc.
// Designed for KTS scripts, with experimental support for Kotlin's multi-platform plugin
// https://github.com/gmazzo/gradle-buildconfig-plugin/releases
// https://plugins.gradle.org/plugin/com.github.gmazzo.buildconfig
internal fun FluxoKmpConfContext.prepareBuildConfigKmpPlugin(project: Project) {
    plugins.withId(BUILD_CONFIG_PLUGIN_ID) {
        // Automatically build configs during Gradle sync in IDE
        onProjectInSyncRun {
            project.logger.l("prepareKmpBuildConfigPlugin")

            val tasks = project.tasks
            val buildConfigTasks = tasks.withType<BuildConfigTask>()
            val configureSyncTasks: Task.() -> Unit = {
                dependsOn(buildConfigTasks)
            }

            // Since 5.3.0 it generates build config class at Gradle Sync (IDEA) itself.
            // But only the ` prepareKotlinIdeaImport ` task is used.
            // https://github.com/gmazzo/gradle-buildconfig-plugin/pull/113/files
            // https://github.com/gmazzo/gradle-buildconfig-plugin/pull/114/files
//            tasks.maybeRegister(KOTLIN_IDEA_IMPORT_TASK, configureSyncTasks)
            tasks.maybeRegister(KOTLIN_IDEA_BSM_TASK, configureSyncTasks)
        }
    }
}

internal fun Project.markAsMustRunAfterBuildConfigTasks(
    forTask: NamedDomainObjectProvider<out Task>,
) {
    plugins.withId(BUILD_CONFIG_PLUGIN_ID) {
        // NOTE: tasks.withType<BuildConfigTask>() fails here with NoClassDefFoundError.
        // So find the tasks by name instead.
        /** @see com.github.gmazzo.buildconfig.BuildConfigPlugin.configureSourceSet */
        val buildConfigTasks = tasks.namedCompat { name ->
            name.startsWith("generate") && name.endsWith("BuildConfig")
        }
        forTask.configure {
            mustRunAfter(buildConfigTasks)
        }
    }
}
