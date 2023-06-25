package fluxo.conf.dsl

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


    public var enablePublication: Boolean?


    /**
     * The version for publication of this project.
     *
     * @see org.gradle.api.Project.getVersion
     */
    public var version: String?

    /**
     * The group for publication of this project.
     *
     * @see org.gradle.api.Project.getGroup
     */
    public var group: String?

    /**
     * Description of this project, if any.
     *
     * @see org.gradle.api.Project.getDescription
     */
    public var description: String?

    public var projectName: String?


    public var defaultGitBranchName: String

    /** For example: `fluxo-kt/fluxo` */
    public var githubProject: String?


    public var reproducibleSnapshots: Boolean?


    public var publicationConfig: FluxoPublicationConfig?

    public fun publicationConfig(configure: FluxoPublicationConfig.() -> Unit)


    public companion object {
        public const val DEFAULT_BRANCH_NAME: String = "main"
    }
}
