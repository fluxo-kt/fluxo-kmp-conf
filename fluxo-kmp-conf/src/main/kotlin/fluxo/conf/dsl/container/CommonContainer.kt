package fluxo.conf.dsl.container

import fluxo.conf.dsl.FluxoKmpConfDsl
import fluxo.conf.dsl.InternalFluxoApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

@FluxoKmpConfDsl
public class CommonContainer
internal constructor(context: ContainerContext, targetName: String = NAME) :
    Container.ConfigurableTarget(context, targetName) {

    override fun setup(k: KotlinMultiplatformExtension) {
        k.sourceSets.getByName(KotlinSourceSet.COMMON_MAIN_SOURCE_SET_NAME) {
            lazySourceSetMainConf()
        }
        k.sourceSets.getByName(KotlinSourceSet.COMMON_TEST_SOURCE_SET_NAME) {
            lazySourceSetTestConf()
        }

        // setup is only ever called if at least 1 target is enabled,
        // and is always called after all targets have been configured.
        applyPlugins(k.targets.first().project)
    }

    override val sortOrder: Byte = (Byte.MAX_VALUE - 1).toByte()

    @JvmSynthetic
    @InternalFluxoApi
    override fun hashCode(): Int = typeHashCode<CommonContainer>()

    @JvmSynthetic
    @InternalFluxoApi
    override fun equals(other: Any?): Boolean = other is CommonContainer

    internal companion object {
        internal const val NAME = "common"
    }
}
