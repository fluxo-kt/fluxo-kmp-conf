package fluxo.util

internal inline fun <T, reified R> Array<out T>.mapToArray(transform: (T) -> R): Array<R> =
    Array(size) { index ->
        transform(get(index))
    }

internal inline fun <T, reified R, C> C.mapToArray(transform: (T) -> R): Array<R>
    where C : List<T>, C : RandomAccess =
    Array(size) { index ->
        transform(get(index))
    }

internal inline fun <T, reified R> Collection<T>.mapToArray(transform: (T) -> R): Array<R> {
    if (this is RandomAccess && this is List) {
        val list: List<T> = this
        return Array(size) { index ->
            transform(list[index])
        }
    } else {
        val iterator = iterator()
        return Array(size) {
            transform(iterator.next())
        }
    }
}
