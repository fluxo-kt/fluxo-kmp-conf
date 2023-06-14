package fluxo.conf.deps

import com.diffplug.spotless.FileSignature
import java.io.File
import java.io.Serializable
import java.util.stream.Collectors

internal class JarState
private constructor(
    private val fileSignature: FileSignature,
) : Serializable {

    fun jarUrls() = fileSignature.files().map { it.toURI().toURL() }.toTypedArray()

    val classLoader: ClassLoader
        get() = FluxoCache.classloader(this)

    fun getClassLoader(key: Serializable) = FluxoCache.classloader(key, this)

    companion object {
        private const val serialVersionUID = 1L

        fun from(mavenCoordinate: String, provisioner: Provisioner) =
            from(listOf(mavenCoordinate), provisioner)

        fun from(mavenCoordinates: Collection<String>, provisioner: Provisioner) =
            provision(withTransitives = true, mavenCoordinates, provisioner)

        fun withoutTransitives(
            mavenCoordinates: Collection<String>,
            provisioner: Provisioner,
        ): JarState = provision(false, mavenCoordinates, provisioner)

        private fun provision(
            withTransitives: Boolean,
            mavenCoordinates: Collection<String>,
            provisioner: Provisioner,
        ): JarState {
            val jars = provisioner.provisionWithTransitives(withTransitives, mavenCoordinates)
            if (jars.isEmpty()) {
                throw NoSuchElementException(
                    "Resolved to an empty result: " + mavenCoordinates.stream().collect(
                        Collectors.joining(", "),
                    ),
                )
            }
            return preserveOrder(jars)
        }

        fun preserveOrder(jars: Collection<File>): JarState =
            JarState(FileSignature.signAsList(jars))
    }
}
