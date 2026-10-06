package fluxo.conf.feat

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.data.BuildConstants.FLUXO_BCV_JS_PLUGIN_ID
import fluxo.conf.data.BuildConstants.FLUXO_BCV_JS_PLUGIN_VERSION
import fluxo.conf.deps.loadAndApplyPluginIfNotApplied
import fluxo.conf.dsl.BinaryCompatibilityValidatorConfig
import fluxo.log.l
import org.gradle.api.Project

// Kotlin JS/WASM TypeScript definitions API support for the KotlinX Binary Compatibility Validator.
// https://github.com/fluxo-kt/fluxo-bcv-js
internal fun Project.setupBinaryCompatibilityValidatorTs(
    config: BinaryCompatibilityValidatorConfig?,
    ctx: FluxoKmpConfContext,
    hasWebTarget: Boolean,
) {
    // Only a module with a built JS or Wasm target has TypeScript definitions to check. Decided
    // from the declared targets and applied now: the plugin registers `afterEvaluate` work, which
    // Gradle 9 rejects once Kotlin creates the targets after evaluation.
    if (config?.tsApiChecks == false || !hasWebTarget) {
        return
    }
    logger.l("Setup Fluxo TS-based BinaryCompatibilityValidator for JS")
    // Its classes reference the Kotlin plugin's JS DSL, so it must come from the module's build
    // classpath (the settings plugin puts it there), never from a class loader of fluxo's or
    // Gradle's own: neither can see the Kotlin plugin.
    ctx.loadAndApplyPluginIfNotApplied(
        id = FLUXO_BCV_JS_PLUGIN_ID,
        version = FLUXO_BCV_JS_PLUGIN_VERSION,
        project = this,
        onBuildClasspath = true,
    )
}
