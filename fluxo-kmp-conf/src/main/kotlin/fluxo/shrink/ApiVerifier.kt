@file:Suppress("UnstableApiUsage")

package fluxo.shrink

import java.lang.reflect.Member
import java.lang.reflect.Modifier
import java.util.Collections.emptyList
import kotlin.contracts.contract
import org.gradle.api.GradleException
import org.gradle.api.internal.tasks.testing.DefaultTestFailure
import org.gradle.api.internal.tasks.testing.DefaultTestFailureDetails
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.api.tasks.testing.TestFailure

internal interface ApiVerifier {
    val continueOnFailure: Boolean

    val proc: TestResultProcessor

    val currentTestClass: String

    val currentTestMethod: String

    val currentTestFile: String?

    val currentClassTestDescriptor: TestDescriptorInternal

    val currentTestDescriptor: TestDescriptorInternal

    var verified: Boolean


    fun methodTestDescriptor(methodName: String): TestDescriptorInternal


    val ClassMemberSignature.signature: String
        get() = when (this) {
            is MethodSignature -> "$name(${parameterTypes.joinToString()}): $returnType"
            is FieldSignature -> "$name: $returnType"
        }
}

internal fun ApiVerifier.requirePublic(member: Member, lazyName: () -> String): Boolean {
    val modifiers = member.modifiers
    val isPublic = modifiers and Modifier.PUBLIC != 0
    return require(isPublic) {
        "Expected public, but it's not (modifiers=$modifiers): ${lazyName()}"
    }
}

internal inline fun <T : Any> ApiVerifier.requireNotNull(
    value: T?,
    lazyName: () -> String = { "Required value" },
): T {
    contract {
        returns() implies (value != null)
    }
    if (value == null) {
        verified = false
        throw GradleException("${lazyName()} was null.")
    } else {
        return value
    }
}

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
internal fun <@kotlin.internal.OnlyInputTypes T> ApiVerifier.requireEquals(
    expected: T,
    actual: T,
    lazyMessage: () -> Any,
): Boolean {
    return require(
        value = expected == actual,
        expected = expected,
        actual = actual,
        lazyMessage = lazyMessage,
    )
}

internal fun ApiVerifier.require(
    value: Boolean,
    expected: Any? = null,
    actual: Any? = null,
    throwable: Throwable? = null,
    lazyMessage: () -> Any,
): Boolean {
    if (!value) {
        verified = false
        val message = lazyMessage().toString()
        val th = throwable ?: IllegalStateException(message)
        val isAssertion = throwable == null || throwable is AssertionError || expected != null
        val failure = testFailure(
            message,
            th,
            isAssertionFailure = isAssertion,
            expected = expected?.toString(),
            actual = actual?.toString(),
        )
        try {
            proc.failure(currentTestDescriptor.id, failure)
        } catch (e: Throwable) {
            e.addSuppressed(th)
            throw e
        }
        if (!continueOnFailure) {
            throw GradleException(message)
        }
        return false
    }
    return true
}

private fun ApiVerifier.testFailure(
    message: String,
    th: Throwable,
    isAssertionFailure: Boolean = false,
    expected: String? = null,
    actual: String? = null,
): TestFailure {
    val className = currentTestClass

    th.stackTrace = th.stackTrace.toMutableList().also {
        val file = currentTestFile?.substringAfterLast('/')
        val newElement = StackTraceElement(className, currentTestMethod, file, -1)
        it.add(0, newElement)
    }.toTypedArray()

    return DefaultTestFailure(
        th,
        DefaultTestFailureDetails(
            message,
            className,
            th.stackTraceToString(),
            isAssertionFailure,
            false,
            expected,
            actual,
            null,
            null,
        ),
        emptyList(),
    )
}
