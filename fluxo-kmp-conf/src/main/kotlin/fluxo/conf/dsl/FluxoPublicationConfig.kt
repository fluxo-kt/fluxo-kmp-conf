package fluxo.conf.dsl

import fluxo.conf.dsl.FluxoConfigurationExtensionPublication.Companion.DEFAULT_BRANCH_NAME

@Suppress("LongParameterList")
public data class FluxoPublicationConfig(
    public var group: String,
    public var version: String,
    public var projectName: String? = null,

    public var projectDescription: String? = null,
    public var projectUrl: String? = null,
    public var scmUrl: String? = null,

    public var developerId: String? = null,
    public var developerName: String? = null,
    public var developerEmail: String? = null,

    public var signingKey: String? = null,
    public var signingPassword: String? = null,

    public var repositoryUserName: String? = null,
    public var repositoryPassword: String? = null,

    public var publicationUrl: String? = projectUrl,
    public var isSnapshot: Boolean = version.contains("SNAPSHOT", ignoreCase = true),

    public var scmTag: String? = if (isSnapshot) DEFAULT_BRANCH_NAME else "v$version",

    public var repositoryUrl: String = when {
        isSnapshot -> "https://s01.oss.sonatype.org/content/repositories/snapshots/"
        else -> "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
    },

    public var licenseName: String? = "The Apache License, Version 2.0",
    public var licenseUrl: String? = "http://www.apache.org/licenses/LICENSE-2.0.txt",
) {
    public val isSigningEnabled: Boolean
        get() = !signingKey.isNullOrEmpty()
}
