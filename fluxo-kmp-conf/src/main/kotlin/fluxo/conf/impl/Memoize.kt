package fluxo.conf.impl

import org.gradle.api.internal.provider.AbstractMinimalProvider
import org.gradle.api.internal.provider.DefaultValueSourceProviderFactory
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.internal.provider.ValueSupplier
import org.gradle.api.provider.Provider

/** @see org.jetbrains.intellij.memoize */
internal fun <T> Provider<T>.memoize(): Provider<T> = when (this) {
    // ValueSource instances already memoize their value
    is DefaultValueSourceProviderFactory.ValueSourceProvider<*, *> -> this

    // cannot memoize something that isn't a ProviderInternal; pretty much everything *must*
    // be a ProviderInternal, as this is how internal state (dependencies, etc) are carried.
    !is ProviderInternal<T> -> error("Expected ProviderInternal, got $this")

    else -> MemoizedProvider(this)
}

/** @see org.jetbrains.intellij.MemoizedProvider */
@Suppress("HasPlatformType")
private class MemoizedProvider<T>(private val delegate: ProviderInternal<T>) :
    AbstractMinimalProvider<T>() {

    // guarantee at-most-once execution of original provider
    private val memoizedValue =
        lazy { delegate.calculateValue(ValueSupplier.ValueConsumer.IgnoreUnsafeRead) }

    // always the same type as Provider value we are memoizing
    override fun getType(): Class<T>? = delegate.type

    // the producer is from the source provider
    override fun getProducer() = delegate.producer

    override fun toString() = "memoized($delegate)"

    override fun calculateOwnValue(valueConsumer: ValueSupplier.ValueConsumer) = memoizedValue.value
}
