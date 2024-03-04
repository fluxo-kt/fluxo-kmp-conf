package fluxo.conf

import fluxo.conf.MergeDetektBaselinesTask.Companion.TASK_NAME
import fluxo.conf.impl.i
import fluxo.conf.impl.l
import fluxo.gradle.ioFile
import io.github.detekt.tooling.api.BaselineProvider
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * Collects all generated baselines and merges them into the global one.
 *
 * This is only configured to run when the [TASK_NAME] task name is passed in,
 * as detekt reuses the `baseline` argument for both the input file to regular
 * detekt tasks and output file of its `create*` tasks.
 * Since we don't want the created tasks to overwrite each other into the same
 * output task, we dynamically configure this as needed.
 * When [TASK_NAME] is specified, all detekt baselines are pointed to an
 * intermediate output file in that project's build directory, and the misc
 * "detektBaseline" tasks are wired to have their outputs to be inputs to this
 * task's [baselineFiles].
 *
 * Usage:
 * `./gradlew detektBaselineMerge --continue`
 */
@CacheableTask
internal abstract class MergeDetektBaselinesTask : DefaultTask() {

    // https://github.com/detekt/detekt/issues/1589#issuecomment-605744874
    // https://github.com/slackhq/slack-gradle-plugin/blob/424810e/slack-plugin/src/main/kotlin/slack/gradle/tasks/detektbaseline/MergeDetektBaselinesTask.kt

    internal companion object {
        internal const val TASK_NAME = "detektBaselineMerge"
    }

    init {
        description = "Collects all generated detekt baselines and merges them into the global one."
        group = LifecycleBasePlugin.VERIFICATION_GROUP
    }

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val baselineFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun merge() {
        val files = baselineFiles.files
        logger.l("merging Detekt baseline from ${files.size} files")
        logger.i(files.joinToString(separator = "\n\t", prefix = "\t") { it.absolutePath })

        val baselineFiles = baselineFiles.filter { it.exists() }
        if (baselineFiles.isEmpty) {
            logger.l("No Detekt baseline files to merge")
            return
        }

        val bp = try {
            BaselineProvider.load()
        } catch (e: Throwable) {
            throw IllegalStateException("Couldn't load BaselineProvider: $e", e)
        }
        val merged = baselineFiles
            .map {
                logger.i("merge {}", it)
                bp.read(it.toPath())
            }
            .reduce { acc, baseline ->
                val manuallySuppressed = acc.manuallySuppressedIssues +
                    baseline.manuallySuppressedIssues
                bp.of(
                    manuallySuppressedIssues = manuallySuppressed,
                    currentIssues = acc.currentIssues + baseline.currentIssues,
                )
            }
        val sorted = bp.of(
            manuallySuppressedIssues = merged.manuallySuppressedIssues.toSortedSet(),
            currentIssues = merged.currentIssues.toSortedSet(),
        )

        val outputFile = outputFile.ioFile
        bp.write(targetPath = outputFile.toPath(), baseline = sorted)
        val fileRelative = outputFile.absoluteFile.relativeTo(project.projectDir)
        logger.l("Merged Detekt baseline files to $fileRelative")
    }
}
