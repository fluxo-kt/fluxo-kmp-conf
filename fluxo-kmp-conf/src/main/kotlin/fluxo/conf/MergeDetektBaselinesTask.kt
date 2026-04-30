package fluxo.conf

import fluxo.conf.MergeDetektBaselinesTask.Companion.TASK_NAME
import fluxo.gradle.ioFile
import fluxo.log.i
import fluxo.log.l
import java.io.File
import java.util.SortedSet
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
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
import org.w3c.dom.Element
import org.w3c.dom.Node

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
 * Implementation deliberately avoids any static reference to
 * `io.github.detekt.tooling.api.BaselineProvider` or any other Detekt type.
 * Each Gradle plugin runs in an **isolated classloader** scoped to its own
 * POM runtime dependencies; sibling plugin classloaders are not visible to each
 * other. A static type reference would cause `ClassNotFoundException` at task
 * execution time even when `detekt-tooling` is `compileOnly`. The Detekt
 * baseline XML format is a stable, documented contract — parsing it directly
 * via JDK DOM/Transformer is both simpler and more robust than reflection.
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
        description = "Collects all generated detekt baselines" +
            " and merges them into the global one (Fluxo task)."
        group = LifecycleBasePlugin.VERIFICATION_GROUP
    }

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val baselineFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    private val rootProjectDir: File = project.rootProject.projectDir

    @TaskAction
    fun merge() {
        val filteredFiles = baselineFiles.filter { it.exists() }
        if (filteredFiles.isEmpty) {
            logger.l("No Detekt baseline files to merge")
            return
        }
        val files = filteredFiles.files
        logger.l("merging Detekt baseline from ${files.size} files")
        logger.i(files.joinToString(separator = "\n\t", prefix = "\t") { it.absolutePath })

        val outputFile = outputFile.ioFile
        mergeDetektBaselines(files, outputFile)
        val fileRelative = outputFile.absoluteFile.relativeTo(rootProjectDir)
        logger.l("Merged Detekt baseline to $fileRelative")
    }
}

/**
 * Parses each input [inputFiles] as a Detekt baseline XML, unions all
 * `ManuallySuppressedIssues` and `CurrentIssues` ID sets, and writes the
 * merged result to [outputFile].
 *
 * Extracted as a top-level function for unit-testability without Gradle coupling.
 *
 * Expected input XML schema:
 * ```xml
 * <SmellBaseline>
 *   <ManuallySuppressedIssues><ID>…</ID>…</ManuallySuppressedIssues>
 *   <CurrentIssues><ID>…</ID>…</CurrentIssues>
 * </SmellBaseline>
 * ```
 */
internal fun mergeDetektBaselines(inputFiles: Iterable<File>, outputFile: File) {
    val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    val manuallySuppressed = sortedSetOf<String>()
    val currentIssues = sortedSetOf<String>()
    for (file in inputFiles) {
        readBaselineXml(builder, file, manuallySuppressed, currentIssues)
    }
    writeBaselineXml(builder, outputFile, manuallySuppressed, currentIssues)
}

private fun readBaselineXml(
    builder: DocumentBuilder,
    file: File,
    manuallySuppressed: SortedSet<String>,
    currentIssues: SortedSet<String>,
) {
    val root = builder.parse(file).documentElement
    val children = root.childNodes
    for (i in 0 until children.length) {
        val node = children.item(i)
        if (node == null || node.nodeType != Node.ELEMENT_NODE) continue
        when (node.nodeName) {
            "ManuallySuppressedIssues" -> addIds(node as Element, manuallySuppressed)
            "CurrentIssues" -> addIds(node as Element, currentIssues)
        }
    }
}

private fun addIds(element: Element, target: SortedSet<String>) {
    val ids = element.getElementsByTagName("ID")
    for (j in 0 until ids.length) {
        val id = ids.item(j).textContent.trim()
        if (id.isNotEmpty()) target.add(id)
    }
}

private fun writeBaselineXml(
    builder: DocumentBuilder,
    outputFile: File,
    manuallySuppressed: Set<String>,
    currentIssues: Set<String>,
) {
    val doc = builder.newDocument()
    val root = doc.createElement("SmellBaseline")
    doc.appendChild(root)
    appendSection(doc, root, "ManuallySuppressedIssues", manuallySuppressed)
    appendSection(doc, root, "CurrentIssues", currentIssues)
    TransformerFactory.newInstance().newTransformer().apply {
        setOutputProperty(OutputKeys.INDENT, "yes")
        setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
    }.transform(DOMSource(doc), StreamResult(outputFile))
}

private fun appendSection(
    doc: org.w3c.dom.Document,
    root: Element,
    sectionName: String,
    ids: Set<String>,
) {
    val section = doc.createElement(sectionName)
    root.appendChild(section)
    for (id in ids) {
        val elem = doc.createElement("ID")
        elem.textContent = id
        section.appendChild(elem)
    }
}
