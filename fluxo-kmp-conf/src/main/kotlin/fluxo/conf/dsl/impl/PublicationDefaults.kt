package fluxo.conf.dsl.impl

import fluxo.conf.dsl.FluxoPublicationConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal fun FluxoPublicationConfig.finalizePublicationDefaults(
    githubProjectUrl: String?,
    fallbackScmTag: String,
    reproducibleArtifacts: Boolean?,
    localSnapshotSuffix: String,
    timestamp: Date = Date(),
) {
    isSnapshot = version.contains("SNAPSHOT", ignoreCase = true)
    val resolvedScmTag = scmTag.orEmpty().ifBlank { fallbackScmTag }
    if (reproducibleArtifacts != false && isSnapshot) {
        version = reproducibleSnapshotVersion(
            rawVersion = version,
            scmTag = resolvedScmTag,
            localSnapshotSuffix = localSnapshotSuffix,
            timestamp = timestamp,
        )
    }
    isSnapshot = version.contains("SNAPSHOT", ignoreCase = true)
    if (scmTag.isNullOrBlank()) {
        scmTag = when {
            isSnapshot -> fallbackScmTag
            else -> "v$version"
        }
    }
    if (publicationUrl.isNullOrBlank() && !githubProjectUrl.isNullOrBlank()) {
        publicationUrl = "$githubProjectUrl/tree/$scmTag"
    }
}

internal fun reproducibleSnapshotVersion(
    rawVersion: String,
    scmTag: String,
    localSnapshotSuffix: String,
    timestamp: Date,
): String {
    var result = rawVersion.substringBeforeLast("SNAPSHOT")
    if (scmTag.isNotEmpty()) {
        // Version structure: `major.minor-COMMIT_SHA-SNAPSHOT`.
        result = result.trimEnd { !it.isDigit() }
        val idx = result.lastIndexOf('.')
        if (idx > 0) result = result.substring(0, idx)
        return "$result-$scmTag-SNAPSHOT"
    }

    // Version structure: `major.minor.patch-yyMMddHHmmss-buildNumber-SNAPSHOT`.
    val timestampSuffix = SimpleDateFormat("yyMMddHHmmss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(timestamp)
    return "$result$timestampSuffix$localSnapshotSuffix-SNAPSHOT"
}
