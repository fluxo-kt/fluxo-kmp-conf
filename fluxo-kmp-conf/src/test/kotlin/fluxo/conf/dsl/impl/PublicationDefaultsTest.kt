package fluxo.conf.dsl.impl

import fluxo.conf.dsl.FluxoPublicationConfig
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class PublicationDefaultsTest {

    @Test
    fun `release defaults derive from the final configured version`() {
        val config = FluxoPublicationConfig(
            group = "io.github.fluxo-kt",
            version = "0.14.1",
            projectName = "fluxo-kmp-conf",
            publicationUrl = null,
            isSnapshot = true,
            scmTag = null,
        )

        config.finalizePublicationDefaults(
            githubProjectUrl = "https://github.com/fluxo-kt/fluxo-kmp-conf",
            fallbackScmTag = "dev",
            reproducibleArtifacts = true,
            localSnapshotSuffix = "-local",
        )

        assertFalse(config.isSnapshot)
        assertEquals("v0.14.1", config.scmTag)
        assertEquals(
            "https://github.com/fluxo-kt/fluxo-kmp-conf/tree/v0.14.1",
            config.publicationUrl,
        )
    }

    @Test
    fun `snapshot defaults derive from scm tag when reproducible artifacts are enabled`() {
        val config = FluxoPublicationConfig(
            group = "io.github.fluxo-kt",
            version = "0.14.1-SNAPSHOT",
            projectName = "fluxo-kmp-conf",
            publicationUrl = null,
            scmTag = null,
        )

        config.finalizePublicationDefaults(
            githubProjectUrl = "https://github.com/fluxo-kt/fluxo-kmp-conf",
            fallbackScmTag = "abc1234",
            reproducibleArtifacts = true,
            localSnapshotSuffix = "-local",
        )

        assertTrue(config.isSnapshot)
        assertEquals("0.14-abc1234-SNAPSHOT", config.version)
        assertEquals("abc1234", config.scmTag)
        assertEquals(
            "https://github.com/fluxo-kt/fluxo-kmp-conf/tree/abc1234",
            config.publicationUrl,
        )
    }

    @Test
    fun `local reproducible snapshot uses stable timestamp and build suffix fallback`() {
        val result = reproducibleSnapshotVersion(
            rawVersion = "0.14.1-SNAPSHOT",
            scmTag = "",
            localSnapshotSuffix = "-42",
            timestamp = Date(0),
        )

        assertEquals("0.14.1-700101000000-42-SNAPSHOT", result)
    }
}
