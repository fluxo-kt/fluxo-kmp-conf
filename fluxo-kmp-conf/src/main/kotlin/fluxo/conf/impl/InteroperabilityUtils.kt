package fluxo.conf.impl

import groovy.lang.Closure
import org.gradle.api.Action

internal fun <T, V : Any> Any.closureOf(action: T.() -> V?): Closure<V?> =
    KotlinClosure1(action, this, this)

internal fun <T, U, V : Any> Any.closureOf(action: (T, U) -> V?): Closure<V?> =
    KotlinClosure2(action, this, this)

private class KotlinClosure1<in T, V : Any>(
    val function: T.() -> V?,
    owner: Any? = null,
    thisObject: Any? = null,
) : Closure<V?>(owner, thisObject) {

    @Suppress("unused") // to be called dynamically by Groovy
    fun doCall(it: T): V? = it.function()
}

private class KotlinClosure2<in T, in U, V : Any>(
    val function: (T, U) -> V?,
    owner: Any? = null,
    thisObject: Any? = null,
) : Closure<V?>(owner, thisObject) {

    @Suppress("unused") // to be called dynamically by Groovy
    fun doCall(t: T, u: U): V? = function(t, u)
}


internal fun <T> actionOf(action: T.() -> Unit): Action<T> = Action { action() }
