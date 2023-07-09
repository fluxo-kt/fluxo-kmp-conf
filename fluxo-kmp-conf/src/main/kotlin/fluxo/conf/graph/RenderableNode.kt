package fluxo.conf.graph

import fluxo.conf.impl.isTestRelated
import org.gradle.api.Named

/**
 * @see org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency
 */
internal interface RenderableNode : Named, Comparable<RenderableNode> {
    val id: Any
    val children: Collection<RenderableNode>
    val attrs: Map<String, Any?> get() = mapOf()
    val isConstraint: Boolean get() = false
    val isUnresolvableResult: Boolean get() = false

    val isTest: Boolean get() = isTestRelated()

    override fun getName(): String = id.toString()

    override fun compareTo(other: RenderableNode): Int {
        var r = isTest.compareTo(other.isTest)
        if (r == 0) r = name.compareTo(other.name)
        if (r == 0) r = children.size.compareTo(children.size)
        if (r == 0) r = attrs.size.compareTo(attrs.size)
        return r
    }
}
