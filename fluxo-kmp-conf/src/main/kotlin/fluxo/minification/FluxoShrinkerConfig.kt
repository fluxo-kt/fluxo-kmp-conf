package fluxo.minification

import fluxo.gradle.notNullProperty
import fluxo.gradle.nullableProperty
import javax.inject.Inject
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

public interface FluxoShrinkerConfig {

    public val maxHeapSize: Property<String?>

    public val configurationFiles: ConfigurableFileCollection

    /**
     * `false` by default.
     */
    public val obfuscate: Property<Boolean>

    /**
     * `true` by default.
     */
    public val optimize: Property<Boolean>

    /**
     * `true` by default.
     *
     * @see fluxo.conf.dsl.FluxoConfigurationExtensionKotlin.enableApiValidation
     */
    public val autoGenerateKeepRulesFromApis: Property<Boolean>
}

/**
 *
 * @see org.jetbrains.compose.desktop.application.dsl.ProguardSettings
 */
@Suppress("UnnecessaryAbstractClass")
internal abstract class FluxoShrinkerConfigImpl @Inject constructor(
    objects: ObjectFactory,
) : FluxoShrinkerConfig {
    override val maxHeapSize: Property<String?> = objects.nullableProperty()
    override val configurationFiles: ConfigurableFileCollection = objects.fileCollection()
    override val obfuscate: Property<Boolean> = objects.notNullProperty(false)
    override val optimize: Property<Boolean> = objects.notNullProperty(true)
    override val autoGenerateKeepRulesFromApis: Property<Boolean> = objects.notNullProperty(true)
}
