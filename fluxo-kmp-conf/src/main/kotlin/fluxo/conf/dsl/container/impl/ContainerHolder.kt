package fluxo.conf.dsl.container.impl

import fluxo.conf.dsl.container.Container
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.kotlin.KOTLIN_PLUGIN_VERSION_STRING
import fluxo.conf.impl.uncheckedCast
import fluxo.log.d
import fluxo.log.logDecision
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectSet

internal class ContainerHolder(
    conf: FluxoConfigurationExtensionImpl,
    private val onlyTarget: KmpTargetCode?,
) : ContainerContext(conf) {

    private val KmpTargetCode.isEnabled: Boolean
        get() = ctx.isTargetEnabled(this)

    val containers: NamedDomainObjectSet<Container> =
        objects.namedDomainObjectSet(Container::class.java)

    /** `true` while a target group adds its targets, see [group]. */
    private var inGroup = false

    /**
     * Runs a target group (`ios()`, `allDefaultTargets()`, ...). A group adds only targets the
     * consumer's Kotlin fully supports, and not iosX64 (ruled 2026-10-04: Compose Multiplatform
     * dropped it). Kotlin deprecates native targets and later deletes their DSL, so a group that
     * kept them would warn, then fail once Kotlin stops tolerating the target, then crash with
     * NoSuchMethodError. An explicit per-target call still adds a deprecated target.
     * Groups nest (`allDefaultTargets()` calls `ios()`), so only the outermost one resets the
     * flag. A plain field: one project is configured on one thread.
     */
    fun group(addTargets: () -> Unit) {
        if (inGroup) return addTargets()
        inGroup = true
        try {
            addTargets()
        } finally {
            inGroup = false
        }
    }

    fun <T : KmpTargetContainerImpl<*>> configure(
        targetName: String,
        contruct: (ContainerContext, targetName: String) -> T,
        code: KmpTargetCode,
        action: T.() -> Unit,
    ) {
        if (onlyTarget != null && onlyTarget != code) {
            // Return early if configuring only a single target.
            // It's not an error as default KMP targets can be applied to JVM-obly subprojects.
            // So just skip the unexpected ones.
            project.logger.d("Skipping target '$targetName' because only '$onlyTarget' is allowed.")
            return
        }

        var container = findByName<T>(targetName)
        if (container == null) {
            // Don't contruct the container for turned-off targets.
            if (!code.isEnabled) conf.kmpTargetFilteredOut = true
            if (!code.isEnabled || !isAddable(targetName, code)) {
                return
            }
            container = contruct(this, targetName)
            if (code == KmpTargetCode.JS || code == KmpTargetCode.WASM_JS) {
                conf.kmpHasWebTarget = true
            }
            require(containers.add(container)) { "Couldn't add container for target '$targetName'" }
        }
        action(container)
    }

    /** See [group]. An explicit call to a target Kotlin removed fails with the fix. */
    private fun isAddable(targetName: String, code: KmpTargetCode): Boolean {
        val support = code.kotlinSupport()
        val kotlin = KOTLIN_PLUGIN_VERSION_STRING
        if (inGroup && (support != KotlinSupport.FULL || code == KmpTargetCode.IOS_X64)) {
            ctx.logDecision(
                project,
                setting = "KMP target $targetName",
                value = "skipped",
                reason = "target groups add only targets Kotlin fully supports, and " + when {
                    support == KotlinSupport.UNSUPPORTED -> "Kotlin $kotlin no longer supports it"
                    support == KotlinSupport.DEPRECATED -> "Kotlin $kotlin deprecates it"
                    else -> "Compose Multiplatform dropped it"
                },
                howToChange = "call $targetName() explicitly",
            )
            if (support == KotlinSupport.DEPRECATED) reportSkippedDeprecated(targetName)
            return false
        }
        if (support == KotlinSupport.UNSUPPORTED) {
            throw GradleException(
                "Kotlin $kotlin no longer supports the ${code.name.lowercase()} target " +
                    "(requested as '$targetName' in fkcSetupMultiplatform). Remove that target, " +
                    "or build with an older Kotlin.",
            )
        }
        return true
    }

    /**
     * One warning per build, listing every deprecated target any module's groups skipped: a
     * library that stops publishing a target breaks its own consumers, so this must be seen.
     * Removed targets are not listed: nothing can bring them back.
     */
    private fun reportSkippedDeprecated(targetName: String) {
        val kotlin = KOTLIN_PLUGIN_VERSION_STRING
        ctx.buildEndReport.warnAggregated(SKIPPED_DEPRECATED_TARGETS, targetName) { names ->
            "w: KMP target groups (allDefaultTargets(), ios(), macos(), ...) skipped the " +
                "targets Kotlin $kotlin deprecates: ${names.joinToString()}. " +
                "To keep one, call it explicitly, e.g. ${names[0]}()."
        }
    }

    fun <T, C : CustomTypeContainer<T>> configureCustom(
        name: String,
        contruct: (ContainerContext, name: String) -> C,
        action: T.() -> Unit,
    ) {
        var container = findByName<C>(name)
        if (container == null) {
            container = contruct(this, name)
            check(containers.add(container)) { "Couldn't add container for name '$name'" }
        }
        container.add(action)
    }

    private fun <T : ContainerImpl> findByName(name: String): T? =
        uncheckedCast(containers.findByName(name))
}

private const val SKIPPED_DEPRECATED_TARGETS = "skipped-deprecated-kmp-targets"
