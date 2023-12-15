package fluxo.conf.dsl

import fluxo.conf.impl.EMPTY_FUN

public interface FluxoConfigurationExtensionPublication {

    // Helpful links:
    // https://proandroiddev.com/publishing-android-libraries-to-mavencentral-in-2021-8ac9975c3e52
    // https://github.com/jveverka/java-11-examples/blob/b9819fe0/artefact-publishing-demo/test-artefact/README.md
    // https://motorro.medium.com/thanks-a-lot-for-this-step-by-step-instructions-f6fecbe5a4e6
    // https://central.sonatype.org/publish/requirements/gpg/

    // Second part:
    // https://central.sonatype.org/publish/publish-gradle/
    // https://central.sonatype.org/publish/publish-guide/#initial-setup
    // https://central.sonatype.org/publish/requirements/coordinates/#choose-your-coordinates
    // https://github.com/jonashackt/github-actions-release-maven
    // https://dev.to/kotlin/how-to-build-and-publish-a-kotlin-multiplatform-library-creating-your-first-library-1bp8


    /**
     * Flag to enable publication of this project.
     * Inherited from the parent project if not set.
     *
     * Defaults to `true` if [publicationConfig] is set.
     */
    public var enablePublication: Boolean?


    /**
     * The version for publication of this project.
     * Inherited from the parent project if not set.
     *
     * Auto set using the version names in toml version catalog:
     * `version`, `versionName`, `app`, `appVersion` or `v`.
     *
     * Defaults to `"unspecified"`.
     *
     * @see org.gradle.api.Project.getVersion
     */
    public var version: String

    /**
     * The group for publication of this project.
     * Inherited from the parent project if not set.
     *
     * Auto set using the version names in toml version catalog:
     * `group`, or `package`.
     *
     * Defaults to the project path with dots as separators.
     *
     * @see org.gradle.api.Project.getGroup
     */
    public var group: String

    /**
     * Description of this project, if any.
     *
     * @see org.gradle.api.Project.getDescription
     */
    public var description: String?

    public var projectName: String?


    public var defaultGitBranchName: String

    /** For example: `namespace/name`. Don't use URLs! */
    public var githubProject: String?


    /**
     * Flag to disable reproducible artifact generation.
     *
     * Defaults to `true`.
     */
    public var reproducibleArtifacts: Boolean?


    /**
     * Config for the project artifacts publication.
     */
    public var publicationConfig: FluxoPublicationConfig?

    /**
     * Reasonably configures the [FluxoPublicationConfig]
     * with provided values with possibility for customization.
     *
     * @see publicationConfig
     */
    public fun publicationConfig(configure: FluxoPublicationConfig.() -> Unit = EMPTY_FUN)


    public companion object {
        public const val DEFAULT_BRANCH_NAME: String = "main"
    }
}
