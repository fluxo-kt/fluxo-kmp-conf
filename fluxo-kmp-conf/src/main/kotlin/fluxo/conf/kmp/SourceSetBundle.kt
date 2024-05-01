package fluxo.conf.kmp

import fluxo.annotation.InternalFluxoApi
import fluxo.conf.impl.memoize
import getValue
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

public interface SourceSetBundle {
    public val main: KotlinSourceSet
    public val test: KotlinSourceSet
    public val moreTests: Array<KotlinSourceSet>? get() = null

    @JvmSynthetic
    public operator fun contains(other: KotlinSourceSet): Boolean {
        return main == other || test == other || moreTests?.contains(other) == true
    }

    @JvmSynthetic
    public operator fun <T> invoke(action: SourceSetBundle.() -> T): T = action()
}

internal fun SourceSetBundle(
    main: Provider<KotlinSourceSet>,
    test: Provider<KotlinSourceSet>,
    moreTests: Provider<Array<KotlinSourceSet>?>? = null,
): SourceSetBundle = SourceSetBundleLazy(main, test, moreTests)

internal fun SourceSetBundle(
    main: KotlinSourceSet,
    test: KotlinSourceSet,
    moreTests: Array<KotlinSourceSet>? = null,
): SourceSetBundle = SourceSetBundleSimple(main, test, moreTests)

private class SourceSetBundleLazy(
    main: Provider<KotlinSourceSet>,
    test: Provider<KotlinSourceSet>,
    moreTests: Provider<Array<KotlinSourceSet>?>? = null,
) : SourceSetBundleBase() {
    override val main: KotlinSourceSet by main.memoize()
    override val test: KotlinSourceSet by test.memoize()

    private val _moreTests = moreTests?.memoize()
    override val moreTests: Array<KotlinSourceSet>? get() = _moreTests?.get()
}

private class SourceSetBundleSimple(
    override val main: KotlinSourceSet,
    override val test: KotlinSourceSet,
    override val moreTests: Array<KotlinSourceSet>? = null,
) : SourceSetBundleBase()

private abstract class SourceSetBundleBase : SourceSetBundle {

    @JvmSynthetic
    override fun toString(): String {
        val name = main.name.substringBeforeLast("Main")
        return "SourceSetBundle($name)"
    }

    @JvmSynthetic
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SourceSetBundle) return false
        if (main != other.main) return false
        if (test != other.test) return false
        if (moreTests != null) {
            if (other.moreTests == null) return false
            if (!moreTests.contentEquals(other.moreTests)) return false
        } else if (other.moreTests != null) return false
        return true
    }

    @JvmSynthetic
    @InternalFluxoApi
    override fun hashCode(): Int {
        var result = main.hashCode()
        result = 31 * result + test.hashCode()
        result = 31 * result + (moreTests?.contentHashCode() ?: 0)
        return result
    }
}
