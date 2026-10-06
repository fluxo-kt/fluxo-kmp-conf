package fluxo.conf.feat

import fluxo.annotation.VersionGated
import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.dsl.BinaryCompatibilityValidatorConfig
import fluxo.conf.dsl.DEFAULT_CONSTRUCTOR_MARKER_CLASS
import fluxo.conf.dsl.JVM_SYNTHETIC_CLASS
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.log.logDecision
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

/**
 * Picks the engine for a module's ABI dumps and checks, logging the choice.
 *
 * Kotlin Gradle plugin 2.4+ ships ABI validation that writes the same files as BCV
 * (`api/<module>.api`, `api/<target>/<module>.api`, `<module>.klib.api`; byte-identical with the
 * same filters on KGP 2.4.20 vs BCV 0.18.2), so there fluxo uses it and the consumer needs no BCV
 * plugin. Older KGP versions had experimental shapes with another object model (a separate
 * multiplatform extension, `legacyDump {}`, `klib { enabled }`) that the KGP API fluxo compiles
 * against no longer has, so driving them would need reflection; BCV covers them as before.
 *
 * @return `true` when KGP's engine was set up, `false` when the caller must set up BCV.
 */
internal fun Project.setupKotlinAbiValidationIfUsable(
    conf: FluxoConfigurationExtensionImpl,
    isMultiplatform: Boolean,
): Boolean {
    val ctx = conf.ctx
    val bcvReason = bcvOnlyReason(conf.apiValidationGetter, ctx)
    ctx.logDecision(
        project = this,
        setting = "ABI validation engine",
        value = if (bcvReason == null) "Kotlin Gradle plugin" else "BCV",
        reason = bcvReason ?: "Kotlin ${ctx.kotlinPluginVersion} ships its own",
        howToChange = if (bcvReason == null) {
            "apply $KOTLINX_BCV_PLUGIN_ID in the module to keep BCV"
        } else {
            "Kotlin 2.4+ uses the Kotlin Gradle plugin's engine"
        },
    )
    if (bcvReason != null) return false
    setupKgpAbiValidation(conf, isMultiplatform)
    return true
}

private fun Project.bcvOnlyReason(
    config: BinaryCompatibilityValidatorConfig?,
    ctx: FluxoKmpConfContext,
): String? = when {
    // BCV applied in the root configures every subproject itself.
    sequenceOf(this, rootProject).any { it.plugins.hasPlugin(KOTLINX_BCV_PLUGIN_ID) } ->
        "the build applies BCV"

    !kgpHasStableAbiValidation() ->
        "Kotlin ${ctx.kotlinPluginVersion} has no Kotlin Gradle plugin ABI validation in its 2.4 form"

    config != null && (!config.klibValidationEnabled || config.klibSignatureVersion != null) ->
        "klibValidationEnabled/klibSignatureVersion are BCV-only settings"

    else -> null
}

/** `BinariesSource` arrived with the 2.4 DSL; probing by class never enables validation. */
private fun kgpHasStableAbiValidation(): Boolean = try {
    Class.forName(
        "org.jetbrains.kotlin.gradle.dsl.abi.BinariesSource",
        false,
        KotlinProjectExtension::class.java.classLoader,
    )
    true
} catch (_: ClassNotFoundException) {
    false
} catch (_: LinkageError) {
    false
}

private fun Project.setupKgpAbiValidation(
    conf: FluxoConfigurationExtensionImpl,
    isMultiplatform: Boolean,
) {
    val config = conf.apiValidationGetter
    val ctx = conf.ctx
    val ignoredPackages = config?.ignoredPackages.orEmpty()
    val ignoredClasses = config?.ignoredClasses ?: setOf(DEFAULT_CONSTRUCTOR_MARKER_CLASS)
    val nonPublicMarkers = config?.nonPublicMarkers ?: setOf(JVM_SYNTHETIC_CLASS)

    // BCV ignores a package with its subpackages; `**` matches any depth.
    enableKgpAbiValidation(
        excludedNames = ignoredPackages.map { "$it.**" } + ignoredClasses,
        nonPublicMarkers = nonPublicMarkers,
    )

    // BCV's task names, kept because consumer scripts, CI and `./updateBaseline` call them; a
    // Kotlin upgrade that switches the engine must not remove them. KGP has one check and one
    // update for all targets, so a per-target name runs those.
    registerAbiAliases(prefix = "api")
    if (isMultiplatform) {
        extensions.getByType(KotlinMultiplatformExtension::class.java).targets.configureEach {
            when (platformType) {
                KotlinPlatformType.jvm, KotlinPlatformType.androidJvm ->
                    registerAbiAliases(prefix = "${name}Api")

                KotlinPlatformType.common -> {}

                else -> registerAbiAliases(prefix = "klibApi")
            }
        }
    }

    // One check covers every target, so when KMP_TARGETS (or split targets) leaves out a target
    // this module declares, it would compare a partial dump with the full one and fail, and an
    // update would drop that target's ABI. Targets are all known once tasks are realised.
    if (!isMultiplatform) return
    val project = this
    var logged = false
    tasks.named { it == KGP_ABI_CHECK_TASK || it == KGP_ABI_UPDATE_TASK }.configureEach {
        if (!conf.kmpTargetFilteredOut) return@configureEach
        enabled = false
        if (logged) return@configureEach
        logged = true
        ctx.logDecision(
            project = project,
            setting = "ABI check and dump",
            value = "skipped",
            reason = "KMP_TARGETS leaves out a target of this module, and the dump covers all",
            howToChange = "run without a target filter",
        )
    }
}

/**
 * Reading `abiValidation` is what enables it, as `abiValidation {}` does (it also adds the check
 * to `check`). No lambdas here: the linkage check can't see [VersionGated] on a lambda's body.
 */
@VersionGated
@OptIn(ExperimentalAbiValidation::class)
private fun Project.enableKgpAbiValidation(
    excludedNames: Collection<String>,
    nonPublicMarkers: Collection<String>,
) {
    val exclude = extensions.getByType(KotlinProjectExtension::class.java)
        .abiValidation.filters.exclude
    exclude.byNames.addAll(excludedNames)
    exclude.annotatedWith.addAll(nonPublicMarkers)
}

/** Registers `<prefix>Dump`/`<prefix>Check` unless something already owns the name. */
private fun Project.registerAbiAliases(prefix: String) {
    val names = tasks.names
    val dump = prefix.replaceFirstChar { it.lowercase() } + "Dump"
    if (dump !in names) {
        tasks.register(dump) {
            group = "other"
            description = "Updates the ABI dumps (runs $KGP_ABI_UPDATE_TASK)"
            dependsOn(KGP_ABI_UPDATE_TASK)
        }
    }
    val check = prefix.replaceFirstChar { it.lowercase() } + "Check"
    if (check !in names) {
        tasks.register(check) {
            group = "verification"
            description = "Checks the ABI against the dumps (runs $KGP_ABI_CHECK_TASK)"
            dependsOn(KGP_ABI_CHECK_TASK)
        }
    }
}

internal const val KGP_ABI_UPDATE_TASK = "updateKotlinAbi"
internal const val KGP_ABI_CHECK_TASK = "checkKotlinAbi"
