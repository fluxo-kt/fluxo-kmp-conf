package fluxo.shrink

import fluxo.conf.impl.SHOW_DEBUG_LOGS
import fluxo.util.mapToArray
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.net.URLClassLoader

internal fun ApiVerifier.verifyApiWithReflection(
    classLoader: URLClassLoader,
    signature: ClassSignature,
) {
    val className = signature.name
    val clazz = Class.forName(className, true, classLoader)

    // Check the kotlin class reflection.
    val kClass = clazz.kotlin
    kClass.members.forEach { member ->
        member.parameters.forEach {
            it.type
            it.isOptional
        }
        member.returnType
    }

    // TODO: Check parent classes and interfaces.

    // Check the class type.
    verifyClassType(signature.type, clazz, className)

    // Check the modifiers.
    val isClassPublic = clazz.modifiers and Modifier.PUBLIC != 0
    require(isClassPublic) {
        "Expected public class, but it's not (modifiers=${clazz.modifiers}): $className"
    }

    // Check the methods and fields.
    val declaredMethods = clazz.declaredMethods
        .groupByTo(HashMap()) { it.name }
    val declaredConstructors = clazz.declaredConstructors.asList()
    for (member in signature.memberSignatures.values) {
        val name = member.name
        val returnType = descriptorTypeFromName(member.returnType, javaName = true)
        when (member) {
            is FieldSignature -> verifyField(clazz, name, returnType, className, member)
            is MethodSignature -> when (name) {
                "<init>" -> verifyConstructor(declaredConstructors, className, member)
                else -> verifyMethod(declaredMethods, name, returnType, className, member)
            }
        }
    }
}

private fun ApiVerifier.verifyConstructor(
    declared: List<Constructor<*>>,
    className: String,
    member: MethodSignature,
) {
    val pTypes = member.descriptorParameterTypes
    val constructors = member.filteredMethodsFrom(declared, pTypes)
    require(constructors?.size == 1) {
        "Constructor was not found: $className#${member.signature}"
    }
    constructors?.let {
        requireParamsAndPublic(it[0], pTypes, className, member)
    }
}

private fun ApiVerifier.verifyMethod(
    declared: Map<String, List<Method>>,
    name: String,
    returnType: String,
    className: String,
    member: MethodSignature,
) {
    val pTypes = member.descriptorParameterTypes
    val methods = member.filteredMethodsFrom(declared[name], pTypes)
    val method = when {
        methods.isNullOrEmpty() -> null
        else -> when (methods.size) {
            1 -> methods.single()
            else -> methods.firstOrNull {
                it.returnType.name == returnType
            }
        }
    }

    // FIXME: $default methods are not visible in declaredMethods.
    if (method == null && name.endsWith("\$default")) {
        return
    }
    require(method != null) {
        "Method was not found: $className#${member.signature}"
    }
    val actualReturnType = requireNotNull(method).returnType.name
    require(actualReturnType == returnType) {
        "Method return type mismatch (actual=$actualReturnType): $className#${member.signature}"
    }
    requireParamsAndPublic(method, pTypes, className, member)
}

private fun <M> ApiVerifier.requireParamsAndPublic(
    method: M,
    pTypes: Array<String>,
    className: String,
    member: MethodSignature,
) where M : Executable, M : Member {
    val actualParams = method.parameterTypes.mapToArray { it.name }
    require(actualParams contentEquals pTypes) {
        "Parameters mismatch (actual=$actualParams): $className#${member.signature}"
    }
    requirePublic(method) { "$className#${member.signature}" }
}

private val MethodSignature.descriptorParameterTypes
    get() = parameterTypes.mapToArray {
        descriptorTypeFromName(it, javaName = true)
    }

private fun <M : Executable> MethodSignature.filteredMethodsFrom(
    declared: List<M>?,
    pTypes: Array<String>,
): List<M>? {
    if (!SHOW_DEBUG_LOGS && (declared.isNullOrEmpty() || declared.size == 1)) {
        return declared
    }
    return declared?.filter f@{ m ->
        if (m.parameterCount != pTypes.size) return@f false
        val t = m.parameterTypes
        for (element in t.indices) if (t[element].name != pTypes[element]) return@f false
        return@f true
    }
}

private fun ApiVerifier.verifyField(
    clazz: Class<*>,
    name: String,
    returnType: String,
    className: String,
    member: ClassMemberSignature,
) {
    val field = clazz.getDeclaredField(name)
    val actual = field.type.name
    require(actual == returnType) {
        "Field type mismatch (actual=$actual): $className#${member.signature}"
    }
    requirePublic(field) { "$className#${member.signature}" }
}

private fun ApiVerifier.verifyClassType(
    classType: ClassType,
    clazz: Class<*>,
    className: String,
) {
    when (classType) {
        ClassType.Interface -> require(clazz.isInterface) {
            "Expected interface, but it's not: $className"
        }

        ClassType.Annotation -> require(clazz.isAnnotation) {
            "Expected annotation, but it's not: $className"
        }

        ClassType.Enum -> require(clazz.isEnum) {
            "Expected enum, but it's not: $className"
        }

        ClassType.Class -> {
            val checks = !clazz.isInterface &&
                !clazz.isAnnotation &&
                !clazz.isEnum &&
                !clazz.isArray &&
                !clazz.isPrimitive
            require(checks) {
                "Expected class, but it's not: $className"
            }
        }
    }
}
