package fluxo.conf.feat

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.deps.loadAndApplyPluginIfNotApplied
import fluxo.conf.dsl.BinaryCompatibilityValidatorConfig
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.log.l
import org.gradle.api.Project

// Kotlin JS/WASM TypeScript definitions API support for the KotlinX Binary Compatibility Validator.
// https://github.com/fluxo-kt/fluxo-bcv-js
internal fun Project.setupBinaryCompatibilityValidatorTs(
    config: BinaryCompatibilityValidatorConfig?,
    ctx: FluxoKmpConfContext,
) {
    if (config != null && !config.tsApiChecks) {
        return
    }
    if (!ctx.isTargetEnabled(KmpTargetCode.JS) &&
        !ctx.isTargetEnabled(KmpTargetCode.WASM_JS)
    ) {
        return
    }

    logger.l("Setup Fluxo TS-based BinaryCompatibilityValidator for JS")
    ctx.loadAndApplyPluginIfNotApplied(
        id = FLUXO_BCV_TS_PLUGIN_ID,
        catalogPluginIds = CATALOG_PLUGIN_IDS,
        catalogVersionIds = CATALOG_PLUGIN_IDS,
        fetchWithGradle = true,
        project = this,
    )
}

private val CATALOG_PLUGIN_IDS = arrayOf(
    "bcv-js",
    "bcv-ts",
    "fluxo-bcv-js",
    "fluxo-bcv-ts",
)

private const val FLUXO_BCV_TS_PLUGIN_ID = "io.github.fluxo-kt.binary-compatibility-validator-js"
