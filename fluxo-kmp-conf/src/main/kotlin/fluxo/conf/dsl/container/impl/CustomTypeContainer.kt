package fluxo.conf.dsl.container.impl

import fluxo.conf.impl.set

internal open class CustomTypeContainer<T>(
    context: ContainerContext,
    private val name: String,
) : ContainerImpl(context) {

    private val set = context.objects.set<T.() -> Unit>()

    fun add(action: T.() -> Unit): Boolean = set.add(action)

    fun setupCustom(k: T) = set.configureEach { k.this() }


    override val sortOrder: Byte = CUSTOM_SORT_ORDER
    final override fun getName(): String = name
}
