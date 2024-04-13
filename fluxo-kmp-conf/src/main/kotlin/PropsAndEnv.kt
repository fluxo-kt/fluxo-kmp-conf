@file:Suppress("TooManyFunctions", "ktPropBy")
@file:JvmName("Fkc")
@file:JvmMultifileClass

import fluxo.conf.dsl.container.impl.KmpTargetCode.Companion.KMP_TARGETS_ALL_PROP
import fluxo.conf.dsl.container.impl.KmpTargetCode.Companion.KMP_TARGETS_PROP
import fluxo.conf.dsl.container.impl.KmpTargetCode.Companion.SPLIT_TARGETS_PROP
import fluxo.conf.feat.LOAD_KMM_CODE_COMPLETION_FLAG
import fluxo.conf.impl.envOrPropFlag
import fluxo.conf.impl.envOrPropFlagValue
import fluxo.conf.impl.envOrPropValue
import fluxo.conf.impl.envOrPropValueLenient
import fluxo.conf.impl.memoizeSafe
import fluxo.log.e
import fluxo.log.i
import java.util.regex.Pattern
import org.gradle.api.Incubating
import org.gradle.api.Project
import org.gradle.api.provider.Provider


// region Easy delegation

public operator fun Provider<Boolean>.getValue(t: Any?, p: Any?): Boolean = orNull == true

@JvmName("getValueOrNull")
public operator fun <T : Any> Provider<T?>.getValue(t: Any?, p: Any?): T? = orNull

public operator fun <T> Provider<T>.getValue(t: Any?, p: Any?): T = get()

// endregion


public fun Project.envOrPropValue(name: String): String? = envOrPropValue(name)

public fun Project.envOrPropInt(name: String): Int? = envOrPropValue(name)?.toIntOrNull()

public fun Project.envOrPropList(name: String): List<String> =
    envOrPropValue(name)?.split(Pattern.compile("\\s*,\\s*")).orEmpty()


public fun Project.isCI(): Provider<Boolean> = envOrPropFlag("CI")

public fun Project.isRelease(): Provider<Boolean> = envOrPropFlag("RELEASE")

public fun Project.useKotlinDebug(): Provider<Boolean> = envOrPropFlag("USE_KOTLIN_DEBUG")

public fun Project.disableTests(): Provider<Boolean> = envOrPropFlag("DISABLE_TESTS")

public fun Project.areComposeMetricsEnabled(): Provider<Boolean> = envOrPropFlag("COMPOSE_METRICS")

public fun Project.isDesugaringEnabled(): Provider<Boolean> = envOrPropFlag("DESUGARING")

public fun Project.isMaxDebugEnabled(): Provider<Boolean> = envOrPropFlag("MAX_DEBUG")

internal fun Project.isFluxoVerbose(): Provider<Boolean> = envOrPropFlag("FLUXO_VERBOSE")

public fun Project.isShrinkerDisabled(): Provider<Boolean> = provider {
    val disabled = envOrPropFlagValue("DISABLE_R8") ||
        envOrPropFlagValue("DISABLE_SHRINKER") ||
        envOrPropFlagValue("DISABLE_PROGUARD")

    val enabled = envOrPropFlagValue("OPTIMIZE") ||
        envOrPropFlagValue("SHRINK") ||
        envOrPropFlagValue("ENABLE_R8") ||
        envOrPropFlagValue("ENABLE_PROGUARD") ||
        envOrPropFlagValue("ENABLE_SHRINKER")

    disabled && !enabled
}.memoizeSafe()

public fun Project?.buildNumber(): String? = envOrPropValueLenient("BUILD_NUMBER")

internal fun Project.noManualHierarchy(): Boolean = envOrPropFlagValue("NO_MANUAL_HIERARCHY")

internal fun Project.allKmpTargetsEnabled(): Boolean = envOrPropFlagValue(KMP_TARGETS_ALL_PROP)

internal fun Project.isSplitTargetsEnabled(): Boolean =
    envOrPropFlagValue(SPLIT_TARGETS_PROP) || envOrPropFlagValue(SPLIT_TARGETS_PROP.uppercase())

internal fun Project.requestedKmpTargets(): String? = envOrPropValue(KMP_TARGETS_PROP)

internal fun Project.loadKmmCodeCompletion(): Boolean =
    envOrPropFlagValue(LOAD_KMM_CODE_COMPLETION_FLAG)


public fun Project.signingKey(): String? =
    envOrPropValue("SIGNING_KEY")?.replace("\\n", "\n")

public fun Project?.buildNumberSuffix(default: String = "", delimiter: String = "."): String {
    val n = buildNumber()
    return if (!n.isNullOrBlank()) "$delimiter$n" else default
}


@Incubating
@Suppress("ComplexCondition", "MagicNumber")
internal fun Project.scmTag(allowBranch: Boolean = true): Provider<String?> {
    // TODO: Optimize, make lazy accessible via root project including current branch name
    //  see com.diffplug.gradle.spotless.GitRatchetGradle
    //  com.diffplug.gradle.spotless.SpotlessTask.getRatchet

    // FIXME: Called 3 times with same error when no git installed or global config error.

    // FIXME: Don't check tag for the snapshot publication.
    // FIXME: Don't allow commit/branch for the release.
    //  If release version is specified, it should be in a tag.

    return provider {
        var result = envOrPropValue("SCM_TAG")
        if (result.isNullOrBlank()) {
            // current tag name
            result = runCommand("git tag -l --points-at HEAD")
            if (result != null &&
                result.length >= 2 &&
                result[0] == 'v' &&
                result[1].isDigit()
            ) {
                result = result.substring(1)
            }

            if (result == null) {
                // current commit short hash
                // FIXME: Consider using `GIT_COMMIT` (JitPack) or similar env var instead!
                result = runCommand("git rev-parse --short=7 HEAD")
                    // current branch name
                    // FIXME: Consider using `GIT_BRANCH` (JitPack) or similar env var instead!
                    ?: when {
                        allowBranch -> runCommand("git rev-parse --abbrev-ref HEAD")
                        else -> null
                    }
            }
        } else if (result.length == 40) {
            // full commit hash, sha1
            result = result.substring(0, 7)
        }
        result
    }.memoizeSafe()
}

@Suppress("UnstableApiUsage")
private fun Project.runCommand(command: String): String? {
    // https://docs.gradle.org/8.6/userguide/configuration_cache.html#config_cache:requirements:external_processes
    return try {
        val exec = providers.exec {
            isIgnoreExitValue = true
            commandLine(command.split(COMMAND_LINE_DELIMITER))
            logger.i("Executing command: `{}`", command)
        }
        val result = exec.result.get()
        val exitCodeIsNormal = result.exitValue == 0
        val error = exec.standardError.asText.get()
        when {
            error.isEmpty() && exitCodeIsNormal -> exec.standardOutput.asText.get()
                .trim().ifEmpty { null }

            else -> {
                logger.e("Error running command `{}`: {}", command, error)
                null
            }
        }
    } catch (e: Throwable) {
        logger.e("Error running command `$command`: $e", e)
        null
    }
}

private val COMMAND_LINE_DELIMITER = "\\s".toRegex()
