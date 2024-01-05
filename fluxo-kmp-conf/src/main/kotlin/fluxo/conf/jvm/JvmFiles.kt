package fluxo.conf.jvm

import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider

internal class JvmFiles(
    val mainJar: Provider<RegularFile>,
    val allJars: FileCollection,
    private val taskDependencies: Array<Any>,
) {
    fun <T : Task> configureUsageBy(task: T, fn: T.(JvmFiles) -> Unit) {
        task.dependsOn(taskDependencies)
        task.fn(this)
    }
}
