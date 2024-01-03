@file:Suppress("TooManyFunctions", "ktPropBy")

import fluxo.conf.dsl.container.impl.KmpTargetCode.Companion.KMP_TARGETS_ALL_PROP
import fluxo.conf.dsl.container.impl.KmpTargetCode.Companion.KMP_TARGETS_PROP
import fluxo.conf.dsl.container.impl.KmpTargetCode.Companion.SPLIT_TARGETS_PROP
import fluxo.conf.feat.LOAD_KMM_CODE_COMPLETION_FLAG
import fluxo.conf.impl.envOrPropFlag
import fluxo.conf.impl.envOrPropFlagValue
import fluxo.conf.impl.envOrPropValue
import fluxo.conf.impl.envOrPropValueLenient
import fluxo.conf.impl.memoizeSafe
import java.util.regex.Pattern
import org.gradle.api.Incubating
import org.gradle.api.Project
import org.gradle.api.provider.Provider

public operator fun Provider<Boolean>.getValue(t: Any?, p: Any?): Boolean = orNull == true

@JvmName("getValueOrNull")
public operator fun <T : Any> Provider<T?>.getValue(t: Any?, p: Any?): T? = orNull

public operator fun <T> Provider<T>.getValue(t: Any?, p: Any?): T = get()


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

public fun Project.isR8Disabled(): Provider<Boolean> = envOrPropFlag("DISABLE_R8")

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
public fun Project.scmTag(allowBranch: Boolean = true): Provider<String?> {
    // TODO: Optimize, make lazy accessible via root project
    //  see com.diffplug.gradle.spotless.GitRatchetGradle
    //  com.diffplug.gradle.spotless.SpotlessTask.getRatchet

    return provider {
        var result = envOrPropValue("SCM_TAG")
        if (result.isNullOrBlank()) {
            // current tag name
            var tagName = runCommand("git tag -l --points-at HEAD")
            if (tagName != null && tagName.length >= 2 && tagName[0] == 'v' && tagName[1].isDigit()) {
                tagName = tagName.substring(1)
            }

            result = tagName
                // current commit short hash
                ?: runCommand("git rev-parse --short=7 HEAD")
                    // current branch name
                    ?: if (allowBranch) runCommand("git rev-parse --abbrev-ref HEAD") else null
        } else if (result.length == 40) {
            // full commit hash, sha1
            result = result.substring(0, 7)
        }
        result
    }.memoizeSafe()
}

@Suppress("UnstableApiUsage")
private fun Project.runCommand(command: String): String? {
    // https://docs.gradle.org/7.5.1/userguide/configuration_cache.html#config_cache:requirements:external_processes
    return try {
        val exec = providers.exec {
            commandLine(command.split("\\s".toRegex()))
        }
        val error = exec.standardError.asText.get()
        when {
            error.isEmpty() -> exec.standardOutput.asText.get().trim().ifEmpty { null }
            else -> {
                logger.error("Error running command `$command`: $error")
                null
            }
        }
    } catch (e: Throwable) {
        logger.error("Error running command `$command`: $e", e)
        null
    }
}
