package fluxo.conf.dsl.container

import fluxo.conf.impl.set
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

internal class KmpExtensionActionContainer
internal constructor(context: ContainerContext) :
    Container(context, NAME) {

    private val lazyKotlin = context.objects.set<KotlinMultiplatformExtension.() -> Unit>()

    internal fun kotlinMultiplatform(action: KotlinMultiplatformExtension.() -> Unit) {
        lazyKotlin.add(action)
    }

    override fun setup(k: KotlinMultiplatformExtension) {
        lazyKotlin.all { k.this() }
    }

    override val sortOrder: Byte = Byte.MAX_VALUE
    override fun hashCode(): Int = typeHashCode<KmpExtensionActionContainer>()
    override fun equals(other: Any?): Boolean = other is KmpExtensionActionContainer

    internal companion object {
        internal const val NAME = "kmpExtension"
    }
}
