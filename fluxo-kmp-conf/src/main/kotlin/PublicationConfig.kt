import java.text.SimpleDateFormat
import java.util.Date
import org.gradle.api.Project

@Suppress("LongParameterList", "MemberVisibilityCanBePrivate")
public class PublicationConfig(
    public val group: String,
    version: String,
    public val projectName: String,

    public val projectDescription: String,
    public val projectUrl: String,
    public val scmUrl: String,

    public val developerId: String,
    public val developerName: String,
    public val developerEmail: String,

    public val signingKey: String?,
    public val signingPassword: String?,

    public val repositoryUserName: String?,
    public val repositoryPassword: String?,

    public val publicationUrl: String = projectUrl,
    public val isSnapshot: Boolean = version.contains("SNAPSHOT", ignoreCase = true),
    reproducibleSnapshots: Boolean = version.endsWith("SNAPSHOT", ignoreCase = true),

    public val scmTag: String? = if (isSnapshot) "main" else "v$version",

    public val repositoryUrl: String = when {
        isSnapshot -> "https://s01.oss.sonatype.org/content/repositories/snapshots/"
        else -> "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
    },

    public val licenseName: String? = "The Apache License, Version 2.0",
    public val licenseUrl: String? = "http://www.apache.org/licenses/LICENSE-2.0.txt",

    /** Set for [reproducibleSnapshots] */
    project: Project? = null,

    // Halpful links:
    // https://proandroiddev.com/publishing-android-libraries-to-mavencentral-in-2021-8ac9975c3e52
    // https://github.com/jveverka/java-11-examples/blob/b9819fe0/artefact-publishing-demo/test-artefact/README.md
    // https://motorro.medium.com/thanks-a-lot-for-this-step-by-step-instructions-f6fecbe5a4e6
    // https://central.sonatype.org/publish/requirements/gpg/
) {
    public val version: String

    public val isSigningEnabled: Boolean
        get() = !signingKey.isNullOrEmpty()

    init {
        var v = version

        // Make snapshot builds safe and reproducible for usage
        if (reproducibleSnapshots && isSnapshot) {
            v = v.substringBeforeLast("SNAPSHOT")

            // commit short hash is more convenient for usage as date-n-build
            val commitSha = project?.scmTag(allowBranch = false)?.orNull
            if (!commitSha.isNullOrEmpty()) {
                // Version structure: `major.minor-COMMIT_SHA-SNAPSHOT`.
                v = v.trimEnd { !it.isDigit() }
                val idx = v.lastIndexOf('.')
                if (idx > 0) v = v.substring(0, idx)
                v += "-$commitSha"
            } else {
                // Version structure: `major.minor.patch-yyMMddHHmmss-buildNumber-SNAPSHOT`.
                v += SimpleDateFormat("yyMMddHHmmss").format(Date())
                v += project.buildNumberSuffix("-local", "-")
            }
            v += "-SNAPSHOT"
        }

        this.version = v
    }
}
