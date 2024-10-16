@file:Suppress("ReturnCount")

package fluxo.conf

import fluxo.conf.graph.AsciiGraphReportRenderer
import fluxo.conf.graph.GraphNode
import fluxo.conf.graph.RenderableNode
import fluxo.conf.graph.SimpleNodeRenderer
import fluxo.conf.impl.uncheckedCast
import java.io.File
import kotlin.reflect.KProperty
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.diagnostics.AbstractProjectBasedReportTask
import org.gradle.api.tasks.diagnostics.internal.ProjectDetails
import org.gradle.api.tasks.options.Option
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.sources.DefaultKotlinSourceSet
import org.jetbrains.kotlin.konan.target.KonanTarget.Companion.deprecatedTargets

// TODO: Mark source sets with deprecated or 3rd tier targets

@Suppress("UnstableApiUsage")
@DisableCachingByDefault(because = "Not worth caching")
internal abstract class KotlinSourceSetsReportTask :
    AbstractProjectBasedReportTask<KotlinSourceSetsReportTask.StubModel>() {

    @get:Internal
    internal abstract val propShowTests: Property<Boolean>

    @set:Option(option = "tests", description = "Allows to show test source sets in graph.")
    var showTests: Boolean
        @Input get() = propShowTests.getOrElse(false)
        set(value) = propShowTests.set(value)


    @get:Internal
    internal abstract val propShowOnlyTests: Property<Boolean>

    @set:Option(option = "tests-only", description = "Show only test source sets in graph.")
    var showOnlyTests: Boolean
        @Input get() = propShowOnlyTests.getOrElse(false)
        set(value) = propShowOnlyTests.set(value)

    private val lazyRenderer by lazy {
        val showTests = showTests || showOnlyTests
        AsciiGraphReportRenderer(
            showTests = showTests,
            showOnlyTests = showOnlyTests,
            nodeRenderer = SourceSetNodeRenderer(showTests = showTests),
        )
    }

    private val projectDir: File = project.layout.projectDirectory.asFile

    override fun getRenderer() = lazyRenderer


    override fun generateReportFor(pd: ProjectDetails, stub: StubModel) {
        val projectDir = projectDir
        val model = KotlinSourceSetsModel(project.kotlinExtension.sourceSets) {
            it.relativeTo(projectDir).path
        }
        var trees = model.buildTrees()
        while (true) {
            val filtered = trees.filter { it.canShow() }
            if (filtered.isNotEmpty()) {
                filtered.forEach { lazyRenderer.render(it) }
                break
            }
            trees = trees.map { it.children }.flatten()
        }
    }

    private fun GraphNode.canShow(): Boolean {
        return if (showOnlyTests) isTest else showTests || !isTest
    }

    override fun calculateReportModelFor(project: Project) = StubModel()


    /**
     * @see org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
     */
    internal class KotlinSourceSetsModel(
        private val items: Collection<KotlinSourceSet>,
        private val pathMapper: (File) -> String,
    ) {
        private val roots = mutableListOf<KotlinSourceSetGraphNode>()
        private val map = mutableMapOf<String, KotlinSourceSetGraphNode>()

        private val androidMain = items.find { it.name == "androidMain" }

        @Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
        private fun fillNode(
            node: KotlinSourceSetGraphNode,
            s: KotlinSourceSet,
        ) {
            node.kotlinSourceDirs = s.kotlin.srcDirs.map(pathMapper)
            node.resourcesDirs = s.resources.srcDirs.map(pathMapper)

            s.languageSettings.run {
                node.languageVersion = languageVersion
                node.apiVersion = apiVersion
                node.progressiveMode = progressiveMode
                node.optIns = optInAnnotationsInUse.sorted()
            }

            val dependencies = LinkedHashSet(s.dependsOn)
            if (s is DefaultKotlinSourceSet) {
                dependencies.addAll(s.dependsOnClosure)
                /** @see org.jetbrains.kotlin.gradle.plugin.KotlinCompilation.associateWith */
                dependencies.addAll(s.getAdditionalVisibleSourceSets())

                val compilations = s.compilations.toTypedArray()
                val targets = HashSet<KotlinTarget>()
                for (c in compilations) {
                    targets.add(c.target)
                    if (c.name.contains("test", ignoreCase = true)) {
                        node.isTest = true
                    }
                }
                if (targets.size == 1) {
                    targets.first().run {
                        node.targetName = name
                        node.platformType = platformType.name
                        try {
                            if (this is KotlinNativeTarget && konanTarget in deprecatedTargets) {
                                node.isDeprecatedTarget = true
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
            }

            // Android variants aren't linked to the main Android set somehow.
            androidMain?.let {
                if (s != it && "android" in node.name) {
                    dependencies.add(it)
                }
            }

            if (dependencies.isNotEmpty()) {
                // Kotlin doesn't allow circular dependencies,
                // so it's safe to do it right here.
                for (dep in dependencies) {
                    val parent = graphNode(dep.name, dep)
                    parent.children.add(node)
                    node.parents.add(parent)
                }
            } else {
                roots.add(node)
            }
        }

        private fun graphNode(
            name: String,
            s: KotlinSourceSet,
        ): KotlinSourceSetGraphNode {
            return map.getOrPut(name) {
                val node = KotlinSourceSetGraphNode(name)
                fillNode(node, s)
                node
            }
        }

        @Suppress("NestedBlockDepth")
        fun buildTrees(): List<GraphNode> {
            if (roots.isNotEmpty()) {
                return roots
            }
            for (s in items) {
                graphNode(s.name, s)
            }

            for (node in map.values) {
                if (node.parents.size <= 1) {
                    continue
                }

                // Remove parents that reachable from other parents
                val remove = hashSetOf<GraphNode>()
                val parents = node.parents.toTypedArray()
                for (parent in parents) {
                    for (otherParent in parents) {
                        if (parent == otherParent || otherParent in remove) {
                            continue
                        }
                        if (parent.isReachableThrough(otherParent)) {
                            remove.add(parent)
                        }
                    }
                }
                if (remove.isNotEmpty()) {
                    node.parents.removeAll(remove)
                    for (parent in remove) {
                        parent.children.remove(node)
                    }
                }
            }

            return roots
        }

        private fun GraphNode.isReachableThrough(node: GraphNode): Boolean {
            if (this == node) {
                return true
            }
            for (parent in node.parents) {
                if (isReachableThrough(parent)) {
                    return true
                }
            }
            return false
        }
    }

    internal class KotlinSourceSetGraphNode(id: Any, name: String = id.toString()) :
        GraphNode(id, name) {

        @Suppress("UnusedPrivateMember")
        private operator fun <V, V1 : V> Map<String, V>.getValue(r: Any?, p: KProperty<*>): V1 =
            uncheckedCast(get(p.name))

        var kotlinSourceDirs: List<String> by attrs
        var resourcesDirs: List<String> by attrs
        var optIns: List<String> by attrs
        var languageVersion: String? by attrs
        var apiVersion: String? by attrs
        var progressiveMode: Boolean by attrs
        var targetName: String? by attrs
        var platformType: String? by attrs
        var isDeprecatedTarget: Boolean? by attrs
    }

    internal class SourceSetNodeRenderer(showTests: Boolean) : SimpleNodeRenderer(showTests) {

        @Suppress("CyclomaticComplexMethod")
        override fun renderAttrs(
            sto: StyledTextOutput,
            node: RenderableNode,
            parent: RenderableNode?,
        ) {
            if (node.attrs.isEmpty() || node !is KotlinSourceSetGraphNode) {
                return
            }
            if (parent !is KotlinSourceSetGraphNode?) {
                return
            }

            var hasWarning = false
            val items = mutableListOf<String>()

            val lang = node.languageVersion
            if (!lang.isNullOrEmpty() && (parent == null || parent.languageVersion != lang)) {
                items.add(lang)
            }

            val api = node.apiVersion
            if (!api.isNullOrEmpty() && (parent == null || parent.apiVersion != api)) {
                if (api != lang) {
                    items.add("api: $api")
                }
            }

            val progressive = node.progressiveMode
            if (progressive && parent?.progressiveMode != true) {
                items.add("progressive")
            }

            val isDeprecatedTarget = node.isDeprecatedTarget == true
            if (isDeprecatedTarget && parent?.isDeprecatedTarget != true) {
                hasWarning = true
                items.add("deprecated target")
            }

            if (items.isEmpty()) {
                return
            }
            val style = when {
                hasWarning -> StyledTextOutput.Style.Error
                else -> StyledTextOutput.Style.Info
            }
            val output = sto.withStyle(style)
            output.append(" (")
            items.joinTo(output, ", ")
            output.append(")")
        }
    }

    internal class StubModel
}
