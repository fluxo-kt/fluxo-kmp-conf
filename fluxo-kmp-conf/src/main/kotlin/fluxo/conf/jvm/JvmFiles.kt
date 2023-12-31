package fluxo.conf.jvm

import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider

internal class JvmFiles(
    val allRuntimeJars: FileCollection,
    val mainJar: Provider<RegularFile>,
    private val taskDependencies: Array<Any>,
) {
    operator fun component1() = allRuntimeJars

    operator fun component2() = mainJar

    fun <T : Task> configureUsageBy(task: T, fn: T.(JvmFiles) -> Unit) {
        task.dependsOn(taskDependencies)
        task.fn(this)
    }
}
