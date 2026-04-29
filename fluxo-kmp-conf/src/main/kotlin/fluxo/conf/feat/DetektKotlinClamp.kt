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

/**
 * Parses a Kotlin language version string (`"<major>"` or `"<major>.<minor>"`)
 * into a [kotlin.KotlinVersion] with patch = 0. Missing minor defaults to 0.
 * Pre-release suffixes aren't expected in this surface (the consumer's
 * `kotlinLangVersion` is always a stable value).
 *
 * `KotlinVersion` is `Comparable<KotlinVersion>` and lives in the kotlin stdlib
 * (no KGP coupling), so callers and tests can compare without going through
 * a Logger or the `@Volatile` warning state.
 */
internal fun parseDetektLangVersion(s: String): KotlinVersion {
    val parts = s.split(".")
    return KotlinVersion(parts[0].toInt(), parts.getOrNull(1)?.toInt() ?: 0)
}

/**
 * `true` iff [requested] is at or below [DETEKT_MAX_SUPPORTED_KOTLIN_VERSION] —
 * i.e., Detekt can analyse with the requested language version directly.
 *
 * Replaces a previous float-based comparator that worked only for single-digit
 * minors: `"1.10".toFloat() == 1.1f` (treated as below 1.9) and
 * `"2.10".toFloat() == 2.1f` (treated as equal to 2.1, no clamp triggered).
 * `KotlinVersion`'s major-then-minor-then-patch compareTo is the right shape.
 */
internal fun isWithinDetektSupportedLangVersion(requested: String): Boolean =
    parseDetektLangVersion(requested) <= parseDetektLangVersion(DETEKT_MAX_SUPPORTED_KOTLIN_VERSION)

internal fun clampKotlinLangVersionForDetekt(
    requested: String,
    logger: Logger,
): String {
    if (isWithinDetektSupportedLangVersion(requested)) return requested
    val max = DETEKT_MAX_SUPPORTED_KOTLIN_VERSION
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
