package fluxo.conf.impl.kotlin

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * `DISABLE_KOTLIN_DEFAULTS` must accept a flag copied from the build log in any of its shapes,
 * and must fail on a name it does not know: Gradle silently ignores a misspelled property, so a
 * parser that skipped unknown names would leave the default on with no sign.
 */
internal class KotlinDefaultTest {

    @Test
    fun `accepts names with or without -X and a value`() {
        assertEquals(
            setOf(KotlinDefault.JSR305, KotlinDefault.VALIDATE_BYTECODE, KotlinDefault.PROGRESSIVE),
            parseDisabledKotlinDefaults(
                listOf("-Xjsr305=strict", " validate-bytecode ", "", "-progressive"),
            ),
        )
    }

    @Test
    fun `fails on a typo and suggests the intended name`() {
        val e = assertFailsWith<IllegalArgumentException> {
            parseDisabledKotlinDefaults(listOf("jsr3O5"))
        }
        assertTrue("Did you mean jsr305?" in e.message.orEmpty(), e.message)
    }

    @Test
    fun `fails on an unrelated name without a suggestion`() {
        val e = assertFailsWith<IllegalArgumentException> {
            parseDisabledKotlinDefaults(listOf("werror"))
        }
        val message = e.message.orEmpty()
        assertTrue("Did you mean" !in message, message)
        assertTrue("jdk-release" in message, message)
    }
}
