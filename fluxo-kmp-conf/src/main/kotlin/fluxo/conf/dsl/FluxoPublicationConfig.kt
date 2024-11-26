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
     * Use instead of [repositoryUrl] for the Vanniktech's Maven Publish plugin.
     *
     * Use only [com.vanniktech.maven.publish.SonatypeHost] instances!
     * The default is [com.vanniktech.maven.publish.SonatypeHost.DEFAULT].
     *
     * Type is `Any` to avoid compilation errors for the projects
     * that don't use the Vanniktech's Maven Publish plugin.
     *
     * @see com.vanniktech.maven.publish.SonatypeHost
     */
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
