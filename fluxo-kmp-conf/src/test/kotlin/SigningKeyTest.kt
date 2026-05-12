import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

internal class SigningKeyTest {

    @Test
    fun `normalizes escaped newlines`() {
        assertEquals(PGP_KEY, PGP_KEY.replace("\n", "\\n").normalizeSigningKey())
    }

    @Test
    fun `extracts armored private key from wrapped secret`() {
        val wrapped = """
            signingInMemoryKey="$PGP_KEY"
        """.trimIndent()

        assertEquals(PGP_KEY, wrapped.normalizeSigningKey())
    }

    @Test
    fun `leaves non armored value unchanged after trimming`() {
        assertEquals("not-a-key", "  not-a-key  ".normalizeSigningKey())
    }

    private companion object {
        private val PGP_KEY = """
            -----BEGIN PGP PRIVATE KEY BLOCK-----
            
            test-key-body
            -----END PGP PRIVATE KEY BLOCK-----
        """.trimIndent()
    }
}
