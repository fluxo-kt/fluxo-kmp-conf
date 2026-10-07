package fluxo.conf.feat

import fluxo.annotation.VersionGated
import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.dsl.BinaryCompatibilityValidatorConfig
import fluxo.conf.dsl.DEFAULT_CONSTRUCTOR_MARKER_CLASS
import fluxo.conf.dsl.JVM_SYNTHETIC_CLASS
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.log.logDecision
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

/**
 * Picks the engine for a module's ABI dumps and checks, logging the choice.
 *
 * The Kotlin Gradle plugin ships ABI validation from 2.2, and new ABI work lands there while BCV
 * only gets fixes, so fluxo uses it wherever it exists and the consumer needs no BCV plugin.
 * Experimental on every version, as BCV was never stable either; it only checks, so it changes
 * nothing consumers of a library get. It writes the same files as BCV (`api/<module>.api`,
 * `api/<target>/<module>.api`, `<module>.klib.api`), byte-identical with the same filters
 * (compat `runKmpKgpAbiCase`, each Kotlin line vs BCV 0.18.2), so committed BCV dumps keep
 * passing. BCV stays where the build applies it or asks for a BCV-only setting.
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
            "Kotlin 2.2+ uses the Kotlin Gradle plugin's engine"
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

    !kgpHasAbiValidation() && legacyAbiValidation() == null ->
        "Kotlin ${ctx.kotlinPluginVersion} has no ABI validation of its own"

    config != null && (!config.klibValidationEnabled || config.klibSignatureVersion != null) ->
        "klibValidationEnabled/klibSignatureVersion are BCV-only settings"

    else -> null
}

/** `BinariesSource` arrived with the 2.4 DSL; probing by class never enables validation. */
private fun kgpHasAbiValidation(): Boolean = try {
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
    val excludedNames = ignoredPackages.map { "$it.**" } + ignoredClasses
    val legacy = legacyAbiValidation()
    val abi = if (legacy == null) {
        enableKgpAbiValidation(excludedNames, nonPublicMarkers)
        AbiTasks(check = KGP_ABI_CHECK_TASK, update = KGP_ABI_UPDATE_TASK)
    } else {
        // These versions don't add their check to `check`.
        legacy.enableLegacyAbiValidation(excludedNames, nonPublicMarkers)
            .also { t -> tasks.named(CHECK_TASK_NAME) { dependsOn(t.check) } }
    }

    // BCV's task names, kept because consumer scripts, CI and `./updateBaseline` call them; a
    // Kotlin upgrade that switches the engine must not remove them. KGP has one check and one
    // update for all targets, so a per-target name runs those.
    registerAbiAliases(prefix = "api", abi)
    if (isMultiplatform) {
        extensions.getByType(KotlinMultiplatformExtension::class.java).targets.configureEach {
            when (platformType) {
                KotlinPlatformType.jvm, KotlinPlatformType.androidJvm ->
                    registerAbiAliases(prefix = "${name}Api", abi)

                KotlinPlatformType.common -> {}

                else -> registerAbiAliases(prefix = "klibApi", abi)
            }
        }
    }

    // One check covers every target, so when KMP_TARGETS (or split targets) leaves out a target
    // this module declares, it would compare a partial dump with the full one and fail, and an
    // update would drop that target's ABI. Targets are all known once tasks are realised.
    if (!isMultiplatform) return
    val project = this
    var logged = false
    tasks.named { it == abi.check || it == abi.update }.configureEach {
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

/** The Kotlin Gradle plugin's ABI check and update task names. */
private class AbiTasks(val check: String, val update: String)

/** Registers `<prefix>Dump`/`<prefix>Check` unless something already owns the name. */
private fun Project.registerAbiAliases(prefix: String, abi: AbiTasks) {
    val names = tasks.names
    val dump = prefix.replaceFirstChar { it.lowercase() } + "Dump"
    if (dump !in names) {
        tasks.register(dump) {
            group = "other"
            description = "Alias of ${abi.update}, which updates every target's ABI " +
                "dump; `-x $dump` excludes nothing, exclude ${abi.update}"
            dependsOn(abi.update)
        }
    }
    val check = prefix.replaceFirstChar { it.lowercase() } + "Check"
    if (check !in names) {
        tasks.register(check) {
            group = "verification"
            description = "Alias of ${abi.check}, which checks every target's ABI; " +
                "`-x $check` excludes nothing, exclude ${abi.check}"
            dependsOn(abi.check)
        }
    }
}

/**
 * Kotlin 2.2 and 2.3's ABI validation: an `abiValidation` extension on the `kotlin` extension,
 * absent on 2.4 (there [kgpHasAbiValidation] holds). `null` when neither exists.
 */
private fun Project.legacyAbiValidation(): Any? =
    (extensions.findByName(KOTLIN_EXT) as? ExtensionAware)?.extensions?.findByName(ABI_EXT)
        ?.takeUnless { kgpHasAbiValidation() }

/**
 * Drives the 2.2/2.3 shape by reflection: its types are gone from the 2.4 API fluxo compiles
 * against, and 2.2 and 2.3 differ from each other (`filters.excluded` became `exclude`), so no
 * single typed call compiles for both — the case AGENTS.md allows reflection for. A member
 * missing on some future patch fails here, naming it.
 *
 * It must be enabled explicitly, and a multiplatform module's klib dumps too (BCV dumps them).
 * Its tasks are read from its own providers, so their names (`checkLegacyAbi` on 2.2,
 * `checkKotlinAbi` on 2.3.20+) need no table.
 */
private fun Any.enableLegacyAbiValidation(
    excludedNames: Collection<String>,
    nonPublicMarkers: Collection<String>,
): AbiTasks {
    property<Property<Boolean>>("enabled").set(true)
    val filters = property<Any>("filters")
    val exclude = filters.propertyOrNull<Any>("exclude") ?: filters.property<Any>("excluded")
    exclude.property<SetProperty<String>>("byNames").addAll(excludedNames)
    exclude.property<SetProperty<String>>("annotatedWith").addAll(nonPublicMarkers)
    propertyOrNull<Any>("klib")?.property<Property<Boolean>>("enabled")?.set(true)
    val legacyDump = property<Any>("legacyDump")
    return AbiTasks(
        check = legacyDump.property<TaskProvider<*>>("legacyCheckTaskProvider").name,
        update = legacyDump.property<TaskProvider<*>>("legacyUpdateTaskProvider").name,
    )
}

private fun <T> Any.property(name: String): T =
    checkNotNull(propertyOrNull(name)) { "Kotlin's ABI validation has no '$name' in $javaClass" }

@Suppress("UNCHECKED_CAST")
private fun <T> Any.propertyOrNull(name: String): T? = try {
    javaClass.getMethod("get" + name.replaceFirstChar(Char::uppercaseChar)).invoke(this) as T
} catch (_: NoSuchMethodException) {
    null
}

private const val KOTLIN_EXT = "kotlin"
private const val ABI_EXT = "abiValidation"

internal const val KGP_ABI_UPDATE_TASK = "updateKotlinAbi"
internal const val KGP_ABI_CHECK_TASK = "checkKotlinAbi"
