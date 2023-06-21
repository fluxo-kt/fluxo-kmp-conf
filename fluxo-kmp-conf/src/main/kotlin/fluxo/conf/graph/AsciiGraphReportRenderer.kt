package fluxo.conf.graph

import org.gradle.api.tasks.diagnostics.internal.ProjectDetails
import org.gradle.api.tasks.diagnostics.internal.TextReportRenderer
import org.gradle.internal.graph.GraphRenderer
import org.gradle.internal.logging.text.StyledTextOutput

/**
 * @see org.gradle.api.tasks.diagnostics.internal.dependencies.AsciiDependencyReportRenderer
 * @see com.dropbox.gradle.plugins.dependencyguard.internal.tree.AsciiDependencyReportRenderer2
 */
internal class AsciiGraphReportRenderer(
    private val renderRoot: Boolean = true,
    private val showTests: Boolean = false,
    private val showOnlyTests: Boolean = false,
    private val nodeRenderer: NodeRenderer = SimpleNodeRenderer(showTests = showTests),
) : TextReportRenderer() {
    private var hasNodes = false
    private lateinit var renderer: GraphRenderer
    private lateinit var nodeGraphsRenderer: NodeGraphsRenderer

    override fun startProject(project: ProjectDetails) {
        super.startProject(project)
        prepareVisit()
    }

    private fun prepareVisit() {
        hasNodes = false
        renderer = GraphRenderer(textOutput)
        val nodeRenderer = nodeRenderer
        val rootRenderer = if (renderRoot) nodeRenderer else NodeRenderer.NO_OP
        nodeGraphsRenderer = NodeGraphsRenderer(
            output = textOutput,
            renderer = renderer,
            rootRenderer = rootRenderer,
            nodeRenderer = nodeRenderer,
            showTests = showTests,
            showOnlyTests = showOnlyTests,
        )
    }

    override fun completeProject(project: ProjectDetails) {
        if (!hasNodes) {
            textOutput.withStyle(StyledTextOutput.Style.Info).println("No details")
        }
        super.completeProject(project)
    }

    internal fun render(root: RenderableNode) {
        if (hasNodes) {
            textOutput.println()
        }
        hasNodes = true
        nodeGraphsRenderer.render(listOf(root))
    }

    override fun complete() {
        nodeGraphsRenderer.complete()
        super.complete()
    }
}
