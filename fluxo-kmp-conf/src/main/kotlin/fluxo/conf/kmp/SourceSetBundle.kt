package fluxo.conf.kmp

import fluxo.annotation.InternalFluxoApi
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

public class SourceSetBundle(
    public val main: KotlinSourceSet,
    public val test: KotlinSourceSet,
    public val otherTests: Array<KotlinSourceSet>? = null,
) {
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
        if (otherTests != null) {
            if (other.otherTests == null) return false
            if (!otherTests.contentEquals(other.otherTests)) return false
        } else if (other.otherTests != null) return false
        return true
    }

    @JvmSynthetic
    @InternalFluxoApi
    override fun hashCode(): Int {
        var result = main.hashCode()
        result = 31 * result + test.hashCode()
        result = 31 * result + (otherTests?.contentHashCode() ?: 0)
        return result
    }

    @JvmSynthetic
    public operator fun contains(other: KotlinSourceSet): Boolean {
        return main == other || test == other || otherTests?.contains(other) == true
    }

    @JvmSynthetic
    public operator fun component0(): KotlinSourceSet = main

    @JvmSynthetic
    public operator fun component1(): KotlinSourceSet = test

    @JvmSynthetic
    public operator fun component2(): KotlinSourceSet? = otherTests?.getOrNull(0)

    @JvmSynthetic
    public operator fun component3(): KotlinSourceSet? = otherTests?.getOrNull(1)
}
