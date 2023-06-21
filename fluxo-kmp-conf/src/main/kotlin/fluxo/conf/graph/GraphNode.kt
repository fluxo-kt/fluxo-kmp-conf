package fluxo.conf.graph

internal open class GraphNode(
    override val id: Any,
    private val name: String = id.toString(),
) : RenderableNode {
    override val children = sortedSetOf<GraphNode>()
    override val attrs = mutableMapOf<String, Any?>()

    val parents = mutableSetOf<GraphNode>()

    override var isTest: Boolean = false
        get() = field || super.isTest

    override fun getName() = name

    override fun toString() = name

    override fun hashCode() = id.hashCode()

    override fun equals(other: Any?): Boolean {
        return this === other || id == (other as? GraphNode)?.id
    }
}
