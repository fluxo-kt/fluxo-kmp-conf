package fluxo.conf.dsl.container.target

import fluxo.conf.dsl.FluxoKmpConfDsl
import fluxo.conf.dsl.InternalFluxoApi
import fluxo.conf.dsl.container.CommonContainer
import fluxo.conf.dsl.container.ContainerHolder
import fluxo.conf.dsl.container.KotlinExtensionActionContainer
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

@FluxoKmpConfDsl
public class KmpConfigurationContainerDsl
internal constructor(
    private val containerHolder: ContainerHolder,
) : TargetJvmContainer.Configure,
    TargetAndroidContainer.Configure,
    TargetJsContainer.Configure,
    TargetAppleIosContainer.Configure,
    TargetAppleMacosContainer.Configure,
    TargetAppleTvosContainer.Configure,
    TargetAppleWatchosContainer.Configure,
    TargetLinuxContainer.Configure,
    TargetMingwContainer.Configure,
    TargetWasmContainer.Configure,
    TargetAndroidNativeContainer.Configure,
    TargetWasmNativeContainer.Configure {

    @InternalFluxoApi
    @get:JvmSynthetic
    override val ContainerHolderAware.holder: ContainerHolder
        get() = containerHolder


    public fun common(action: CommonContainer.() -> Unit) {
        holder.configure(CommonContainer.NAME, ::CommonContainer, action)
    }

    public fun kotlin(action: KotlinMultiplatformExtension.() -> Unit) {
        val container = holder.findKotlinContainer() ?: KotlinExtensionActionContainer(holder)
        container.kotlin(action)
        holder.add(container)
    }
}
