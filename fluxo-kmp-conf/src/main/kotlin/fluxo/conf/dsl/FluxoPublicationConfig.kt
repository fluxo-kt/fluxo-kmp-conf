package fluxo.conf.dsl

import fluxo.conf.dsl.FluxoConfigurationExtensionPublication.Companion.DEFAULT_BRANCH_NAME

// TODO: Make an immutable resulting class

@Suppress("LongParameterList")
public data class FluxoPublicationConfig(
    public var group: String,
    public var version: String,
    /** Artifact ID, basically */
    public var projectName: String? = null,

    public var inceptionYear: String? = null,

    public var projectDescription: String? = null,
    /** Project website or repository url */
    public var projectUrl: String? = null,
    /** `scm:git:git://..` url */
    public var scmUrl: String? = null,

    public var developerId: String? = null,
    public var developerName: String? = null,
    public var developerEmail: String? = null,

    /** PGP signing key */
    public var signingKey: String? = null,
    /** PGP signing key ID */
    public var signingKeyId: String? = null,
    /** PGP signing password */
    public var signingPassword: String? = null,

    /** Maven publishing repository username */
    public var repositoryUserName: String? = null,
    /** Maven publishing repository password */
    public var repositoryPassword: String? = null,

    /** VCS link to the specific publication (e.g., release tag) */
    public var publicationUrl: String? = projectUrl,
    public var isSnapshot: Boolean = version.contains("SNAPSHOT", ignoreCase = true),

    /** VCS tag or branch name for publication */
    public var scmTag: String? = if (isSnapshot) DEFAULT_BRANCH_NAME else "v$version",

    /**
     * No longer consulted: Vanniktech's Maven Publish plugin removed `SonatypeHost`
     * (and all OSSRH support) in 0.34.0 — Sonatype Central Portal is the only target now.
     * The field is retained for one release as a no-op so consumers see a deprecation
     * warning instead of an unresolved-reference compile error; remove it from your
     * build script and migrate publish credentials to a Central Portal user-token.
     *
     * @see <a href="https://central.sonatype.org/publish/publish-portal-gradle/">Central Portal Gradle guide</a>
     */
    @Deprecated(
        "Vanniktech 0.34+ removed SonatypeHost; Central Portal is the only target. " +
            "This field is now ignored — remove it from your build script.",
        level = DeprecationLevel.WARNING,
    )
    public var sonatypeHost: Any? = null,

    public var repositoryUrl: String = when {
        isSnapshot -> "https://s01.oss.sonatype.org/content/repositories/snapshots/"
        else -> "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
    },

    public var licenseName: String? = "The Apache License, Version 2.0",
    public var licenseUrl: String? = "https://www.apache.org/licenses/LICENSE-2.0.txt",
) {
    public val isSigningEnabled: Boolean
        get() = !signingKey.isNullOrEmpty()
}
