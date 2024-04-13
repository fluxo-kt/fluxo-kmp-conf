package fluxo.artifact.proc

import fluxo.log.l
import fluxo.log.vb
import java.io.File
import org.gradle.api.Project
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.file.RegularFile
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Provider

internal fun Project.replaceOutgoingArtifactJarInConfigurations(
    jarProvider: Provider<RegularFile>,
    builtBy: Any? = null,
) {
    val project = this
    val logger = project.logger
    val projectDir = project.projectDir
    logger.l("Replace outgoing artifact jar with processed one (in configurations)")
    project.configurations.configureEach {
        val confName = name
        outgoing {
            var removed = false
            lateinit var artifactName: String
            val iterator = artifacts.iterator()
            for (artifact in iterator) {
                if (!artifact.classifier.isNullOrBlank() ||
                    artifact.extension != "jar" ||
                    artifact.type != "jar"
                ) {
                    continue
                }
                iterator.remove()
                artifactName = artifact.name
                removed = true
                artifact.logRemoved(logger, confName, projectDir)
            }
            if (removed) {
                artifact(jarProvider) {
                    // Pom and maven consumers do not like the
                    // `-all` or `-shadowed` classifiers.
                    classifier = ""
                    type = "jar"
                    extension = "jar"
                    name = artifactName
                    builtBy?.let { builtBy(it) }
                }
            }
        }
    }
}

private fun PublishArtifact.logRemoved(
    logger: Logger,
    confName: String,
    projectDir: File,
) = logger.vb {
    append("Replaced non-classified artifact from configuration '")
    append(confName).append("':\n    ")
    append('{')
    append("name=").append(name).append(", ")
    append("ext=").append(extension).append(", ")
    append("type=").append(type).append(", ")
    append("file=").append(file.toRelativeString(projectDir)).append(", ")
    append("date=").append(date).append(", ")
    append('}')
}
