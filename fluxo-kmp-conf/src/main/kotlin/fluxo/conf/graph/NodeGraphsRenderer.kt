package fluxo.conf.graph

import kotlin.math.min
import org.gradle.internal.graph.GraphRenderer
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.logging.text.StyledTextOutput.Style.Info

/**
 * @see org.gradle.api.tasks.diagnostics.internal.graph.DependencyGraphsRenderer
 */
internal class NodeGraphsRenderer(
    private val output: StyledTextOutput,
    private val renderer: GraphRenderer,
    private val rootRenderer: NodeRenderer,
    private val nodeRenderer: NodeRenderer,
    private val showTests: Boolean = false,
    private val showOnlyTests: Boolean = false,
) {
    private val legendRenderer = LegendRenderer(output)

    @Suppress("MemberVisibilityCanBePrivate")
    var isShowSinglePath = false

    fun render(items: Collection<RenderableNode>) {
        val size = items.size
        items.forEachIndexed { i, item ->
            renderRoot(item)
            val isLast = i + 1 == size
            if (!isLast) {
                output.println()
            }
        }
    }

    private fun renderRoot(root: RenderableNode) {
        if (root.isUnresolvableResult) {
            legendRenderer.hasUnresolvableConfigurations = true
        }
        if (rootRenderer !== NodeRenderer.NO_OP) {
            renderNode(root, isLast = true, nodeRenderer = rootRenderer)
        }
        val visited = HashSet<Any>()
        visited.add(root.id)
        renderChildren(root, visited)
    }

    private fun renderChildren(
        parent: RenderableNode,
        visited: MutableSet<Any>,
    ) {
        val children = parent.children.filter {
            if (showOnlyTests) it.isTest else showTests || !it.isTest
        }

        var i = 0
        val childCould = children.size
        val count = if (isShowSinglePath) min(1, childCould) else childCould
        if (count > 0) {
            renderer.startChildren()
            for (child in children) {
                val last = ++i == count
                doRender(child, parent, last, visited)
                if (last) {
                    break
                }
            }
            renderer.completeChildren()
        }
    }

    private fun doRender(
        node: RenderableNode,
        parent: RenderableNode,
        isLast: Boolean,
        visited: MutableSet<Any>,
    ) {
        // Do a shallow render of any constraint edges, and do not mark the node as visited.
        if (node.isConstraint) {
            renderNode(node, parent = parent, isLast = isLast)
            legendRenderer.hasConstraints = true
            return
        }

        val alreadyRendered = !visited.add(node.id)
        if (alreadyRendered) {
            legendRenderer.hasCyclicDependencies = true
        }
        renderNode(node, parent = parent, isLast = isLast, isDuplicate = alreadyRendered)
        if (!alreadyRendered) {
            renderChildren(node, visited)
        }
    }

    private fun renderNode(
        node: RenderableNode,
        parent: RenderableNode? = null,
        isLast: Boolean = false,
        isDuplicate: Boolean = false,
        nodeRenderer: NodeRenderer = this.nodeRenderer,
    ) {
        renderer.visit({ nodeRenderer.renderNode(node, isDuplicate, parent) }, isLast)
    }

    fun complete() {
        legendRenderer.printLegend()
    }


    /** @see org.gradle.api.tasks.diagnostics.internal.graph.LegendRenderer */
    private class LegendRenderer(
        private val output: StyledTextOutput,
    ) {
        var hasCyclicDependencies = false
        var hasUnresolvableConfigurations = false
        var hasConstraints = false
        var hasNonPrimaryTiers = false

        fun printLegend() {
            if (hasConstraints) {
                output.println()
                output.withStyle(Info)
                    .text("(c) - A constraint, not a dependency.")
            }
            if (hasCyclicDependencies) {
                output.println()
                output.withStyle(Info)
                    .println("(*) - Repeated occurrences of a transitive dependency subtree.")
            }
            if (hasUnresolvableConfigurations) {
                output.println()
                output.withStyle(Info)
                    .println("(n) - A dependency that cannot be resolved.")
            }
            @Suppress("ControlFlowWithEmptyBody")
            if (hasNonPrimaryTiers) {
                // FIXME: This is not implemented yet.
            }
        }
    }
}
