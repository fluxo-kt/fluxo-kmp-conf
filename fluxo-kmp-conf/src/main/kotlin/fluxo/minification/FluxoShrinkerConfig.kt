package fluxo.minification

import fluxo.gradle.notNullProperty
import fluxo.gradle.nullableProperty
import javax.inject.Inject
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

public interface FluxoShrinkerConfig {

    /**
     * The maximum heap size for the shrinker process.
     * Only used for unbundled R8 or ProGuard.
     *
     * `null` by default.
     */
    public val maxHeapSize: Property<String?>

    /**
     * Optionals configuration files for the shrinker.
     *
     * `pg/rules.pro` always included if it exists.
     */
    public val configurationFiles: ConfigurableFileCollection

    /**
     * Whether to obfuscate the output artifacts.
     *
     * `false` by default.
     */
    public val obfuscate: Property<Boolean>

    /**
     * Whether to optimize the output artifacts.
     *
     * `true` by default.
     */
    public val optimize: Property<Boolean>

    /**
     * Whether to use R8 instead of ProGuard.
     *
     * R8 is better optimized for Android, but more fretful and can be tricky to configure.
     * ProGuard seems to provide better results for JVM library artifacts.
     *
     * `false` by default.
     */
    public val useR8: Property<Boolean>

    /**
     * Whether to create tasks for both R8 and ProGuard.
     *
     * `false` by default.
     */
    public val useBothShrinkers: Property<Boolean>

    /**
     * Whether to use extrenal R8 jar instead of the one bundled/avaliable in the classpath.
     *
     * `false` by default.
     *
     * @TODO: Support R8 or ProgGuard available in the classpath (bundled).
     */
    public val forceUnbundledShrinker: Property<Boolean>

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
    override val useR8: Property<Boolean> = objects.notNullProperty(false)
    override val useBothShrinkers: Property<Boolean> = objects.notNullProperty(false)
    override val forceUnbundledShrinker: Property<Boolean> = objects.notNullProperty(false)
    override val autoGenerateKeepRulesFromApis: Property<Boolean> = objects.notNullProperty(true)
}
