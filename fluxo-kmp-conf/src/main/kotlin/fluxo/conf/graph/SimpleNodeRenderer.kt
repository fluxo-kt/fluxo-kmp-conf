package fluxo.conf.graph

import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.logging.text.StyledTextOutput.Style.Info

/**
 * @see org.gradle.api.tasks.diagnostics.internal.graph.SimpleNodeRenderer
 */
internal open class SimpleNodeRenderer(
    private val showTests: Boolean = false,
) : NodeRenderer {
    context(StyledTextOutput)
    override fun renderNode(
        node: RenderableNode,
        alreadyRendered: Boolean,
        parent: RenderableNode?,
    ) {
        var name = node.name
        if (!showTests) {
            name = name.removeSuffix("Main")
        }
        text(name)

        renderAttrs(node, parent)

        if (alreadyRendered) {
            withStyle(Info).text(" (*)")
        }
    }

    context(StyledTextOutput)
    protected open fun renderAttrs(node: RenderableNode, parent: RenderableNode?) {}
}
