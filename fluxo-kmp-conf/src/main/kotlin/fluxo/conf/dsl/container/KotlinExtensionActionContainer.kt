package fluxo.conf.dsl.container

import fluxo.conf.impl.container
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

internal class KotlinExtensionActionContainer
internal constructor(context: ContainerContext) :
    Container(context, NAME) {

    private val lazyKotlin =
        context.objects.container<KotlinMultiplatformExtension.() -> Unit>()

    internal fun kotlin(action: KotlinMultiplatformExtension.() -> Unit) {
        lazyKotlin.add(action)
    }

    override fun KotlinMultiplatformExtension.setup() {
        lazyKotlin.all { this() }
    }

    override val sortOrder: Byte = Byte.MAX_VALUE
    override fun hashCode(): Int = typeHashCode<KotlinExtensionActionContainer>()
    override fun equals(other: Any?): Boolean = other is KotlinExtensionActionContainer

    internal companion object {
        internal const val NAME = "kotlinExtension"
    }
}
