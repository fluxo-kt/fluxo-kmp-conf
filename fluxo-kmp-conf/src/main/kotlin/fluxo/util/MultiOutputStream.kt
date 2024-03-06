package fluxo.util

import java.io.FilterOutputStream
import java.io.OutputStream

/**
 * A [FilterOutputStream] that duplicates the output to a secondary stream.
 *
 * @param mainStream the primary stream to write to.
 * @param secondaryStream the mirror stream to write to.
 */
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
