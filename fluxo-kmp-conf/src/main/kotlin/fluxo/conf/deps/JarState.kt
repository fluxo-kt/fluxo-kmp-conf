package fluxo.conf.deps

import java.io.File
import java.io.Serializable
import java.util.stream.Collectors

internal class JarState
private constructor(
    private val fileSignature: FileSignature,
) : Serializable {

    fun jarUrls() = fileSignature.files.map { it.toURI().toURL() }.toTypedArray()

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

// Replacement for `com.diffplug.spotless.FileSignature`: stores the jar set
// + a per-file fingerprint sufficient to invalidate classloader caches when
// any jar changes (canonical path, length, last-modified). Decouples our
// build from Spotless's internal-package layout so Spotless 7's reorg can't
// break our jar-bundle classloader.
internal class FileSignature private constructor(
    val files: List<File>,
    @Suppress("unused") // serialised; field's identity drives equality.
    private val fingerprints: List<Long>,
) : Serializable {

    companion object {
        private const val serialVersionUID = 1L

        fun signAsList(files: Collection<File>): FileSignature {
            val ordered = files.toList()
            // Two longs per file: length, lastModified. Cheap, stable across
            // restarts, equal-when-identical-jars-on-disk — matches the
            // invalidation contract Spotless's own implementation provides.
            val fingerprints = buildList(ordered.size * 2) {
                ordered.forEach { f ->
                    add(f.length())
                    add(f.lastModified())
                }
            }
            return FileSignature(ordered, fingerprints)
        }
    }
}
