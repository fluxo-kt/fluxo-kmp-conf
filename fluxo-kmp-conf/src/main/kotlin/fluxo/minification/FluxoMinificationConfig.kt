package fluxo.minification

import fluxo.gradle.notNullProperty
import fluxo.gradle.nullableProperty
import javax.inject.Inject
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

public interface FluxoMinificationConfig {
    public val maxHeapSize: Property<String?>
    public val configurationFiles: ConfigurableFileCollection
    public val isEnabled: Property<Boolean>
    public val obfuscate: Property<Boolean>
    public val optimize: Property<Boolean>
}

/**
 *
 * @see org.jetbrains.compose.desktop.application.dsl.ProguardSettings
 */
@Suppress("UnnecessaryAbstractClass")
internal abstract class FluxoMinificationConfigImpl @Inject constructor(
    objects: ObjectFactory,
) : FluxoMinificationConfig {
    override val maxHeapSize: Property<String?> = objects.nullableProperty()
    override val configurationFiles: ConfigurableFileCollection = objects.fileCollection()
    override val isEnabled: Property<Boolean> = objects.notNullProperty(true)
    override val obfuscate: Property<Boolean> = objects.notNullProperty(false)
    override val optimize: Property<Boolean> = objects.notNullProperty(true)
}
