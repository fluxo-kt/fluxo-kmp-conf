package fluxo.conf.deps

import java.io.File

internal fun interface Provisioner {
    fun provisionWithTransitives(
        withTransitives: Boolean,
        vararg mavenCoordinates: String,
    ): Set<File> = provisionWithTransitives(withTransitives, mavenCoordinates.asList())

    fun provisionWithTransitives(
        withTransitives: Boolean,
        mavenCoordinates: Collection<String>,
    ): Set<File>
}
