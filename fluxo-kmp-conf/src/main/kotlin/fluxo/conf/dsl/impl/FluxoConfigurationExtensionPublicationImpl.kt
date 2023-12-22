package fluxo.conf.dsl.impl

import buildNumberSuffix
import envOrPropValue
import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.dsl.FluxoConfigurationExtension
import fluxo.conf.dsl.FluxoConfigurationExtensionPublication
import fluxo.conf.dsl.FluxoConfigurationExtensionPublication.Companion.DEFAULT_BRANCH_NAME
import fluxo.conf.dsl.FluxoPublicationConfig
import fluxo.conf.impl.v
import java.text.SimpleDateFormat
import java.util.Date
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import scmTag
import signingKey

internal interface FluxoConfigurationExtensionPublicationImpl :
    FluxoConfigurationExtensionPublication {

    val project: Project
    val context: FluxoKmpConfContext
    val parent: FluxoConfigurationExtension?


    @get:Input
    val enablePublicationProp: Property<Boolean?>
    override var enablePublication: Boolean?
        get() = enablePublicationProp.orNull ?: parent?.enablePublication
        set(value) = enablePublicationProp.set(value)


    @get:Input
    val versionProp: Property<String?>
    override var version: String
        get() = versionProp.orNull
            ?: parent?.version?.takeIf { it.isNotBlank() }
            ?: context.libs.v("version", "versionName", "app", "appVersion", "v")
            ?: project.version.toString()
        set(value) = versionProp.set(value)

    @get:Input
    val groupProp: Property<String?>
    override var group: String
        get() = groupProp.orNull
            ?: parent?.group?.takeIf { it.isNotBlank() }
            ?: context.libs.v("group", "package")
            ?: project.group.toString()
        set(value) = groupProp.set(value)

    @get:Input
    val projectNameProp: Property<String?>
    override var projectName: String?
        get() = projectNameProp.orNull ?: parent?.projectName
        set(value) = projectNameProp.set(value)


    @get:Input
    val descriptionProp: Property<String?>
    override var description: String?
        get() {
            return descriptionProp.orNull
                ?: project.description?.takeIf { it.isNotBlank() }
                ?: parent?.description
        }
        set(value) = descriptionProp.set(value)


    @get:Input
    val defaultBranchProp: Property<String?>
    override var defaultGitBranchName: String
        get() = defaultBranchProp.orNull ?: parent?.defaultGitBranchName ?: DEFAULT_BRANCH_NAME
        set(value) = defaultBranchProp.set(value)


    @get:Input
    val githubProjectProp: Property<String?>
    override var githubProject: String?
        get() = githubProjectProp.orNull ?: parent?.githubProject
        set(value) = githubProjectProp.set(value)


    @get:Input
    val reproducibleSnapshotsProp: Property<Boolean?>
    override var reproducibleArtifacts: Boolean?
        get() = reproducibleSnapshotsProp.orNull ?: parent?.reproducibleArtifacts
        set(value) = reproducibleSnapshotsProp.set(value)


    @get:Input
    val publicationConfigProp: Property<FluxoPublicationConfig?>
    override var publicationConfig: FluxoPublicationConfig?
        get() = publicationConfigProp.orNull ?: parent?.publicationConfig?.updateForCurrentProject()
        set(value) = publicationConfigProp.set(value)

    private fun FluxoPublicationConfig.updateForCurrentProject(): FluxoPublicationConfig {
        val conf = this@FluxoConfigurationExtensionPublicationImpl
        return this.copy(
            projectName = projectNamePrep().ifEmpty { projectName },
            projectDescription = conf.description.orEmpty().ifEmpty { projectDescription },
            group = conf.group.ifEmpty { group },
        )
    }

    private fun projectNamePrep(): String {
        return projectName ?: project.rootProject.name.let {
            when {
                it.length <= 1 -> it.uppercase()
                else -> it[0].uppercase() + it.substring(1)
            }
        }
    }

    override fun publicationConfig(configure: FluxoPublicationConfig.() -> Unit) {
        val project = project
        var version = version
        val group = group
        val description = description
        val isSnapshot = version.contains("SNAPSHOT", ignoreCase = true)
        var url: String? = null
        var scmUrl: String? = null
        var publicationUrl: String? = null

        val scmTag = when {
            !isSnapshot -> "v$version"
            else -> project.scmTag().orNull ?: defaultGitBranchName
        }

        // TODO: Add validation for value. Shouldn't be url, but `namespace/name`
        githubProject?.let { githubProject ->
            url = "https://github.com/$githubProject"
            publicationUrl = "$url/tree/$scmTag"
            scmUrl = "scm:git:git://github.com/$githubProject.git"
        }

        // Make snapshot builds safe and reproducible for usage
        if (reproducibleArtifacts != false && isSnapshot) {
            version = version.substringBeforeLast("SNAPSHOT")

            // commit short hash is more convenient for usage as date-n-build
            val commitSha = project.scmTag(allowBranch = false).orNull
            if (!commitSha.isNullOrEmpty()) {
                // Version structure: `major.minor-COMMIT_SHA-SNAPSHOT`.
                version = version.trimEnd { !it.isDigit() }
                val idx = version.lastIndexOf('.')
                if (idx > 0) version = version.substring(0, idx)
                version += "-$commitSha"
            } else {
                // Version structure: `major.minor.patch-yyMMddHHmmss-buildNumber-SNAPSHOT`.
                version += SimpleDateFormat("yyMMddHHmmss").format(Date())
                version += project.buildNumberSuffix("-local", "-")
            }
            version += "-SNAPSHOT"
        }

        publicationConfig = FluxoPublicationConfig(
            group = group,
            version = version,
            projectName = projectNamePrep(),
            projectDescription = description.orEmpty(),
            projectUrl = url,
            publicationUrl = publicationUrl,
            scmUrl = scmUrl,
            scmTag = scmTag,
            signingKey = project.signingKey(),
            signingPassword = project.envOrPropValue("SIGNING_PASSWORD"),
            repositoryUserName = project.envOrPropValue("OSSRH_USER"),
            repositoryPassword = project.envOrPropValue("OSSRH_PASSWORD"),
        ).apply(configure)
    }
}
