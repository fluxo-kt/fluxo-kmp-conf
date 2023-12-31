package fluxo.util

import java.io.FilterOutputStream
import java.io.OutputStream

private class MultiOutputStream(
    mainStream: OutputStream,
    private val secondaryStream: OutputStream,
) : FilterOutputStream(mainStream) {
    override fun write(b: Int) {
        super.write(b)
        secondaryStream.write(b)
    }

    override fun flush() {
        super.flush()
        secondaryStream.flush()
    }

    override fun close() {
        secondaryStream.use {
            super.close()
        }
    }
}

internal fun OutputStream.alsoOutputTo(secondaryStream: OutputStream): OutputStream =
    MultiOutputStream(this, secondaryStream)
