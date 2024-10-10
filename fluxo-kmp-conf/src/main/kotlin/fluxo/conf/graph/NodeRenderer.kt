package fluxo.conf.graph

import org.gradle.internal.logging.text.StyledTextOutput

/**
 * @see org.gradle.api.tasks.diagnostics.internal.graph.NodeRenderer
 */
internal fun interface NodeRenderer {
    companion object {
        internal val NO_OP = NodeRenderer { _, _, _, _ -> }
    }

    fun renderNode(
        sto: StyledTextOutput,
        node: RenderableNode,
        alreadyRendered: Boolean,
        parent: RenderableNode?,
    )
}
