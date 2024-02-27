package fluxo.artifact.dsl

import fluxo.conf.dsl.FluxoKmpConfDsl
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property

@FluxoKmpConfDsl
public sealed interface ProcessorConfigShrinker : ProcessorConfig {

    /**
     * Optional configuration files for the shrinker.
     *
     * `pg/r8.pro` always included if it exists for R8.
     * `pg/proguard.pro` always included if it exists for ProGuard.
     * `pg/rules.pro` always included if it exists, and no specific shrinker file is selected.
     */
    public val configurationFiles: ConfigurableFileCollection

    /**
     * Whether to optimize the output artifacts.
     *
     * `true` by default.
     */
    public val optimize: Property<Boolean>

    /**
     * Whether to obfuscate the output artifacts.
     *
     * `false` by default.
     */
    public val obfuscate: Property<Boolean>

    /**
     * Whether to use incremental obfuscation.
     *
     * Slightly reduces the possible obfuscation and optimization level,
     * but if f disabled and multistep obfuscation is enabled,
     * stacktraces will be non-reversible!
     *
     * `true` by default.
     */
    public val obfuscateIncrementally: Property<Boolean>
}
