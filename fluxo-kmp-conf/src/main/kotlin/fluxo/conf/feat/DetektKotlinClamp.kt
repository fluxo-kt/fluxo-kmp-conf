package fluxo.conf.feat

import org.gradle.api.logging.Logger

/**
 * Detekt 1.23.x's analyser doesn't recognise Kotlin language versions above
 * [DETEKT_MAX_SUPPORTED_KOTLIN_VERSION]. Without clamping the requested
 * `languageVersion`, Detekt would drop the suffix bytecode and a consumer
 * who bumps `kotlinLangVersion` past that point gets reduced analysis silently.
 *
 * The first time the cap actually triggers we warn at config time so the
 * maintainer notices and can either bump the table or accept the cap.
 * The warning is intentionally one-shot per JVM (a `@Volatile` boolean) to
 * keep the build log readable across hundreds of detekt task variants.
 *
 * Extracted from `SetupDetekt.kt` to keep that file under the
 * `TooManyFunctions` ceiling.
 */
internal const val DETEKT_MAX_SUPPORTED_KOTLIN_VERSION = "2.1"

@Volatile
private var WARNED_DETEKT_KOTLIN_CLAMP = false

internal fun clampKotlinLangVersionForDetekt(
    requested: String,
    logger: Logger,
): String {
    val max = DETEKT_MAX_SUPPORTED_KOTLIN_VERSION
    if (requested.toFloat() <= max.toFloat()) return requested
    if (!WARNED_DETEKT_KOTLIN_CLAMP) {
        WARNED_DETEKT_KOTLIN_CLAMP = true
        logger.warn(
            "[fluxo-kmp-conf] Detekt's max-supported Kotlin language version " +
                "($max) is older than the requested $requested. Detekt will " +
                "analyse with --language-version=$max. Bump Detekt to a release " +
                "that supports Kotlin $requested or update " +
                "DETEKT_MAX_SUPPORTED_KOTLIN_VERSION in DetektKotlinClamp.kt.",
        )
    }
    return max
}
