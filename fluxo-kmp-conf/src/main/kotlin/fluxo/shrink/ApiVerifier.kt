package fluxo.shrink

import fluxo.conf.impl.e
import java.lang.reflect.Member
import java.lang.reflect.Modifier
import kotlin.contracts.contract
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger

internal interface ApiVerifier {
    val continueOnFailure: Boolean

    val logger: Logger

    var verified: Boolean


    val ClassMemberSignature.signature: String
        get() = when (this) {
            is MethodSignature -> "$name(${parameterTypes.joinToString()}): $returnType"
            is FieldSignature -> "$name: $returnType"
        }
}

internal inline fun ApiVerifier.requirePublic(member: Member, lazyName: () -> String) {
    val modifiers = member.modifiers
    val isPublic = modifiers and Modifier.PUBLIC != 0
    require(isPublic) {
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

internal inline fun ApiVerifier.require(value: Boolean, lazyMessage: () -> Any) {
    if (!value) {
        verified = false
        val message = lazyMessage().toString()
        when {
            continueOnFailure -> logger.e(message)
            else -> throw GradleException(message)
        }
    }
}
