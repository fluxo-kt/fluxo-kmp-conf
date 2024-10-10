package fluxo.conf.graph

import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.logging.text.StyledTextOutput.Style.Info

/**
 * @see org.gradle.api.tasks.diagnostics.internal.graph.SimpleNodeRenderer
 */
internal open class SimpleNodeRenderer(
    private val showTests: Boolean = false,
) : NodeRenderer {
    override fun renderNode(
        sto: StyledTextOutput,
        node: RenderableNode,
        alreadyRendered: Boolean,
        parent: RenderableNode?,
    ) {
        var name = node.name
        if (!showTests) {
            name = name.removeSuffix("Main")
        }
        sto.text(name)

        renderAttrs(sto, node, parent)

        if (alreadyRendered) {
            sto.withStyle(Info).text(" (*)")
        }
    }

    protected open fun renderAttrs(
        sto: StyledTextOutput,
        node: RenderableNode,
        parent: RenderableNode?,
    ) {}
}
