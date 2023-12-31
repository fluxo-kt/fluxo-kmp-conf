package fluxo.util

import java.util.Locale
import kotlin.math.abs
import kotlin.math.sign

/**
 * Returns a human-readable file size (e.g., "1.2 MB") from a number of bytes.
 */
internal fun readableByteSize(bytes: Int) = readableByteSize(bytes.toLong())

/**
 * Returns a human-readable file size (e.g., "1.2 MB") from a number of bytes.
 */
@Suppress("MagicNumber")
internal fun readableByteSize(bytes: Long): String {
    //                   0:        0 B
    //                  27:       27 B
    //                 999:      999 B
    //                1000:     1000 B
    //                1023:     1023 B
    //                1024:     1.0 KB
    //                1728:     1.7 KB
    //              110592:   108.0 KB
    //             7077888:     6.8 MB
    //           452984832:   432.0 MB
    //         28991029248:    27.0 GB
    //       1855425871872:     1.7 TB
    // 9223372036854775807:     8.0 EB   (Long.MAX_VALUE)

    // based on https://stackoverflow.com/a/3758880
    val absB = if (bytes == Long.MIN_VALUE) Long.MAX_VALUE else abs(bytes)
    if (absB < 1024) {
        return "$bytes B"
    }
    var value = absB
    val chars = "KMGTPE"
    var ix = 0
    for (i in 40 downTo 0 step 10) {
        if (absB > 0xfffccccccccccccL shr i) {
            value = value shr 10
            ix += 1
        }
    }
    value *= bytes.sign
    return "%.1f %cB".format(Locale.US, value / 1024.0, chars[ix])
}
