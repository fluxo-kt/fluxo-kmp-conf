package fluxo.artifact.dsl

import fluxo.conf.dsl.FluxoKmpConfDsl
import org.gradle.api.provider.Property

@FluxoKmpConfDsl
public sealed interface ProcessorConfigR8 : ProcessorConfigShrinker {

    /**
     * Whether to use R8 in full mode, also called "non-compat mode".
     *
     * `android.enableR8.fullMode` gradle property enables it for android builds.
     *  See [docs](https://r8.googlesource.com/r8/+/refs/heads/main/compatibility-faq.md#r8-full-mode)
     *  for more details.
     *
     * `false` by default.
     */
    public val fullMode: Property<Boolean>
}
