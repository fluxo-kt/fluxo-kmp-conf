package fluxo.conf.dsl.impl

import fluxo.conf.dsl.FluxoPublicationConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal fun FluxoPublicationConfig.finalizePublicationDefaults(
    githubProjectUrl: String?,
    /**
     * Read only for snapshot versions: it runs git, and git's output is a configuration-cache
     * input, so reading it for a release version would discard the cache on every commit.
     */
    fallbackScmTag: () -> String,
    reproducibleArtifacts: Boolean?,
    localSnapshotSuffix: String,
    timestamp: Date = Date(),
) {
    isSnapshot = version.contains("SNAPSHOT", ignoreCase = true)
    if (reproducibleArtifacts != false && isSnapshot) {
        version = reproducibleSnapshotVersion(
            rawVersion = version,
            scmTag = scmTag.orEmpty().ifBlank(fallbackScmTag),
            localSnapshotSuffix = localSnapshotSuffix,
            timestamp = timestamp,
        )
    }
    isSnapshot = version.contains("SNAPSHOT", ignoreCase = true)
    if (scmTag.isNullOrBlank()) {
        scmTag = when {
            isSnapshot -> fallbackScmTag()
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
        // Version structure: `major.minor-COMMIT_SHA-SNAPSHOT`: the commit replaces the patch,
        // so a version without one (`1.2`) keeps all its parts.
        result = result.trimEnd { !it.isDigit() }
        if (result.count { it == '.' } >= 2) result = result.substringBeforeLast('.')
        return "$result-$scmTag-SNAPSHOT"
    }

    // Version structure: `major.minor.patch-yyMMddHHmmss-buildNumber-SNAPSHOT`.
    val timestampSuffix = SimpleDateFormat("yyMMddHHmmss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(timestamp)
    return "$result$timestampSuffix$localSnapshotSuffix-SNAPSHOT"
}
