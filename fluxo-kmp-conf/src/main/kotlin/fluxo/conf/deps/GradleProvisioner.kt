package fluxo.conf.deps

import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.attributes.Bundling

internal object GradleProvisioner {

    fun forProject(project: Project): Provisioner {
        return forConfigurationContainer(project, project.configurations, project.dependencies)
    }

    fun forRootProjectBuildscript(project: Project): Provisioner {
        val rootProject = project.rootProject
        val buildscript = rootProject.buildscript
        return forConfigurationContainer(
            rootProject,
            buildscript.configurations,
            buildscript.dependencies,
        )
    }

    private fun forConfigurationContainer(
        project: Project,
        configurations: ConfigurationContainer,
        dependencies: DependencyHandler,
    ): Provisioner {
        val classpathConf = configurations.findByName("classpath")
        return Provisioner { withTransitives: Boolean, mavenCoords: Collection<String> ->
            try {
                val config = configurations.create(
                    "fluxo" + Request(withTransitives, mavenCoords).hashCode(),
                )
                mavenCoords.map { dependencies.create(it) }
                    .forEach {
                        // Note in classpath configuration
                        classpathConf?.dependencies?.add(it)
                        config.dependencies.add(it)
                    }
                config.setDescription(mavenCoords.toString())
                config.setTransitive(withTransitives)
                config.isCanBeConsumed = false
                config.setVisible(false)
                config.attributes {
                    attribute(
                        Bundling.BUNDLING_ATTRIBUTE,
                        project.objects.named(Bundling::class.java, Bundling.EXTERNAL),
                    )
                }
                config.resolve()
            } catch (e: Throwable) {
                var projName = project.path.substring(1).replace(':', '/')
                if (projName.isNotEmpty()) {
                    projName = "$projName/"
                }
                throw GradleException(
                    String.format(
                        "You need to add a repository containing the '%s'" +
                            " artifact in '%sbuild.gradle'.%n" +
                            "E.g.: 'repositories { mavenCentral() }'",
                        mavenCoords,
                        projName,
                    ),
                    e,
                )
            }
        }
    }

    internal enum class Policy {
        INDEPENDENT,
        ROOT_PROJECT,
        ROOT_BUILDSCRIPT,
        ;

        fun dedupingProvisioner(project: Project): DedupingProvisioner {
            return when (this) {
                ROOT_PROJECT -> DedupingProvisioner(
                    forProject(
                        project,
                    ),
                )

                ROOT_BUILDSCRIPT -> DedupingProvisioner(
                    forRootProjectBuildscript(
                        project,
                    ),
                )

                else -> throw UnsupportedOperationException(name)
            }
        }
    }

    internal class DedupingProvisioner(private val provisioner: Provisioner) : Provisioner {
        private val cache: MutableMap<Request, Set<File>> = HashMap()

        @Suppress("ReturnCount")
        override fun provisionWithTransitives(
            withTransitives: Boolean,
            mavenCoordinates: Collection<String>,
        ): Set<File> {
            val req = Request(withTransitives, mavenCoordinates)
            val result = synchronized(cache) { cache[req] }
            if (result != null) {
                return result
            }
            synchronized(cache) {
                var r = cache[req]
                if (r != null) return r
                r = provisioner.provisionWithTransitives(
                    req.withTransitives,
                    req.mavenCoords,
                )
                cache[req] = r
                return r
            }
        }

        /** A child [Provisioner] which retries cached elements only.  */
        val cachedOnly =
            Provisioner { withTransitives: Boolean, mavenCoordinates: Collection<String> ->
                val req = Request(withTransitives, mavenCoordinates)
                synchronized(cache) { cache[req] }
                    ?: throw GradleException(
                        "Add a step with ${req.mavenCoords} into the " +
                            "`spotlessPredeclare` block in the root project.",
                    )
            }
    }

    /** Models a request to the provisioner.  */
    private class Request(val withTransitives: Boolean, mavenCoords: Collection<String>) {
        val mavenCoords: List<String>

        init {
            this.mavenCoords = mavenCoords.toList()
        }

        override fun hashCode(): Int {
            return if (withTransitives) mavenCoords.hashCode() else mavenCoords.hashCode().inv()
        }

        override fun equals(other: Any?): Boolean {
            return when {
                this === other -> true
                other is Request -> {
                    val o: Request = other
                    o.withTransitives == withTransitives && o.mavenCoords == mavenCoords
                }

                else -> false
            }
        }

        override fun toString(): String {
            val coords: String = mavenCoords.toString()
            val builder = StringBuilder()
            builder.append(coords, 1, coords.length - 1) // strip off []
            if (withTransitives) {
                builder.append(" with transitives")
            } else {
                builder.append(" no transitives")
            }
            return builder.toString()
        }
    }
}
