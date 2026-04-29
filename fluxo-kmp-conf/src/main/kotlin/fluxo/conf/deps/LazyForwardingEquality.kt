@file:Suppress("UnusedPrivateMember", "FunctionParameterNaming")

package fluxo.conf.deps

import fluxo.conf.impl.uncheckedCast
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

// Was: `: Serializable, NoLambda` (com.diffplug.spotless.NoLambda).
// Spotless's marker interface only required `toBytes(): ByteArray`, which we
// already declare here. Decoupling lets us bump Spotless across major versions
// without depending on its internal-package layout (Spotless 7 reorganises
// the internal classes — see CHANGELOG entry "POSSIBLY BREAKING").
internal abstract class LazyForwardingEquality<T : Serializable> : Serializable {
    @Volatile
    @Transient
    private var state: T? = null

    protected abstract fun calculateState(): T

    protected fun state(): T {
        val s = state
        if (s != null) return s
        synchronized(this) {
            var state = state
            if (state == null) {
                state = calculateState()
                this.state = state
            }
            return state
        }
    }

    private fun writeObject(out: ObjectOutputStream) = out.writeObject(state())

    private fun readObject(`in`: ObjectInputStream) {
        state = uncheckedCast<T>(`in`.readObject())
    }

    private fun readObjectNoData(): Unit = throw UnsupportedOperationException()

    open fun toBytes(): ByteArray = toBytes(state())

    override fun equals(other: Any?): Boolean = when {
        other == null -> false

        javaClass == other.javaClass -> (other as LazyForwardingEquality<*>).toBytes()
            .contentEquals(toBytes())

        else -> false
    }

    override fun hashCode() = toBytes().contentHashCode()

    companion object {
        private const val serialVersionUID = 1L

        fun toBytes(obj: Serializable?): ByteArray {
            val outputStream = ByteArrayOutputStream()
            ObjectOutputStream(outputStream).use { it.writeObject(obj) }
            return outputStream.toByteArray()
        }

        fun unlazy(value: Any?) {
            when (value) {
                is LazyForwardingEquality<*> -> value.state()
                is Iterable<*> -> value.forEach { unlazy(it) }
            }
        }
    }
}
